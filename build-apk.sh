#!/usr/bin/env sh
set -eu

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java/JDK no encontrado" >&2
  exit 1
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: Gradle 9.6.0 no encontrado" >&2
  exit 1
fi

if [ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  echo "ERROR: ANDROID_HOME o ANDROID_SDK_ROOT no esta definido" >&2
  exit 1
fi

gradle --no-daemon :app:assembleDebug
APK="app/build/outputs/apk/debug/app-debug.apk"
test -f "$APK"
echo "APK: $APK"
sha256sum "$APK" 2>/dev/null || true
