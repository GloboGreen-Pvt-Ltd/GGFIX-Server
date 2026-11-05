import numpy as np

from app import matching, store
from conftest import row, unit


def _index(*rows):
    store.save(np.stack([unit(1, i + 1, 0, 0) for i in range(len(rows))]), list(rows))


def test_exact_match_tolerates_label_punctuation_and_spacing():
    _index(row("w", "Samsung", "Galaxy Watch", codes=["SM-L315F"]))
    for text in ["Model: SM-L315F", "MODEL SM L315F", "sm-l315f/DS", "Model:SM-L315F"]:
        assert matching.find_exact_model_number(text, None)["id"] == "w", text


def test_exact_match_reads_barcode_payloads():
    _index(row("w", "Samsung", "Galaxy Watch", codes=["SM-L315F"]))
    assert matching.find_exact_model_number(None, "https://example.com/p?model=SM-L315F&x=1")["id"] == "w"


def test_short_code_must_be_a_whole_token():
    _index(row("m10", "Samsung", "Galaxy M10", codes=["M10"]), row("ind", "Acme", "Thing", codes=["IND"]))
    assert matching.find_exact_model_number("SM-M105F", None) is None
    assert matching.find_exact_model_number("Made in India", None) is None
    assert matching.find_exact_model_number("Model M10 64GB", None)["id"] == "m10"


def test_words_far_apart_do_not_glue_into_a_code():
    _index(row("x", "Acme", "X", codes=["AB12CD"]))
    assert matching.find_exact_model_number("AB 64GB 12 RAM CD", None) is None


def test_longest_code_wins():
    _index(row("short", "Samsung", "A15", codes=["SM-A15"]), row("long", "Samsung", "A15 5G", codes=["SM-A156E"]))
    assert matching.find_exact_model_number("Model: SM-A156E/DS", None)["id"] == "long"


def test_ambiguous_code_falls_back_to_visual():
    _index(row("a", "Vivo", "T1", codes=["V2153"]), row("b", "Vivo", "T1 Pro", codes=["V2153"]))
    assert matching.find_exact_model_number("V2153", None) is None


def test_no_text_is_no_match():
    _index(row("a", "Vivo", "T1", codes=["V2153"]))
    assert matching.find_exact_model_number(None, None) is None
    assert matching.find_exact_model_number("  --  ", "") is None


def test_rerank_boosts_brand_tokens_including_two_letter_brands():
    lg = row("lg", "LG", "Gram")
    hp = row("hp", "HP", "Pavilion")
    ranked = matching.rerank_with_text_signals([(hp, 0.70), (lg, 0.66)], "LG Electronics", None)
    assert [r["id"] for r in ranked] == ["lg", "hp"]
    assert ranked[0]["matchedBy"] == "visual+brand"
    assert ranked[0]["rawVisualSimilarity"] == 0.66


def test_rerank_model_code_boost_and_cap():
    a = row("a", "Samsung", "A15", codes=["SM-A155F"])
    b = row("b", "Samsung", "A25", codes=["SM-A256E"])
    ranked = matching.rerank_with_text_signals([(b, 0.95), (a, 0.94)], "Samsung SM-A155F", None)
    assert ranked[0]["id"] == "a"
    assert ranked[0]["matchedBy"] == "visual+model"
    assert ranked[0]["similarity"] == 1.0  # capped


def test_rerank_without_text_keeps_visual_order():
    a, b = row("a", "X", "A"), row("b", "Y", "B")
    ranked = matching.rerank_with_text_signals([(a, 0.9), (b, 0.8)], None, None)
    assert [(r["id"], r["matchedBy"]) for r in ranked] == [("a", "visual"), ("b", "visual")]
