#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -e .env ] || [ -e .secrets ]; then
    echo "Local configuration already exists; refusing to overwrite keys or database credentials." >&2
    exit 1
fi
command -v openssl >/dev/null
umask 077
mkdir -p .secrets/auth
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .secrets/auth/private.pem
openssl pkey -in .secrets/auth/private.pem -pubout -out .secrets/auth/public.pem
cp .secrets/auth/public.pem .secrets/public.pem
printf 'DB_PASSWORD=%s\n' "$(openssl rand -hex 24)" > .env
printf 'LOCAL_UID=%s\nLOCAL_GID=%s\n' "$(id -u)" "$(id -g)" >> .env
# The auth container uses the local UID to read a private key that remains owner-only on the host.
chmod 755 .secrets
chmod 700 .secrets/auth
chmod 600 .secrets/auth/private.pem
chmod 644 .secrets/auth/public.pem .secrets/public.pem
echo "Generated local-only keys and database password. Never commit .env or .secrets."
