#!/usr/bin/env python
"""Build (or incrementally update) the visual-search index from the REAL,
live GGFIX device catalog. The work itself lives in app/indexer.py, which the
API's background re-indexer shares.

Usage:
    python scripts/index_all.py                 # index everything new/changed
    python scripts/index_all.py --force          # re-embed the whole catalog
    python scripts/index_all.py --limit 200       # bounded run, for a smoke test
    python scripts/index_all.py --concurrency 12  # image-download concurrency

A running API picks the rebuilt index up on its next request — no restart
needed.
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import INDEX_CONCURRENCY  # noqa: E402
from app.indexer import run_index  # noqa: E402

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true", help="re-embed every catalog image, ignoring the skip-unchanged check")
    parser.add_argument("--limit", type=int, default=None, help="only process the first N catalog rows (testing)")
    parser.add_argument("--concurrency", type=int, default=INDEX_CONCURRENCY, help="parallel image downloads")
    args = parser.parse_args()
    asyncio.run(run_index(force=args.force, limit=args.limit, concurrency=args.concurrency))


if __name__ == "__main__":
    main()
