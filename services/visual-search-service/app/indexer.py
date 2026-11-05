"""Builds (or incrementally updates) the visual-search index from the REAL,
live GGFIX device catalog — every embedded image is downloaded from the
catalog's own imageUrl (media.ggfix.in), nothing here is a placeholder.

Shared by scripts/index_all.py (manual/CLI runs) and the API's optional
background indexer (VS_AUTO_INDEX / VS_REINDEX_INTERVAL_HOURS in app/main.py).

Safe to interrupt and re-run: already-indexed, unchanged images (same
imageUrl -> same hash) are skipped, and a device whose image could not be
fetched this run keeps its previous embedding instead of dropping out of the
index. Devices that are no longer in the catalog at all are removed, so
search never returns a model that has been deleted.
"""

from __future__ import annotations

import asyncio
import logging
import time

import httpx
import numpy as np

from . import store
from .catalog import fetch_catalog
from .config import EMBEDDING_DIM, INDEX_CONCURRENCY
from .embeddings import embed_image_batch

log = logging.getLogger("visual_search.indexer")

BATCH_SIZE = 16


async def download_one(client: httpx.AsyncClient, url: str, sem: asyncio.Semaphore) -> bytes | None:
    async with sem:
        for attempt in range(2):
            try:
                resp = await client.get(url, timeout=20.0)
                resp.raise_for_status()
                return resp.content
            except Exception as e:
                if attempt == 1:
                    log.warning("download failed for %s: %s", url, e)
                    return None
                await asyncio.sleep(0.5)
    return None


async def run_index(force: bool = False, limit: int | None = None, concurrency: int = INDEX_CONCURRENCY) -> dict:
    t0 = time.time()
    log.info("fetching live catalog from GGFIX API...")
    catalog = await fetch_catalog()
    full_catalog = limit is None
    if limit:
        catalog = catalog[:limit]
    log.info("catalog rows to consider: %d", len(catalog))

    store.load()
    prev_vectors, prev_meta = store.snapshot()
    prev_by_id = {m["id"]: (m, prev_vectors[i]) for i, m in enumerate(prev_meta)}
    existing = {} if force else {m["id"]: m.get("sourceHash") for m in prev_meta}

    to_process = []
    unchanged_ids = set()
    for row in catalog:
        h = store.image_hash(row["imageUrl"])
        if existing.get(row["id"]) == h:
            unchanged_ids.add(row["id"])
            continue
        row["sourceHash"] = h
        to_process.append(row)
    log.info("to embed: %d, skipped (already indexed, unchanged): %d", len(to_process), len(unchanged_ids))

    # Rows that are not being re-embedded this run are carried forward as-is.
    # After a FULL catalog fetch, ids the catalog no longer has are dropped;
    # a --limit run only saw part of the catalog, so it prunes nothing.
    catalog_ids = {r["id"] for r in catalog}
    reprocessing_ids = {r["id"] for r in to_process}
    kept_meta: list[dict] = []
    kept_vectors: list[np.ndarray] = []
    removed = 0
    for m, vec in prev_by_id.values():
        if m["id"] in reprocessing_ids:
            continue
        if full_catalog and catalog and m["id"] not in catalog_ids:
            removed += 1
            continue
        kept_meta.append(m)
        kept_vectors.append(vec)

    new_meta: list[dict] = []
    new_vectors: list[np.ndarray] = []
    failed = 0
    carried_after_failure = 0

    def keep_previous(row: dict) -> None:
        # A changed image that could not be fetched/decoded keeps the device's
        # previous embedding rather than losing it from the index. No
        # sourceHash change, so the next run retries it.
        nonlocal carried_after_failure
        prev = prev_by_id.get(row["id"])
        if prev is not None:
            kept_meta.append(prev[0])
            kept_vectors.append(prev[1])
            carried_after_failure += 1

    sem = asyncio.Semaphore(max(1, concurrency))
    async with httpx.AsyncClient(follow_redirects=True) as client:
        for batch_start in range(0, len(to_process), BATCH_SIZE):
            batch = to_process[batch_start : batch_start + BATCH_SIZE]
            raw_list = await asyncio.gather(*[download_one(client, row["imageUrl"], sem) for row in batch])

            ok_rows = []
            for row, raw in zip(batch, raw_list):
                if raw:
                    ok_rows.append((row, raw))
                else:
                    failed += 1
                    keep_previous(row)

            if ok_rows:
                vectors, keep_idx = embed_image_batch([raw for _, raw in ok_rows])
                embedded = set(keep_idx)
                for vec_pos, orig_idx in enumerate(keep_idx):
                    new_meta.append(dict(ok_rows[orig_idx][0]))
                    new_vectors.append(vectors[vec_pos])
                for i, (row, _) in enumerate(ok_rows):
                    if i not in embedded:
                        failed += 1
                        keep_previous(row)

            done = batch_start + len(batch)
            log.info("progress: %d/%d catalog rows processed (embedded so far: %d, failed so far: %d)", done, len(to_process), len(new_meta), failed)

    final_meta = kept_meta + new_meta
    all_vectors = kept_vectors + new_vectors
    final_vectors = np.array(all_vectors, dtype="float32") if all_vectors else np.zeros((0, EMBEDDING_DIM), dtype="float32")
    if final_meta or prev_meta:
        store.save(final_vectors, final_meta)

    summary = {
        "embedded": len(new_meta),
        "carriedOver": len(kept_meta),
        "total": len(final_meta),
        "skippedUnchanged": len(unchanged_ids),
        "failed": failed,
        "keptAfterFailure": carried_after_failure,
        "removedFromCatalog": removed,
        "seconds": round(time.time() - t0, 1),
    }
    log.info(
        "DONE in %.1fs — indexed=%d (this run) + %d (carried over) = %d total; skipped-unchanged=%d; failed=%d (kept previous: %d); removed (no longer in catalog)=%d",
        summary["seconds"], summary["embedded"], summary["carriedOver"], summary["total"],
        summary["skippedUnchanged"], summary["failed"], summary["keptAfterFailure"], summary["removedFromCatalog"],
    )
    return summary
