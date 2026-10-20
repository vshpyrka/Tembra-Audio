#!/usr/bin/env bash
# Installs the Android app the way users get it on every attached device and emulator: the `release` build type
# (R8-shrunk, not debuggable, the committed baseline profile inside), signed with this Mac's debug key because the
# project has no release keystore, then compiled from its baseline profile as Google Play would. `installDebug`
# builds are debuggable, and ART runs a debuggable app far slower, so judge smoothness on this build only (the
# perf rounds measure the `perf` type: the same optimisations, profileable, plus the tracing facade).
#
# Usage: tools/release-android.sh [--no-build] [--serial <serial>]... [--clean] [--no-profile] [--no-launch]
#   --no-build    reuse the APK of the last `./gradlew :androidApp:assembleRelease`
#   --serial      only this adb serial (repeatable); default: every device `adb devices` lists as ready
#   --clean       uninstall audio.tembra.app first, which deletes its projects and settings; without it the install
#                 replaces a debug or perf build in place (all three carry the debug key)
#   --no-profile  skip the baseline-profile compilation; the first runs are then JIT-only, unlike a Play install
#   --no-launch   install only
# Output: androidApp/build/outputs/apk/release/androidApp-release-debugkey.apk
set -euo pipefail

PACKAGE=audio.tembra.app
ACTIVITY=audio.tembra.android.MainActivity
PROFILE_RECEIVER=androidx.profileinstaller.ProfileInstallReceiver
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APK_DIR="$APP_DIR/androidApp/build/outputs/apk/release"
UNSIGNED_APK="$APK_DIR/androidApp-release-unsigned.apk"
ALIGNED_APK="$APK_DIR/androidApp-release-aligned.apk"
SIGNED_APK="$APK_DIR/androidApp-release-debugkey.apk"
DEBUG_KEYSTORE="$HOME/.android/debug.keystore"

usage() {
  echo "usage: $(basename "$0") [--no-build] [--serial <serial>]... [--clean] [--no-profile] [--no-launch]" >&2
  exit 2
}

build=1
clean=0
profile=1
launch=1
serials=()
while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-build) build=0 ;;
    --serial) [ "$#" -ge 2 ] || usage; serials+=("$2"); shift ;;
    --clean) clean=1 ;;
    --no-profile) profile=0 ;;
    --no-launch) launch=0 ;;
    -h|--help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    *) usage ;;
  esac
  shift
done

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk_dir" ] && [ -f "$APP_DIR/local.properties" ]; then
  sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$APP_DIR/local.properties")"
fi
if [ -z "$sdk_dir" ] || [ ! -d "$sdk_dir/build-tools" ]; then
  echo "Android SDK not found: set ANDROID_HOME or sdk.dir in local.properties" >&2
  exit 1
fi
build_tools="$sdk_dir/build-tools/$(ls "$sdk_dir/build-tools" | sort -V | tail -1)"
adb="$sdk_dir/platform-tools/adb"

if [ "$build" = 1 ]; then
  (cd "$APP_DIR" && ./gradlew :androidApp:assembleRelease)
fi
if [ ! -f "$UNSIGNED_APK" ]; then
  echo "$UNSIGNED_APK not found; build it with ./gradlew :androidApp:assembleRelease" >&2
  exit 1
fi

# Page-align the native libraries for 16 KB devices, then sign. v4 is off: an .idsig beside the APK would make
# adb try an incremental install.
"$build_tools/zipalign" -f -P 16 4 "$UNSIGNED_APK" "$ALIGNED_APK"
"$build_tools/apksigner" sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --v4-signing-enabled false --out "$SIGNED_APK" "$ALIGNED_APK"
rm -f "$ALIGNED_APK"

manifest="$("$build_tools/aapt2" dump xmltree --file AndroidManifest.xml "$SIGNED_APK")"
if grep -q 'android:debuggable([^)]*)=true' <<<"$manifest"; then
  echo "$SIGNED_APK is debuggable: not a release build" >&2
  exit 1
fi
listing="$(unzip -l "$SIGNED_APK" assets/dexopt/baseline.prof 2>/dev/null || true)"
profile_bytes="$(awk '$4 == "assets/dexopt/baseline.prof" { print $1 }' <<<"$listing")"
echo "release APK: $SIGNED_APK ($(( $(stat -f %z "$SIGNED_APK") / 1048576 )) MB, not debuggable," \
  "baseline profile ${profile_bytes:-missing} bytes)"

if [ "${#serials[@]}" -eq 0 ]; then
  while read -r serial state _; do
    if [ "$state" = device ]; then serials+=("$serial"); fi
  done < <("$adb" devices | tail -n +2)
fi
if [ "${#serials[@]}" -eq 0 ]; then
  echo "no Android device or emulator is attached and ready (adb devices)" >&2
  exit 1
fi

# The first `status=` under the package's block of `dumpsys package dexopt` (tracepulse's profile_verify reads it so).
dexopt_status() {
  awk -v block="[$PACKAGE]" '
    { line = $0; gsub(/^[ \t\r]+|[ \t\r]+$/, "", line) }
    line ~ /^\[.*\]$/ { inside = (line == block); next }
    inside && !found && match(line, /status=[A-Za-z0-9_-]+/) { print substr(line, RSTART + 7, RLENGTH - 7); found = 1 }'
}

results=()
failed=0
for serial in "${serials[@]}"; do
  model="$("$adb" -s "$serial" shell getprop ro.product.model </dev/null | tr -d '\r' || true)"
  echo "== ${model:-unknown} ($serial)"
  "$adb" -s "$serial" shell input keyevent KEYCODE_WAKEUP </dev/null >/dev/null 2>&1 || true
  "$adb" -s "$serial" shell wm dismiss-keyguard </dev/null >/dev/null 2>&1 || true
  if [ "$clean" = 1 ]; then
    "$adb" -s "$serial" uninstall "$PACKAGE" </dev/null >/dev/null 2>&1 || true
  fi

  install_out="$("$adb" -s "$serial" install -r "$SIGNED_APK" </dev/null 2>&1 || true)"
  if ! grep -q '^Success' <<<"$install_out"; then
    reason="$(grep -o 'INSTALL_[A-Z_]*' <<<"$install_out" | head -1 || true)"
    if [ "$reason" = INSTALL_FAILED_UPDATE_INCOMPATIBLE ]; then
      reason="$reason: the installed copy is signed with another key; rerun with --clean (deletes the app's data)"
    fi
    results+=("$model ($serial): install failed: ${reason:-$install_out}")
    failed=1
    continue
  fi
  package_dump="$("$adb" -s "$serial" shell dumpsys package "$PACKAGE" </dev/null)"
  if grep -q 'pkgFlags=.*DEBUGGABLE' <<<"$package_dump"; then
    results+=("$model ($serial): installed, but the device reports the package as debuggable")
    failed=1
    continue
  fi

  compiled="not compiled (--no-profile)"
  if [ "$profile" = 1 ]; then
    # tracepulse profile verify's sequence: first launch, the profile installer's broadcast, the background pass.
    "$adb" -s "$serial" shell am start -n "$PACKAGE/$ACTIVITY" </dev/null >/dev/null 2>&1 || true
    sleep 5
    broadcast="$("$adb" -s "$serial" shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE \
      -n "$PACKAGE/$PROFILE_RECEIVER" </dev/null 2>&1 || true)"
    result="$(sed -n '/result=/{s/.*result=\([0-9]*\).*/\1/p;q;}' <<<"$broadcast")"
    "$adb" -s "$serial" shell cmd package compile -r bg-dexopt "$PACKAGE" </dev/null >/dev/null 2>&1 || true
    status="$("$adb" -s "$serial" shell dumpsys package dexopt </dev/null | dexopt_status || true)"
    "$adb" -s "$serial" shell am force-stop "$PACKAGE" </dev/null || true
    if [ "$status" = speed-profile ]; then
      compiled="compiled from the baseline profile (speed-profile)"
    else
      compiled="profile broadcast result ${result:-none}, compile status ${status:-unknown}: the first runs use the JIT"
    fi
  fi

  launched="not launched (--no-launch)"
  if [ "$launch" = 1 ]; then
    start_out="$("$adb" -s "$serial" shell am start -n "$PACKAGE/$ACTIVITY" </dev/null 2>&1 || true)"
    if grep -q '^Error' <<<"$start_out"; then
      launched="launch failed: $(grep -m1 '^Error' <<<"$start_out")"
      failed=1
    else
      launched="launched"
    fi
  fi
  results+=("$model ($serial): release build installed, $compiled, $launched")
done

echo
printf '%s\n' "${results[@]}"
exit "$failed"
