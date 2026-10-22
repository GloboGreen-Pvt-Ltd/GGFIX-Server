import os
import sys
import tempfile
from pathlib import Path

import numpy as np
import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

# app.config reads these at import time, so they are set before any test
# module imports the app. Empty values beat a developer's local .env.
os.environ["VS_DATA_DIR"] = tempfile.mkdtemp(prefix="vs-test-")
os.environ["VS_PRELOAD_MODEL"] = "0"
os.environ["VS_AUTO_INDEX"] = "0"
os.environ["VS_REINDEX_INTERVAL_HOURS"] = "0"
os.environ["JWT_SECRET"] = ""
os.environ["VS_API_KEY"] = ""

from app import store  # noqa: E402
from app.config import INDEX_FILE, META_FILE  # noqa: E402

DIM = 4


def unit(*values: float) -> np.ndarray:
    v = np.array(values, dtype="float32")
    return v / np.linalg.norm(v)


def row(id_: str, brand: str, name: str, codes=(), image="https://media.example/x.png") -> dict:
    return {
        "id": id_,
        "brand": brand,
        "name": name,
        "slug": name.lower().replace(" ", "-"),
        "modelNumbers": list(codes),
        "colors": [],
        "imageUrl": image,
        "categoryId": None,
        "categoryName": None,
        "sourceHash": store.image_hash(image),
    }


@pytest.fixture(autouse=True)
def clean_store():
    for path in (INDEX_FILE, META_FILE):
        path.unlink(missing_ok=True)
    store._vectors, store._meta, store._loaded_mtime = None, [], None
    yield
    store._vectors, store._meta, store._loaded_mtime = None, [], None


@pytest.fixture
def catalog_index():
    """A small saved index: red phone, green watch, blue laptop."""
    meta = [
        row("phone-1", "Samsung", "Galaxy A15", codes=["SM-A155F"], image="https://media.example/a15.png"),
        row("watch-1", "Samsung", "Galaxy Watch", codes=["SM-L315F"], image="https://media.example/watch.png"),
        row("laptop-1", "LG", "Gram 14", codes=[], image="https://media.example/gram.png"),
    ]
    vectors = np.stack([unit(1, 0, 0, 0), unit(0, 1, 0, 0), unit(0, 0, 1, 0)])
    store.save(vectors, meta)
    return meta
