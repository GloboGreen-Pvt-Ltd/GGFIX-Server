import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


def _load_dotenv(path: Path) -> None:
    # `.env.example` says "copy to .env", so a .env next to the service must
    # actually take effect — for the API and for scripts/index_all.py alike.
    # Real environment variables always win over the file.
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip().removeprefix("export ").strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        if key:
            os.environ.setdefault(key, value)


_load_dotenv(BASE_DIR / ".env")


# A key that is present but blank (`VS_HIGH_CONFIDENCE=`, as deploy env files
# often have) means "use the default", not "crash on float('')".
def _env_str(name: str, default: str) -> str:
    value = os.environ.get(name, "").strip()
    return value or default


def _env_float(name: str, default: float) -> float:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        return float(raw)
    except ValueError:
        raise ValueError(f"{name} must be a number, got {raw!r}") from None


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        raise ValueError(f"{name} must be an integer, got {raw!r}") from None


def _env_bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name, "").strip().lower()
    if not raw:
        return default
    return raw in {"1", "true", "yes", "on"}


# A relative VS_DATA_DIR (the `./data` in .env.example) is resolved against the
# service folder, not whatever directory the process happened to start in.
_data_dir = Path(_env_str("VS_DATA_DIR", str(BASE_DIR / "data")))
DATA_DIR = _data_dir if _data_dir.is_absolute() else (BASE_DIR / _data_dir).resolve()
DATA_DIR.mkdir(parents=True, exist_ok=True)

# The GGFIX catalog API this service reads from. Public read-only endpoints
# (no auth token needed) — see app/catalog.py. On the EC2 host this can point
# at master-data-service directly instead of going out through the edge.
GGFIX_API_BASE = _env_str("GGFIX_API_BASE", "https://api.ggfix.in").rstrip("/")

# CLIP model. ViT-B-32 on laion2b is a good accuracy/speed/size tradeoff for
# CPU inference on a catalog of a few thousand images — see app/embeddings.py.
CLIP_MODEL_NAME = _env_str("VS_CLIP_MODEL", "ViT-B-32")
CLIP_PRETRAINED = _env_str("VS_CLIP_PRETRAINED", "laion2b_s34b_b79k")
# Output width of CLIP_MODEL_NAME's image tower (512 for ViT-B-32). Only used to
# shape empty arrays before the model is loaded.
EMBEDDING_DIM = _env_int("VS_EMBEDDING_DIM", 512)

# Cosine similarity (0..1) thresholds that decide which result state the API
# returns. Centralised here — nothing below should hardcode a bare number.
HIGH_CONFIDENCE_THRESHOLD = _env_float("VS_HIGH_CONFIDENCE", 0.85)
MEDIUM_CONFIDENCE_THRESHOLD = _env_float("VS_MEDIUM_CONFIDENCE", 0.62)

# How many ranked candidates to return by default.
DEFAULT_TOP_K = _env_int("VS_TOP_K", 5)

# Text-derived boosts from OCR/barcode, added on top of the visual score. An
# exact modelNumber hit bypasses this entirely and returns with confidence
# "high" regardless of the visual score — see app/matching.py.
BRAND_TEXT_BOOST = _env_float("VS_BRAND_BOOST", 0.08)
MODEL_TEXT_PARTIAL_BOOST = _env_float("VS_MODEL_PARTIAL_BOOST", 0.12)

INDEX_FILE = DATA_DIR / "embeddings.npy"
META_FILE = DATA_DIR / "embeddings_meta.json"

MAX_UPLOAD_BYTES = _env_int("VS_MAX_UPLOAD_BYTES", 15 * 1024 * 1024)  # 15MB

# How many CLIP inferences may run at once (each already uses every core).
MAX_CONCURRENT_EMBEDS = _env_int("VS_MAX_CONCURRENT", 2)
# Load the model in the background at startup instead of on the first scan.
PRELOAD_MODEL = _env_bool("VS_PRELOAD_MODEL", True)

# Auth. Either credential is accepted; with neither configured the endpoint is
# open, which is only appropriate for local development — see app/main.py.
#   JWT_SECRET  the same secret every Spring service signs/verifies with, so
#               the app's existing `Authorization: Bearer` token just works.
#   VS_API_KEY  optional shared secret sent as X-API-Key (server-to-server).
JWT_SECRET = _env_str("JWT_SECRET", "")
API_KEY = _env_str("VS_API_KEY", "")

# Browser origins allowed to call the API (the Partner App's Expo web build).
# Comma separated; "*" matches the Spring services' allowedOriginPatterns("*").
CORS_ORIGINS = [o.strip() for o in _env_str("VS_CORS_ORIGINS", "*").split(",") if o.strip()]

# Keeps the index fresh on a server without a cron job. With VS_AUTO_INDEX on,
# an empty index is built in the background at startup; with an interval set,
# new/changed catalog images are picked up every N hours (0 = never).
AUTO_INDEX = _env_bool("VS_AUTO_INDEX", False)
REINDEX_INTERVAL_HOURS = _env_float("VS_REINDEX_INTERVAL_HOURS", 0.0)
INDEX_CONCURRENCY = _env_int("VS_INDEX_CONCURRENCY", 8)
