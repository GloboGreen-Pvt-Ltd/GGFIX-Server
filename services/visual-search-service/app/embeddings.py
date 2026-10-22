"""Real CLIP image embeddings (open_clip, CPU). This is the actual
image-CONTENT comparison the feature needs — every embedding is computed
from decoded pixel data, never from a filename, URL string, or brand/model
text. Loaded once per process; both the indexing script and the live API
share this module so a captured photo and a catalog photo are embedded with
the exact same model/preprocessing, which is required for their vectors to
be comparable at all.

torch/open_clip are imported on first use rather than at module import, so
the API can bind its port and answer /health while the model is still
loading, and the pure-Python parts of the service stay testable without it.
"""

from __future__ import annotations

import io
import logging
import threading

import numpy as np
from PIL import Image, ImageOps

from .config import CLIP_MODEL_NAME, CLIP_PRETRAINED, EMBEDDING_DIM

log = logging.getLogger("visual_search.embeddings")

# iPhones save HEIC by default and the API accepts image/heic, but Pillow
# cannot decode it on its own — without this every HEIC upload passed the
# MIME check and then failed as "could not read that image".
try:
    from pillow_heif import register_heif_opener

    register_heif_opener()
except ImportError:  # optional dependency; JPEG/PNG/WebP still work
    log.warning("pillow-heif is not installed; HEIC/HEIF uploads cannot be decoded")

_lock = threading.Lock()
_model = None
_preprocess = None
_torch = None


def _load():
    global _model, _preprocess, _torch
    if _model is not None:
        return
    with _lock:
        if _model is not None:
            return
        log.info("loading CLIP model %s/%s (CPU) — first call only, then cached", CLIP_MODEL_NAME, CLIP_PRETRAINED)
        import open_clip
        import torch

        model, _, preprocess = open_clip.create_model_and_transforms(CLIP_MODEL_NAME, pretrained=CLIP_PRETRAINED)
        model.eval()
        _torch = torch
        _preprocess = preprocess
        _model = model  # assigned last: it is the "ready" flag checked above
        log.info("CLIP model ready")


def warm_up() -> None:
    """Loads the model ahead of the first request (called at API startup)."""
    _load()


def is_ready() -> bool:
    return _model is not None


def _decode(raw: bytes) -> Image.Image:
    img = Image.open(io.BytesIO(raw))
    # Phone cameras store rotation in EXIF instead of rotating the pixels, so a
    # portrait capture would otherwise be embedded lying on its side.
    img = ImageOps.exif_transpose(img)
    return img.convert("RGB")


def embed_image_bytes(raw: bytes) -> np.ndarray:
    """raw -> a single L2-normalised float32 embedding vector."""
    _load()
    tensor = _preprocess(_decode(raw)).unsqueeze(0)
    with _torch.no_grad():
        features = _model.encode_image(tensor)
        features = features / features.norm(dim=-1, keepdim=True)
    return features.squeeze(0).cpu().numpy().astype("float32")


def embed_image_batch(raw_list: list[bytes]) -> tuple[np.ndarray, list[int]]:
    """Batched version for indexing — same normalisation, much faster than
    calling embed_image_bytes in a loop for a large catalog. Returns the
    vectors plus the input positions they belong to (undecodable images are
    skipped, so the two can be shorter than raw_list)."""
    _load()
    tensors = []
    keep = []
    for i, raw in enumerate(raw_list):
        try:
            tensors.append(_preprocess(_decode(raw)))
            keep.append(i)
        except Exception as e:  # corrupt/undecodable image — skip, don't crash the batch
            log.warning("skipping undecodable image at batch index %d: %s", i, e)
    if not tensors:
        return np.zeros((0, EMBEDDING_DIM), dtype="float32"), []
    batch = _torch.stack(tensors)
    with _torch.no_grad():
        features = _model.encode_image(batch)
        features = features / features.norm(dim=-1, keepdim=True)
    return features.cpu().numpy().astype("float32"), keep
