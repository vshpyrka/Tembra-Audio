#!/usr/bin/env bash
# SC-007 / FR-014 (feature 007): no catalogue signpost name may exist in a release binary.
# Builds the Android release APK and the iOS Release app, then greps the APK's native libraries and dex and the
# iOS binary for every signpost name in app/shared/engine/perf-catalogue.txt. Enum identifiers (TUNER_FRAME) may
# remain: the release build does not shrink and they are not what a trace tool sees.
#
# Usage: app/perf/tools/check-release-strings.sh [--android-only|--ios-only] [--no-build]
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$(cd "$HERE/../.." && pwd)"
CATALOGUE="$APP/shared/engine/perf-catalogue.txt"
PLATFORMS="android ios"
BUILD=1
for arg in "$@"; do
  case "$arg" in
    --android-only) PLATFORMS="android" ;;
    --ios-only) PLATFORMS="ios" ;;
    --no-build) BUILD=0 ;;
    *) echo "unknown argument: $arg" >&2; exit 4 ;;
  esac
done

names=()
while read -r enum family signpost; do
  [[ -z "$enum" || "$enum" == \#* ]] && continue
  names+=("$signpost")
done < "$CATALOGUE"
echo "checking ${#names[@]} catalogue names: ${names[*]}"

failures=0
scan() {  # scan <label> <file>
  local label="$1" file="$2" hit
  for name in "${names[@]}"; do
    if strings -a "$file" | grep -qxF "$name"; then
      echo "FAIL: $label contains catalogue name '$name'"
      failures=$((failures + 1))
    fi
  done
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if [[ "$PLATFORMS" == *android* ]]; then
  if [[ $BUILD -eq 1 ]]; then
    (cd "$APP" && ./gradlew --console=plain :androidApp:assembleRelease >"$work/gradle-release.log" 2>&1) || {
      echo "FAIL: ./gradlew :androidApp:assembleRelease failed; see $work/gradle-release.log"; exit 3; }
  fi
  apk="$(ls "$APP"/androidApp/build/outputs/apk/release/androidApp-release*.apk | head -1)"
  echo "android: $apk"
  mkdir -p "$work/apk" && unzip -q -o "$apk" -d "$work/apk"
  for so in "$work"/apk/lib/*/*.so; do scan "$(basename "$(dirname "$so")")/$(basename "$so")" "$so"; done
  for dex in "$work"/apk/*.dex; do scan "$(basename "$dex")" "$dex"; done
fi

if [[ "$PLATFORMS" == *ios* ]]; then
  derived="$work/DerivedData"
  if [[ $BUILD -eq 1 ]]; then
    (cd "$APP/iosApp" && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Release \
      -destination 'generic/platform=iOS' -derivedDataPath "$derived" CODE_SIGNING_ALLOWED=NO build >"$work/xcodebuild-release.log" 2>&1) || {
      echo "FAIL: xcodebuild -configuration Release failed; see $work/xcodebuild-release.log"; exit 3; }
    app_dir="$(find "$derived/Build/Products/Release-iphoneos" -maxdepth 1 -name '*.app' | head -1)"
  else
    app_dir="$(find "$APP/iosApp/build/Build/Products/Release-iphoneos" -maxdepth 1 -name '*.app' 2>/dev/null | head -1)"
  fi
  [[ -n "$app_dir" ]] || { echo "FAIL: no Release .app found"; exit 3; }
  binary="$app_dir/$(basename "$app_dir" .app)"
  echo "ios: $binary"
  scan "ios binary" "$binary"
  for fw in "$app_dir"/Frameworks/*.framework; do
    [[ -d "$fw" ]] || continue
    scan "$(basename "$fw")" "$fw/$(basename "$fw" .framework)"
  done
fi

if [[ $failures -gt 0 ]]; then
  echo "release binaries contain $failures catalogue name hit(s)"; exit 1
fi
echo "OK: no catalogue signpost name in the release binaries"
