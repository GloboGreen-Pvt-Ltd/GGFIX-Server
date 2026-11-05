"""Multi-signal fusion: visual embedding similarity + OCR text + barcode,
combined the way the product spec asks — an exact model-number hit (read by
OCR, or encoded in a scanned barcode/QR) is trusted over the visual match
outright, since it names one specific catalog entry; short of that, OCR/
barcode text can only ever REORDER/boost the real visual candidates the
embedding search already found, never invent a candidate that visual search
didn't return.

Nothing here is brand-specific — every candidate and every catalog item
compared against came from the same live GGFIX catalog fetched in
app/catalog.py, so it works for whatever brand a device actually is.
"""

from __future__ import annotations

import re

from . import store
from .config import BRAND_TEXT_BOOST, MODEL_TEXT_PARTIAL_BOOST

# A model code printed on a label rarely comes back from OCR/a barcode
# payload with punctuation matching the catalog's own spelling exactly
# ("SM-L315F" vs "SML315F" vs "SM L315F"), so codes are compared with all
# punctuation stripped — the way phone numbers are compared digits-only.
_NON_ALNUM_RE = re.compile(r"[^A-Z0-9]")
# Where one piece of label/barcode text ends and the next begins. Catalog codes
# never contain these (app/catalog.py splits stored codes on / , ;).
_TOKEN_SPLIT_RE = re.compile(r"[\s/\\|;,:=?&#]+")

_MIN_CODE_LEN = 3
# A code at least this long may also match INSIDE a longer token
# ("SML315FDS" from a smudged "SM-L315F/DS"); shorter codes must match a whole
# token, or "M10" would hit every "M105F" and "IND" every "MADE IN INDIA".
_MIN_EMBEDDED_CODE_LEN = 6
# "SM L315F" or a code OCR'd across a line break spans adjacent tokens, so up
# to this many neighbours are also tried joined — never arbitrary words from
# opposite ends of the label.
_MAX_JOINED_TOKENS = 3


def _normalize(text: str | None) -> str:
    if not text:
        return ""
    return _NON_ALNUM_RE.sub("", text.upper())


class TextSignals:
    """Normalised OCR + barcode text, prepared once per request."""

    def __init__(self, *texts: str | None):
        candidates: set[str] = set()
        for text in texts:
            if not text:
                continue
            tokens = [t for t in (_normalize(p) for p in _TOKEN_SPLIT_RE.split(text.upper())) if t]
            for i in range(len(tokens)):
                joined = ""
                for token in tokens[i : i + _MAX_JOINED_TOKENS]:
                    joined += token
                    candidates.add(joined)
        self.candidates = candidates
        # One string for the substring case, so a catalog-wide scan is a C-level
        # `in` per code instead of a Python loop over every candidate.
        self._haystack = "|" + "|".join(candidates) + "|"

    def __bool__(self) -> bool:
        return bool(self.candidates)

    def has_code(self, code: str | None) -> bool:
        norm = _normalize(code)
        if len(norm) < _MIN_CODE_LEN:
            return False
        if norm in self.candidates:
            return True
        return len(norm) >= _MIN_EMBEDDED_CODE_LEN and norm in self._haystack

    def has_brand(self, brand: str | None) -> bool:
        norm = _normalize(brand)
        # Whole-token only, so two-letter brands (LG, HP) work without matching
        # inside other words.
        return bool(norm) and norm in self.candidates


def _longest_code_hit(meta: dict, signals: TextSignals) -> int:
    return max((len(_normalize(c)) for c in meta.get("modelNumbers") or [] if signals.has_code(c)), default=0)


def find_exact_model_number(ocr_text: str | None, barcode: str | None) -> dict | None:
    """Scans the FULL catalog (not just the visual top-k) for a literal
    modelNumber match against the OCR text or a scanned barcode/QR payload —
    a label can be perfectly readable even when the photo itself is a poor
    visual match (bad angle, glare), so this must not be limited to whatever
    the embedding search already ranked highly.

    The longest matching code wins. If that code names more than one catalog
    entry the text is ambiguous, so this returns None and leaves the decision
    to the visual search (where the code still boosts every entry it names).
    """
    signals = TextSignals(ocr_text, barcode)
    if not signals:
        return None
    best_len = 0
    best: list[dict] = []
    for meta in store.all_meta():
        hit = _longest_code_hit(meta, signals)
        if hit == 0 or hit < best_len:
            continue
        if hit > best_len:
            best_len, best = hit, []
        best.append(meta)
    if len({m["id"] for m in best}) == 1:
        return best[0]
    return None


def rerank_with_text_signals(
    candidates: list[tuple[dict, float]],
    ocr_text: str | None,
    barcode: str | None,
) -> list[dict]:
    signals = TextSignals(ocr_text, barcode)
    scored = []
    for meta, sim in candidates:
        boost = 0.0
        matched_by = "visual"
        if signals and signals.has_brand(meta.get("brand")):
            boost += BRAND_TEXT_BOOST
            matched_by = "visual+brand"
        if signals and _longest_code_hit(meta, signals):
            boost += MODEL_TEXT_PARTIAL_BOOST
            matched_by = "visual+model"
        result = {**meta, "similarity": round(min(1.0, sim + boost), 4), "rawVisualSimilarity": round(sim, 4), "matchedBy": matched_by}
        scored.append((sim + boost, result))
    # Ordered by the UNCAPPED score: two strong visual matches can both reach
    # the 1.0 cap, and the text signal must still decide between them.
    scored.sort(key=lambda pair: pair[0], reverse=True)
    return [result for _, result in scored]
