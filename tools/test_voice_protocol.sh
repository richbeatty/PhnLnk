#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 17 -d "$OUT" \
  "$ROOT/tools/voice-tests/android/content/Context.java" \
  "$ROOT/tools/voice-tests/com/richbeatty/phnlnk/RemoteClient.java" \
  "$ROOT/tools/voice-tests/com/richbeatty/phnlnk/PairingManager.java" \
  "$ROOT/app/src/main/java/com/richbeatty/phnlnk/VoiceSecureTransport.java" \
  "$ROOT/tools/voice-tests/com/richbeatty/phnlnk/VoiceProtocolSelfTest.java"
java -cp "$OUT" com.richbeatty.phnlnk.VoiceProtocolSelfTest
