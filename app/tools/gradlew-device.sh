#!/usr/bin/env bash
# Runs the Gradle wrapper against one Android target (feature 010, quickstart §3): the device lanes read ANDROID_SERIAL,
# so `tools/gradlew-device.sh <serial> checkAndroidDevice` runs the connected-device lane on that device alone while
# the others stay attached. Every agent-initiated call still goes through the gradle-run wrapper.
#
# `--adb-server-port <port>` points the lane at another adb server (ANDROID_ADB_SERVER_PORT, honoured by adb and by
# AGP's ddmlib): when the phones are served through an ssh tunnel on the default port 5037, a local emulator cannot
# register there and runs on a second, local server instead (`ANDROID_ADB_SERVER_PORT=5038 adb start-server`, the
# emulator launched with the same variable; 2026-11-09).
#
# Usage: tools/gradlew-device.sh [--adb-server-port <port>] <serial> <gradle arguments...>
set -euo pipefail
adb_port=""
if [ "${1:-}" = "--adb-server-port" ]; then
  [ "$#" -ge 2 ] || { echo "usage: $(basename "$0") [--adb-server-port <port>] <serial> <gradle arguments...>" >&2; exit 2; }
  adb_port="$2"
  shift 2
fi
if [ "$#" -lt 2 ]; then
  echo "usage: $(basename "$0") [--adb-server-port <port>] <serial> <gradle arguments...>" >&2
  exit 2
fi
serial="$1"
shift
cd "$(dirname "$0")/.."
if [ -n "$adb_port" ]; then
  ANDROID_ADB_SERVER_PORT="$adb_port" ANDROID_SERIAL="$serial" exec ./gradlew "$@"
fi
ANDROID_SERIAL="$serial" exec ./gradlew "$@"
