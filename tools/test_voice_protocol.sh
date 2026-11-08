#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac --release 17 -d "$OUT" \
  "$ROOT/tools/voice-tests/android/content/Context.java" \
  "$ROOT/tools/voice-tests/android/content/SharedPreferences.java" \
  "$ROOT/tools/voice-tests/android/telephony/TelephonyManager.java" \
  "$ROOT/tools/voice-tests/android/util/Log.java" \
  "$ROOT/tools/voice-tests/com/richbeatty/phnlnk/AudioAccessState.java" \
  "$ROOT/tools/voice-tests/com/richbeatty/phnlnk/RemoteClient.java" \
  "$ROOT/tools/voice-tests/com/richbeatty/phnlnk/PairingManager.java" \
  "$ROOT/app/src/main/java/com/richbeatty/phnlnk/VoiceSecureTransport.java" \
  "$ROOT/app/src/main/java/com/richbeatty/phnlnk/SocketDeadline.java" \
  "$ROOT/app/src/main/java/com/richbeatty/phnlnk/VoiceRelayServer.java" \
  "$ROOT/tools/voice-tests/com/richbeatty/phnlnk/VoiceProtocolSelfTest.java" \
  "$ROOT/tools/voice-tests/com/richbeatty/phnlnk/VoiceRelaySelfTest.java"
java -cp "$OUT" com.richbeatty.phnlnk.VoiceProtocolSelfTest
java -cp "$OUT" com.richbeatty.phnlnk.VoiceRelaySelfTest
