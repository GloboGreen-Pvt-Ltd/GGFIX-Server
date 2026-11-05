"""On-disk vector store: one L2-normalised embedding row per catalog item,
plus a JSON metadata sidecar with the same row order. This is a deliberate
stand-in for pgvector — the service keeps its OWN small index instead of
touching the production database. If/when that moves into Postgres,
`search()`'s cosine-similarity top-k here is exactly what a
`ORDER BY embedding <=> :query LIMIT :k` pgvector query would replace — see
README.md's migration note.

Because embeddings never need recomputing for a search (only for a NEW or
CHANGED catalog image), the index is built by scripts/index_all.py (or the
API's own background indexer) and loaded read-only for searching. A rebuilt
index on disk is picked up by `refresh_if_changed()` without a restart.
"""

from __future__ import annotations

import hashlib
import json
import logging
import os
import threading

import numpy as np

from .config import EMBEDDING_DIM, INDEX_FILE, META_FILE

log = logging.getLogger("visual_search.store")

_lock = threading.Lock()
_vectors: np.ndarray | None = None
_meta: list[dict] = []
_loaded_mtime: float | None = None


def image_hash(image_url: str) -> str:
    """What 'skip unchanged' compares against — the catalog doesn't expose an
    updatedAt/etag, so a changed URL (the backend renames the file whenever
    the image itself changes) is the available signal that a re-embed is
    actually needed."""
    return hashlib.sha256(image_url.encode("utf-8")).hexdigest()[:16]


def _empty() -> np.ndarray:
    return np.zeros((0, EMBEDDING_DIM), dtype="float32")


def _index_mtime() -> float | None:
    try:
        return max(INDEX_FILE.stat().st_mtime, META_FILE.stat().st_mtime)
    except FileNotFoundError:
        return None


def load() -> int:
    """(Re)reads the index from disk. A missing or inconsistent index never
    replaces one that is already loaded — a half-written or mismatched pair of
    files would otherwise map vectors to the wrong catalog entries."""
    global _vectors, _meta, _loaded_mtime
    with _lock:
        mtime = _index_mtime()
        if mtime is None:
            if _vectors is None:
                _vectors, _meta = _empty(), []
            log.warning("no index found at %s — run scripts/index_all.py first", INDEX_FILE)
            return len(_meta)
        try:
            vectors = np.load(INDEX_FILE)
            meta = json.loads(META_FILE.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            log.error("could not read index at %s: %s", INDEX_FILE, e)
            if _vectors is None:
                _vectors, _meta = _empty(), []
            return len(_meta)
        if _index_mtime() != mtime:
            # Rewritten while we were reading it; the next call picks up the
            # finished files instead of a possibly mixed pair.
            log.info("index changed while loading; will retry on next request")
            if _vectors is None:
                _vectors, _meta = _empty(), []
            return len(_meta)
        if vectors.ndim != 2 or vectors.shape[0] != len(meta):
            log.error(
                "index is inconsistent: %s vectors vs %d metadata rows — keeping the previous index; rebuild with scripts/index_all.py",
                vectors.shape, len(meta),
            )
            if _vectors is None:
                _vectors, _meta = _empty(), []
            return len(_meta)
        _vectors = vectors.astype("float32", copy=False)
        _meta = meta
        _loaded_mtime = mtime
        log.info("loaded index: %d vectors", len(_meta))
        return len(_meta)


def refresh_if_changed() -> None:
    """Cheap per-request check (two stat calls) so an index rebuilt on disk by
    scripts/index_all.py is served without restarting the API."""
    mtime = _index_mtime()
    if mtime is not None and mtime != _loaded_mtime:
        load()


def save(vectors: np.ndarray, meta: list[dict]) -> None:
    global _vectors, _meta, _loaded_mtime
    if vectors.shape[0] != len(meta):
        raise ValueError(f"refusing to save an inconsistent index: {vectors.shape[0]} vectors vs {len(meta)} rows")
    # Write both files in full first, then swap them in with two back-to-back
    # renames — an interrupted save never leaves a truncated file for the API.
    index_tmp = INDEX_FILE.with_name(INDEX_FILE.name + ".tmp")
    meta_tmp = META_FILE.with_name(META_FILE.name + ".tmp")
    with open(index_tmp, "wb") as fh:
        np.save(fh, vectors)
    meta_tmp.write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    with _lock:
        os.replace(meta_tmp, META_FILE)
        os.replace(index_tmp, INDEX_FILE)
        _vectors = vectors
        _meta = meta
        _loaded_mtime = _index_mtime()
    log.info("saved index: %d vectors -> %s", len(meta), INDEX_FILE)


def is_loaded() -> bool:
    return _vectors is not None and len(_meta) > 0


def size() -> int:
    return len(_meta)


def snapshot() -> tuple[np.ndarray, list[dict]]:
    """The currently loaded vectors + metadata, consistent with each other."""
    with _lock:
        vectors = _vectors if _vectors is not None else _empty()
        return vectors, list(_meta)


def existing_hashes() -> dict[str, str]:
    """catalog id -> stored image_hash, for the indexing script's
    skip-unchanged check."""
    return {m["id"]: m.get("sourceHash") for m in _meta}


def search(query_vec: np.ndarray, top_k: int) -> list[tuple[dict, float]]:
    with _lock:  # read the pair together; load()/save() swap both under this lock
        vectors, meta = _vectors, _meta
    if vectors is None or len(meta) == 0 or top_k < 1:
        return []
    if vectors.shape[1] != query_vec.shape[-1]:
        raise ValueError(
            f"index was built with {vectors.shape[1]}-dim embeddings but the model produces {query_vec.shape[-1]} — rebuild with --force"
        )
    # Both sides are already L2-normalised, so a dot product IS cosine
    # similarity — no separate normalisation step needed per query.
    sims = vectors @ query_vec
    top_k = min(top_k, len(sims))
    idx = np.argpartition(-sims, top_k - 1)[:top_k] if top_k < len(sims) else np.arange(len(sims))
    idx = idx[np.argsort(-sims[idx])]
    return [(meta[i], float(sims[i])) for i in idx]


def all_meta() -> list[dict]:
    return list(_meta)
