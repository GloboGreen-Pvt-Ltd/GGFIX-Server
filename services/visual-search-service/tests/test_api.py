import io
import time

import numpy as np
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app import embeddings, main
from conftest import unit
from test_auth import SECRET, make_token


def _fake_embed(raw: bytes) -> np.ndarray:
    # Decodes for real (so bad bytes still fail like the real model would),
    # then maps the dominant colour channel onto the test index's axes.
    img = Image.open(io.BytesIO(raw)).convert("RGB")
    r, g, b = np.asarray(img, dtype="float32").reshape(-1, 3).mean(axis=0)
    return unit(r + 1e-3, g + 1e-3, b + 1e-3, 0)


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(embeddings, "embed_image_bytes", _fake_embed)
    monkeypatch.setattr(embeddings, "is_ready", lambda: True)
    with TestClient(main.app) as c:
        yield c


def _png(color=(250, 5, 5)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (32, 32), color).save(buf, format="PNG")
    return buf.getvalue()


def _post(client, data=None, content_type="image/jpeg", payload=None, headers=None):
    payload = _png() if payload is None else payload
    return client.post(
        "/visual-search/device",
        files={"file": ("scan.jpg", payload, content_type)},
        data=data or {},
        headers=headers or {},
    )


def test_health_endpoints(client, catalog_index):
    assert client.get("/actuator/health").json() == {"status": "UP"}
    body = client.get("/health").json()
    assert body["status"] == "ok" and body["indexedImages"] == 3 and body["index"] == "ready"


def test_visual_match(client, catalog_index):
    res = _post(client)
    assert res.status_code == 200
    body = res.json()
    assert body["confidence"] == "high"
    assert body["bestMatch"]["id"] == "phone-1"
    assert body["bestMatch"]["model"] == "Galaxy A15"
    assert body["matches"][0]["matchedBy"] == "visual"
    assert res.headers["X-Request-Id"] == body["requestId"]


def test_generic_content_types_are_accepted_and_decoded(client, catalog_index):
    # The Partner App's web upload sends the Blob's type, often empty/octet-stream.
    for ctype in ["application/octet-stream", "image/jpg", "image"]:
        assert _post(client, content_type=ctype).status_code == 200, ctype


def test_non_image_type_is_415_with_a_readable_message(client, catalog_index):
    res = _post(client, content_type="video/mp4")
    assert res.status_code == 415
    assert "photo" in res.json()["message"]


def test_undecodable_bytes_are_422(client, catalog_index):
    res = _post(client, payload=b"definitely not an image")
    assert res.status_code == 422
    assert res.json()["message"] == "Could not read that image. Try another photo."


def test_empty_upload_is_422(client, catalog_index):
    assert _post(client, payload=b"").status_code == 422


def test_oversized_upload_is_413(client, catalog_index, monkeypatch):
    monkeypatch.setattr(main, "MAX_UPLOAD_BYTES", 100)
    assert _post(client).status_code == 413


def test_missing_file_field_explains_itself(client, catalog_index):
    res = client.post("/visual-search/device", data={"ocrText": "x"})
    assert res.status_code == 422
    assert "file" in res.json()["message"]


def test_no_index_is_503(client):
    res = _post(client)
    assert res.status_code == 503
    assert res.json()["message"]


def test_exact_model_number_from_ocr_wins(client, catalog_index):
    res = _post(client, data={"ocrText": "Model: SM-L315F  Made in Vietnam"})
    body = res.json()
    assert body["confidence"] == "high"
    assert body["bestMatch"]["id"] == "watch-1"
    assert body["bestMatch"]["matchedBy"] == "modelNumber"


def test_low_confidence_returns_no_matches(client, catalog_index, monkeypatch):
    monkeypatch.setattr(embeddings, "embed_image_bytes", lambda raw: unit(0, 0, 0, 1))
    body = _post(client).json()
    assert body["confidence"] == "low" and body["bestMatch"] is None and body["matches"] == []


def test_limit_trims_results(client, catalog_index, monkeypatch):
    monkeypatch.setattr(main, "MEDIUM_CONFIDENCE_THRESHOLD", -1.0)
    body = _post(client, data={"limit": "2"}).json()
    assert len(body["matches"]) == 2


def test_browser_preflight_is_allowed(client):
    res = client.options(
        "/visual-search/device",
        headers={
            "Origin": "http://localhost:19006",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "authorization",
        },
    )
    assert res.status_code == 200
    assert res.headers["access-control-allow-origin"] in ("*", "http://localhost:19006")


def test_unknown_route_uses_the_same_error_shape(client):
    res = client.get("/nope")
    assert res.status_code == 404 and res.json()["success"] is False


class TestAuth:
    @pytest.fixture(autouse=True)
    def require_auth(self, monkeypatch):
        monkeypatch.setattr(main, "JWT_SECRET", SECRET)
        monkeypatch.setattr(main, "API_KEY", "server-key")

    def test_no_credentials_is_401(self, client, catalog_index):
        res = _post(client)
        assert res.status_code == 401
        assert res.json()["message"]

    def test_app_bearer_token_is_accepted(self, client, catalog_index):
        token = make_token({"sub": "owner-1", "exp": int(time.time()) + 600}, alg="HS512")
        assert _post(client, headers={"Authorization": f"Bearer {token}"}).status_code == 200

    def test_bad_bearer_token_is_401(self, client, catalog_index):
        token = make_token({"sub": "owner-1"}, secret="not-the-server-secret-not-the-server")
        assert _post(client, headers={"Authorization": f"Bearer {token}"}).status_code == 401

    def test_api_key_is_accepted(self, client, catalog_index):
        assert _post(client, headers={"X-API-Key": "server-key"}).status_code == 200

    def test_health_stays_public(self, client, catalog_index):
        assert client.get("/actuator/health").status_code == 200
