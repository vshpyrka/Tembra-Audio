# Feature 012 — screenshots, lanes and the final pass

Captures per FR-034 and the lane results on every target of quickstart §2. Run conditions of quickstart §3
apply before every device run; the two sounding steps (§6 pan on headphones, §8 loopback) are recorded in
`../evidence/levels.md` and announced before they start.

## The merged base (T001)

- `main` (with `010-timeline-recording`, `011-large-screens-desktop`, the CI, Renovate and Play workflows and the
  perf follow-ups up to PR #16, `7ec955a4`) merged into `012-mixer` on 2026-11-05 as `6ca9c2f5`. One conflict,
  `CLAUDE.md`: the build-order line kept this branch's text (constitution v1.3.5 names the mixer feature 012);
  everything else took `main`. The application id is `com.tembraaudio` from here on.
- Baseline lanes on the merged base, before any mixer change (2026-11-05, `gradle-run` workflow
  `6316677439215761ab79a4683cb7cb97`, run 0002): `./gradlew checkAndroidHost engineHostTest --warning-mode all`
  green in 1 min 4 s, 251 Catch2 cases, zero warnings. The worktree needed `app/local.properties` (git-ignored)
  copied from the main checkout first: without it the build stops at "Android SDK not found".

## Targets available during the implementation run (2026-11-05)

No physical Android device or iPhone was attached when the run started (`adb devices` empty); the owner asked for
simulator and emulator runs first and to be told before any real-device step. Emulators: `Pixel_9_Pro_Fold`,
`Tembra_Desktop_API34`, `Tembra_Desktop_API37`. Simulators: iPhone 18 Pro, iPad Pro 13-inch (M5), iPhone Duo.
Real-device steps (the Pixel 6 Pro lane of T039/T043/T055, the device screenshots of T089, the loopback of T111,
TalkBack of T093, the perf rounds) wait for the owner's go.

## Lanes

| Target | Lane | Result | Counts | Notes |
|---|---|---|---|---|
| Host (JVM, every module) + Engine (C++) | `checkAndroidHost engineHostTest` | green, zero warnings | 251 Catch2 cases | 2026-11-05, the merged base `6ca9c2f5` before any mixer change (T001) |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | catalogue 52 names (37 sections, 15 counters), 66 anchors, the new glyph, `StrictModePolicyTest` | 2026-11-05 after Phase 1 (T002–T008); the plan's "49 names" was an arithmetic slip: 43 + 7 + 2 = 52 |
| Engine (C++) | `engineHostTest` | green, zero warnings | 291 Catch2 cases (40 new: pan law, parameters, ABI hold, meter, strip, bus, renderer, health, core) | 2026-11-05 after Phase 2 (T041); two meter-test timings corrected on the way (a 2 s settle; 14 blocks past the 64 ms window) |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | `PanTest`, `FaderTaperTest`, `ProjectEditorSetPanTest`, the golden with `pan`, `oldDocumentReadsCentred`, `ProjectSessionTest` (+6), `FakeAudioEngineTest` (+3), `AudioHealthTelemetryTest` (15 counters), `TimelineUiStateTest` (+1) | 2026-11-05 after Phase 2 (T042); `FaderTaper`'s knot arrays had to be declared before `ticks` (object initialisers run in order) |
| iPhone 18 Pro simulator, engine module | `:shared:engine:iosSimulatorArm64Test` | green | `NativeAudioEngineMixerTest` (the playback checks skip in the bare simulator process, as the transport test's do: no output unit), `NativeAudioEngineMixerCostTest` (median 0.08 µs), `NativeAudioEngineBlockingDispatchTest` (+ the mixer calls do not dispatch) | 2026-11-05 (T043, engine half; the full `checkIos` runs with Phase 3). Two fixes on the way: cinterop gives the nested `float peak[64][2]` no indexer (read flat through a reinterpreted pointer); one 1.08 ms scheduler outlier among 3,000 sub-microsecond calls made the "max < 1 ms" bound a retry-once / p99 < 1 ms / max < 10 ms rule in both cost tests |
