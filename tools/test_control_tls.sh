#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
# Ephemeral test identities only. Never use the Android release signing key.
for name in first second; do
  keytool -genkeypair -alias host -keyalg EC -groupname secp256r1 \
    -dname CN=PhnLnk-test -validity 2 -storetype PKCS12 \
    -keystore "$OUT/$name.p12" -storepass test-identity-only \
    -keypass test-identity-only -noprompt > /dev/null 2>&1
done
javac --release 17 -d "$OUT" \
  "$ROOT/app/src/main/java/com/richbeatty/phnlnk/ControlTls.java" \
  "$ROOT/tools/control-tests/com/richbeatty/phnlnk/ControlTlsSelfTest.java"
java -cp "$OUT" com.richbeatty.phnlnk.ControlTlsSelfTest "$OUT/first.p12" "$OUT/second.p12"
