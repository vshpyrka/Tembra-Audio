#!/usr/bin/env bash
# Captures the Auto Listen meter once per catalogue test signal (specs/003-instrument-tuner, research R16, spec FR-029):
# launches the debug app with the signal argument, waits two seconds and stores
# <platform>-compact-autoListen-<id>.png, then prints the expected reading for the review.
#
# Usage: tools/capture-tuner-states.sh --android [--out <dir>] [--settle <seconds>]
#        tools/capture-tuner-states.sh --ios "<simulator name>" [--out <dir>] [--settle <seconds>]
# The settle time defaults to 2 s on a device and 5 s on the simulator, whose first tuner frame takes longer.
# Requires a debug build installed on the device or simulator (see specs/003-instrument-tuner/quickstart.md §1).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_DIR="$REPO_ROOT/specs/003-instrument-tuner/screenshots"
PLATFORM=""
SIMULATOR=""
SETTLE_SECONDS=""

# id | expected reading (data-model.md §9)
CATALOGUE=(
  "e2-in-tune|E2, in tune, needle centred, IN TUNE lit"
  "e2-flat-20|E2, 20 cents flat, needle 36° left, Flat lit"
  "e2-sharp-20|E2, 20 cents sharp, needle 36° right, Sharp lit"
  "e2-flat-45|E2, 45 cents flat, needle 81° left, Flat lit"
  "a2-sharp-40|A2, 40 cents sharp, needle 72° right, Sharp lit"
  "d3-flat-5|D3, 5 cents flat, needle 9° left, Flat lit, pill unlit"
  "g3-in-tune|G3, in tune"
  "b3-in-tune|B3, in tune"
  "e4-in-tune|E4, in tune"
  "c2-in-tune|C2, in tune (low string, correct octave)"
  "b1-in-tune|B1, in tune (low string, correct octave)"
  "a1-in-tune|A1, in tune (lowest string, correct octave)"
  "e5-in-tune|E5, in tune (top of the range)"
  "out-of-range-2khz|resting: Listening, needle centred, nothing lit"
)

usage() {
  echo "usage: $(basename "$0") --android [--out <dir>] [--settle <seconds>] | --ios \"<simulator name>\" [--out <dir>] [--settle <seconds>]" >&2
  exit 2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --android) PLATFORM=android; shift ;;
    --ios) PLATFORM=ios; SIMULATOR="${2:-}"; [ -n "$SIMULATOR" ] || usage; shift 2 ;;
    --out) OUT_DIR="${2:-}"; [ -n "$OUT_DIR" ] || usage; shift 2 ;;
    --settle) SETTLE_SECONDS="${2:-}"; [ -n "$SETTLE_SECONDS" ] || usage; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$PLATFORM" ] || usage
if [ -z "$SETTLE_SECONDS" ]; then
  if [ "$PLATFORM" = ios ]; then SETTLE_SECONDS=5; else SETTLE_SECONDS=2; fi
fi
mkdir -p "$OUT_DIR"

capture_android() {
  local id="$1" file="$2"
  adb shell am force-stop audio.tembra.app
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null
  adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity --es tembra.tuner.testSignal "$id" >/dev/null
  sleep "$SETTLE_SECONDS"
  android screen capture --output "$file"
}

IOS_BUNDLE=""
capture_ios() {
  local id="$1" file="$2"
  if [ -z "$IOS_BUNDLE" ]; then
    local plist="$REPO_ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/Tembra Audio.app/Info.plist"
    [ -f "$plist" ] || { echo "error: build the iOS app first (quickstart §1); $plist not found" >&2; exit 1; }
    IOS_BUNDLE="$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$plist")"
  fi
  xcrun simctl terminate "$SIMULATOR" "$IOS_BUNDLE" >/dev/null 2>&1 || true
  xcrun simctl launch "$SIMULATOR" "$IOS_BUNDLE" "--tembra-tuner-test-signal=$id" >/dev/null
  sleep "$SETTLE_SECONDS"
  xcrun simctl io "$SIMULATOR" screenshot "$file" >/dev/null
}

printf '%-20s %s\n' "capture" "expected reading"
for entry in "${CATALOGUE[@]}"; do
  id="${entry%%|*}"
  expected="${entry#*|}"
  file="$OUT_DIR/$PLATFORM-compact-autoListen-$id.png"
  if [ "$PLATFORM" = android ]; then capture_android "$id" "$file"; else capture_ios "$id" "$file"; fi
  printf '%-20s %s\n' "$id" "$expected"
done
echo "wrote ${#CATALOGUE[@]} captures to $OUT_DIR"
