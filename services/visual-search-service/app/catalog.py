"""Reads the REAL, live GGFIX device catalog from the same public
master-data API the mobile app already calls (getBrands()/getAllModels() in
GGFIX-Partner-App's src/api/masterData.js) — GET /master/brands and
GET /master/models. The catalog and its product images (media.ggfix.in) are
public reads, exactly like the app itself relies on.

This module never invents a catalog entry — every record returned here is a
literal item from that live API response, brand-joined by brandId.
"""

from __future__ import annotations

import logging
import re
from typing import Any

import httpx

from .config import GGFIX_API_BASE

log = logging.getLogger("visual_search.catalog")

_MODEL_NUMBER_SPLIT_RE = re.compile(r"[/,;]+")


async def fetch_json(client: httpx.AsyncClient, path: str) -> Any:
    resp = await client.get(f"{GGFIX_API_BASE}{path}", timeout=60.0)
    resp.raise_for_status()
    return resp.json()


def _rows(payload: Any) -> list[dict]:
    # Same tolerance as the app's unwrap(): a bare list, or a Spring Page /
    # envelope carrying the list under `content` or `data`.
    if isinstance(payload, dict):
        payload = payload.get("content", payload.get("data"))
    if not isinstance(payload, list):
        return []
    return [r for r in payload if isinstance(r, dict)]


def parse_model_numbers(value: Any) -> list[str]:
    """Mirrors the app's parseModelNumbers(): the field is a JSON array of
    codes, but older records still arrive as one slash/comma separated string
    — iterating that string directly would index every single character as
    its own "model number"."""
    if value is None:
        return []
    raw = value if isinstance(value, list) else _MODEL_NUMBER_SPLIT_RE.split(str(value))
    out: list[str] = []
    for code in raw:
        code = str(code).strip().upper() if code is not None else ""
        if code and code not in out:
            out.append(code)
    return out


async def fetch_catalog() -> list[dict]:
    """Returns one row per catalog model WITH a real image, brand name
    already joined in. Shape:
        { id, brand, name, slug, modelNumbers: [...], colors: [...], imageUrl,
          categoryId, categoryName }
    """
    async with httpx.AsyncClient(follow_redirects=True) as client:
        brands, models, categories = await _fetch_all(client)

    brand_by_id = {b["id"]: b.get("name") for b in brands if b.get("id")}
    category_by_id = {c["id"]: c.get("name") for c in categories if c.get("id")}

    rows: list[dict] = []
    seen_ids: set[str] = set()
    for m in models:
        model_id = m.get("id")
        image_url = m.get("imageUrl")
        if not model_id or not image_url or model_id in seen_ids:
            continue  # nothing to embed — skip, don't fabricate a placeholder
        seen_ids.add(model_id)
        colors = m.get("colors")
        rows.append({
            "id": model_id,
            "brand": brand_by_id.get(m.get("brandId")) or "Unknown",
            "name": m.get("name") or "",
            "slug": m.get("slug") or "",
            "modelNumbers": parse_model_numbers(m.get("modelNumber")),
            "colors": colors if isinstance(colors, list) else [],
            "imageUrl": image_url,
            "categoryId": m.get("categoryId"),
            "categoryName": category_by_id.get(m.get("categoryId")),
        })
    log.info("catalog: %d brands, %d models total, %d with a usable image", len(brands), len(models), len(rows))
    return rows


async def _fetch_all(client: httpx.AsyncClient) -> tuple[list[dict], list[dict], list[dict]]:
    brands = _rows(await fetch_json(client, "/master/brands"))
    models = _rows(await fetch_json(client, "/master/models"))
    try:
        categories = _rows(await fetch_json(client, "/master/device-categories"))
    except Exception as e:
        log.warning("could not fetch device categories (%s); category names will be blank", e)
        categories = []  # optional — category name is a display nicety, not required for matching
    return brands, models, categories
