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

Still to run for Phase 3 (T055): the Pixel 6 Pro lane, the quickstart §5 steps 1–4 by hand and the
`mixer-half-360.png` / `mixer-full-360.png` captures, which wait for the owner's go on the real device. The emulator
lane ran (below): the Pixel 9 Pro Fold (API 36, `emulator-5584`) takes 2 h 36 min for the three device-test modules,
so it is a once-per-phase-group check, not a per-change one.

| Target | Lane | Result | Counts | Notes |
|---|---|---|---|---|
| Host (JVM, every module) + Engine (C++) | `checkAndroidHost engineHostTest` | green, zero warnings | 251 Catch2 cases | 2026-11-05, the merged base `6ca9c2f5` before any mixer change (T001) |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | catalogue 52 names (37 sections, 15 counters), 66 anchors, the new glyph, `StrictModePolicyTest` | 2026-11-05 after Phase 1 (T002–T008); the plan's "49 names" was an arithmetic slip: 43 + 7 + 2 = 52 |
| Engine (C++) | `engineHostTest` | green, zero warnings | 291 Catch2 cases (40 new: pan law, parameters, ABI hold, meter, strip, bus, renderer, health, core) | 2026-11-05 after Phase 2 (T041); two meter-test timings corrected on the way (a 2 s settle; 14 blocks past the 64 ms window) |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | `PanTest`, `FaderTaperTest`, `ProjectEditorSetPanTest`, the golden with `pan`, `oldDocumentReadsCentred`, `ProjectSessionTest` (+6), `FakeAudioEngineTest` (+3), `AudioHealthTelemetryTest` (15 counters), `TimelineUiStateTest` (+1) | 2026-11-05 after Phase 2 (T042); `FaderTaper`'s knot arrays had to be declared before `ticks` (object initialisers run in order) |
| iPhone 18 Pro simulator, engine module | `:shared:engine:iosSimulatorArm64Test` | green | `NativeAudioEngineMixerTest` (the playback checks skip in the bare simulator process, as the transport test's do: no output unit), `NativeAudioEngineMixerCostTest` (median 0.08 µs), `NativeAudioEngineBlockingDispatchTest` (+ the mixer calls do not dispatch) | 2026-11-05 (T043, engine half; the full `checkIos` runs with Phase 3). Two fixes on the way: cinterop gives the nested `float peak[64][2]` no indexer (read flat through a reinterpreted pointer); one 1.08 ms scheduler outlier among 3,000 sub-microsecond calls made the "max < 1 ms" bound a retry-once / p99 < 1 ms / max < 10 ms rule in both cost tests |
| Host (JVM, `:shared:ui`) | `:shared:ui:testAndroidHostTest` | green, zero warnings | `TimelineViewModelTest` (+8: toggle, X, strips, groups, anchors, the project switch), `MixerAnchorsTest` (7), the catalogue test with the X entry | 2026-11-05 after Phase 3 (T055, host part). Compose UI 1.12.2's `BackHandler` is deprecated: the sheet uses Navigation 3's `NavigationBackHandler` instead (see "Phase 3 notes") |
| Pixel 9 Pro Fold emulator (API 36, `emulator-5584`), every module | `tools/gradlew-device.sh emulator-5584 checkAndroidDevice --continue` | engine green; UI 1,156 tests, 3 failures, all fixed or explained | engine: `NativeAudioEngineMixerTest`, `NativeAudioEngineMixerCostTest`, `NativeAudioEngineBlockingDispatchTest` green on the third run; UI: the two `MixerStripTest` size assertions failed on pixel rounding at the fold's 2.6× density (72.2 dp for 72, 43.9 dp for 44: half a dp of tolerance added), `TembraGradientCardTest.theOverlayShadesTopAndBottomDifferently` timed out at 2 s (feature 002's test; the emulator ran the module in 2 h 36 min, a slow-emulator flake, not a mixer change) | 2026-11-05/06 (T055, emulator part; four runs). The first two runs failed `aClipPannedHardLeftMetersOnTheLeftAndReadsSilenceOnTheRight` on the right side's level: the renderer kept each strip's gains across transport sessions and ramped from the old centre for 10 ms, which the 64 ms peak window and the 300 ms rms window kept for seconds; `TransportRenderer::silence()` now resets the strips so a new session snaps (Catch2 case added, 292 cases). The second run also failed the fold's `MetronomeTimingEndToEndTest` once (fitted period 498.6 ms, virtual-clock jitter under the full lane); green on the third and fourth |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | `MeterFrameCounterTest` (4), `EngineHealthJsonTest` with the two new keys, `TimelineViewModelTest` (+3: the armed strip's meter source, the two clip resets) | 2026-11-06 after Phase 4 (T065, host part) |
| iPhone 18 Pro simulator, every module | `checkIos` | green, zero warnings | 1,183 Compose UI tests, 0 failures: `LevelMeterTest` (6), `VuDialTest` (5), `MixerSheetTest` (10), `MixerStripTest` (8) | 2026-11-06 after Phase 4 (T065, simulator part). Two fixes on the way: a meter measured shorter than its hold mark while the sheet slides in drew the mark into an empty range (guarded, the dial likewise); the per-frame read test must freeze the clock before the first frame, because the harness parks an infinite animation (the frame tick) for good once it has run under the auto-advancing clock |
| Engine (C++) | `engineHostTest` | green, zero warnings | 292 Catch2 cases (+1: a new session snaps the strips to the stored pan) | 2026-11-06, the renderer fix the emulator lane asked for |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | `TimelineViewModelTest` (+2: the pan edit, a knob turn as one undo step), `TrackSettingsViewModelTest` (+1) | 2026-11-06 after Phase 5 (T074, host part) |
| iPhone 18 Pro simulator, every module | `checkIos` | green, zero warnings | 1,195 Compose UI tests, 0 failures: `PanKnobTest` (8), the header's pan caption and the settings' pan row, the strip's knob | 2026-11-06 after Phase 5 (T074, simulator part). On the way: `TrackHeaderTest` measured the expanded header's gain readout at 16 dp (feature 010's arm toggle had filled its row), so the arm moved to the first row in the instrument chip's place and the pan reads under the gain inside the readout; the knob's release snap read the composition's value a frame behind its last report (it keeps the value the turn reached); the header's Tab order test now expects the arm before S |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | `TimelineViewModelTest` (+4: 200 random toggles compared through `TrackUi` and `StripUi`, the master drag as one step, the selection's strip, the strip carried into the open) | 2026-11-06 after Phase 6 (T080, host part) |
| iPhone 18 Pro simulator, every module | `checkIos` | green, zero warnings | 1,204 Compose UI tests, 0 failures: `MasterStripTest` (3), `MixerStripTest` (+2: the lavender Solo and dim Mute, M and O on the focused strip) | 2026-11-06 after Phase 6 (T080, simulator part); two assertion slips of the new tests on the way (the master frame has no merged description; a node's own capture keeps the dim fill's alpha) |
| iPhone 18 Pro simulator, every module | `checkIos` | green, zero warnings | 1,160 Compose UI tests in `:shared:ui`, 0 failures: `FaderTest` (10), `MixerStripTest` (6), `MixerSheetTest` (7), `TimelineScreenTest` (+2), `TimelineToolbarTest` (+2), `TimelineKeyboardTest` (+1) | 2026-11-05 after Phase 3 (T055, simulator part). Three fixes on the way: the tap detector consumes the down, so the fader's drag takes the down unconsumed (the `GainReadout` pattern); the compact toolbar lets the Mixer button yield to the tempo pill at twice the font size (`CompactTail`, a layout-phase decision from the pill's intrinsic width; `TimelineLargeFontTest` had caught the 88 dp pill); the half sheet's floor is 256 dp (above) |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | the catalogue test with the nine Mixer entries (`everyActionIsInTheCatalogueOnce`, `everyActionReachesItsScopesTarget` through `RecordingTargets.mixer`), `MixerAnchorsTest` with the 440/292/280 dp forms | 2026-11-06 after Phase 7 (T088, host part) |
| iPhone 18 Pro simulator, every module | `checkIos` | green, zero warnings | 1,218 tests in `:shared:ui`, 0 failures: `MixerSemanticsTest` (4), `MixerSheetTest` (+6: the four widths, landscape 600 × 400, the 440/280 dp drop order, the largest font, the layout change, the scrollbar), `MixerStripTest` (+2: the strip's keys from the toggles, the secondary click), `FaderTest` and `PanKnobTest` (+1 each: tooltip and hover layer) | 2026-11-06 after Phase 7 (T088, simulator part). One fix on the way: Skia's text layout on iOS does not report `isLineEllipsized`, so the ellipsis check reads `hasVisualOverflow` and the layout's width |
| iPad Pro 13-inch (M5) simulator, every module | `checkIos -Ptembra.iosSimulator="iPad Pro 13-inch (M5)"` | green, zero warnings | the same 1,218 tests | 2026-11-06 after Phase 7 (T088) |
| iPhone Duo simulator (`8AF0F092-16BB-4286-8F8F-E0AB208CBA8A`), every module | `checkIos -Ptembra.iosSimulator=8AF0F092-…` | green, zero warnings | the same 1,218 tests | 2026-11-06 after Phase 7 (T088) |
| Pixel 9 Pro Fold emulator (API 36, `emulator-5584`), every module | `tools/gradlew-device.sh emulator-5584 checkAndroidDevice --continue` | engine green; UI 1,214 tests, 10 failures, all test-side or emulator flakes (below) | five `captureToImage` window-capture timeouts at 2 s on the slow emulator (`PointerAreaTest.aSegment`, `TembraGradientCardTest` ×2, `MetronomeControlsTest.hoverRaisesTheTapCard`, `PlayheadOverlayTest.theLineLeavesOutTheExpandedTracksSettings`: features 002–011's tests, the class recorded after Phase 3); `MixerSheetTest.theAnchorsFollowTheLayoutClassAtEveryWidth` asserted the master *displayed* inside a 1024 dp root wider than the screen (now its bounds); `…aLayoutClassChangeKeepsTheSheetOpenAtItsHeightAndTheRowsScroll` scrolled 12 strips past what an 840 dp row can keep (24 strips now); `MixerStripTest.soloLightsLavenderAndMuteDimly` compared alphas that Android's capture composites away (both fills over black, by brightness now); `TrackHeaderTest` ×2 read the 56 dp gain readout at 55.38 dp (one pixel of rounding at the fold's density; the floor is asserted at 55 dp) | 2026-11-06 06:18, 2 h 47 min (T088, device part; the Phases 4–7 changes in one lane). The Pixel 6 Pro lane follows (owner's go of 2026-11-08) |
| Pixel 6 Pro (`19021FDEE001AH`, Android 17, 411 × 891 dp), every module | `tools/gradlew-device.sh 19021FDEE001AH checkAndroidDevice --continue` (run conditions applied first: music stream muted, do-not-disturb, brightness floor) | engine 114 tests green, data green; UI 1,214 tests, 4 failures, all test-side | the four were test roots wider than the 411 dp screen, which Compose centres and overflows: `MixerSheetTest.theAnchorsFollowTheLayoutClassAtEveryWidth` read the master's right edge against the root's width (now the sheet's), `…landscapeSixHundredByFourHundred…` and `TimelineScreenTest.theExpandedToolbarCarriesTheMixerButton` asserted controls *displayed* off the screen's edge (now their bounds inside the sheet, or existence), `…aLayoutClassChangeKeepsTheSheetOpen…` counted the first *displayed* strip (now the first inside the row's bounds) | 2026-11-08, 17 min 27 s (the owner's go of 2026-11-08; T039/T043/T055/T065/T074/T080's device part). The `:shared:ui` rerun after the fixes follows |
| Pixel 6 Pro (`19021FDEE001AH`), `:shared:ui` | `tools/gradlew-device.sh 19021FDEE001AH :shared:ui:connectedAndroidDeviceTest` | green | 1,220 UI tests, 0 failures, 4 skipped (the Phase 7 tests after the screen-width fixes, `MixerStripRowTest` (2) and the ten open/close cycles of Phase 8) | 2026-11-08, 16 min (T088 device part closed; the device half of T039/T043/T055/T065/T074/T080) |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | `StressProjectRequestTest` (3: the argument, the seeder's one build after the startup with one loop per track, nothing on a second launch), `PerfAnchorsCatalogueTest` at 67 anchors, `TimelineViewModelTest` (+2: the window button and the capability), `MixerWindowViewModelTest` (3: the strips follow the session, a drag in the window is one undo step, the state ends with the project) | 2026-11-08 after Phases 8 and 9 (code) |
| iPhone 18 Pro simulator, every module | `checkIos` | green, zero warnings | `MixerStripRowTest` (2: 24 strips with the master pinned, a removed track keeps the row's place), `MixerSheetTest` (+3: ten open/close cycles settle within the 300 ms tween plus four frames with 24 strips, the handle's window button with and without the capability) | 2026-11-08 after Phases 8 and 9 (code). On the way: the cycles test counted frames by hand (the request's frame, the effect's start and two that report the animation over) |
| Perf pipeline (Python) | `cd app/perf && uv run pytest` | 367 passed, 1 skipped | the `mixer` journey in every registry, the two mixer counters, the profile fixtures regenerated with a `mixer` method (only additions), the report golden regenerated (only the two new catalogue entries) | 2026-11-08 (T108) |
| Tembra_Desktop_API37 (`emulator-5582`, Android 17 desktop, user 10, `-no-audio`), `:androidApp` | `tools/gradlew-device.sh emulator-5582 :androidApp:connectedDebugAndroidTest` | 50 tests, 4 failures, all feature 011's full-screen tests | `AndroidWindowCapabilitiesTest` green (the second free-form window opens in its own task and reports back: the T100 spike's go, `../evidence/window-spike.md`); the four failures are `FullScreenTest`, `WindowResizeAudioTest` × 2 and `WindowForegroundTest` timing out on the Android 17 preview's full-screen and minimise transitions (feature 011 recorded the shell's "denied by system policy" quirk on this emulator), not the mixer's window: rerun of the four classes below | 2026-11-08, 36 min (T100, T102) |
| Pixel 6 Pro + Pixel Tablet, captures | `capture_android.py` (adb, by test tag) after `:androidApp:installDebug` on each | 31 files | `android/README.md`: six states at 360/600/840 dp (plus font 2.0) on the Pixel 6 Pro, 1024/1200 dp on the tablet; the clipped and armed states are live (meters, latches, the input meter); one defect found: the master label clipped to "Mast" at font 2.0 (now ellipsised) | 2026-11-08 (T089, the devices' part) |
| Tembra_Desktop_API37, `FullScreenTest` alone | `tools/gradlew-device.sh emulator-5582 :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=audio.tembra.android.desktop.FullScreenTest` | green | the same test that timed out in the full lane passed once the Settings and Files windows other tests had left open were force-stopped and the three animation scales were set to 0 (the state feature 011 recorded for this emulator: "window animations off"); the full lane had run with animations on and those windows in front. The other three classes rerun next | 2026-11-08 |
| Tembra_Desktop_API37, `WindowResizeAudioTest` + `WindowForegroundTest` | the same, `…class=audio.tembra.android.WindowResizeAudioTest,audio.tembra.android.WindowForegroundTest` | green | the three other tests of the full lane's four pass in the same prepared state: the emulator's `:androidApp` suite is green for the branch once the other windows are closed and animations are off | 2026-11-08 |
| Tembra_Desktop_API37, quickstart §12 by hand | adb by test tag and content description, `am task resize` | done | `android/desktop-api37/README.md`: open from the handle, beside the timeline, a fader in one read in the other, undo in the timeline, X brings the window forward, the timeline window's close closes both (the caption button; Back only backgrounds the task on Android 12+). The timeline window's close finishing the mixer window was added during the walkthrough (`MainActivity.onDestroy` → `AndroidWindowCapabilities.closeMixerWindow`) | 2026-11-08 (T105) |
| iPhone 18 Pro, iPad Pro 13-inch (M5), iPhone Duo simulators, captures | `xcodebuild test-without-building -xctestrun …TembraPerf…` with `TEST_RUNNER_TEMBRA_CAPTURE_DIR` in the environment (the Mac muted) | 21 files | `ios/README.md`: seven states per simulator (the iPad's settings inline); the environment form is the one the test runner sees, build-setting arguments skip the matrix; landscape needs a relaunch after the rotation (Compose keeps the portrait accessibility frames); the iPhone's landscape state is missing: its toolbar sits above the screen in landscape (pre-existing, see "Findings for the owner") | 2026-11-08, T090 |
| Pixel 9 Pro Fold emulator (`emulator-5584`, `-no-audio`), captures | `capture_android.py` after `:androidApp:installDebug`, open and closed (`adb emu fold`) | 11 files | `android/README.md`: the open posture's six states, the closed posture's five (folding locks the screen: swipe keyguard dismissed first; the first taps after a restart need retries on this emulator; the closed landscape is an emulator quirk kept as evidence) | 2026-11-08, T089 |
| Pixel 6 Pro, manual mixer session with `adb logcat -s StrictMode` | `strictmode_session.py` (adb by test tag, five minutes, playback with the music stream muted) | 10 `StrictMode` lines, all at the app's start | `../evidence/main-thread-audit.md` §4: ten `DiskReadViolation`s from the DI graph's construction in `MainActivity.onCreate` (pre-existing), none during the mixer's use | 2026-11-08, T121 |
| Pixel 6 Pro (`19021FDEE001AH`), `:shared:ui` | `tools/gradlew-device.sh 19021FDEE001AH :shared:ui:connectedAndroidDeviceTest` (run conditions applied; `adb logcat -s StrictMode` beside it) | green | 1,231 UI tests, 0 failures, 18 min 36 s, after the master label's ellipsis, the mixer window's anchor root and the timeline window's close hook; zero `StrictMode` lines during the lane (`../evidence/main-thread-audit.md` §4) | 2026-11-08 |
| iPhone 18 Pro simulator, every module | `checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` | green, zero warnings | after the master label's ellipsis, the mixer window's anchor root, the timeline window's close hook and the screenshot matrix's landscape relaunch | 2026-11-09 |
| iPad Pro 13-inch (M5) simulator, every module | `checkIos -Ptembra.iosSimulator="iPad Pro 13-inch (M5)"` | green, zero warnings | 1,231 UI tests in `:shared:ui`, 0 failures (T118) | 2026-11-09 |
| iPhone Duo simulator (`8AF0F092-…`), every module | `checkIos -Ptembra.iosSimulator=8AF0F092-…` | green, zero warnings | 1,231 UI tests in `:shared:ui`, 0 failures (T118) | 2026-11-09 |
| Host (JVM, every module) | `checkAndroidHost` | green, zero warnings | after the sheet's focus-on-open (T092's finding) | 2026-11-09 |
| iPhone 18 Pro simulator, every module | `checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` | green, zero warnings | 1,232 UI tests in `:shared:ui`, 0 failures: `MixerSemanticsTest.openingTheSheetUnderAKeyboardFocusesTheHandle` (+1) | 2026-11-09 |
| iPhone 18 Pro simulator, every module | `checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` | green, zero warnings | 1,233 UI tests, 0 failures: `TimelineScreenTest.theSheetTakesAndReturnsTheKeyboardFocus` (+1, the focus return to the Mixer button) | 2026-11-09 |
| Pixel 7 Pro (`33241FDH3000NZ`, Android 17, 420 dpi), every module | `tools/gradlew-device.sh 33241FDH3000NZ checkAndroidDevice --continue` (run conditions applied) | engine and data green; UI 1,228 tests, 1 failure, test-side | `TrackHeaderTest.expandedHeaderReadsThePanUnderTheGain` read the readout's height as exactly 44 dp: the lane row's height rounds to whole pixels and the readout is 114 px = 43.4 dp on this 2.625× screen; the test now accepts 44 dp within a pixel. The class reran green (12 tests), 19 min 54 s + 32 s | 2026-11-09, T112 |
| Pixel Tablet (`3621105H803UFX`, Android 17, 2560 × 1600), every module | `tools/gradlew-device.sh 3621105H803UFX checkAndroidDevice --continue` (run conditions applied) | green, zero warnings | engine 114, data 156, UI 1,229 tests, 0 failures, 20 min 4 s | 2026-11-09, T113 |
| OPPO CPH2239 (`O7PBYLZP4HMBW8S4`, Android 11, 360 × 800 dp), every module | `tools/gradlew-device.sh O7PBYLZP4HMBW8S4 checkAndroidDevice --continue` (run conditions applied; the brightness refused as always) | engine 114 (2 recording tests skipped as in 010), data 156 green; UI 1,229 tests, 1 failure, test-side | `TimelineToolbarTest.theMixerButtonIsInTheFullRowAndNotItsOverflow` composes the expanded toolbar unconstrained, so the full row (about 655 dp) overflows this 360 dp screen and the Mixer button's injected touch landed off-screen; the test clicks that row's buttons through their semantics action now. The class reran green (6 tests), 16 min 8 s + the rerun | 2026-11-09, T114 |
| Redmi Note 5 (`e9446ac2`, Android 9), every module | `tools/gradlew-device.sh e9446ac2 checkAndroidDevice --continue` (run conditions applied; do-not-disturb refused as always) | green, zero warnings | engine 114, data 156, UI 1,229 tests, 0 failures, 6 skipped; 12 min | 2026-11-09, T115 |
| Tembra_Desktop_API34 (`emulator-5580`, Android 14 desktop, 16:9, no touchscreen), `:androidApp` | `tools/gradlew-device.sh emulator-5580 :androidApp:connectedDebugAndroidTest` (animation scales 0, other windows closed) | 50 tests green across the runs | the first run: 13 failures from one cause: Android 14 keeps a closed free-form window's task in the app's task list as not running (verified by hand: the caption close drops it from `am stack list`, recents keeps it), so `AndroidWindowCapabilitiesTest`'s close assertion failed and the leftover tripped twelve desktop tests' one-task precondition; `DesktopWindow` counts running tasks now and the window test waits for its counts (green alone). The two `WindowResizeAudioTest` cases (011) fail on an emulator booted with `-no-audio` (the playhead 0.6 % short over 65 s, one click short of 89: no audio clock behind the engine) and pass once the emulator runs with its audio backend (the Mac muted) | 2026-11-09, T117 |
| Tembra_Desktop_API37 (`emulator-5582`, Android 17 desktop, user 10, audio backend on), every module | `tools/gradlew-device.sh emulator-5582 checkAndroidDevice --continue`, then `:shared:ui:connectedAndroidDeviceTest` alone after the emulator's reboot with `-gpu host` | engine 114 and data 156 green; UI 1,229 tests: 32 failures on the first run, 0 failures (2 skipped) on the rerun | the 32 were pixel captures that never arrived: `captureToImage` reporting "Failed waiting for PixelCopy!" or its forced redraw not within 2 s, plus three `runTest` time-outs, spread over the whole run (positions 5 to 1,096 of 1,229), and the UI module alone took 3 h 36 min; a rerun of the 23 classes failed 22 other captures the same way in 58 min. Cause: `avdmanager` creates the AVD with `hw.gpu.enabled=no`, so the emulator renders through SwiftShader (`dumpsys SurfaceFlinger`: "SwiftShader Device (LLVM 10.0.0)") at 2880 × 1800. Booted with `-gpu host` (the Mac's GPU through Metal) the whole UI module ran green in 5 min 49 s. The three AVDs' `config.ini` now carry `hw.gpu.mode=host`, the README's setup and CLAUDE.md say so; not an app finding | 2026-11-09, T117 |
| Pixel_9_Pro_Fold (`emulator-5584`, Android 16, 2076 × 2152 inner display, host GPU, audio backend on, animation scales 0), every module | `tools/gradlew-device.sh emulator-5584 checkAndroidDevice --continue` | green, zero warnings | engine 114, data 156, UI 1,229 tests, 4 skipped, 0 failures, 6 min 47 s. The emulator was rebooted with `-gpu host` first: it had been rendering through SwiftShader like the desktop AVDs (the Android 17 desktop row above) | 2026-11-09, T116 |

## Phase 7 notes (User Story 5, 2026-11-06)

- The strip's keys are catalogue entries now (`ShortcutScope.Mixer`, nine actions): the shortcut sheet lists them as a
  "Mixer" group after the Timeline's. `M` and `O` appear under both groups by design (the Timeline's act on the selected
  track from anywhere; the Mixer's on the focused strip, and consume first).
- A strip's number and name select the track by tap and through a screen reader but are no Tab stops
  (`canFocus = false`): Tab walks handle → (knob) → Solo → Mute → fader per strip → master fader. The knob sits above
  the toggles in the full form, so it comes first, not after Mute as the contract line says (corrected with T120).
- The strip width stays 72 dp at every font size; the labels grow in height and the name ellipsises. A `widthIn(min)`
  would let a long name widen the strip inside the `LazyRow` instead.
- Skia's `TextLayoutResult.isLineEllipsized` is false on iOS for an ellipsised line; `hasVisualOverflow` is true.
- T092's iOS half is the lanes above (the Compose tests drive Tab, the arrows, Page, Home, M, O, Escape, hover,
  tooltips, the wheel and the secondary click on the iPad and the Duo); the desktop emulator pass, the T089/T090
  captures and the T091 comparison wait for the emulator lane (one Gradle build at a time).

## T091: the captures against the Stitch mixer screens and `theme.json` (2026-11-08)

References: `design/stitch/screens/mixer-timeline/phone-drawer/screenshot.jpg` (the sheet labelled MIXER rising over the
lanes with a master fader and a stereo meter) and `tablet-split-console/screenshot.jpg` (numbered strips: meter, VU dial,
knob, Solo, Mute, a scaled fader, the name at the foot). Captures: `android/pixel-6-pro/*`, `android/pixel-tablet/*`.

| Element | Design | App | Verdict |
|---|---|---|---|
| The sheet | a grabber row labelled MIXER over the lanes, `surface-level-2`, rounded top | the same: the 32 dp handle with the pill and "Mixer", 16 dp top corners, the seam | matches |
| Strip order | number, meter, VU, knob, Solo, Mute, fader (scale beside), name | number, VU (tall sheets), knob, S/M, fader with the meter beside it (research R2's "dual LED meter beside the fader"), name | matches R2; the console's meter-above-dial order is the Studio Sound screen's, not this design system's |
| Solo and Mute | "Solo"/"Mute" pills | the header's `S`/`M` squares (FR-010: the same component and state as the track headers) | deviation by decision (plan, R2), recorded for T124 |
| Fader | lavender rail, dark handle with a line, scale marks | `primary` lit rail, `surface-container-high` 44 × 28 dp handle, `micro-label` marks | matches `theme.json` |
| Meters | two bars, body/warning/clip | `meter_body` green to −6 dB, `meter_warning` above, `meter_clip` square (lit in `360dp-mixer-clipped`) | matches the tokens |
| VU dial | on every strip | only on sheets ≥ 480 dp tall (`FullNoVu` below): none on the phone or the 640 dp-tall tablet screen in these captures | by FR-025's drop order; shows on tall windows (the Duo's open posture, the desktop window) |
| Master | pinned at the right, labelled | pinned on `surface-level-3`, "Master", the percent readout | matches |
| Typography and spacing | `tabular-param` numbers, `label-md` names, 72 dp strips | the same tokens; the names ellipsise at font 2.0 | matches; one fix from the captures: the master label now ellipsises too |

No token or spacing change was needed beyond the master label's overflow.


## T091 continued: the iOS and fold captures against the design (2026-11-08)

| Capture | Verdict |
|---|---|
| iPhone 18 Pro half / full / clipped / armed / settings / largest font | the same tokens as the Android set; the full sheet is the full form (dials and knobs) on the 874 pt screen; "Master" ellipsises at the largest font ("Mast…", the fix of the Android round); the clipped master latch lights as on Android |
| iPad Pro 13-inch half | the expanded half anchor leaves 192 dp of lanes (data model §5), so the sheet is about 1,050 dp tall and still the compact form: faders over 900 dp long. Spec-conformant, visually odd; listed below for T124 |
| iPad Pro 13-inch full / landscape / settings inline | the full form with dials and knobs; landscape is the two-anchor expanded layout at the half anchor (compact strips about 500 dp tall); the inline settings carry the pan row at the design's position |
| iPhone Duo (closed, upright) | the end-rail layout keeps the sheet's geometry; four strips with the master pinned over the row's end at 466 pt; nothing of the mixer collides with the rail |
| Pixel 9 Pro Fold open | the expanded layout with the rail; half compact, full with dials; identical to the Pixel Tablet's forms |
| Pixel 9 Pro Fold closed | the compact layout at 443 dp: the half sheet shows four strips and the master; the landscape capture shows an emulator rendering quirk (the window in the left 890 px), not compared |

## Findings for the owner (T124) and follow-ups

- **The half anchor on tall screens.** `StripForm.of` returns the compact form at the half anchor whatever the
  sheet's height (data model §5). On the iPad Pro in portrait the half sheet is about 1,050 dp tall; on the Pixel
  9 Pro Fold open, about 290 dp; on the iPad in landscape about 500 dp. Candidates: let the form follow the
  height at the half anchor too (the full form from 480 dp), or cap the expanded half anchor so more lanes stay
  visible on tall windows. The owner's call; nothing changed tonight.
- **iPhone landscape: the toolbar sits above the screen.** In landscape on the iPhone 18 Pro simulator the timeline's
  toolbar row is laid out above the window's top (XCUITest computes the overflow button's hit point at y = −7; the
  probe screenshot shows the ruler at the very top and only the pill's lower edge): the Mixer button cannot be
  reached, so `ios-mixer-landscape` is missing for the iPhone. The branch changes no inset code (the diff against
  `main` has none), so this looks pre-existing (the single-anchor landscape layout of 009/011); verify on `main`
  and open a fix outside this feature if it reproduces there.
- **X with the focus on the navigation rail.** On the desktop emulator a mouse click on a rail item leaves the focus
  on the rail; X then does nothing (the Timeline scope's handler sits under the screen, not the shell), the sheet
  opens once the focus is back inside the timeline (a click on the ruler). 011's scope model decides whether the
  shell should route the Timeline shortcuts while the rail has focus; recorded, not changed.
- **Fixed from the same pass (2026-11-09):** X opened the sheet but left the focus in the toolbar, so Tab walked the
  toolbar and every track header before the first strip (about 35 stops on the demo, 150 on 24 tracks). The sheet now
  moves the focus to its handle when it opens under a keyboard (`MixerSheet`, `LaunchedEffect(ui.open)` gated on the
  keyboard input mode; `MixerSemanticsTest.openingTheSheetUnderAKeyboardFocusesTheHandle`); touch devices are
  untouched.
- **TalkBack and VoiceOver (T093)** are left to the owner: the screen readers speak, and the run tonight had to stay
  silent.

- **The stress project clips the master.** With every clip reading its own frames (`fffcc1a4`), the 24-track stress project, the demo's four loops six times over at unity, sums about 15.6 dB above one copy: `audio.master_clips` reads 14,760 clamped blocks an iteration and the master latch lights (`../evidence/trial-round.md`, round 9). Expected physics, informational in the catalogue; a fixture that sums sanely (each imported track about −15.6 dB) is a choice for the owner.

## The lane tasks of Phases 2–7 (T039, T043, T055, T065, T074, T080, T088)

Marked done on 2026-11-08 against the lane table above: every phase's host, iOS and Pixel 6 Pro lanes ran green
(the Pixel lane once for all of them on 2026-11-08, its four test-side fixes recorded), the iPad and Duo lanes after
Phase 7. The captures those tasks name (`pixel6pro/mixer-half-360.png` and friends) are the Pixel 6 Pro files
`pixel-6-pro/360dp-mixer-*.png` of T089. The by-hand steps with headphones (quickstart §6 and §7, "announced") were
not performed under the no-sound rule: they fold into T111 (the loopback measurements, announced) and T124.

## T092: the keyboard and pointer pass on `Tembra_Desktop_API34` (2026-11-09)

`emulator-5580` (Android 14 desktop, 3840 × 2160 at 320 dpi, no touchscreen), the branch's debug build in a 1600 × 950 dp
free-form window, driven over adb: keys with `input keyevent`, clicks with `input mouse tap` (a mouse-sourced tap the
window always takes), the tree read with `uiautomator dump` (the focused node, `checked`, the header's gain text);
captures in `android/desktop-api34/`. The scratchpad script `t092_api34.py` ran five times; the first runs found the two
focus gaps below, the last run verified the fixed build.

| Check | Result | Evidence |
|---|---|---|
| X opens the sheet from the timeline | pass: the sheet opens and the focus lands on the handle | `01-x-open.png`; "focus right after X => mixer.handle" |
| Tab order from the handle | pass: "Open in a window", then Solo, Mute, fader per strip (the compact form has no knob), then the next strip; the master after the last | the 11-Tab walk read `mixer.window.open, solo.0, mute.0, fader.0, solo.1, …` |
| Arrow keys on the focused fader | pass: Up, Up → +1.0 dB; Down → +0.5 dB (0.5 dB steps, FR-028) | the Drums header's readout; `02a-fader-after-up-up.png` |
| Page Up / Page Down / Home on the fader | pass: Page Up → +6.0 dB (the fader's top), Page Down → +0.0 dB, Home → 0 dB | the header's readout |
| M and O on the focused strip | pass: Mute checked true then false, Solo checked true then false | `02b-after-M.png`, `02c-after-O.png` |
| Enter on the handle | pass: the sheet goes to its full height; the knobs and dials appear | `03-full-form-after-enter.png` |
| Tab to the knob, Right, Left, Page Up, Home | pass: two Tabs from the handle reach `mixer.strip.pan.0` (the window button between); the keys turn the indicator; the focus shows the "Drums, pan" tooltip | `03a-knob-after-right-right.png`, `03b-knob-after-page-up.png` (the readout lives in the state description, which the dump does not print; the step sizes are `PanKnobTest`'s) |
| Escape | pass: the sheet closes and the focus returns to the toolbar's Mixer button, so X reopens it | "Escape closes => True; X reopens => True" |
| Back | pass in two presses: with a control focused, Compose's first Back clears the focus (the platform's keyboard convention); the second closes the sheet; X reopens it | `05-after-back.png` (the sheet open, no focus ring) |
| Hover, tooltips on hover, the wheel, a secondary click, a pointer drag, the row's scrollbar | not verifiable on this emulator tonight: the virtio tablet's `sendevent` stream reaches the kernel (`getevent` shows it) but no longer the app after 22:40 (one early run worked); `input` cannot hover, scroll or right-click. Covered by the Compose tests on every target (`FaderTest`/`PanKnobTest` tooltip and hover layer, `MixerStripTest` secondary click, `MixerSheetTest` scrollbar, the wheel in the control's own steps) and by 011's hover parity on API37 | `04-hover-tooltip.png`, `06-secondary-click.png`, `08/09-row-*.png` show the untouched state |
| 24 strips | the sheet opens on the stress project (`--es tembra.stress.tracks 24`), 19 strips and the master visible at 1600 dp; `uiautomator dump` never settles while it is open (the meters redraw every frame), so the row's checks are the Compose tests' | `08-row-24-strips-hover.png` |

Two gaps the pass found and the fixes (both in `MixerSheet`, lanes green on host, iPhone 18 Pro, Pixel 7 Pro's header class):

1. X opened the sheet but left the focus in the toolbar, so Tab walked the toolbar and every track header before the
   first strip (about 35 stops on the demo, 150 on 24 tracks). Under the keyboard input mode the sheet now requests the
   handle's focus when it opens (`MixerSemanticsTest.openingTheSheetUnderAKeyboardFocusesTheHandle`).
2. Closing the sheet with Escape left the focus nowhere, so X did nothing until a click. The sheet now hands the focus
   back to the toolbar's Mixer button when it closes under the keyboard (`TimelineScreenContent` passes the button's
   `FocusRequester`; `TimelineScreenTest.theSheetTakesAndReturnsTheKeyboardFocus`). Touch devices are untouched: both
   requests are gated on `InputMode.Keyboard`.

The iOS half of T092 is the Compose UI lanes on the iPad Pro and the Duo (the same tests drive Tab, the arrows, Page,
Home, M, O, Escape, hover, tooltips, the wheel and the secondary click): green on 2026-11-09 with the fixes.

## Merged main (2026-11-09)

`origin/main` moved on during the implementation: `3185b8a2` merges it into the branch (no conflicts; the three
overlapping files were documentation). What came in: the R8 keep rules moved to the `keepRules` source set with the
R8 configuration analysis (#17), `androidx.test.uiautomator` 2.4.1 (#18), `androidx.window` 1.5.1 (#19) and Metro
1.5.1 (#20). The lanes rerun on the merged state:

| Target | Command | Result | Notes | When |
|---|---|---|---|---|
| Host (JVM) | `./gradlew checkAndroidHost` | green, zero warnings | 45 s | 2026-11-09 08:20 |
| Tembra_Desktop_API37 (`emulator-5582`, host GPU, audio on), every module | `tools/gradlew-device.sh emulator-5582 checkAndroidDevice --continue`, then `:shared:engine:connectedAndroidDeviceTest` alone | data 156 and UI 1,229 (2 skipped) green; engine 114 with one failure, then 114 green on the rerun | `NativeAudioEngineTest.theStreamProbeSeesAnOpenOutput` missed its one-second window once ("the probe did not see the tone's output stream"): the emulator's output thread had gone to standby three seconds before the tone started (`dumpsys media.audio_flinger`), and the first stream open after standby took longer than the probe waits. Test-side timing on an emulator, not a change of this merge (feature 011 already made this probe compare players by identity) | 2026-11-09 08:22 |
| Pixel_9_Pro_Fold (`emulator-5584`, host GPU, audio on), every module | `tools/gradlew-device.sh emulator-5584 checkAndroidDevice --continue` | green, zero warnings | engine 114, data 156, UI 1,229 (4 skipped), 6 min 51 s | 2026-11-09 08:30 |
| iPad Pro 13-inch (M5) simulator | `./gradlew checkIos -Ptembra.iosSimulator=<udid>` | green, zero warnings | core 262, engine 115, data 156, UI 1,233 tests; 1 min 23 s with the Kotlin/Native test binaries already built | 2026-11-09 08:39 |
| Pixel 7 Pro (`33241FDH3000NZ`, Android 17), every module | `tools/gradlew-device.sh 33241FDH3000NZ checkAndroidDevice --continue` (run conditions applied) | green, zero warnings | engine 114, data 156, UI 1,229 (4 skipped), 21 min 11 s | 2026-11-09 10:42 |
| Pixel Tablet (`3621105H803UFX`, Android 17), every module | `tools/gradlew-device.sh 3621105H803UFX checkAndroidDevice --continue` (run conditions applied), twice | first run: engine 114 with one failure, UI stopped at 562 of 1,229 ("Test run failed to complete"); rerun: engine 114, data 156, UI 1,229 (4 skipped) green, zero warnings, 21 min 22 s | the first run's two problems: `NativeAudioEngineRecordingTest.armsRecordsATakeWithTheCountInAndDisarms` read an output latency of 0 from the duplex stream (the same test was green on this tablet before the merge and on the rerun), and the UI instrumentation ended after `ProjectsKeyHandlingTest.ctrlFFocusesTheSearchField` with no crash, kill or adb disconnect in the logs; both an hour after the USB hub had dropped every phone. Not reproduced | 2026-11-09 11:20 |

Not rerun on the merged state, by choice: the OPPO CPH2239 and the Redmi (API 28), whose lanes were green on the
branch the same morning and which none of main's four changes targets (Metro's codegen is covered by the host lane
on every module, `androidx.window` 1.5.1 by the tablet and the two desktop emulators, the keep-rules move by the
release build below, `uiautomator` 2.4.1 by the perf journeys on the Pixel 6 Pro); the iPhone 18 Pro and Duo
simulators, whose Kotlin/Native test binaries are the ones the iPad ran. The Pixel 6 Pro's lane on the merged state
follows the trial rounds (the device was measuring).

## Release builds (T119)

The way users get the app: `tools/release-ios.sh` (Release configuration, every connected device and booted
simulator) and `tools/release-android.sh` (the `release` type signed with the debug key, the committed baseline
profile compiled). Built on the merged branch (`3185b8a2` and after).

| Target | OS | Build | Outcome | When |
|---|---|---|---|---|
| iPhone 18 Pro simulator | iOS 27.0 | Release, iphonesimulator | installed as `com.tembraaudio<team>`, launched | 2026-11-09 10:25 |
| iPad Pro 13-inch (M5) simulator | iOS 27.0 | Release, iphonesimulator | installed, launched | 2026-11-09 10:25 |
| iPhone Duo simulator | iOS 27.1 | Release, iphonesimulator | installed, launched | 2026-11-09 10:25 |
| iPhone 14 (`00008110-001049660E3A401E`) | iOS 27.2 | Release, iphoneos | installed, launched (the script warned that iOS 27.2 is newer than the installed Xcode's SDK; the install went through) | 2026-11-09 10:25 |

The script's check is the launch; the mixer on the Release build was not opened by the agent (the Perf build's
screenshot matrix opened it on the same three simulators, `ios/README.md`). The Android rows follow once the USB
hub returns the phones (dropped at 08:44, see the trial round).

## After the clip-source fix (2026-11-09, `fffcc1a4`)

The trial round's stress journey found `ClipSourceRegistry` sharing one engine source among every clip of an asset
(`../evidence/trial-round.md`, round 8); the fix touches `:shared:ui`'s session, so the lanes ran again:

| Target | Command | Result | Notes | When |
|---|---|---|---|---|
| Host (JVM) | `./gradlew checkAndroidHost` | green, zero warnings | the nine new tests (mapper, registry, session) with the suite | 2026-11-09 14:30 |
| iPad Pro 13-inch (M5) simulator | `./gradlew checkIos -Ptembra.iosSimulator=<udid>` | green, zero warnings | UI 1,242 tests, 1 min 24 s | 2026-11-09 14:36 |
| Tembra_Desktop_API37 (`emulator-5582`, local adb server on 5038), every module | `tools/gradlew-device.sh --adb-server-port 5038 emulator-5582 checkAndroidDevice --continue`, then `:shared:engine:connectedAndroidDeviceTest` alone | data 156 and UI 1,238 (2 skipped) green; engine 114 with one failure, then 114 green | `MetronomeTimingEndToEndTest.ticksAreFiveHundredMillisecondsApartAndTheFirstArrivesQuickly` read spacings of 488 to 516 ms once (fitted period 498.8 ms) on the emulator's audio backend; green on the rerun and in every earlier lane today | 2026-11-09 15:02 |
| Pixel_9_Pro_Fold (`emulator-5584`, local adb server on 5038), every module | `tools/gradlew-device.sh --adb-server-port 5038 emulator-5584 checkAndroidDevice --continue` | green, zero warnings | engine 114, data 156, UI 1,238 (4 skipped); 18 min with the Mac also pulling a round's traces | 2026-11-09 15:25 |
