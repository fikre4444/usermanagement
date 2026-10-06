#!/usr/bin/env sh
# Generates the RSA key pair used to sign access tokens.
#   ./scripts/generate-jwt-keys.sh [output-dir]   (default: ./secrets)
# Then configure:
#   JWT_PRIVATE_KEY=file:<dir>/jwt-private.pem
#   JWT_PUBLIC_KEY=file:<dir>/jwt-public.pem
# or pass the PEM contents directly in those variables (e.g. from a secret manager).
set -eu
OUT="${1:-./secrets}"
mkdir -p "$OUT"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$OUT/jwt-private.pem"
openssl pkey -in "$OUT/jwt-private.pem" -pubout -out "$OUT/jwt-public.pem"
chmod 600 "$OUT/jwt-private.pem"
echo "Keys written to $OUT (keep jwt-private.pem secret)"
