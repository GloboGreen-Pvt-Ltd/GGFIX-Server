"""Verifies the Bearer tokens auth-service issues, so the Partner App's normal
login token authorises a scan with no extra credential.

auth-service signs with jjwt: `Keys.hmacShaKeyFor(JWT_SECRET UTF-8 bytes)`
and `signWith(key)`, which picks HS256/HS384/HS512 by key length. Verifying
that needs nothing beyond hmac/hashlib, so there is no JWT dependency.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time

_DIGESTS = {"HS256": hashlib.sha256, "HS384": hashlib.sha384, "HS512": hashlib.sha512}

# Tolerated clock difference between this host and auth-service, in seconds.
_LEEWAY_SECONDS = 30


class InvalidToken(Exception):
    pass


def _b64url_decode(segment: str) -> bytes:
    return base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4))


def verify_jwt(token: str, secret: str, now: float | None = None) -> dict:
    """Returns the token's claims, or raises InvalidToken."""
    parts = token.split(".")
    if len(parts) != 3:
        raise InvalidToken("malformed token")
    header_b64, payload_b64, signature_b64 = parts
    try:
        header = json.loads(_b64url_decode(header_b64))
        claims = json.loads(_b64url_decode(payload_b64))
        signature = _b64url_decode(signature_b64)
        signing_input = f"{header_b64}.{payload_b64}".encode("ascii")
    except ValueError:  # bad base64 / JSON / non-ASCII input
        raise InvalidToken("malformed token") from None
    if not isinstance(header, dict) or not isinstance(claims, dict):
        raise InvalidToken("malformed token")

    # The algorithm comes from the header but only HMAC algorithms are
    # accepted, so "alg": "none" (or an asymmetric alg) can never pass.
    digest = _DIGESTS.get(header.get("alg"))
    if digest is None:
        raise InvalidToken("unsupported algorithm")
    expected = hmac.new(secret.encode("utf-8"), signing_input, digest).digest()
    if not hmac.compare_digest(expected, signature):
        raise InvalidToken("bad signature")

    now = time.time() if now is None else now
    try:
        exp = claims.get("exp")
        if exp is not None and now > float(exp) + _LEEWAY_SECONDS:
            raise InvalidToken("token expired")
        nbf = claims.get("nbf")
        if nbf is not None and now < float(nbf) - _LEEWAY_SECONDS:
            raise InvalidToken("token not yet valid")
    except (TypeError, ValueError):
        raise InvalidToken("malformed token") from None
    return claims


def api_key_matches(provided: str | None, expected: str) -> bool:
    if not provided or not expected:
        return False
    return hmac.compare_digest(provided.encode("utf-8"), expected.encode("utf-8"))
