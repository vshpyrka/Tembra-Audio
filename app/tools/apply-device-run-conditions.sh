#!/usr/bin/env bash
# The owner's run conditions for every lane, capture and perf round on a physical Android device (feature 010,
# quickstart §2; the rules of 2026-10-25): the screen awake and unlocked, brightness at its floor, the music stream
# silent and notifications quiet. Each command tolerates a refusal (the OPPO's ColorOS refuses the brightness
# settings; dim it by hand), so the script never fails a lane.
#
# Usage: tools/apply-device-run-conditions.sh <serial>
set -uo pipefail
if [ "$#" -ne 1 ]; then
  echo "usage: $(basename "$0") <serial>" >&2
  exit 2
fi
serial="$1"
run() {
  if ! adb -s "$serial" shell "$@" >/dev/null 2>&1; then
    echo "warning: '$*' was refused on $serial (set it by hand)" >&2
  fi
}
run input keyevent KEYCODE_WAKEUP
run wm dismiss-keyguard
run settings put system screen_brightness_mode 0
run settings put system screen_brightness 0
# The music stream: `media volume` on older builds, `cmd media_session volume` where the media shell command is gone (Android 15+).
if ! adb -s "$serial" shell media volume --stream 3 --set 0 >/dev/null 2>&1; then
  run cmd media_session volume --stream 3 --set 0
fi
run cmd notification set_dnd on
echo "run conditions applied on $serial (brightness floor, music silent, do-not-disturb)"
