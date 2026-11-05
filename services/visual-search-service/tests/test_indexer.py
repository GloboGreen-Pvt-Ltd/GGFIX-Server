import asyncio

import numpy as np

from app import indexer, store
from conftest import row, unit


def _run(monkeypatch, catalog_rows, downloads, **kwargs):
    async def fake_catalog():
        return [dict(r) for r in catalog_rows]

    async def fake_download(_client, url, _sem):
        return downloads.get(url)

    def fake_embed(raw_list):
        # One deterministic vector per payload; b"bad" is undecodable.
        keep = [i for i, raw in enumerate(raw_list) if raw != b"bad"]
        vectors = np.stack([unit(len(raw_list[i]), 1, 0, 0) for i in keep]) if keep else np.zeros((0, 4), "float32")
        return vectors, keep

    monkeypatch.setattr(indexer, "fetch_catalog", fake_catalog)
    monkeypatch.setattr(indexer, "download_one", fake_download)
    monkeypatch.setattr(indexer, "embed_image_batch", fake_embed)
    return asyncio.run(indexer.run_index(**kwargs))


def _ids():
    return sorted(m["id"] for m in store.all_meta())


def test_first_build_indexes_every_downloadable_image(monkeypatch):
    rows = [row("a", "X", "A", image="u/a"), row("b", "X", "B", image="u/b"), row("c", "X", "C", image="u/c")]
    summary = _run(monkeypatch, rows, {"u/a": b"1", "u/b": b"22", "u/c": b"bad"})
    assert _ids() == ["a", "b"]
    assert summary["embedded"] == 2 and summary["failed"] == 1


def test_unchanged_images_are_skipped(monkeypatch, catalog_index):
    summary = _run(monkeypatch, catalog_index, {})
    assert summary["skippedUnchanged"] == 3 and summary["embedded"] == 0
    assert store.size() == 3


def test_changed_image_that_fails_to_download_keeps_previous_embedding(monkeypatch, catalog_index):
    before = {m["id"]: v for m, v in zip(catalog_index, store.snapshot()[0])}
    changed = [dict(r) for r in catalog_index]
    changed[0]["imageUrl"] = "https://media.example/a15-new.png"  # new URL, download fails
    summary = _run(monkeypatch, changed, {})
    assert _ids() == ["laptop-1", "phone-1", "watch-1"]
    assert summary["keptAfterFailure"] == 1
    vectors, meta = store.snapshot()
    kept = dict(zip([m["id"] for m in meta], vectors))
    np.testing.assert_allclose(kept["phone-1"], before["phone-1"])
    # Old hash kept, so the next run retries the new image.
    assert next(m for m in meta if m["id"] == "phone-1")["sourceHash"] == store.image_hash("https://media.example/a15.png")


def test_changed_image_that_downloads_is_re_embedded(monkeypatch, catalog_index):
    changed = [dict(r) for r in catalog_index]
    changed[1]["imageUrl"] = "https://media.example/watch-v2.png"
    summary = _run(monkeypatch, changed, {"https://media.example/watch-v2.png": b"333"})
    assert summary["embedded"] == 1 and store.size() == 3
    watch = next(m for m in store.all_meta() if m["id"] == "watch-1")
    assert watch["imageUrl"].endswith("watch-v2.png")


def test_models_deleted_from_catalog_are_pruned(monkeypatch, catalog_index):
    summary = _run(monkeypatch, catalog_index[:2], {})
    assert _ids() == ["phone-1", "watch-1"]
    assert summary["removedFromCatalog"] == 1


def test_limited_run_prunes_nothing(monkeypatch, catalog_index):
    _run(monkeypatch, catalog_index, {}, limit=1)
    assert store.size() == 3


def test_empty_catalog_response_never_wipes_the_index(monkeypatch, catalog_index):
    _run(monkeypatch, [], {})
    assert store.size() == 3
