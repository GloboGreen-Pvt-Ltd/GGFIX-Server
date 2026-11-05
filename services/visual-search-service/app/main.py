"""Visual-search service for GGFIX's Lens/Visual Device Scanner. Exposes
POST /visual-search/device: takes a captured/picked device photo, embeds it
with CLIP, compares against the real GGFIX catalog's own product images
(indexed by app/indexer.py from the public master-data API), and returns
ranked matches.

Runs beside the Spring services (port 8199, behind nginx at /visual-search/
on the EC2 host) — see README.md for how it is deployed and for the
pgvector migration path.
"""

from __future__ import annotations

import asyncio
import logging
import threading
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Form, Header, HTTPException, Request, UploadFile
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool
from starlette.exceptions import HTTPException as StarletteHTTPException

from . import embeddings, indexer, matching, store
from .auth import InvalidToken, api_key_matches, verify_jwt
from .config import (
    API_KEY,
    AUTO_INDEX,
    CORS_ORIGINS,
    DEFAULT_TOP_K,
    HIGH_CONFIDENCE_THRESHOLD,
    JWT_SECRET,
    MAX_CONCURRENT_EMBEDS,
    MAX_UPLOAD_BYTES,
    MEDIUM_CONFIDENCE_THRESHOLD,
    PRELOAD_MODEL,
    REINDEX_INTERVAL_HOURS,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("visual_search.api")

# Declared types that say nothing about the format: the Partner App's web
# upload path sends the browser Blob's type, which is often empty, and other
# uploaders fall back to octet-stream. The bytes are decoded for real below,
# which is the actual validation.
_GENERIC_UPLOAD_TYPES = {"", "application/octet-stream", "binary/octet-stream", "image"}
_TYPE_ALIASES = {"image/jpg": "image/jpeg", "image/pjpeg": "image/jpeg", "image/x-png": "image/png"}

# Retry delay for the initial background index build when it fails (no network
# yet, catalog API down).
_INITIAL_INDEX_RETRY_SECONDS = 300

_index_state: dict = {"running": False, "lastFinishedAt": None, "lastError": None, "lastSummary": None}

# CLIP inference is CPU-bound and torch already spreads one call across every
# core, so only a few run at once; the rest wait in the threadpool.
_embed_slots = threading.BoundedSemaphore(max(1, MAX_CONCURRENT_EMBEDS))


def _embed_bounded(raw: bytes):
    with _embed_slots:
        return embeddings.embed_image_bytes(raw)


def _warm_up_model() -> None:
    try:
        embeddings.warm_up()
    except Exception:
        # Not fatal: the first request retries the load and answers 503 if it
        # still fails.
        log.exception("CLIP model failed to load at startup")


def _run_index_once(reason: str) -> None:
    log.info("background index run starting (%s)", reason)
    _index_state["running"] = True
    try:
        _index_state["lastSummary"] = asyncio.run(indexer.run_index())
        _index_state["lastError"] = None
    except Exception as e:
        log.exception("background index run failed")
        _index_state["lastError"] = str(e) or e.__class__.__name__
    finally:
        _index_state["running"] = False
        _index_state["lastFinishedAt"] = time.time()


def _index_worker(stop: threading.Event) -> None:
    if AUTO_INDEX:
        while not store.is_loaded() and not stop.is_set():
            _run_index_once("index is empty")
            if store.is_loaded() or stop.wait(_INITIAL_INDEX_RETRY_SECONDS):
                break
    if REINDEX_INTERVAL_HOURS > 0:
        while not stop.wait(REINDEX_INTERVAL_HOURS * 3600):
            _run_index_once("scheduled refresh")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    n = store.load()
    log.info("startup: index has %d catalog images loaded", n)
    if not (JWT_SECRET or API_KEY):
        log.warning("JWT_SECRET and VS_API_KEY are both unset — /visual-search/device is OPEN (local development only)")

    stop = threading.Event()
    if PRELOAD_MODEL:
        threading.Thread(target=_warm_up_model, name="clip-warm-up", daemon=True).start()
    if AUTO_INDEX or REINDEX_INTERVAL_HOURS > 0:
        threading.Thread(target=_index_worker, args=(stop,), name="indexer", daemon=True).start()
    yield
    stop.set()


app = FastAPI(title="GGFIX Visual Search", version="1.1.0", lifespan=lifespan)

# Same policy as the Spring services' WebConfig. Credentials travel in the
# Authorization header, not cookies, so a wildcard origin is safe here.
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
    expose_headers=["X-Request-Id"],
    max_age=3600,
)


# The Partner App shows `message` from an error body (falling back to the raw
# text), so errors carry it alongside FastAPI's usual `detail`.
@app.exception_handler(StarletteHTTPException)
async def _http_error(_request: Request, exc: StarletteHTTPException):
    detail = exc.detail if isinstance(exc.detail, str) else "Request failed."
    return JSONResponse({"success": False, "message": detail, "detail": detail}, status_code=exc.status_code, headers=exc.headers)


@app.exception_handler(RequestValidationError)
async def _validation_error(_request: Request, exc: RequestValidationError):
    fields = sorted({str(err.get("loc", ["?"])[-1]) for err in exc.errors()})
    if "file" in fields:
        message = "Send the photo as a multipart field named `file`."
    else:
        message = f"Invalid value for: {', '.join(fields)}."
    return JSONResponse({"success": False, "message": message, "detail": jsonable_encoder(exc.errors())}, status_code=422)


def _index_status() -> str:
    if store.is_loaded():
        return "ready"
    return "building" if _index_state["running"] else "empty"


@app.get("/health")
def health():
    return {
        "status": "ok",
        "indexedImages": store.size(),
        "index": _index_status(),
        "modelLoaded": embeddings.is_ready(),
        "authRequired": bool(JWT_SECRET or API_KEY),
    }


# Same path and body shape as the Spring services' actuator, so the EC2 deploy
# health gate (deploy/aws/health-check.sh) probes this service like the others.
@app.get("/actuator/health")
def actuator_health():
    return {"status": "UP"}


def _confidence_label(score: float) -> str:
    if score >= HIGH_CONFIDENCE_THRESHOLD:
        return "high"
    if score >= MEDIUM_CONFIDENCE_THRESHOLD:
        return "medium"
    return "low"


def _check_auth(authorization: str | None, x_api_key: str | None, request_id: str) -> None:
    # Open only when neither credential is configured (local development). On
    # the server JWT_SECRET comes from the same .env the Spring services use,
    # so the app's normal login token is what authorises a scan.
    if not (JWT_SECRET or API_KEY):
        return
    if API_KEY and api_key_matches(x_api_key, API_KEY):
        return
    if JWT_SECRET and authorization:
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() == "bearer" and token.strip():
            try:
                verify_jwt(token.strip(), JWT_SECRET)
                return
            except InvalidToken as e:
                log.warning("[%s] rejected token: %s", request_id, e)
    raise HTTPException(status_code=401, detail="Sign in again to use the device scanner.")


def _declared_type(upload: UploadFile) -> str:
    content_type = (upload.content_type or "").split(";", 1)[0].strip().lower()
    return _TYPE_ALIASES.get(content_type, content_type)


@app.post("/visual-search/device")
async def visual_search_device(
    file: UploadFile = File(...),
    ocrText: str | None = Form(None),
    barcode: str | None = Form(None),
    limit: int = Form(DEFAULT_TOP_K),
    authorization: str | None = Header(None),
    x_api_key: str | None = Header(None),
):
    # Multipart field name is `file`, matching GGFIX-Partner-App's existing
    # upload convention (uploadMedia/uploadRequest in the app repo) — the
    # mobile client's native uploader hardcodes that field name.
    request_id = uuid.uuid4().hex[:12]
    started = time.time()
    _check_auth(authorization, x_api_key, request_id)

    declared = _declared_type(file)
    if declared not in _GENERIC_UPLOAD_TYPES and not declared.startswith("image/"):
        log.warning("[%s] rejected upload: not an image (%s)", request_id, declared)
        raise HTTPException(status_code=415, detail="Unsupported file type. Upload a photo (JPEG, PNG, WebP or HEIC).")

    # Read at most one byte past the limit instead of buffering whatever size
    # the client sent.
    raw = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(raw) > MAX_UPLOAD_BYTES:
        log.warning("[%s] rejected upload: over the %d byte limit", request_id, MAX_UPLOAD_BYTES)
        raise HTTPException(status_code=413, detail="Image too large.")
    if not raw:
        raise HTTPException(status_code=422, detail="The uploaded image is empty.")

    store.refresh_if_changed()
    if not store.is_loaded():
        log.error("[%s] no catalog index loaded (state: %s)", request_id, _index_status())
        raise HTTPException(status_code=503, detail="Visual search index has not been built yet. Try again in a few minutes.")

    log.info("[%s] request received: %d bytes, ocrText=%s, barcode=%s", request_id, len(raw), bool(ocrText), bool(barcode))

    # 1. An exact model-number hit from OCR/barcode text beats visual
    #    matching outright — it names one specific catalog entry.
    exact = matching.find_exact_model_number(ocrText, barcode)
    if exact:
        log.info("[%s] exact modelNumber match: %s %s", request_id, exact.get("brand"), exact.get("name"))
        best = {**exact, "similarity": 1.0, "matchedBy": "modelNumber"}
        return _response(request_id, started, best, [best], ocrText, barcode)

    # 2. Real visual embedding search. CLIP inference is CPU-bound, so it runs
    #    off the event loop (and a bounded number at a time) — otherwise one
    #    scan would stall every other request, /health included.
    if not embeddings.is_ready():
        try:
            await run_in_threadpool(embeddings.warm_up)
        except Exception:
            log.exception("[%s] CLIP model is not available", request_id)
            raise HTTPException(status_code=503, detail="Visual search is starting up. Try again shortly.")

    try:
        query_vec = await run_in_threadpool(_embed_bounded, raw)
    except Exception as e:
        log.warning("[%s] could not decode uploaded image: %s", request_id, e)
        raise HTTPException(status_code=422, detail="Could not read that image. Try another photo.")

    top_k = max(1, min(limit or DEFAULT_TOP_K, 20))
    try:
        raw_candidates = store.search(query_vec, top_k=max(top_k, 15))  # widen for reranking, trim after
    except ValueError as e:
        log.error("[%s] %s", request_id, e)
        raise HTTPException(status_code=503, detail="Visual search index needs to be rebuilt.")
    log.info("[%s] visual candidates: %d, top raw similarity=%.3f", request_id, len(raw_candidates), raw_candidates[0][1] if raw_candidates else -1.0)

    ranked = matching.rerank_with_text_signals(raw_candidates, ocrText, barcode)[:top_k]
    best = ranked[0] if ranked else None
    if best:
        log.info("[%s] best match: %s %s similarity=%.3f confidence=%s", request_id, best.get("brand"), best.get("name"), best["similarity"], _confidence_label(best["similarity"]))
    else:
        log.info("[%s] no candidates at all (empty index?)", request_id)

    return _response(request_id, started, best, ranked, ocrText, barcode)


def _response(request_id, started, best, matches, ocr_text, barcode):
    conf = _confidence_label(best["similarity"]) if best else "low"
    took_ms = round((time.time() - started) * 1000)
    body = {
        "success": True,
        "requestId": request_id,
        "tookMs": took_ms,
        "confidence": conf,
        "bestMatch": _public(best, conf) if best and conf != "low" else None,
        "matches": [_public(m, _confidence_label(m["similarity"])) for m in matches] if conf != "low" else [],
        "ocr": [ocr_text] if ocr_text else [],
        "barcode": barcode or None,
    }
    return JSONResponse(body, headers={"X-Request-Id": request_id})


def _public(meta: dict, confidence: str) -> dict:
    return {
        "id": meta["id"],
        "brand": meta.get("brand"),
        "model": meta.get("name"),
        "modelCode": (meta.get("modelNumbers") or [None])[0],
        "modelNumbers": meta.get("modelNumbers") or [],
        "colors": meta.get("colors") or [],
        "categoryName": meta.get("categoryName"),
        "imageUrl": meta.get("imageUrl"),
        "similarity": meta.get("similarity"),
        "confidence": confidence,
        "matchedBy": meta.get("matchedBy", "visual"),
    }
