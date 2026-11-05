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

## Phase 3 notes (User Story 1, 2026-11-05)

- The half sheet's floor is 256 dp, not the 240 dp research R2 budgeted: R2 left the 32 dp handle row out of its sum,
  and with it the compact strip's fader would have fallen to 112 dp, under FR-018's 120 dp throw. 256 dp keeps the
  fader at 128 dp; FR-018's "at least 240 dp" still holds. `MixerAnchors.MinHalf` carries the reason.
- Back closes the sheet through Navigation 3's `NavigationBackHandler` (`navigationevent-compose`, an API dependency of
  `navigation3-ui`): Compose UI 1.12.2 deprecates its own `BackHandler`, and the zero-warning gate forbids the warning.
  The common UI tests prove the close through the shell's overlay stack (Escape, ⌘.), since no lane can raise a platform
  Back event inside `runComposeUiTest`; Back itself is checked by hand on the emulator and the simulator (quickstart §5).
- Solo and Mute are the 36 dp squares research R2 and the design size them (two fill the 72 dp strip); the 44 dp rule
  holds for the fader, the handle row and the toolbar button. Recorded in `../evidence/skill-review.md` for T121.

## Lanes

Still to run for Phase 3 (T055): `checkAndroidDevice` on an emulator (the Pixel 9 Pro Fold was booting when the
session's usage limit ended; no run yet), then the Pixel 6 Pro lane, the quickstart §5 steps 1–4 by hand and the
`mixer-half-360.png` / `mixer-full-360.png` captures, which wait for the owner's go on the real device.

| Target | Lane | Result | Counts | Notes |
|---|---|---|---|---|
| Host (JVM, every module) + Engine (C++) | `checkAndroidHost engineHostTest` | green, zero warnings | 251 Catch2 cases | 2026-11-05, the merged base `6ca9c2f5` before any mixer change (T001) |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | catalogue 52 names (37 sections, 15 counters), 66 anchors, the new glyph, `StrictModePolicyTest` | 2026-11-05 after Phase 1 (T002–T008); the plan's "49 names" was an arithmetic slip: 43 + 7 + 2 = 52 |
| Engine (C++) | `engineHostTest` | green, zero warnings | 291 Catch2 cases (40 new: pan law, parameters, ABI hold, meter, strip, bus, renderer, health, core) | 2026-11-05 after Phase 2 (T041); two meter-test timings corrected on the way (a 2 s settle; 14 blocks past the 64 ms window) |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | `PanTest`, `FaderTaperTest`, `ProjectEditorSetPanTest`, the golden with `pan`, `oldDocumentReadsCentred`, `ProjectSessionTest` (+6), `FakeAudioEngineTest` (+3), `AudioHealthTelemetryTest` (15 counters), `TimelineUiStateTest` (+1) | 2026-11-05 after Phase 2 (T042); `FaderTaper`'s knot arrays had to be declared before `ticks` (object initialisers run in order) |
| iPhone 18 Pro simulator, engine module | `:shared:engine:iosSimulatorArm64Test` | green | `NativeAudioEngineMixerTest` (the playback checks skip in the bare simulator process, as the transport test's do: no output unit), `NativeAudioEngineMixerCostTest` (median 0.08 µs), `NativeAudioEngineBlockingDispatchTest` (+ the mixer calls do not dispatch) | 2026-11-05 (T043, engine half; the full `checkIos` runs with Phase 3). Two fixes on the way: cinterop gives the nested `float peak[64][2]` no indexer (read flat through a reinterpreted pointer); one 1.08 ms scheduler outlier among 3,000 sub-microsecond calls made the "max < 1 ms" bound a retry-once / p99 < 1 ms / max < 10 ms rule in both cost tests |
| Host (JVM, `:shared:ui`) | `:shared:ui:testAndroidHostTest` | green, zero warnings | `TimelineViewModelTest` (+8: toggle, X, strips, groups, anchors, the project switch), `MixerAnchorsTest` (7), the catalogue test with the X entry | 2026-11-05 after Phase 3 (T055, host part). Compose UI 1.12.2's `BackHandler` is deprecated: the sheet uses Navigation 3's `NavigationBackHandler` instead (see "Phase 3 notes") |
| iPhone 18 Pro simulator, every module | `checkIos` | green, zero warnings | 1,160 Compose UI tests in `:shared:ui`, 0 failures: `FaderTest` (10), `MixerStripTest` (6), `MixerSheetTest` (7), `TimelineScreenTest` (+2), `TimelineToolbarTest` (+2), `TimelineKeyboardTest` (+1) | 2026-11-05 after Phase 3 (T055, simulator part). Three fixes on the way: the tap detector consumes the down, so the fader's drag takes the down unconsumed (the `GainReadout` pattern); the compact toolbar lets the Mixer button yield to the tempo pill at twice the font size (`CompactTail`, a layout-phase decision from the pill's intrinsic width; `TimelineLargeFontTest` had caught the 88 dp pill); the half sheet's floor is 256 dp (above) |
