# Quickstart: validating the performance pipeline

How to prove the feature works end to end once implemented. Commands assume the repository root; contracts in [contracts/](contracts/), entities in [data-model.md](data-model.md).

## Prerequisites

- Apple Silicon Mac with Xcode 26.x, the iPhone 14 paired (Developer Mode on, unlocked), `uv` installed; Android SDK with `adb`, JDK 21; a Pixel on USB (`adb devices` shows `device`). The 2015 Mac needs only the Android half (JDK 21, platform-tools, `uv`, git with push access to the repository).
- `cd app/perf && uv sync` (creates `.venv`, installs scipy/perfetto/PyYAML/Jinja2/pytest; the Perfetto trace-processor binary downloads on first use).
- Local `app/iosApp/Configuration/Config.xcconfig` carries your `TEAM_ID` (it already does; skip-worktree keeps it out of git).

## 1. Unit suites (no devices)

```
cd app/perf && uv run pytest                                 # trace-pulse suite + ios collector, gate, records, catalogue, report golden
cd app && ./gradlew checkAndroidHost --warning-mode all      # through the gradle-run wrapper; PerfTrace/AudioHealth tests in :shared:engine
cd app && ./gradlew engineHostTest                           # Catch2 incl. the audio-health ABI test and the real-time poison test
cd app && ./gradlew checkIos                                 # shared tests on the simulator
```

Expected: all green, zero warnings; `pytest` reports the Skia vectors, the iOS collector fixtures (`fixtures/xcresult-metrics.json`, `fixtures/engine-health.json`, `fixtures/audio-ioproc.xml`), gate cases (inactive / within / above / improvement), record schema validation, and the report golden.

## 2. Android round (User Story 1, SC-001)

```
cd app/perf && uv run tracepulse devices                     # Pixel listed; unknown devices marked "new"
uv run tracepulse doctor --device pixel6                     # exit 0, or exit 2 naming the failed check
uv run tracepulse run --device pixel6 --label 007-performance-pipeline
```

Expected within 20 minutes: progress lines for build (`./gradlew --console=plain -Ptembra.perf=true`, log in the round directory), fresh install, warm-up, the four journeys × 20 iterations, collection, publish, sync, analyze, report; a final block as in `contracts/cli.md`; `app/perf/data/rounds/<round_id>/` holds `benchmarkData.json`, `gradle.log` and 80 `.perfetto-trace` files; `git log perf-history -1` shows the record; `app/perf/reports/report.html` opens with the Pixel card.

Refusal check: heat the phone or drop it below 50 % → `run` exits 2 before building, names the check; no record published. Two Android devices attached and no `--device` → exit 4. A second `run` on the same device while one is in progress → exit 4 naming the holder. From an agent session: build through the `gradle-run` wrapper, then `uv run tracepulse run --device pixel6 --label … --no-build --apk <app.apk> --test-apk <benchmarks.apk>`.

## 3. iOS round (User Story 2, SC-002)

```
uv run tracepulse run --device iphone14 --label 007-performance-pipeline
```

Expected: `xcodebuild build-for-testing` once, then per round fresh install, warm-up launch, `test-without-building` with the `TembraPerfTests` plan; `perf.xcresult` under the round directory; `xcresulttool get test-results metrics` output converted to samples (20 per metric); the record published; the iPhone card appears in the grid; network state shown on the round ("wifi" if you left it on; the round is not refused); `size: skipped` in the summary unless `--size` was given (`uv run tracepulse size --device iphone14` measures it separately; archive + export takes minutes). After User Story 5: `engine-health.json` pulled from the app container (zeros today) and an `audio.trace` from the attached Audio System Trace recording during the stress journey.

Simulators: `uv run tracepulse run --simulators --label 007-performance-pipeline` runs every booted simulator; their cards carry the `simulator` and `non-reference` badges; gates show `inactive (simulator series)`.

## 4. Report and gate (User Story 3, SC-005)

- Open `app/perf/reports/report.html`: grid → click a device → charts with label boundaries → click a chart → metric page. Verify zero network requests (browser dev tools) and that the page works offline.
- Gate: in `app/tracepulse.yml` set `gate.limits_pct.journey.duration_ms: 1`, run one more round; the command still exits 0, prints `gate: 1 above limit …`, the report flags the round and lists it near the top. Restore the limit.
- Injection self-test (SC-004): `uv run tracepulse inject R1` (startup delay in `Application.onCreate` / `iOSApp` init) → six rounds on the Pixel **and** six on the iPhone → `analyze` shows a confirmed `startup.*` onset at the injection round on both series, no false confirmations on the clean spans; `revert R1`.

## 5. Instrumentation (User Story 4, SC-006, SC-007)

- Add `PerfTrace.section(PerfSection.TUNER_MODE_SWITCH) { … }` around the By Ear switch (it is in the journey) and run one round per platform: `section.tuner.modeSwitch.sum_ms` appears on both device pages.
- Release build check: `app/perf/tools/check-release-strings.sh` builds the Android release APK and the iOS Release app and fails if any signpost name from `app/shared/engine/perf-catalogue.txt` (e.g. `tuner.modeSwitch`) appears in the native libraries, the dex or the iOS binary (enum identifiers may remain). Measurement-build startup median within 5 % of a release round: `uv run tracepulse run --device pixel6 --label release-baseline --build-type release` and `… --device iphone14 --label release-baseline --configuration Release`, then compare medians in the report.

## 6. Audio health plumbing (User Story 5, SC-010)

`checkAndroidHost` includes the `FakeAudioEngine.scriptAudioHealth` test proving six counters reach the sink; a Python test feeds `fixtures/engine-health.json` and a counter-track Perfetto fixture through the collectors and asserts the six `audio.*` series values. On a device the stress journey yields `audio.*` rows with zeros (current engine).

## 7. Sessions and the history branch (User Story 6, SC-009)

From the 2015 Mac over SSH: `ssh <mac2> 'cd …/app/perf && uv run tracepulse run --device <serial> --label 007-performance-pipeline --rounds 5'` → five records pushed; on the Apple Silicon Mac `uv run tracepulse sync && uv run tracepulse report --open` shows the five rounds under that label on the Pixel series next to the iPhone series.

## 8. Lanes unaffected (FR-020, SC-008)

Time `./gradlew checkAndroidHost` and `checkIos` before and after; `:perf-benchmarks` is absent from `./gradlew projects` unless `-Ptembra.perf=true`; `--warning-mode all` shows nothing new.

## 9. Noise baseline (SC-003)

`uv run tracepulse run --device pixel6 --label noise-baseline --rounds 10` and the same on `iphone14` with an unchanged build; `uv run tracepulse status --json` reports, per series, the coefficient of variation of the round medians: `startup.*` and `memory.*` at or below 3 % on each device. Record the numbers in `app/perf/README.md`; they calibrate the gate limits.
