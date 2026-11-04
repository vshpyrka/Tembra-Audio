#!/usr/bin/env bash
# Captures the tuner's noise gate control on one target in one orientation (specs/006-tuner-noise-gate,
# contracts/tuner-screen.md §8, spec FR-023 and FR-024). It launches the debug app three times with a test signal and
# a gate for that launch, and stores <target>-<orientation>-off|below|above.png:
#
#   off    e2-in-tune, gate off   E2 shown, the fill in its active colour, the handle at "Off"
#   below  e2-55db, gate -50      "Listening", the fill muted and short of the handle
#   above  e2-45db, gate -50      E2 shown, the fill active and past the handle
#
# On Android it turns the screen itself and, after each launch, reads the layout with `uiautomator dump` and fails
# unless the element described "Noise gate" lies inside the display and touches neither the element described
# "Tuner meter" nor a selector (a node whose text is "Guitar", "Cello", "Auto Listen" or "By Ear"). The dump holds the
# adjustable element only; the label drawn over it is hidden from accessibility and is checked by the layout tests
# and by eye on the captures. On iOS the simulator is turned beforehand (quickstart §4) and a sideways capture that
# the simulator stored lying on its side is turned upright with `sips`.
#
# Usage: tools/capture-noise-gate.sh --android <serial> --orientation upright|sideways [options]
#        tools/capture-noise-gate.sh --ios "<simulator name or id>" --orientation upright|sideways [options]
# Options: --display <id>     the display to capture when the target has several: the id `dumpsys SurfaceFlinger
#                             --display-id` prints (Android), or the UUID `simctl io <device> enumerate` prints (iOS)
#          --name <target>    the first part of the file names (default: the device's model, or the simulator's name)
#          --label <text>     replaces the orientation in the file names, for a posture such as "tabletop" or "book"
#          --out <dir>        where the captures go (default: specs/006-tuner-noise-gate/screenshots)
#          --settle <seconds> the wait between a launch and its capture (default 3 on a device, 5 on a simulator)
#          --no-turn          (iOS) never turn a sideways capture, whatever its shape
# Requires a debug build installed on the target (specs/006-tuner-noise-gate/quickstart.md §2).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_DIR="$REPO_ROOT/specs/006-tuner-noise-gate/screenshots"
PACKAGE="com.tembraaudio"
ACTIVITY="$PACKAGE/audio.tembra.android.MainActivity"
PLATFORM=""
SERIAL=""
DISPLAY_ID=""
SIMULATOR=""
ORIENTATION=""
NAME=""
LABEL=""
SETTLE_SECONDS=""
TURN_IOS=1

# name | test signal | gate | what the capture must show
LAUNCHES=(
  "off|e2-in-tune|off|E2 shown, fill active, handle at Off"
  "below|e2-55db|-50|Listening, fill muted and short of the handle"
  "above|e2-45db|-50|E2 shown, fill active and past the handle"
)

usage() {
  sed -n '16,26p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --android) PLATFORM=android; SERIAL="${2:-}"; [ -n "$SERIAL" ] || usage; shift 2 ;;
    --display) DISPLAY_ID="${2:-}"; [ -n "$DISPLAY_ID" ] || usage; shift 2 ;;
    --ios) PLATFORM=ios; SIMULATOR="${2:-}"; [ -n "$SIMULATOR" ] || usage; shift 2 ;;
    --orientation) ORIENTATION="${2:-}"; shift 2 ;;
    --name) NAME="${2:-}"; [ -n "$NAME" ] || usage; shift 2 ;;
    --label) LABEL="${2:-}"; [ -n "$LABEL" ] || usage; shift 2 ;;
    --out) OUT_DIR="${2:-}"; [ -n "$OUT_DIR" ] || usage; shift 2 ;;
    --settle) SETTLE_SECONDS="${2:-}"; [ -n "$SETTLE_SECONDS" ] || usage; shift 2 ;;
    --no-turn) TURN_IOS=0; shift ;;
    *) usage ;;
  esac
done
[ -n "$PLATFORM" ] || usage
case "$ORIENTATION" in upright|sideways) ;; *) usage ;; esac
if [ -z "$SETTLE_SECONDS" ]; then
  if [ "$PLATFORM" = ios ]; then SETTLE_SECONDS=5; else SETTLE_SECONDS=3; fi
fi
mkdir -p "$OUT_DIR"

slug() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//'; }

# --- Android ---------------------------------------------------------------------------------------------------------

adb_target() { adb -s "$SERIAL" "$@"; }

PREVIOUS_AUTO=""
PREVIOUS_ROTATION=""
restore_rotation() {
  [ -n "$PREVIOUS_ROTATION" ] && adb_target shell settings put system user_rotation "$PREVIOUS_ROTATION" >/dev/null 2>&1 || true
  [ -n "$PREVIOUS_AUTO" ] && adb_target shell settings put system accelerometer_rotation "$PREVIOUS_AUTO" >/dev/null 2>&1 || true
}

turn_android() {
  PREVIOUS_AUTO="$(adb_target shell settings get system accelerometer_rotation | tr -d '\r')"
  PREVIOUS_ROTATION="$(adb_target shell settings get system user_rotation | tr -d '\r')"
  trap restore_rotation EXIT
  adb_target shell settings put system accelerometer_rotation 0
  if [ "$ORIENTATION" = sideways ]; then
    adb_target shell settings put system user_rotation 1
  else
    adb_target shell settings put system user_rotation 0
  fi
  sleep 1
}

# Reads the layout dump and prints one line; fails when the control is off the display or touches the gauge or a selector.
check_android_layout() {
  local dump
  dump="$(mktemp -t noise-gate-dump)"
  local attempt
  for attempt in 1 2 3; do
    if adb_target shell uiautomator dump /sdcard/tembra-noise-gate.xml >/dev/null 2>&1 &&
      adb_target exec-out cat /sdcard/tembra-noise-gate.xml > "$dump" && [ -s "$dump" ]; then
      break
    fi
    sleep 1
    : > "$dump"
  done
  [ -s "$dump" ] || { echo "error: uiautomator gave no layout dump after three tries" >&2; rm -f "$dump"; return 1; }
  python3 - "$dump" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

def rect(node):
    left, top, right, bottom = (int(v) for v in re.findall(r"-?\d+", node.get("bounds", "")))
    return left, top, right, bottom

def inside(inner, outer):
    return inner[0] >= outer[0] and inner[1] >= outer[1] and inner[2] <= outer[2] and inner[3] <= outer[3]

def intersects(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]

nodes = list(ET.parse(sys.argv[1]).getroot().iter("node"))
if not nodes:
    sys.exit("layout check failed: the dump is empty")
display = rect(nodes[0])
gates = [rect(n) for n in nodes if n.get("content-desc") == "Noise gate"]
meters = [rect(n) for n in nodes if n.get("content-desc") == "Tuner meter"]
selectors = [(n.get("text"), rect(n)) for n in nodes if n.get("text") in ("Guitar", "Cello", "Auto Listen", "By Ear")]
problems = []
if len(gates) != 1:
    problems.append(f"{len(gates)} elements described \"Noise gate\" (want 1)")
if len(meters) != 1:
    problems.append(f"{len(meters)} elements described \"Tuner meter\" (want 1)")
if len(selectors) != 4:
    problems.append(f"{len(selectors)} selector labels (want 4)")
if gates:
    gate = gates[0]
    if gate[2] <= gate[0] or gate[3] <= gate[1]:
        problems.append(f"the control has no area: {gate}")
    if not inside(gate, display):
        problems.append(f"the control {gate} leaves the display {display}")
    for meter in meters:
        if intersects(gate, meter):
            problems.append(f"the control {gate} touches the gauge {meter}")
    for text, selector in selectors:
        if intersects(gate, selector):
            problems.append(f"the control {gate} touches the selector \"{text}\" {selector}")
if not gates and not meters and len(selectors) == 4:
    problems.append("the tuner is showing By Ear, which has no noise gate: choose Auto Listen on the target (the mode is remembered) and run again")
if problems:
    sys.exit("layout check failed: " + "; ".join(problems))
gate, meter = gates[0], meters[0]
print(f"layout ok: control {gate} inside display {display}, clear of the gauge {meter} and {len(selectors)} selectors")
PY
  local status=$?
  rm -f "$dump"
  return $status
}

capture_android() {
  local signal="$1" gate="$2" file="$3"
  adb_target shell am force-stop "$PACKAGE"
  adb_target shell input keyevent KEYCODE_WAKEUP >/dev/null
  adb_target shell am start -n "$ACTIVITY" --es tembra.tuner.testSignal "$signal" --es tembra.tuner.noiseGate "$gate" >/dev/null
  sleep "$SETTLE_SECONDS"
  if [ -n "$DISPLAY_ID" ]; then
    adb_target exec-out screencap -p -d "$DISPLAY_ID" > "$file"
  else
    adb_target exec-out screencap -p > "$file"
  fi
  check_android_layout
}

# --- iOS -------------------------------------------------------------------------------------------------------------

IOS_BUNDLE=""
find_ios_bundle() {
  local plist
  for plist in \
    "$REPO_ROOT/app/iosApp/build/Build/Products/Debug-iphonesimulator/Tembra Audio.app/Info.plist" \
    "$REPO_ROOT/app/iosApp/build-duo/Build/Products/Debug-iphonesimulator/Tembra Audio.app/Info.plist"; do
    if [ -f "$plist" ]; then
      IOS_BUNDLE="$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$plist")"
      return
    fi
  done
  echo "error: build the iOS app first (quickstart §2); no Tembra Audio.app found under app/iosApp/build*" >&2
  exit 1
}

capture_ios() {
  local signal="$1" gate="$2" file="$3"
  xcrun simctl terminate "$SIMULATOR" "$IOS_BUNDLE" >/dev/null 2>&1 || true
  xcrun simctl launch "$SIMULATOR" "$IOS_BUNDLE" "--tembra-tuner-test-signal=$signal" "--tembra-tuner-noise-gate=$gate" >/dev/null
  sleep "$SETTLE_SECONDS"
  if [ -n "$DISPLAY_ID" ]; then
    xcrun simctl io "$SIMULATOR" screenshot "--display=$DISPLAY_ID" "$file" >/dev/null 2>&1
  else
    xcrun simctl io "$SIMULATOR" screenshot "$file" >/dev/null 2>&1
  fi
  # Some simulator versions store the screen as the device holds it upright, with a sideways screen lying on its
  # side inside it; others already deliver it as it is seen. A sideways capture that is taller than wide is turned.
  if [ "$ORIENTATION" = sideways ] && [ "$TURN_IOS" = 1 ]; then
    local width height
    width="$(sips -g pixelWidth "$file" | awk '/pixelWidth/ {print $2}')"
    height="$(sips -g pixelHeight "$file" | awk '/pixelHeight/ {print $2}')"
    if [ "$height" -gt "$width" ]; then sips -r -90 "$file" >/dev/null; fi
  fi
  echo "captured (no layout dump on iOS: the layout tests and the capture itself are the check)"
}

# --- Run -------------------------------------------------------------------------------------------------------------

if [ "$PLATFORM" = android ]; then
  [ -n "$NAME" ] || NAME="$(slug "$(adb_target shell getprop ro.product.model | tr -d '\r')")"
  turn_android
else
  [ -n "$NAME" ] || NAME="$(slug "$SIMULATOR")"
  find_ios_bundle
fi

for entry in "${LAUNCHES[@]}"; do
  IFS='|' read -r state signal gate expected <<<"$entry"
  file="$OUT_DIR/$NAME-${LABEL:-$ORIENTATION}-$state.png"
  if [ "$PLATFORM" = android ]; then
    result="$(capture_android "$signal" "$gate" "$file")"
  else
    result="$(capture_ios "$signal" "$gate" "$file")"
  fi
  printf '%s | %s, gate %s | expect: %s | %s\n' "$(basename "$file")" "$signal" "$gate" "$expected" "$result"
done
echo "wrote ${#LAUNCHES[@]} captures to $OUT_DIR"
