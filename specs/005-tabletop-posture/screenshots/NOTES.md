# Feature 005 — screenshots and manual checks

All captures 2026-10-02. The fold captures come from the Pixel 9 Pro Fold emulator (`127.0.0.1:5556`, AVD
`Pixel_9_Pro_Fold`, Android 16), inner display `4619827259835644672` (`adb exec-out screencap -p -d <id>`; plain
`screencap` refuses because the emulator has two displays). Native 2152 × 2076 px at 390 dpi = an 883 × 852 dp window.

## T001 — the posture API

`currentWindowAdaptiveInfo().windowPosture` (`Posture`, `HingeInfo`) compiles from `commonMain` of `:shared:ui` against
`org.jetbrains.compose.material3.adaptive:adaptive` 1.2.0; the iOS klib (`adaptive-uikitSimArm64Main-1.2.0.klib`) carries
`isTabletop` and `windowPosture` too, so the posture read needs no `expect`/`actual`. No catalog change.

## Driving the posture

| Posture | Command |
|---|---|
| Half-opened (tabletop when the display is rotated so the fold is horizontal) | `adb shell cmd device_state state 1` |
| Flat | `adb shell cmd device_state state 2` |
| Back to the emulator's own state | `adb shell cmd device_state state reset` |
| Book posture (fold vertical) | `settings put system accelerometer_rotation 0; settings put system user_rotation 0` (restored to `3` / `1` afterwards) |

The emulator was already half-opened and rotated (`ROTATION_90`) when the session began, i.e. in tabletop.

## Captures

| File | What it shows |
|---|---|
| `android-fold-tabletop-home-before.png` | Before the feature: the shell on the fold in tabletop (wide layouts cross the fold) |
| `android-fold-tabletop-{autolisten,byear,metronome}-r1.png` | First build: correct split; the lower tuner card was one lonely selector row and the metronome's lower half was lopsided (settings top-aligned, transport centred) |
| `android-fold-tabletop-autolisten.png` | Final: meter (test signal `e2-flat-20`) above the fold; TUNER CONTROLS card with the TEST SIGNAL pill, equal-width Guitar/Cello and Auto Listen/By Ear below |
| `android-fold-tabletop-byear.png` | Final: six string cards in two rows above; selectors, 6/7-String, tuning preset and loop tone below |
| `android-fold-tabletop-metronome.png` | Final, stopped: dial (ring only) beside the tall accent cells above; bar-shape card + sound pill and slider + tap + play in two equal cards below |
| `android-fold-tabletop-metronome-running.png` | After ten flat ↔ half-opened changes while running at 122 BPM: still running ("Stop metronome"), beat 1 lit |
| `android-fold-flat-metronome-running.png` | Unfolded flat while running: the unchanged Wide layout, beat 3 lit, same tempo |
| `android-fold-book-autolisten.png` | Book posture: the unchanged Wide tuner layout |
| `android-fold-tabletop-*-largefont.png` | Font scale 2.0 in tabletop: each half keeps to its side of the fold; the Auto Listen meter scrolls inside the upper card (the landscape rule), the rail's labels break mid-word (pre-existing, feature 002) |

## Manual checks (quickstart §2)

| Check | Result |
|---|---|
| Posture detected on the real emulator, split at the fold's actual position (upper card ends 12 dp above, lower starts 12 dp below) | Pass |
| SC-002 metronome: ten flat ↔ half-opened changes at 1.2 s each while running | Pass: kept running, tempo and settings unchanged |
| SC-002 tuner: ten changes while listening to the test signal | Pass: reading kept updating (81.5 Hz, Flat) |
| SC-003 layout follows within a second | Pass: each capture taken ≤ 1.2 s after the state change |
| Book posture, flat | Existing Wide layouts |
| SC-005 font scale 2.0 | Pass with scrolling inside the regions |

## Regression (T023)

| Target | Capture | Result |
|---|---|---|
| Pixel 6 Pro (Android 17) | `android-pixel6pro-{autolisten,byear,metronome}.png` | Unchanged Stacked layouts; the compact metronome card keeps its 64 dp cells |
| iPhone 17 simulator | `ios-iphone17-autolisten.png` | Unchanged Stacked tuner |
| iPad Pro 13-inch (M5) simulator | `ios-ipadpro-autolisten.png` | Unchanged Wide tuner |

The iOS metronome was not captured by hand (no CLI navigation to it on the simulator); its existing layout tests pass
on both simulators unmodified (below).

## Lanes (T022)

| Lane | Target | Result |
|---|---|---|
| `checkAndroidHost` | JVM | Green, zero warnings (`TabletopFoldTest` 6, `FoldSplitGeometryTest` 6, two new layout cases) |
| `checkIos` | iPhone 17 | Green, zero warnings (tabletop UI tests: tuner 5, metronome 5) |
| `checkIos` | iPad Pro 13-inch (M5) | Green: 473 `:shared:ui` tests, 0 failures |
| `checkAndroidDevice` | Pixel 6 Pro | `:shared:ui` 465/465, core 76, data 39, engine 56 — all green (first run: 224 UI failures while the phone dozed; `svc power stayon usb` set, rerun green) |
| `checkAndroidDevice` | Pixel 9 Pro Fold emulator | `:shared:ui` 465/465, core, data green; engine 2 failures: `MetronomeTimingEndToEndTest` (spacings 490.8–515.0 ms against its 500 ± 1 ms bound; the emulator's audio clock) and `NativeAudioEngineTest.aMetronomeStopLeavesAToneItsFocus` (Idle, not Tone). The engine sources are identical to `main`; both are the emulator's audio path, not this feature |

Final device run after the T029 change: `:shared:ui` 466/466 on both the Pixel 6 Pro and the fold emulator (the new
narrow test included), core and data green on both, engine green on the Pixel; on the emulator only
`MetronomeTimingEndToEndTest` failed again (same emulator audio clock; `aMetronomeStopLeavesAToneItsFocus` passed this
time).

## Review (T027) and compiler report (T026)

- Review finding fixed (T029): a flip-style foldable (~412 dp) half-opened is tabletop too; side by side the metronome's
  cards got ~190 dp and 7/8 cells wrapped into two rows of tall bars that overflowed. Under 600 dp each half now stacks.
- `-PcomposeReports=true`: `FoldSplit`, `TunerTabletopSettings`, `TabletopMetronome`, `TabletopDialCard`,
  `FilledAccentCard`, `AccentCard` are restartable and skippable; `TabletopFold` is stable.
- `FoldSplit` reads its window origin from an `onPlaced` write in measure only: one extra layout pass when first
  placed or moved, no recomposition (research R2).

## Stitch (T028)

Three HTML uploads mirroring the final captures, 883 × 852 instances in project `12040171611712589599`: Tuner Auto
Listen `11577983956293728617` (x 7600, y 1166), Tuner By Ear `15516489587312282918` (x 8600, y 1166), Metronome
`12033912777290214590` (x 3200, y 13317, the metronome row). Re-pulled to
`design/stitch/screens/{instrument-tuner,tuner-by-ear,metronome}/foldable-tabletop/`; the pulled HTML is byte-identical
to the upload.

## Still for a person

A physical foldable on a real table (hinge with height, real audio while folding), and the flip-style narrow form on a
real flip phone (only covered by `aNarrowTabletopStacksEachHalfAndKeepsEveryCellAboveTheFold`).

## Design iteration D1 (2026-10-02)

The user tried the first build on the emulator and kept these changes (spec "Design iteration D1", research R8,
tasks T030–T036):

| File | What it shows |
|---|---|
| `android-fold-d1-tabletop-autolisten.png` | Centred TUNER CONTROLS heading with the TEST SIGNAL pill at the end of its line; Guitar/Cello over Auto Listen/By Ear in one centred column |
| `android-fold-d1-tabletop-byear.png` | The same column with the tuning preset and loop tone stacked under the selectors (cello: no string count) |
| `android-fold-d1-tabletop-metronome.png` | One METRONOME CONTROLS card: 6/8 menu, tiles and Classic Wood on one line, STYLE, the slider, tap and play |
| `android-fold-d1-book-topbar-gap-before.png` | Book posture at 180°: the ~56 dp gap under the top bar (the cutout reported on the bottom edge) before FR-012 |
| `android-fold-d1-book-metronome-hinge.png` | Book posture after FR-011/FR-012: no gap; settings end at px 1008, the dial card starts at px 1068, centred on the hinge at px 1038 |

Lanes after D1: JVM and iPhone 17 green (new `VerticalFoldTest`, `MetronomeHingeLayoutTest`,
`theControlsCardIsHeadedAndReadsTopToBottom`, `theControlsStackUnderACentredHeading`), iPad Pro green; Android device: `:shared:ui`
474/474 on the Pixel 6 Pro and the fold emulator, core/data/engine green except the emulator's
`MetronomeTimingEndToEndTest` (the emulator audio clock, engine unchanged). Not done for D1: the Stitch screens still show the first build's lower regions; the
wide tuner's gauge still sits across a book-posture hinge (not requested).
