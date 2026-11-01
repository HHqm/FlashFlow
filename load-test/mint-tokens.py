"""Mint HS256 JWTs for the seeded load-test buyers.

Reads "user_id<TAB>username<TAB>address_id" lines on stdin (the output of seed-users.sql) and
writes a JSON array for k6. Claims mirror AuthService, so both the gateway and trade-service
accept the tokens. The signing secret comes from the JWT_SECRET environment variable.
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def main(output_path):
    secret = os.environ["JWT_SECRET"].encode("utf-8")
    now = int(time.time())
    ttl = int(os.environ.get("TOKEN_TTL_SECONDS", "7200"))
    header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    buyers = []
    for line in sys.stdin:
        parts = line.split()
        if len(parts) != 3:
            continue
        user_id, username, address_id = parts
        claims = {
            "iss": "flashflow-trade-service",
            "sub": username,
            "iat": now,
            "exp": now + ttl,
            "uid": int(user_id),
            "roles": ["USER"],
        }
        payload = b64url(json.dumps(claims, separators=(",", ":")).encode())
        signing_input = (header + "." + payload).encode("ascii")
        signature = b64url(hmac.new(secret, signing_input, hashlib.sha256).digest())
        buyers.append({
            "userId": int(user_id),
            "addressId": int(address_id),
            "token": header + "." + payload + "." + signature,
        })
    with open(output_path, "w", encoding="utf-8") as output:
        json.dump(buyers, output)
    print("minted %d tokens -> %s" % (len(buyers), output_path), file=sys.stderr)


if __name__ == "__main__":
    main(sys.argv[1])
