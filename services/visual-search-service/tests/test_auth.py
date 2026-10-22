import base64
import hashlib
import hmac
import json
import time

import pytest

from app.auth import InvalidToken, api_key_matches, verify_jwt

SECRET = "dev-only-secret-change-me-please-32chars-minimum"


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def make_token(claims: dict, secret: str = SECRET, alg: str = "HS256") -> str:
    """Same wire format jjwt produces in auth-service's JwtService."""
    digest = {"HS256": hashlib.sha256, "HS384": hashlib.sha384, "HS512": hashlib.sha512}[alg]
    head = _b64(json.dumps({"alg": alg}).encode())
    body = _b64(json.dumps(claims).encode())
    sig = hmac.new(secret.encode(), f"{head}.{body}".encode(), digest).digest()
    return f"{head}.{body}.{_b64(sig)}"


@pytest.mark.parametrize("alg", ["HS256", "HS384", "HS512"])
def test_valid_tokens_for_every_jjwt_hmac_algorithm(alg):
    claims = {"sub": "user-1", "roles": ["SHOP_OWNER"], "exp": int(time.time()) + 3600}
    assert verify_jwt(make_token(claims, alg=alg), SECRET)["sub"] == "user-1"


def test_wrong_secret_is_rejected():
    with pytest.raises(InvalidToken):
        verify_jwt(make_token({"sub": "x"}, secret="another-secret-another-secret-123"), SECRET)


def test_tampered_payload_is_rejected():
    head, _, sig = make_token({"sub": "x"}).split(".")
    forged = _b64(json.dumps({"sub": "admin"}).encode())
    with pytest.raises(InvalidToken):
        verify_jwt(f"{head}.{forged}.{sig}", SECRET)


def test_expired_token_is_rejected():
    with pytest.raises(InvalidToken):
        verify_jwt(make_token({"sub": "x", "exp": int(time.time()) - 3600}), SECRET)


def test_alg_none_is_rejected():
    head = _b64(json.dumps({"alg": "none"}).encode())
    body = _b64(json.dumps({"sub": "x"}).encode())
    with pytest.raises(InvalidToken):
        verify_jwt(f"{head}.{body}.", SECRET)


@pytest.mark.parametrize("token", ["", "abc", "a.b", "a.b.c.d", "!!!.###.$$$", "é.é.é"])
def test_garbage_is_rejected(token):
    with pytest.raises(InvalidToken):
        verify_jwt(token, SECRET)


def test_api_key_comparison():
    assert api_key_matches("k-123", "k-123")
    assert not api_key_matches("k-124", "k-123")
    assert not api_key_matches(None, "k-123")
    assert not api_key_matches("k-123", "")
