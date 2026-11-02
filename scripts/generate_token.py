#!/usr/bin/env python3
"""Generates a development JWT (HS256) accepted by the API: only the sub and exp claims.

Usage: JWT_SECRET=<secret> scripts/generate_token.py [--user-id <uuid>] [--expires-in 24h]
The secret needs at least 32 bytes and must match the one the API runs with. Prints only the token.
Development tool: in production, an identity provider issues the tokens.
"""
import argparse
import base64
import hmac
import hashlib
import json
import os
import re
import sys
import time
import uuid

MIN_SECRET_BYTES = 32
UNIT_SECONDS = {"s": 1, "m": 60, "h": 3600, "d": 86400}
DURATION = re.compile(r"^(\d+)([smhd])$")


def base64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def json_segment(value: dict) -> str:
    return base64url(json.dumps(value, separators=(",", ":")).encode())


def duration_seconds(text: str) -> int:
    match = DURATION.match(text)
    if not match or int(match.group(1)) == 0:
        raise argparse.ArgumentTypeError(f"invalid duration '{text}' (use a positive number plus s, m, h or d)")
    return int(match.group(1)) * UNIT_SECONDS[match.group(2)]


def user_id(text: str) -> str:
    try:
        parsed = uuid.UUID(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"invalid UUID '{text}'")
    if str(parsed) != text.lower():
        raise argparse.ArgumentTypeError(f"invalid UUID '{text}' (use the canonical form)")
    return str(parsed)


def token(secret: bytes, subject: str, expires_in: int) -> str:
    signing_input = ".".join([
        json_segment({"alg": "HS256"}),
        json_segment({"sub": subject, "exp": int(time.time()) + expires_in}),
    ])
    signature = hmac.new(secret, signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{base64url(signature)}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generates a development JWT for the API.")
    parser.add_argument("--user-id", type=user_id, default=str(uuid.uuid4()), help="sub claim (default: random UUID)")
    parser.add_argument("--expires-in", type=duration_seconds, default=duration_seconds("24h"),
                        help="validity: a number plus s, m, h or d (default: 24h)")
    args = parser.parse_args()

    secret = os.environ.get("JWT_SECRET")
    if not secret:
        print("JWT_SECRET is not set.", file=sys.stderr)
        return 1
    secret_bytes = secret.encode("utf-8")
    if len(secret_bytes) < MIN_SECRET_BYTES:
        print(f"JWT_SECRET must have at least {MIN_SECRET_BYTES} bytes (has {len(secret_bytes)}).", file=sys.stderr)
        return 1

    print(token(secret_bytes, args.user_id, args.expires_in))
    return 0


if __name__ == "__main__":
    sys.exit(main())
