# Quickstart: validating Baseline Profiles (feature 008)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contracts**: [contracts/](contracts/)

Prerequisites: `app/README.md` toolchain; `cd app/perf && uv sync`; the Pixel 6 Pro attached over USB, awake, unlocked,
set up per `app/perf/README.md` "Device setup" (airplane mode on, pinned refresh rate, manual brightness). Every Gradle
command below runs from `app/` (agents: through `gradle-run`). Device ids are registry aliases (`pixel6`, `pixel7`).

## 0. Baselines before any change (SC-006, SC-007, SC-008)

```
cd app && for i in 1 2 3; do /usr/bin/time -p ./gradlew checkAndroidHost --warning-mode all >/dev/null; done
for i in 1 2 3; do /usr/bin/time -p ./gradlew :androidApp:assembleDebug >/dev/null; done
cd perf && uv run tracepulse status --series --label app-icons --last 5     # the last session's round time and app size
```

Expected: three timings each (take the median) recorded in the pull request; the Pixel 6 Pro `app.size_bytes` median of the last label.

## 1. Unit suites (test-first evidence)

```
cd app/perf && uv run pytest tests/test_profile_generate.py tests/test_profile_compare.py tests/test_profile_verify.py tests/test_collect_android.py
cd app && ./gradlew :androidApp:testDebugUnitTest --tests "*BaselineProfileCoverageTest" --warning-mode all
```

Expected: pytest green (fake-runner scenarios: refusal exit 2, eligibility refusal, backup/restore on failure, one
instrument per state, Mann–Whitney confirmation on the fixtures, verify's result-code and `dumpsys` parsing);
the coverage test **red** until the profile is generated (message names the generation command), green after §2.

## 2. Generate the profile (US1, SC-003)

```
cd app/perf && time uv run tracepulse profile generate --device pixel6
git -C ../.. status --short app/androidApp/src/main/generated/baselineProfiles/
```

Expected: exit 0 in ≤ 15 min; `baseline-prof.txt` and `startup-prof.txt` present and non-empty; the output lists the
six coverage labels as found; the coverage test green. Negative paths: `--device` omitted with two devices attached
→ exit 4; a locked device → exit 2 naming the check; an API < 33 device (emulator image) → exit 2 naming the
requirement; a journey broken on purpose (rename an anchor) → exit 3 and the committed files unchanged (`git diff`
empty).

Repeat once: a second generation on the same build differs only in ordering and timing-reached rules; the six
descriptors are present in both (spec US1 scenario 6).

## 3. The shrunk build works (FR-005a, SC-010)

```
cd app && ./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf --warning-mode all 2>&1 | tee /tmp/perf-build.log
grep -ci "warning" /tmp/perf-build.log        # expected 0 (R8 warnings count)
unzip -l androidApp/build/outputs/apk/perf/androidApp-perf.apk | grep -E "dexopt/baseline.prof|classes[0-9]*.dex"
cd perf && uv run tracepulse run --device pixel6 --label baseline-profiles-shrink-check --no-publish --no-build --apk ../androidApp/build/outputs/apk/perf/androidApp-perf.apk --test-apk ../perf-benchmarks/build/outputs/apk/perf/perf-benchmarks-perf.apk
```

Expected: `assets/dexopt/baseline.prof` in the APK; the round succeeds (all four journeys) on the shrunk build; no
crash in `logcat`. Then screenshots of every primary destination on the shrunk build (`adb shell am start -n
audio.tembra.app/audio.tembra.android.MainActivity`, navigate, `android screen capture --output …`): Timeline,
Tuner (Auto Listen and By Ear), Effects, Pads, Projects, Metronome (settings open), Profile (and Home until it was
removed on 2026-10-17) — on the Pixel (compact)
and on the tablet or fold emulator (expanded) — compared side by side with the debug build. A difference (missing
text, icon, sound, a crash) is answered with one keep rule in `proguard-rules.pro` with a comment, then this step again.

Debug unchanged: `./gradlew :androidApp:assembleDebug --dry-run | grep -Ei "artprofile|baselineprofile|minify|r8"` prints nothing.

Build refuses without a profile: move the two files aside, `./gradlew :androidApp:assembleRelease` fails with the
generation message; move them back.

## 4. Verify on the device (US2, FR-006, FR-007, SC-005)

```
cd app/perf && uv run tracepulse profile verify --device pixel6
```

Expected: "profile present (<bytes>, <share> %)" with share ≤ 3 %; "recognised (result 1)" (or 2 on a repeat);
"optimised (speed-profile)"; exit 0; the whole step under two minutes after the install. The same as plain `adb`
(documented in `app/perf/README.md`): install, launch, `am broadcast … INSTALL_PROFILE …`, `cmd package compile -r
bg-dexopt audio.tembra.app`, `dumpsys package dexopt | grep -A2 audio.tembra.app`.

## 5. Prove it (US3, SC-001, SC-002, SC-004)

```
cd app/perf && time uv run tracepulse profile compare --device pixel6
cat data/comparisons/<id>/comparison.md
uv run tracepulse profile compare --device pixel6 --with-full      # once, for the gap-closed column
```

Expected: exit 0 in ≤ 40 min for the two default states; `startup.ttid_ms` median at least 15 % lower in `profile`
than in `none` with `confirmed: true`; `startup.ttfd_ms`, `tuner.load`, `metronome.load` lower and confirmed; no
metric `worse` and confirmed. `comparison.md` is pasted into the pull request. `uv run tracepulse status` shows no
new round and `git -C .history status` shows nothing to publish (the comparison never enters the history).

## 6. Keep it current (US4, FR-011, SC-006)

```
cd app && ./gradlew checkAndroidHost --warning-mode all            # passes; timing within 5 % of §0
sed -i '' 's/^timeline .*/timeline Laudio\/tembra\/ui\/timeline\/MissingScreenKt;/' androidApp/baseline-profile-coverage.txt
./gradlew checkAndroidHost                                           # fails naming "timeline (Laudio/tembra/ui/timeline/MissingScreenKt;)"
git checkout androidApp/baseline-profile-coverage.txt
```

Expected: pass, fail-with-name, pass. Until 2026-10-17 the check edited the `home` rule
(`Laudio/tembra/ui/home/MissingScreenKt;`); that rule became the `timeline` rule when Home was removed
(contracts/coverage-rule.md). `app/perf/README.md` "Every feature instruments itself" states the
regeneration sentence; the "Baseline profiles" section documents the three commands, the file locations, when to
regenerate and commit, the `adb` verification and the keep-rule policy.

## 7. Lanes and warnings (FR-013, SC-007)

```
cd app && ./gradlew checkAndroidHost checkIos --warning-mode all
./gradlew engineHostTest
./gradlew :androidApp:assembleRelease --warning-mode all 2>&1 | grep -ci warning     # 0
```

Expected: all green, zero warnings; `checkAndroidHost` and `assembleDebug` within 5 % of §0 (if not, apply the D1
fallback and re-measure); the iOS build untouched (`git diff --stat -- app/iosApp app/shared` empty).

## 8. After the merge (SC-008)

```
cd app/perf && uv run tracepulse run --device pixel6 --label baseline-profiles --rounds 5
uv run tracepulse run --device pixel7 --label baseline-profiles --rounds 5
uv run tracepulse sync && uv run tracepulse report --open
```

Expected: ten rounds published; the report shows the `baseline-profiles` boundary on both Pixels; `app.size_bytes`
lower than the previous label; no family flagged above its limit in the worse direction; any onset at the boundary is
an improvement. The report and the comparison summary are the pull-request evidence (SC-009).
