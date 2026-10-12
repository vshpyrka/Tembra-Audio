#!/usr/bin/env bash
# The FR-006 build gate of feature 008 (tasks T023/T026): with the committed baseline profile moved aside,
# `:androidApp:assembleRelease` must fail with the message that names the generation command, and the
# profile-generation variant (`-Ptembra.perf=true :androidApp:assembleNonMinifiedRelease`) must still build,
# because a profile cannot exist before that variant has run. The two files are put back on every exit.
#
# Usage: app/tools/check-baseline-profile-gate.sh [gradle arguments forwarded to both invocations]
# Exit 0 when both expectations hold, 1 otherwise. Never run it while `tracepulse profile generate` is running.
set -u

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROFILE_DIR="$APP_DIR/androidApp/src/main/generated/baselineProfiles"
FILES=(baseline-prof.txt startup-prof.txt)
ASIDE="$(mktemp -d "${TMPDIR:-/tmp}/baseline-profile-gate.XXXXXX")"
GRADLE_ARGS=("$@")

restore() {
    for f in "${FILES[@]}"; do
        [ -f "$ASIDE/$f" ] && mv "$ASIDE/$f" "$PROFILE_DIR/$f"
    done
    rmdir "$ASIDE" 2>/dev/null || true
}
trap restore EXIT

mkdir -p "$PROFILE_DIR"
for f in "${FILES[@]}"; do
    [ -f "$PROFILE_DIR/$f" ] && mv "$PROFILE_DIR/$f" "$ASIDE/$f"
done
cd "$APP_DIR"

release_out="$(./gradlew "${GRADLE_ARGS[@]}" :androidApp:assembleRelease 2>&1)"
release_rc=$?
if [ "$release_rc" -eq 0 ]; then
    echo "FAIL: :androidApp:assembleRelease succeeded without a committed baseline profile"
    exit 1
fi
if ! grep -q "tracepulse profile generate" <<<"$release_out"; then
    echo "FAIL: :androidApp:assembleRelease failed (rc=$release_rc) but not with the generation message:"
    tail -n 20 <<<"$release_out"
    exit 1
fi
echo "ok: :androidApp:assembleRelease refuses without a committed baseline profile (rc=$release_rc)"

if ! ./gradlew "${GRADLE_ARGS[@]}" -Ptembra.perf=true :androidApp:assembleNonMinifiedRelease >/dev/null 2>&1; then
    echo "FAIL: -Ptembra.perf=true :androidApp:assembleNonMinifiedRelease must build without a profile"
    exit 1
fi
echo "ok: :androidApp:assembleNonMinifiedRelease builds without a profile"
