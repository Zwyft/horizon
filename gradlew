#!/usr/bin/env sh
set -e

APP_HOME="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)"
GRADLE_VERSION="8.7"
DIST_ROOT="$APP_HOME/.gradle-dist"
DIST_DIR="$DIST_ROOT/gradle-$GRADLE_VERSION"
ZIP_PATH="$DIST_ROOT/gradle-$GRADLE_VERSION-bin.zip"
DOWNLOAD_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

mkdir -p "$DIST_ROOT"

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
  if [ ! -f "$ZIP_PATH" ]; then
    curl -fsSL -o "$ZIP_PATH" "$DOWNLOAD_URL"
  fi
  rm -rf "$DIST_DIR"
  unzip -q "$ZIP_PATH" -d "$DIST_ROOT"
fi

exec "$DIST_DIR/bin/gradle" "$@"

