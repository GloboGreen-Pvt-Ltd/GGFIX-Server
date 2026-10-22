import asyncio
import json
import os
import time

import numpy as np
import pytest

from app import catalog, store
from app.config import INDEX_FILE, META_FILE
from conftest import unit


def test_save_then_load_round_trip(catalog_index):
    store._vectors, store._meta = None, []
    assert store.load() == 3
    assert store.is_loaded()
    assert [m["id"] for m in store.all_meta()] == ["phone-1", "watch-1", "laptop-1"]


def test_search_ranks_by_cosine_similarity(catalog_index):
    results = store.search(unit(0.1, 0.9, 0.2, 0), top_k=2)
    assert [m["id"] for m, _ in results] == ["watch-1", "laptop-1"]
    assert results[0][1] > results[1][1]


def test_search_rejects_a_query_from_a_different_model(catalog_index):
    with pytest.raises(ValueError):
        store.search(np.ones(8, dtype="float32"), top_k=3)


def test_inconsistent_files_never_replace_a_good_index(catalog_index):
    META_FILE.write_text(json.dumps([{"id": "only-one"}]), encoding="utf-8")
    store.load()
    assert store.size() == 3  # still the previous, consistent index


def test_inconsistent_files_on_first_load_mean_empty(catalog_index):
    META_FILE.write_text(json.dumps([{"id": "only-one"}]), encoding="utf-8")
    store._vectors, store._meta, store._loaded_mtime = None, [], None
    assert store.load() == 0
    assert not store.is_loaded()


def test_refresh_picks_up_an_index_rebuilt_on_disk(catalog_index):
    vectors = np.stack([unit(1, 0, 0, 0)])
    meta = [{"id": "new"}]
    # Simulate scripts/index_all.py writing from another process.
    np.save(INDEX_FILE, vectors)
    META_FILE.write_text(json.dumps(meta), encoding="utf-8")
    later = time.time() + 5
    os.utime(INDEX_FILE, (later, later))
    os.utime(META_FILE, (later, later))
    store.refresh_if_changed()
    assert [m["id"] for m in store.all_meta()] == ["new"]


def test_save_refuses_mismatched_rows():
    with pytest.raises(ValueError):
        store.save(np.zeros((2, 4), dtype="float32"), [{"id": "a"}])


def test_save_leaves_no_temp_files(catalog_index):
    assert not list(INDEX_FILE.parent.glob("*.tmp"))


@pytest.mark.parametrize(
    "value, expected",
    [
        (["v2153", " V2168 ", None, "V2153"], ["V2153", "V2168"]),
        ("V2153/V2168, V2169;V2170", ["V2153", "V2168", "V2169", "V2170"]),
        ("SM-L315F", ["SM-L315F"]),
        (None, []),
        ("", []),
    ],
)
def test_parse_model_numbers(value, expected):
    assert catalog.parse_model_numbers(value) == expected


def test_fetch_catalog_joins_brands_and_skips_unusable_rows(monkeypatch):
    brands = [{"id": "b1", "name": "Vivo"}]
    models = {
        "content": [
            {"id": "m1", "brandId": "b1", "name": "T1", "modelNumber": "V2153/V2168", "imageUrl": "https://m/1.png", "categoryId": "c1", "colors": ["Blue"]},
            {"id": "m1", "brandId": "b1", "name": "T1 duplicate", "imageUrl": "https://m/1.png"},
            {"id": "m2", "brandId": "b1", "name": "No image"},
            {"id": "m3", "brandId": "missing", "name": "Orphan", "imageUrl": "https://m/3.png", "colors": "Red"},
        ]
    }
    categories = [{"id": "c1", "name": "Phone"}]

    async def fake_fetch_all(_client):
        return catalog._rows(brands), catalog._rows(models), catalog._rows(categories)

    monkeypatch.setattr(catalog, "_fetch_all", fake_fetch_all)
    rows = asyncio.run(catalog.fetch_catalog())
    assert [r["id"] for r in rows] == ["m1", "m3"]
    assert rows[0]["brand"] == "Vivo"
    assert rows[0]["modelNumbers"] == ["V2153", "V2168"]
    assert rows[0]["categoryName"] == "Phone"
    assert rows[1]["brand"] == "Unknown"
    assert rows[1]["colors"] == []
