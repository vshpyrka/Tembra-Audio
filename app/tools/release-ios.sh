#!/usr/bin/env bash
# Installs the iOS app the way users get it on every connected iPhone/iPad and every booted simulator: the `Release`
# configuration (Kotlin/Native release framework, Swift -O, no assertions). The `Debug` configuration links an
# unoptimised Kotlin framework, which makes Compose far slower, so judge smoothness on this build only (the perf rounds
# measure `Perf`: the same optimisations plus the tracing facade).
#
# Usage: tools/release-ios.sh [--no-build] [--device <udid>]... [--team-id <id>] [--clean] [--no-launch]
#   --no-build   reuse the apps of the last run
#   --device     only this device or booted simulator UDID (repeatable); default: every connected physical device
#                (`xcrun devicectl list devices`) and every booted simulator
#   --team-id    Apple team for signing; it is also the bundle id suffix (default: $TEMBRA_TEAM_ID, else TEAM_ID in
#                iosApp/Configuration/Config.xcconfig); a physical device needs one
#   --clean      uninstall the app first, which deletes its projects and settings; without it the install replaces a
#                Debug or Perf build in place
#   --no-launch  install only
# Xcode: honours DEVELOPER_DIR. When it is unset and a target runs a newer iOS than the selected Xcode's SDK (an iOS 27
# phone, the iPhone Duo simulator), the first /Applications/Xcode*.app whose SDK covers every target is used.
# Output: iosApp/build/Build/Products/Release-{iphoneos,iphonesimulator}/<APP_NAME>.app; logs in iosApp/build/release-logs/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
IOS_DIR="$APP_DIR/iosApp"
XCCONFIG="$IOS_DIR/Configuration/Config.xcconfig"
PRODUCTS_DIR="$IOS_DIR/build/Build/Products"
LOG_DIR="$IOS_DIR/build/release-logs"

usage() {
  echo "usage: $(basename "$0") [--no-build] [--device <udid>]... [--team-id <id>] [--clean] [--no-launch]" >&2
  exit 2
}

build=1
clean=0
launch=1
team_id="${TEMBRA_TEAM_ID:-}"
only=()
while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-build) build=0 ;;
    --device) [ "$#" -ge 2 ] || usage; only+=("$2"); shift ;;
    --team-id) [ "$#" -ge 2 ] || usage; team_id="$2"; shift ;;
    --clean) clean=1 ;;
    --no-launch) launch=0 ;;
    -h|--help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    *) usage ;;
  esac
  shift
done

# One line per target: kind (device|simulator), UDID, iOS version, name, tab-separated.
list_targets() {
  local devicectl_json
  devicectl_json="$(mktemp -t tembra-devicectl)"
  xcrun devicectl list devices --json-output "$devicectl_json" >/dev/null 2>&1 || true
  xcrun simctl list devices booted -j | python3 -I -c '
import json, sys
for runtime, devices in json.load(sys.stdin)["devices"].items():
    if ".iOS-" not in runtime:
        continue
    version = runtime.rsplit(".iOS-", 1)[1].replace("-", ".")
    for d in devices:
        if d.get("state") == "Booted":
            print("\t".join(["simulator", d["udid"], version, d["name"]]))
try:
    devices = json.load(open(sys.argv[1]))["result"]["devices"]
except (OSError, ValueError, KeyError):
    devices = []
for d in devices:
    hw, props, conn = d.get("hardwareProperties", {}), d.get("deviceProperties", {}), d.get("connectionProperties", {})
    if (hw.get("reality") == "physical" and hw.get("platform") == "iOS" and conn.get("pairingState") == "paired"
            and conn.get("tunnelState") != "unavailable"):
        print("\t".join(["device", hw["udid"], props.get("osVersionNumber", "0"), props.get("name", hw["udid"])]))
' "$devicectl_json"
  rm -f "$devicectl_json"
}

all_targets="$(list_targets)"
targets=""
if [ "${#only[@]}" -eq 0 ]; then
  targets="$all_targets"
else
  for udid in "${only[@]}"; do
    line="$(awk -F '\t' -v id="$udid" '$2 == id' <<<"$all_targets")"
    if [ -z "$line" ]; then
      echo "$udid is neither a connected device nor a booted simulator" >&2
      exit 1
    fi
    targets="${targets:+$targets$'\n'}$line"
  done
fi
if [ -z "$targets" ]; then
  echo "no connected iPhone/iPad and no booted simulator" >&2
  exit 1
fi
has_device=0
has_simulator=0
if grep -q '^device' <<<"$targets"; then has_device=1; fi
if grep -q '^simulator' <<<"$targets"; then has_simulator=1; fi

# True when version $1 >= version $2.
version_ge() {
  [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" = "$1" ]
}
sdk_version() {
  DEVELOPER_DIR="$1" xcrun --sdk iphoneos --show-sdk-version 2>/dev/null || true
}
newest_os="$(cut -f3 <<<"$targets" | sort -V | tail -1)"
needed_os="$(cut -d. -f1-2 <<<"$newest_os")"
if [ -z "${DEVELOPER_DIR:-}" ]; then
  current_dir="$(xcode-select -p)"
  current_sdk="$(sdk_version "$current_dir")"
  if [ -n "$current_sdk" ] && ! version_ge "$current_sdk" "$needed_os"; then
    for xcode in /Applications/Xcode*.app; do
      candidate_sdk="$(sdk_version "$xcode/Contents/Developer")"
      if [ -n "$candidate_sdk" ] && version_ge "$candidate_sdk" "$needed_os"; then
        export DEVELOPER_DIR="$xcode/Contents/Developer"
        echo "using $xcode (iOS SDK $candidate_sdk): a target runs iOS $newest_os, $current_dir has SDK $current_sdk"
        break
      fi
    done
    if [ -z "${DEVELOPER_DIR:-}" ]; then
      echo "warning: a target runs iOS $newest_os, newer than every installed Xcode's SDK; installs may fail" >&2
    fi
  fi
fi

xcconfig_value() {
  sed -n "s/^$1=//p" "$XCCONFIG"
}
app_name="$(xcconfig_value APP_NAME)"
team_args=()
if [ -n "$team_id" ]; then team_args=("TEAM_ID=$team_id"); fi
if [ "$has_device" = 1 ] && [ -z "${team_id:-$(xcconfig_value TEAM_ID)}" ]; then
  echo "a physical device needs a signing team: --team-id <id>, TEMBRA_TEAM_ID, or TEAM_ID in $XCCONFIG" >&2
  exit 1
fi

# xcodebuild runs `./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode` in a build phase; with the Release
# configuration that links the framework with linkRelease*, which the log check below insists on.
build_release() {
  local sdk="$1" destination="$2"
  shift 2
  local log="$LOG_DIR/xcodebuild-$sdk.log"
  mkdir -p "$LOG_DIR"
  echo "building Release for $sdk ($destination), log: $log"
  if ! (cd "$IOS_DIR" && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release \
      -destination "$destination" -derivedDataPath build ${team_args[@]+"${team_args[@]}"} "$@" build) >"$log" 2>&1; then
    grep -E 'error:|BUILD FAILED|FAILURE:' "$log" | tail -20 >&2 || true
    echo "xcodebuild failed for $sdk; full log: $log" >&2
    exit 1
  fi
  if grep -q 'linkDebugFramework' "$log"; then
    echo "the Kotlin framework was linked in debug mode for $sdk; see $log" >&2
    exit 1
  fi
}

if [ "$build" = 1 ]; then
  if [ "$has_simulator" = 1 ]; then
    # The Release configuration does not exclude x86_64 for the simulator, and the Kotlin build has no iosX64 target.
    build_release iphonesimulator "generic/platform=iOS Simulator" ARCHS=arm64
  fi
  if [ "$has_device" = 1 ]; then
    # One build per device so automatic signing registers each one in the team's provisioning profile.
    while IFS=$'\t' read -r kind udid _ _ <&3; do
      if [ "$kind" = device ]; then
        build_release iphoneos "id=$udid" -allowProvisioningUpdates -allowProvisioningDeviceRegistration
      fi
    done 3<<<"$targets"
  fi
fi

results=()
failed=0
while IFS=$'\t' read -r kind udid os name <&3; do
  if [ "$kind" = device ]; then sdk=iphoneos; else sdk=iphonesimulator; fi
  app="$PRODUCTS_DIR/Release-$sdk/$app_name.app"
  if [ ! -d "$app" ]; then
    results+=("$name ($udid): $app not found; run without --no-build")
    failed=1
    continue
  fi
  bundle_id="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$app/Info.plist")"
  echo "== $name, iOS $os ($udid)"
  if [ "$kind" = simulator ]; then
    if [ "$clean" = 1 ]; then xcrun simctl uninstall "$udid" "$bundle_id" </dev/null >/dev/null 2>&1 || true; fi
    if ! install_out="$(xcrun simctl install "$udid" "$app" </dev/null 2>&1)"; then
      results+=("$name ($udid): install failed: $(tail -1 <<<"$install_out")")
      failed=1
      continue
    fi
    launched="not launched (--no-launch)"
    if [ "$launch" = 1 ]; then
      xcrun simctl terminate "$udid" "$bundle_id" </dev/null >/dev/null 2>&1 || true
      if launch_out="$(xcrun simctl launch "$udid" "$bundle_id" </dev/null 2>&1)"; then
        launched="launched"
      else
        launched="launch failed: $(tail -1 <<<"$launch_out")"
        failed=1
      fi
    fi
  else
    if [ "$clean" = 1 ]; then
      xcrun devicectl device uninstall app --device "$udid" "$bundle_id" </dev/null >/dev/null 2>&1 || true
    fi
    if ! install_out="$(xcrun devicectl device install app --device "$udid" "$app" </dev/null 2>&1)"; then
      results+=("$name ($udid): install failed: $(grep -m1 -i 'error' <<<"$install_out" || tail -1 <<<"$install_out")")
      failed=1
      continue
    fi
    launched="not launched (--no-launch)"
    if [ "$launch" = 1 ]; then
      if launch_out="$(xcrun devicectl device process launch --device "$udid" --terminate-existing "$bundle_id" \
          </dev/null 2>&1)"; then
        launched="launched"
      elif grep -qi 'locked' <<<"$launch_out"; then
        launched="not launched: the device is locked; unlock it and open the app"
      else
        launched="launch failed: $(grep -m1 -i 'error' <<<"$launch_out" || tail -1 <<<"$launch_out")"
        failed=1
      fi
    fi
  fi
  results+=("$name, iOS $os ($udid): Release build installed as $bundle_id, $launched")
done 3<<<"$targets"

echo
printf '%s\n' "${results[@]}"
exit "$failed"
