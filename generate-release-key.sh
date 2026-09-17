#!/bin/sh
set -eu
umask 077

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORKSPACE_DIR=$(dirname -- "$APP_DIR")
JAVA_HOME="$WORKSPACE_DIR/toolchain/jdk8/Contents/Home"
SIGNING_DIR="$APP_DIR/release-signing"
KEYSTORE="$SIGNING_DIR/AstroFish-release.keystore"
CREDENTIALS="$SIGNING_DIR/credentials.env"

mkdir -p "$SIGNING_DIR"
if [ -f "$KEYSTORE" ] && [ -f "$CREDENTIALS" ]; then
    exit 0
fi

STORE_PASSWORD=$(openssl rand -hex 24)
KEY_PASSWORD="$STORE_PASSWORD"
KEY_ALIAS=astrofish
{
    printf 'STORE_PASSWORD=%s\n' "$STORE_PASSWORD"
    printf 'KEY_PASSWORD=%s\n' "$KEY_PASSWORD"
    printf 'KEY_ALIAS=%s\n' "$KEY_ALIAS"
} > "$CREDENTIALS"

"$JAVA_HOME/bin/keytool" -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=yxy, OU=AstroFish, O=yxy, L=Shanghai, C=CN"

