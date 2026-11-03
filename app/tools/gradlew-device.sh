#!/usr/bin/env bash
# Runs the Gradle wrapper against one Android target (feature 010, quickstart §3): the device lanes read ANDROID_SERIAL,
# so `tools/gradlew-device.sh <serial> checkAndroidDevice` runs the connected-device lane on that device alone while
# the others stay attached. Every agent-initiated call still goes through the gradle-run wrapper.
#
# Usage: tools/gradlew-device.sh <serial> <gradle arguments...>
set -euo pipefail
if [ "$#" -lt 2 ]; then
  echo "usage: $(basename "$0") <serial> <gradle arguments...>" >&2
  exit 2
fi
serial="$1"
shift
cd "$(dirname "$0")/.."
ANDROID_SERIAL="$serial" exec ./gradlew "$@"
