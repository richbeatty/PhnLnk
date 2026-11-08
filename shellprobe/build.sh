#!/usr/bin/env bash
set -euo pipefail

# Compile only the shell-UID probe as a standalone classes.dex archive.
# No privileged code is copied from other projects.
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then echo "ANDROID_HOME is required" >&2; exit 1; fi
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
if [[ ! -f "$ANDROID_JAR" ]]; then echo "Android platform 35 is required" >&2; exit 1; fi
D8=""
for tool in "$SDK"/build-tools/*/d8; do
  if [[ -x "$tool" ]]; then D8="$tool"; fi
done
if [[ ! -x "$D8" ]]; then echo "Android d8 tool is required" >&2; exit 1; fi
OUT="$ROOT/build"
mkdir -p "$OUT/classes"
find "$ROOT/src" -type f -name '*.java' | sort > "$OUT/sources.txt"
javac --release 17 -Xlint:all -cp "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -type f -name '*.class' | sort > "$OUT/classes.txt"
"$D8" --lib "$ANDROID_JAR" --min-api 30 --output "$OUT/phnlnk-probe.jar" @"$OUT/classes.txt"
mkdir -p "$ROOT/../app/src/main/assets"
cp "$OUT/phnlnk-probe.jar" "$ROOT/../app/src/main/assets/phnlnk-probe.jar"
echo "Built standalone shell probe asset."
