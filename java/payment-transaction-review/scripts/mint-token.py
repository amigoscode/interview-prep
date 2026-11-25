#!/usr/bin/env python3
"""Mint an HS256 token for the practice transaction-service.

usage: mint-token.py <merchant_id> [scope ...]
The secret must match app.jwt.secret in application.properties.
"""
import base64, hashlib, hmac, json, sys

SECRET = b"transaction-service-jwt-signing-secret-2026"

def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

merchant = sys.argv[1] if len(sys.argv) > 1 else "merchant-a"
scopes = sys.argv[2:] or ["transactions:read", "transactions:write"]
header = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
claims = b64(json.dumps({"iss": "practice-idp", "sub": merchant + "-api", "merchant_id": merchant,
                         "scope": " ".join(scopes)}, separators=(",", ":")).encode())
sig = b64(hmac.new(SECRET, f"{header}.{claims}".encode(), hashlib.sha256).digest())
print(f"{header}.{claims}.{sig}")
