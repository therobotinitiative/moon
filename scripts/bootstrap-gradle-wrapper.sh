#!/usr/bin/env sh
# Bootstrap gradle-wrapper.jar when it's not present.
# Usage: ./scripts/bootstrap-gradle-wrapper.sh

set -eu
ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
WRAPPER_DIR="$ROOT_DIR/gradle/wrapper"
JAR_PATH="$WRAPPER_DIR/gradle-wrapper.jar"
PROPS="$WRAPPER_DIR/gradle-wrapper.properties"

echo "[bootstrap] wrapper jar: $JAR_PATH"
if [ -f "$JAR_PATH" ]; then
  echo "[bootstrap] gradle-wrapper.jar already present"
  exit 0
fi

if [ -x "$(command -v gradle || true)" ]; then
  echo "[bootstrap] running 'gradle wrapper' to generate wrapper jar"
  (cd "$ROOT_DIR" && gradle wrapper)
  if [ -f "$JAR_PATH" ]; then
    echo "[bootstrap] generated gradle-wrapper.jar"
    exit 0
  fi
fi

if [ ! -f "$PROPS" ]; then
  echo "[bootstrap] cannot find $PROPS" >&2
  exit 1
fi

# parse distributionUrl to extract version like gradle-8.14-bin.zip -> 8.14
DIST=$(sed -n 's/^distributionUrl=\(.*\)/\1/p' "$PROPS" | tr -d '\r' | head -n1 || true)
if [ -z "$DIST" ]; then
  echo "[bootstrap] distributionUrl not found in $PROPS" >&2
  exit 1
fi

VER=$(echo "$DIST" | sed -n 's/.*gradle-\([0-9.][^/-]*\)\(-bin\|\).*\.zip/\1/p')
if [ -z "$VER" ]; then
  echo "[bootstrap] could not parse gradle version from distributionUrl: $DIST" >&2
  exit 1
fi

echo "[bootstrap] gradient version detected: $VER"

# Try to download the wrapper jar from Gradle's GitHub for the same tag
URL="https://raw.githubusercontent.com/gradle/gradle/v$VER/gradle/wrapper/gradle-wrapper.jar"
echo "[bootstrap] attempting to download wrapper jar from: $URL"

TMPFILE=$(mktemp)
if command -v curl >/dev/null 2>&1; then
  curl -fSL "$URL" -o "$TMPFILE" || { rm -f "$TMPFILE"; echo "[bootstrap] download failed" >&2; exit 1; }
elif command -v wget >/dev/null 2>&1; then
  wget -O "$TMPFILE" "$URL" || { rm -f "$TMPFILE"; echo "[bootstrap] download failed" >&2; exit 1; }
else
  echo "[bootstrap] neither curl nor wget available to download gradle-wrapper.jar" >&2
  exit 1
fi

mkdir -p "$WRAPPER_DIR"
mv "$TMPFILE" "$JAR_PATH"
echo "[bootstrap] saved gradle-wrapper.jar to $JAR_PATH"

exit 0
