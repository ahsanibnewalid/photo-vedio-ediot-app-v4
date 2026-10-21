#!/bin/sh

set -e

GRADLE_VERSION=8.11.1
DIST="$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin"
ZIP="$HOME/.gradle/gradle-$GRADLE_VERSION-bin.zip"

if [ -x "$DIST/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  exec "$DIST/gradle-$GRADLE_VERSION/bin/gradle" "$@"
fi

mkdir -p "$DIST"
if [ ! -f "$ZIP" ]; then
  curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
fi
unzip -q "$ZIP" -d "$DIST"
exec "$DIST/gradle-$GRADLE_VERSION/bin/gradle" "$@"
