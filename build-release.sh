#!/bin/sh
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WORKSPACE_DIR=$(dirname -- "$APP_DIR")
export JAVA_HOME="$WORKSPACE_DIR/toolchain/jdk8/Contents/Home"
export GRADLE_USER_HOME="$APP_DIR/.gradle-user"
SDK_DIR="$WORKSPACE_DIR/toolchain/android-sdk"
BUILD_TOOLS="$SDK_DIR/build-tools/25.0.2"
GRADLE="$WORKSPACE_DIR/toolchain/gradle/gradle-3.5/bin/gradle"
SIGNING_DIR="$APP_DIR/release-signing"
OUTPUT_DIR="$APP_DIR/release-output"

"$APP_DIR/generate-release-key.sh"
. "$SIGNING_DIR/credentials.env"
export STORE_PASSWORD KEY_PASSWORD

"$APP_DIR/build-local.sh"
"$GRADLE" -p "$APP_DIR" --offline --no-daemon assembleRelease

mkdir -p "$OUTPUT_DIR"
UNSIGNED="$APP_DIR/app/build/outputs/apk/AstroFish-release-1.0.0.apk"
ALIGNED="$OUTPUT_DIR/AstroFish-1.0.0-aligned-unsigned.apk"
SIGNED="$OUTPUT_DIR/AstroFish-1.0.0.apk"

"$BUILD_TOOLS/zipalign" -f 4 "$UNSIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" sign \
    --ks "$SIGNING_DIR/AstroFish-release.keystore" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:STORE_PASSWORD \
    --key-pass env:KEY_PASSWORD \
    --out "$SIGNED" "$ALIGNED"
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$SIGNED"
rm -f "$ALIGNED"

"$JAVA_HOME/bin/keytool" -exportcert -rfc \
    -keystore "$SIGNING_DIR/AstroFish-release.keystore" \
    -storepass "$STORE_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -file "$OUTPUT_DIR/AstroFish-signing-certificate.pem"

