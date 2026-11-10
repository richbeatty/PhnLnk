#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Destructive fixtures are restricted to a fresh CI emulator/debug installation.
QEMU="$(adb shell getprop ro.kernel.qemu | tr -d '\r')$(adb shell getprop ro.boot.qemu | tr -d '\r')"
case "$QEMU" in *1*) ;; *) echo 'Refusing runtime fixtures on a physical device' >&2; exit 1;; esac
adb install --no-streaming -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb install --no-streaming -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
OUT="$(mktemp)"
trap 'rm -f "$OUT"' EXIT
adb shell am instrument -w -e isolated_fixture 1 \
  com.richbeatty.phnlnk.test/com.richbeatty.phnlnk.TlsIntegrationInstrumentation > "$OUT"
cat "$OUT"
grep -q 'PHNLNK_ANDROID_TLS_PASS cases=7' "$OUT"
if grep -q 'PHNLNK_ANDROID_TLS_FAIL' "$OUT"; then exit 1; fi
