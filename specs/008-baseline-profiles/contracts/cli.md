# Contract: `tracepulse profile` commands

Three sub-commands of the existing `tracepulse` CLI (`app/perf`, run with `uv run tracepulse …`). They share the
device registry, the device protocol (`doctor`), the Gradle build step and the `Runner` abstraction of `run`, and they
never write to `data/tracepulse.db`, `data/records/`, `data/unpublished/`, the `perf-history` branch or the report.
Exit statuses follow the `run` convention (0 / 2 / 3 / 4); there is no 5 because nothing is published.

| Code | Meaning (all three commands) |
|---|---|
| 0 | done — for `compare` whatever the numbers say; for `verify` only when every check passed |
| 2 | refused: a device check, the eligibility check or the state of the device failed before anything ran; nothing written |
| 3 | failed: the work started and did not complete (Gradle task, instrumentation, a state, a verification step); generation restores the committed files |
| 4 | usage or configuration error (unknown device, two devices and none named, unknown state, bad path) |

## `tracepulse profile generate`

```
uv run tracepulse profile generate --device <serial|alias> [--keep-build]
```

- Preconditions: exactly one named Android device; `doctor` green (battery, temperature, refresh rate, airplane mode, brightness, storage, awake, unlocked — the round's checks, unchanged); eligibility `ro.build.version.sdk >= 33` (non-rooted profile collection), otherwise refused with "device API <n> cannot generate profiles without root; needs API 33+".
- Steps: copy `app/androidApp/src/main/generated/baselineProfiles/{baseline-prof.txt,startup-prof.txt}` to a backup directory (absent files are recorded as absent) → run `./gradlew --console=plain -Ptembra.perf=true :androidApp:generateBaselineProfile` from `app/` with `ANDROID_SERIAL=<serial>` in the environment and the build log captured → run `./gradlew --console=plain :androidApp:testDebugUnitTest --tests "*BaselineProfileCoverageTest"` → report the two files' line counts and the covered labels.
- On failure of either Gradle step: restore the backups (delete the files if they were absent), print the failing step and the log path, exit 3.
- Output (stdout, plain text): the device, the Gradle task, the resulting file sizes and rule counts, the coverage labels found, and a reminder of when to commit ("commit when covered code changed; noise-only differences need not be committed"). Nothing else is written.
- Non-interactive; the Gradle invocation inherits no TTY.

## `tracepulse profile compare`

```
uv run tracepulse profile compare --device <serial|alias> [--with-full] [--iterations N] [--journeys startup,tuner,…]
                                  [--no-build --apk <app.apk> --test-apk <benchmarks.apk>] [--keep-build]
```

- Preconditions: exactly one named Android device; `doctor` green.
- Build: as `run` (`-Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf`), or the given artefacts with `--no-build`. Build identity (commit, dirty flag) recorded as in a round.
- States: `none`, `profile`, plus `full` with `--with-full`, in that order. Per state: `adb uninstall` (ignored if absent) → `adb install` both APKs → one detached `am instrument -w -e class audio.tembra.perf.ProfileComparisonBenchmark -e tembra.compare.state <state> -e androidx.benchmark.iterations <N> -e additionalTestOutputDir <dir> …` (polling as `run` does) → pull `benchmarkData.json` into `data/comparisons/<id>/<state>/`.
- A state whose instrumentation fails or yields no result for a requested journey fails the command (exit 3) with the state and journey named; results of earlier states are kept on disk.
- Statistics: per catalogue metric present in both `none` and `profile`: medians, `change_pct` = (median_profile − median_none) / median_none × 100, `direction` from the catalogue (`higher_is_worse`), Mann–Whitney U two-sided on the samples with `confirmed = p < 0.01`; with `full`: `gap_closed_pct`.
- Output: `data/comparisons/<id>/comparison.json` (schema `comparison.schema.json`) and `comparison.md`; stdout prints the markdown table and the headline "startup.ttid_ms: <none> → <profile> ms (<change> %, confirmed|not confirmed)". Exit 0 regardless of the direction of the result.
- Defaults: `N` = the `run` default (20); journeys = the four configured ones.

## `tracepulse profile verify`

```
uv run tracepulse profile verify --device <serial|alias> [--apk <app.apk>] [--build-type perf|release]
```

- Preconditions: one named device; `doctor`'s awake/unlocked/adb checks (the thermal and battery checks are not needed and are skipped — this is not a measurement).
- Steps: build `assemble<BuildType>` (default `perf`, debug-signed and installable; `release` is accepted for an inspection of the APK only unless a signing config exists) or use `--apk` → check the APK contains `assets/dexopt/baseline.prof` and compute its share of the APK size → `adb install -r` → `am start` the launcher activity → wait 5 s → `am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n audio.tembra.app/androidx.profileinstaller.ProfileInstallReceiver`, parse `result=<code>` → `cmd package compile -r bg-dexopt audio.tembra.app` → `dumpsys package dexopt`, extract the package's `status=` → `am force-stop`.
- Output: one line per step with its finding; the summary "profile present (<bytes>, <share> %); recognised (result <code>); optimised (<status>)". Exit 0 only when present, result ∈ {1, 2} and status is `speed-profile`; otherwise exit 3 naming the first failing step.

## Shared behaviour

- `--device` resolves through the registry exactly as `run` (serial, UDID or alias); with two Android devices attached and no `--device`, exit 4 with the list.
- Every host and device command goes through `Runner`, so pytest drives all three with scripted outputs (fixtures under `app/perf/tests/fixtures/profile/`, where the pipeline keeps its fixtures).
- The commands print nothing interactive and read no stdin.
