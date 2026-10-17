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

## Design iteration D2 (2026-10-02): the wide By Ear grid

| File | What it shows |
|---|---|
| `android-fold-d2-book-byear-cello-before.png` | Before: the wide grid spanning the whole card (~830 dp), 255 × 160 dp cards, the cello as 3 + 1 |
| `android-fold-d2-flat-byear-cello.png` | Opened flat: the cello as 2 + 2 square cards, centred in both directions |
| `android-fold-d2-flat-byear7.png` | Opened flat: seven strings 3 + 3 + 1, square, the last card centred, all visible |
| `android-fold-d2-flat-byear7-short-before.png` | `wm size 2076x1700` (852 × 697 dp) before the fit: the seventh card below the card's edge |
| `android-fold-d2-flat-byear7-short.png` | The same window after: 4 + 3 square cards, the short row centred, all visible |
| `ios-ipadpro-d2-byear.png` | iPad Pro simulator, By Ear: square cards, grid centred both ways |
| `android-pixel6pro-d2-byear.png` | Pixel 6 Pro portrait: unchanged two-column grid that scrolls |

Runs: the app was installed and launched on the iPhone 17 and iPad Pro simulators, the iPhone 14 (devicectl, process
4104; no CLI screenshot exists for the phone) and the Pixel 6 Pro (the microphone prompt answered "Only this time").
The emulator was reset with `wm size reset` after the short-window check.

Lanes after D2: JVM, iPhone 17 and iPad Pro green (`WideStringGridTest`, `wideCardsAreSquareInACentredGrid`,
`aShorterWideWindowFitsAllSevenCards`); Android device: `:shared:ui` 481/481 on the Pixel 6 Pro and the fold emulator,
core/data/engine green except the emulator's `MetronomeTimingEndToEndTest` (its audio clock; engine unchanged).

Stitch (T043): tabletop v2 screens Auto Listen `13960272211985517803`, By Ear `1287530494016647918`, Metronome
`862177940405487424` replace (and hide) the v1 uploads at the same canvas positions; new screens "Tuner By Ear (Foldable
Open)" `5540163438071420436` (x 9600, y 1166) and "Metronome (Foldable Book)" `6217065375771697420` (x 4200, y 13317).
All five pulled HTML files are byte-identical to the uploads; the v1 instances are hidden.

## Design iteration D3 (2026-10-02): the tuner's hinge split

| File | What it shows |
|---|---|
| `android-fold-d3-book-autolisten-before-heading.png` | First build: controls before the hinge, gauge after it; the heading clipped to "TUNER CONTROI" under the TEST SIGNAL pill |
| `android-fold-d3-book-autolisten.png` | After `CentredHeading`: the heading centred, the pill centred under it |
| `android-fold-d3-book-byear7.png` | By Ear: controls card (7-String, preset, loop tone) before the hinge; seven square cards two per row after it |

Runs: the app on the Pixel 6 Pro, the fold emulator (book posture: half-opened, rotation 0), the iPhone 17 and iPad Pro
simulators and the iPhone 14 (devicectl). Lanes: JVM, iPhone 17, iPad Pro green, zero warnings (one
`UNNECESSARY_SAFE_CALL` in `CentredHeading` fixed on the way). Android device, first run: the emulator 485/485, the
Pixel 6 Pro 2 failures, both test-host artifacts of its 412 dp window: an 852 dp host is centred about 220 dp left of
the window's origin, so `TunerHingeLayoutTest`'s fold (given in window coordinates as if the host sat at 0) left the
pane beside it ~178 dp wide; and `threeColumnsWhenWideTwoWhenStacked` forced the wide layout into a phone-width host,
where the grid now (D3) drops to two columns. The hinge tests now place the fold relative to the host and read bounds
relative to it; the column test uses an 830 × 740 dp host. Rerun: `:shared:ui` 485/485 on both the Pixel 6 Pro and the
emulator (core, data, engine as before; the emulator's `MetronomeTimingEndToEndTest` still fails on its audio clock).

## Recaptured 2026-10-16: the shell has no top bar

The navigation shell's top bar was removed on 2026-10-16 (spec 002 FR-007 reversed at the owner's request), so every
capture here that showed the shell was retaken from that day's build, under the same file name, on the same target
and in the same state. Content now starts right under the status bar (56 dp / pt higher). Other differences come from
features that landed after the original captures and are named below. `*-before*` images and captures from targets
that no longer exist were kept as they were. Android: Pixel 6 Pro `19021FDEE001AH`, the debug APK (no `tembra.perf`
property), `adb exec-out screencap`, app state set with the debug launch extras and UiAutomator taps. iOS: the Debug app
built with the Xcode 27.1 SDK on the iPhone 17 / iPad Pro 13" (M5) (iOS 26.5) / iPhone Duo (iOS 27.1) simulators,
`simctl io screenshot`, app state set with the debug launch arguments, the app's stored preferences (written with the
app stopped, backed up and restored) and real mouse clicks; rotation through the Simulator menu or Device Hub.

### Android, Pixel 6 Pro (4 retaken, 0 left)

| File | How | Differs beyond the top bar |
|---|---|---|
| `android-pixel6pro-autolisten.png` | portrait, `e2-flat-20` | noise gate (feature 006 came later), text-only selector |
| `android-pixel6pro-byear.png`, `android-pixel6pro-d2-byear.png` | portrait, By Ear, 6-String | text-only selector (the two are the same state, as before) |
| `android-pixel6pro-metronome.png` | portrait, Metronome stopped | no |

### iOS simulators (3 retaken, 0 left)

`ios-iphone17-autolisten` (guitar, `e2-flat-20`), `ios-ipadpro-autolisten` (iPad portrait, cello, `e2-flat-20`),
`ios-ipadpro-d2-byear` (iPad portrait, guitar 6, By Ear). Differs: the two Auto Listen ones now show the noise gate
control (feature 006).

### Android, Pixel 9 Pro Fold emulator (18 retaken, 3 left)

The Pixel 9 Pro Fold emulator was recreated the same afternoon (AVD `Pixel_9_Pro_Fold`, Android 16, Google APIs arm64, installed with `sdkmanager`), so its captures were retaken too: 18 retaken, 3 left.

Target: `emulator-5554` (AVD `Pixel_9_Pro_Fold`, Android 16, recreated today). Inner display id unchanged:
`4619827259835644672` (display 0, 2076 x 2152 px at 390 dpi). App: the debug APK
`app/androidApp/build/outputs/apk/debug/androidApp-debug.apk` (13:16 build, no `tembra.perf`), installed with
`adb install -r`, then `pm grant` RECORD_AUDIO and POST_NOTIFICATIONS. Captures with
`adb exec-out screencap -p -d 4619827259835644672`. Navigation by UiAutomator dumps and taps at node centres.

Initial emulator state (recorded before any change, restored at the end): `device_state` OPENED (2) with no override,
`accelerometer_rotation 1`, `user_rotation 0` (ROTATION_0), `font_scale 1.0`, no `wm size` override. Unlike the old
AVD, the new one boots flat and upright, not half-opened and rotated.

Postures used: flat = `cmd device_state state 2` + rotation 0; flat sideways = state 2 + rotation 1; tabletop = state
1 + rotation 1 (the old AVD's tabletop was ROTATION_90 as well); book = state 1 + rotation 0. Rotation locked with
`accelerometer_rotation 0` + `user_rotation <n>`. At the end: `device_state state reset`, `user_rotation 0`,
`accelerometer_rotation 1`, `font_scale 1.0`, `wm size reset`. The app's own preferences were put back to the
fresh-install values (guitar, 6-String, Auto Listen, gate Off; metronome 4/4 at 120 BPM) and the app left open on
the Metronome tab.

Differences common to every retake (not repeated below): no top bar, so the content starts right under the status
bar (56 dp = 136 px higher) and cards that fill the height are 56 dp taller; the status bar's clock and icons are
light and readable (the originals show dark icons on the dark bar); the instrument selector is text only (no
pencil/cello icons, the app-icons change of 2026-10-12). The Auto Listen captures of 005 also show the noise gate
control (feature 006 came later).

A plain launch without extras opens Home; a launch with `tembra.tuner.testSignal` opens the Tuner.

Left (kept as they were):

- `android-fold-tabletop-autolisten-r1.png`, `android-fold-tabletop-byear-r1.png`,
  `android-fold-tabletop-metronome-r1.png`: they record the feature's first build (the lower tuner card was one
  lone selector row, the metronome's lower half was lopsided). That is a code state that today's build can't show:
  a retake would show the same picture as the D1 captures and contradict its caption, so they were kept like the
  `*-before*` images. If they are wanted anyway, the same states are `android-fold-tabletop-autolisten.png` (guitar),
  `android-fold-tabletop-byear.png` and `android-fold-tabletop-metronome.png` (120 BPM, 4/4).

| File | How | Differs beyond the top bar |
|---|---|---|
| `android-fold-tabletop-autolisten.png` | tabletop, guitar, Auto Listen, `tembra.tuner.testSignal e2-flat-20`, gate Off | **yes, later iteration D1**: centred TUNER CONTROLS heading, Guitar/Cello over Auto Listen/By Ear in one centred column (the original has the pre-D1 single row); noise gate control; the taller upper card draws a larger gauge |
| `android-fold-book-autolisten.png` | book, guitar, Auto Listen, `e2-flat-20` | **yes, later iteration D3**: the hinge split (controls card before the hinge, gauge after it) instead of the "unchanged Wide tuner layout" in its caption; now the same state and picture as `android-fold-d3-book-autolisten.png`; noise gate control |
| `android-fold-d3-book-autolisten.png` | book, guitar, Auto Listen, `e2-flat-20` (second capture of the same launch) | noise gate control under the selectors; gauge card taller |
| `android-fold-tabletop-autolisten-largefont.png` | `font_scale 2.0`, tabletop, guitar, `e2-flat-20` | the upper card gained 56 dp: the readout row "81.5 Hz • REFERENCE 82.4 HZ" is now whole (it was cut at the card's edge), Flat/Sharp still scroll out below; the lower card has the D1 column and the noise gate control |
| `android-fold-d1-tabletop-autolisten.png` | tabletop, cello (tapped), Auto Listen, `e2-flat-20` | noise gate control; larger gauge |
| `android-fold-d1-tabletop-byear.png` | tabletop, cello, By Ear (tapped), loop tone on (default) | no |
| `android-fold-d2-flat-byear-cello.png` | flat, cello, By Ear | no |
| `android-fold-tabletop-byear.png` | tabletop, guitar, 6-String, By Ear, loop tone on | **yes, D1**: the controls stack in one centred column (6/7-String as a full-width row, preset and loop tone under it) instead of the pre-D1 single row |
| `android-fold-tabletop-byear-largefont.png` | `font_scale 2.0`, tabletop, guitar 6-String, By Ear | **yes, D1**: the column is taller than the lower card at font 2.0, so it scrolls inside it and the loop tone row runs into the card's foot (the pre-D1 original fit everything in one row) |
| `android-fold-d2-flat-byear7.png` | flat, guitar, 7-String (tapped), By Ear | no (3 + 3 + 1, square, all visible) |
| `android-fold-d2-flat-byear7-short.png` | flat, 7-String, `wm size 2076x1700` (852 x 697 dp), then `wm size reset` | **yes, follows from the top bar**: the seven cards are now 3 + 3 + 1 instead of the 4 + 3 its caption describes. `WideStringGrid.arrange` keeps three columns whenever their square cards stay at or above the minimum size; the 56 dp the card got back makes that true in this window, so the 4 + 3 fallback no longer appears. A window 56 dp shorter brings it back: `alt-d2-flat-byear7-short-2076x1564.png` in this folder (`wm size 2076x1564`, same state, 4 + 3, not copied to the repo) |
| `android-fold-d3-book-byear7.png` | book, guitar 7-String, By Ear | no |
| `android-fold-tabletop-metronome.png` | tabletop, Metronome stopped, defaults: 120 BPM, 4/4, 1/4, Standard, Classic Wood | **yes, D1**: one METRONOME CONTROLS card (the original has the pre-D1 two cards); taller dial card |
| `android-fold-tabletop-metronome-largefont.png` | `font_scale 2.0`, tabletop, 122 BPM (two taps on +), 4/4, stopped | **yes**: D1 single card; the dial now has room for "Allegro (120-156)" where the original showed "Allegro" |
| `android-fold-tabletop-metronome-running.png` | 122 BPM started in tabletop, then ten changes `device_state 2` / `1` at 1.2 s each (rotation 1 throughout), ending half-opened; still running ("Stop metronome"); beat 1 lit, picked from a burst of 8 by the brightness of the cells' outline | **yes, D1**: one controls card |
| `android-fold-flat-metronome-running.png` | right after, `device_state 2` (flat, rotation 1), still running; beat 3 lit (burst of 10) | no |
| `android-fold-d1-tabletop-metronome.png` | 6/8 (tapped in the wide layout; selects its base subdivision 1/8), 122 BPM, tabletop, stopped | no |
| `android-fold-d1-book-metronome-hinge.png` | book, 6/8, 122 BPM, stopped | no; settings end at px 1009 and the dial card starts at px 1067 (original 1008 / 1068), either side of the hinge at 1038 |

### Every remaining capture retaken, 2026-10-16 (the owner's request: update all recorded screenshots)

The files kept above as records of past states (`*-before*`, `stacked-cut`, `bar-insets`, the 005 `-r1` set), the
live-sound captures, the notification crop and the Device Hub window shot were retaken too, in the original
configuration with today's build, so no image here shows the removed top bar any more. Where a file recorded a
defect or a first build, it now shows today's app in the same configuration, and the caption written for it above
describes the original image, which stays in git: `git show 42d8e92:<path>` (main at the start of this work).
The live captures used quickstart §5's 196 Hz tone with four harmonics from the MacBook's loudspeaker, as the
originals did; the output volume was put back to 31 % after every run.

#### Android (Pixel 6 Pro, fold emulator): eight fold captures

Target `emulator-5554`, inner display. Postures: tabletop = `cmd device_state state 1` with rotation 1; book =
state 1 with rotation 0; book at 180° = state 1 with `user_rotation 2`; flat = state 2 with rotation 0. Rotation was
locked with `accelerometer_rotation 0` and `user_rotation <n>`. Order: tabletop (metronome, Home, Auto Listen, By Ear),
book (cello By Ear, Auto Listen), book at 180° (metronome), flat with a short window (7-String). Then everything was
put back: 6-String, Auto Listen, 4/4 at 120 BPM, `wm size reset`, `device_state state reset`, `user_rotation 0`,
`accelerometer_rotation 1`.

Common to all eight: no top bar (content starts under the status bar), light status-bar icons (the old ones are dark
on the dark bar), and the instrument selector with text only.

| File | How | Old image recorded | New image shows instead |
|---|---|---|---|
| `android-fold-tabletop-home-before.png` | tabletop, Home tapped in the rail, 1.5 s later | before the feature: the shell in tabletop, the wide Home layout crossing the fold (Library card from top to bottom), under the top bar | **still the same layout**: Home has no tabletop form (feature 005 split only the Tuner and the Metronome), so the Library card still crosses the fold. Only the top bar is gone and the content sits 56 dp higher |
| `android-fold-tabletop-autolisten-r1.png` | tabletop, cold launch with `tembra.tuner.testSignal e2-in-tune`, `tembra.tuner.noiseGate off`; guitar, Auto Listen | first build: meter above the fold; the lower card held one lone selector row (Guitar / Cello, TEST SIGNAL pill, Auto Listen / By Ear) in the middle of an empty card | iteration D1: a centred TUNER CONTROLS heading with the TEST SIGNAL pill at the end of its line, Guitar / Cello over Auto Listen / By Ear in one centred column, and the noise gate at Off under them. The upper card is 56 dp taller with a larger gauge. Same reading: E2, 82.4 Hz, in tune |
| `android-fold-tabletop-byear-r1.png` | the same launch, then By Ear tapped; guitar, 6-String, Standard, loop tone on | first build: six cards above the fold; in the lower card, selectors in one row, then 6 / 7-String, preset and loop tone in a second row, floating mid-card | D1 column: heading, Guitar / Cello, Auto Listen / By Ear, 6-String / 7-String at full width, the preset, and the loop tone stacked. The six cards above are taller |
| `android-fold-tabletop-metronome-r1.png` | tabletop on the Metronome tab, stopped; 120 BPM, 4/4, 1/4, Standard, Classic Wood | first build: the lower half was lopsided, with the bar-shape card and sound pill top-aligned on the left and the slider, tap and play centred in a second card on the right | D1: one METRONOME CONTROLS card (the 4/4 menu, the subdivision tiles and Classic Wood on one line, the STYLE row, the slider, then tap tempo and play). The dial and the four tall accent cells above are as before |
| `android-fold-d1-book-topbar-gap-before.png` | book at 180°; Metronome, 6/8 tapped (it selects 1/8), two taps on + (122 BPM), stopped; afterwards two taps on − and 4/4 (1/4 came back) | before FR-012: an empty band of about 56 dp under the top bar (the cutout reported on the bottom edge); the wide layout with the six cells in two rows of three, labelled ACCENT / CLICK | **no gap, and no top bar**: the TIME SIGNATURE card starts right under the status bar. The FR-011 hinge split: the settings column ends at about px 1008 and the dial card starts at about px 1068, either side of the hinge, as in `android-fold-d1-book-metronome-hinge.png`. The six 6/8 cells now sit in one row without labels (narrower column) instead of two labelled rows |
| `android-fold-d2-book-byear-cello-before.png` | book; Cello tapped in By Ear (already selected) | before D2: the wide grid across the whole card (255 × 160 dp cards, the cello as 3 + 1), the selectors on a top row, the preset and loop tone in a row above the grid | the D3 hinge split: the controls card (heading, Guitar / Cello, Auto Listen / By Ear, preset, loop tone) before the hinge, and four square cards 2 + 2, centred, after it |
| `android-fold-d2-flat-byear7-short-before.png` | flat, `wm size 2076x1700` (852 × 697 dp); guitar, By Ear, 7-String tapped; then 6-String and `wm size reset` | before the fit: wide three-column cards with the seventh card cut off below the card's edge | square cards 3 + 3 + 1, the last one centred, all seven visible. That is the same picture as today's `android-fold-d2-flat-byear7-short.png`: without the top bar this window fits three columns, so the 4 + 3 fallback does not appear here |
| `android-fold-d3-book-autolisten-before-heading.png` | book; Guitar and Auto Listen tapped, then a cold launch with `e2-flat-20`, gate `off` | first D3 build: the heading clipped to "TUNER CONTROI" under the TEST SIGNAL pill | `CentredHeading`: "TUNER CONTROLS" whole and centred with the TEST SIGNAL pill centred under it, and the noise gate at Off under the selectors. The gauge after the hinge reads E2 81.5 Hz with the needle flat, as before. Same picture as today's `android-fold-d3-book-autolisten.png` |

## Recaptured 2026-10-17: icon-only navigation, no Home (Android)

Home was removed on 2026-10-17 (the app opens on Timeline) and the navigation items show icons only: the Material 3
Expressive bottom bar (active item on a lavender pill) on the phone, a collapsed 96 dp rail (active item on a circle)
on the fold. Every Android capture here was retaken under the same name, on the same target and in the same state.
Builds: the debug APK of that morning (`:androidApp:assembleDebug`, no `tembra.perf`), installed with
`adb install -r -t`; RECORD_AUDIO and POST_NOTIFICATIONS granted with `pm grant`.

Pixel 6 Pro `19021FDEE001AH` (Android 17): fresh install (the app had been uninstalled), `adb exec-out screencap -p`,
UiAutomator taps.

Pixel 9 Pro Fold emulator `emulator-5554` (AVD `Pixel_9_Pro_Fold`, Android 16), inner display
`4619827259835644672` (`screencap -p -d …`). Initial state, restored at the end: `device_state` 2 (OPENED, no
override), `accelerometer_rotation 1`, `user_rotation 0`, font 1.0, no `wm size` override. Postures as on 2026-10-16:
tabletop = `cmd device_state state 1` + rotation 1; book = state 1 + rotation 0; book at 180° = state 1 +
`user_rotation 2`; flat = state 2 + rotation 0; flat sideways = state 2 + rotation 1 (rotation locked with
`accelerometer_rotation 0`). The app's preferences were put back to guitar, 6-String, Auto Listen, gate Off and the
metronome to 4/4 at 120 BPM.

Two things about the emulator today, both affecting only how the captures were made: (1) `uiautomator dump` fails
("null root node returned by UiTestAutomationBridge") or hangs while a test signal animates the meter (the emulator
ran at 200–340 % host CPU with the meter on screen, a screencap took up to 10 s), so in Auto Listen screens the
selectors were tapped at coordinates read off a capture, and UiAutomator was used only on static screens (By Ear, the
stopped metronome, Timeline). (2) Under that load the test-signal reading wobbles from frame to frame (e2-in-tune read
82.1–82.7 Hz, e2-flat-20 81.0–82.0 Hz in single frames), so each test-signal capture was picked from a burst: the
steadiest of 6 frames (the one least different from the others) or the frame, out of 12–20, whose meter matches the
2026-10-16 capture of the same state best (aligned for the wider rail). Every kept frame shows the reading of the
original (82.4 Hz in tune, or 81.5 Hz flat); frames with other readings were discarded, and the first
`tabletop-autolisten-r1` and `tabletop-autolisten-largefont` picks (82.6 and 81.0 Hz) were retaken.

Common to every fold retake: the collapsed rail (five icons) is wider than the old labelled rail, so in flat and
tabletop the content starts about 40 px further right and its cards are narrower; in book posture the cards after the
hinge stay where they were and the card before it is narrower. The emulator's status bar showed no clock in today's
captures.

### Android, Pixel 6 Pro (4 retaken, 0 left)

| File | How | Differs beyond the navigation |
|---|---|---|
| `android-pixel6pro-autolisten.png` | retaken: portrait, `e2-flat-20` | no (the content is pixel-identical; only the bar differs) |
| `android-pixel6pro-byear.png`, `android-pixel6pro-d2-byear.png` | retaken: portrait, Tuner tapped from Timeline, By Ear, 6-String (the same state, as before) | no |
| `android-pixel6pro-metronome.png` | retaken: portrait, Metronome stopped (4/4, 120) | no |

### Android, Pixel 9 Pro Fold emulator (26 retaken, 0 left)

| File | How | Differs beyond the navigation |
|---|---|---|
| `android-fold-tabletop-home-before.png` | retaken: tabletop, cold launch, Timeline tapped in the rail (Home no longer exists) | **yes**: Timeline's placeholder sentence instead of Home and its Library card (which crossed the fold); Timeline, like Home, has no tabletop form, so its text sits in the upper half and the rest is empty |
| `android-fold-tabletop-metronome.png`, `-metronome-r1.png` | retaken: tabletop, Metronome stopped, 120 BPM, 4/4, 1/4, Standard, Classic Wood (the same state, as before) | no |
| `android-fold-tabletop-autolisten-r1.png` | retaken: tabletop, cold launch with `e2-in-tune` + `noiseGate off`; best of 20 frames | no (E2, 82.4 Hz, in tune; D1 column as on 2026-10-16) |
| `android-fold-tabletop-byear-r1.png`, `android-fold-tabletop-byear.png` | retaken: the same launch, By Ear tapped; guitar, 6-String, Standard, loop on | no |
| `android-fold-tabletop-autolisten.png` | retaken: tabletop, guitar, `e2-flat-20`; steadiest of 6 | no (81.5 Hz, needle flat) |
| `android-fold-d1-tabletop-autolisten.png` | retaken: Cello tapped in the same launch; steadiest of 6 | no (81.5 Hz) |
| `android-fold-d1-tabletop-byear.png` | retaken: cello, By Ear tapped | no |
| `android-fold-tabletop-byear-largefont.png` | retaken: font 2.0, tabletop, guitar 6-String, By Ear | no (the rail's labels no longer wrap: it has none) |
| `android-fold-tabletop-autolisten-largefont.png` | retaken: font 2.0, tabletop, `e2-flat-20`; best of 15 frames | no (81.5 Hz) |
| `android-fold-tabletop-metronome-largefont.png` | retaken: font 2.0, Metronome, "+" twice (122 BPM), 4/4, stopped | no |
| `android-fold-d1-tabletop-metronome.png` | retaken: font 1.0, 6/8 from the signature menu (selects 1/8), 122 BPM, stopped | **yes (follows from the wider rail)**: the six tall cells above the fold lost their ACCENT / CLICK labels (the narrower cell card hides them); the original has them |
| `android-fold-book-autolisten.png`, `android-fold-d3-book-autolisten.png` | retaken: book, `e2-flat-20`; each the best of 15 frames of one launch | no (81.5 Hz); the controls card before the hinge is narrower |
| `android-fold-d3-book-autolisten-before-heading.png` | retaken: book, cold launch with `e2-flat-20` + `noiseGate off`; best of 12 | no: "TUNER CONTROLS" whole with the TEST SIGNAL pill under it, as on 2026-10-16 (the clipped heading its caption describes is a past state) |
| `android-fold-d3-book-byear7.png` | retaken: book, By Ear (tapped by coordinates from Auto Listen), 7-String | no |
| `android-fold-d2-book-byear-cello-before.png` | retaken: book, 6-String, then Cello, By Ear | no |
| `android-fold-d1-book-metronome-hinge.png` | retaken: book, Metronome, 6/8, 122 BPM, stopped | **yes (follows from the wider rail)**: the settings column before the hinge is narrower, so the six 6/8 cells wrap into two rows of three, labelled, instead of one unlabelled row, and the STYLE pills wrap (Syncopated on a second line); the dial card after the hinge is unchanged |
| `android-fold-d1-book-topbar-gap-before.png` | retaken: book at 180° (`user_rotation 2`), same metronome state | the same wrap as the hinge capture; no gap under the status bar (as on 2026-10-16) |
| `android-fold-d2-flat-byear-cello.png` | retaken: flat, cello, By Ear | no |
| `android-fold-d2-flat-byear7.png` | retaken: flat, guitar, 7-String | no (3 + 3 + 1) |
| `android-fold-d2-flat-byear7-short.png`, `-short-before.png` | retaken: flat, 7-String, `wm size 2076x1700`, then `wm size reset` and 6-String | no: 3 + 3 + 1 as on 2026-10-16 (the 4 + 3 fallback and the cut-off seventh card in the captions are past states) |
| `android-fold-tabletop-metronome-running.png` | retaken: 4/4 at 122 BPM, started in tabletop, ten `device_state 2` / `1` changes 1.2 s apart (rotation 1 throughout) ending half-opened; frames until beat 1's cell lit (frame 16) | no |
| `android-fold-flat-metronome-running.png` | retaken: then `device_state 2` (flat, rotation 1), still running; frames until beat 3's cell lit (frame 6); stopped, "−" twice back to 120 | the dial card is narrower, so the dial is slightly smaller |

## Recaptured 2026-10-17: Home removed, icon-only navigation (iOS)

The three iOS captures were retaken under the same file names, on the same simulators and in the same states. The
builds were Debug, made from the worktree `nav-icons-only` with the Xcode 27.1 SDK. The two iPad captures show the
rail, so they were taken a second time from the build with 41fffa2, in which the collapsed rail centres its items. State
came from the stored preferences (restored afterwards) and the debug launch arguments.

| File | How | Differs beyond the navigation |
|---|---|---|
| `ios-iphone17-autolisten.png` | retaken: iPhone 17 portrait, guitar, `e2-flat-20`, gate off | no |
| `ios-ipadpro-autolisten.png` | retaken (build with 41fffa2): iPad Pro 13-inch (M5) portrait, cello, `e2-flat-20`, gate off | no |
| `ios-ipadpro-d2-byear.png` | retaken (build with 41fffa2): iPad Pro portrait, guitar, 6-String, By Ear | no |

## Recaptured 2026-10-17 (2): hand-drawn Tuner and Metronome glyphs (Android)

The Tuner and Metronome navigation items now show hand-drawn glyphs, a tuning fork with a sound arc on each side and a
pyramid metronome, instead of Material's music_note and av_timer (branch `experiment/nav-glyphs`, 041819b). All 30
Android captures were retaken under the same names, on the same targets and in the same states as the morning's pass.
Builds: the debug APK from HEAD 2a5d7dc (`:androidApp:assembleDebug` through the gradle-run wrapper), installed with
`adb install -r -t`.

Pixel 6 Pro `19021FDEE001AH` (Android 17): fresh install, permissions granted with `pm grant`,
`adb exec-out screencap -p`, UiAutomator taps.

Pixel 9 Pro Fold emulator `emulator-5554` (Android 16), inner display `4619827259835644672` (`screencap -p -d …`;
anything printed before the PNG signature is stripped). Postures as before: tabletop = `cmd device_state state 1` +
rotation 1; book = state 1 + rotation 0; book at 180° = state 1 + `user_rotation 2`; flat = state 2 + rotation 0; flat
sideways = state 2 + rotation 1. Initial state, restored at the end: `device_state` 2 with no override,
`accelerometer_rotation 1`, `user_rotation 0`, font 1.0, no `wm size` override; the app's preferences back to guitar,
6-String, Auto Listen, gate Off and the metronome at 4/4, 120 BPM, and the app left on Timeline. The emulator was much
faster than in the morning (65 ms median frame, against 1500 ms), and every capture was made as the first of up to
4–25 frames that matched the committed image outside the rail (status bar and gesture handle excluded), so test-signal
readings and the lit beat were checked against the committed pictures rather than by eye alone.

### Android, Pixel 6 Pro (4 retaken, 0 left)

| File | How | Differs beyond the two glyphs |
|---|---|---|
| `android-pixel6pro-autolisten.png` | retaken: portrait, `e2-flat-20` | no |
| `android-pixel6pro-byear.png`, `android-pixel6pro-d2-byear.png` | retaken: portrait, By Ear, 6-String | no |
| `android-pixel6pro-metronome.png` | retaken: portrait, Metronome stopped | no |

### Android, Pixel 9 Pro Fold emulator (26 retaken, 0 left)

Twenty of the twenty-six are identical to the committed images outside the rail (0 px differ). The other six are
test-signal captures whose needle sits at a slightly different angle from the committed frame; the readout is the same
in each (82.4 Hz in tune, or 81.5 Hz flat). In four of them the new frame matches today's sibling captures of the same
state pixel for pixel, and it was the committed image that had been taken a frame early or late:

| File | How | Differs beyond the two glyphs |
|---|---|---|
| `android-fold-tabletop-home-before.png` | retaken: tabletop, Timeline | no |
| `android-fold-tabletop-metronome.png`, `-metronome-r1.png` | retaken: tabletop, Metronome stopped, 120, 4/4 | no |
| `android-fold-tabletop-autolisten-r1.png` | retaken: tabletop, `e2-in-tune` + gate off | needle exactly upright now; the committed frame showed it a hair off centre. The new meter is identical to 006's `pixel-9-pro-fold-tabletop-off` / `-above` / `cycle-10-tabletop` (same state) |
| `android-fold-tabletop-byear-r1.png`, `-byear.png` | retaken: By Ear tapped in the same launch | no |
| `android-fold-tabletop-autolisten.png` | retaken: tabletop, `e2-flat-20` | needle angle: the new frame is identical to the new `android-fold-d1-tabletop-autolisten` (same reading, 81.5 Hz); the committed one leaned further |
| `android-fold-d1-tabletop-autolisten.png`, `-d1-tabletop-byear.png` | retaken: Cello tapped; then By Ear | no |
| `android-fold-tabletop-byear-largefont.png` | retaken: font 2.0, By Ear | no |
| `android-fold-tabletop-autolisten-largefont.png` | retaken: font 2.0, `e2-flat-20` | needle a few pixels off (458 px); same reading, 81.5 Hz |
| `android-fold-tabletop-metronome-largefont.png` | retaken: font 2.0, 122 BPM | no |
| `android-fold-d1-tabletop-metronome.png` | retaken: font 1.0, 6/8, 122 BPM | no |
| `android-fold-book-autolisten.png` | retaken: book, `e2-flat-20` | needle angle: the new frame is identical to the new `android-fold-d3-book-autolisten` (the same state, as the earlier notes say); the committed pair differed from each other |
| `android-fold-d3-book-autolisten.png` | retaken: same launch | no (1 px) |
| `android-fold-d3-book-autolisten-before-heading.png` | retaken: book, `e2-flat-20` + gate off | needle angle as above: identical to the new `d3-book-autolisten` |
| `android-fold-d3-book-byear7.png`, `android-fold-d2-book-byear-cello-before.png` | retaken: book, By Ear 7-String; then 6-String, Cello | no |
| `android-fold-d1-book-metronome-hinge.png`, `-d1-book-topbar-gap-before.png` | retaken: book (and at 180°), Metronome, 6/8, 122 BPM | no |
| `android-fold-d2-flat-byear-cello.png`, `-d2-flat-byear7.png` | retaken: flat, cello; then guitar 7-String | no |
| `android-fold-d2-flat-byear7-short.png`, `-short-before.png` | retaken: flat, `wm size 2076x1700`, then `wm size reset` | no |
| `android-fold-tabletop-metronome-running.png` | retaken: 4/4 at 122 BPM started in tabletop, ten `device_state 2` / `1` changes 1.2 s apart (rotation 1), beat 1 caught (frame 18) | no |
| `android-fold-flat-metronome-running.png` | retaken: then `device_state 2`, still running, beat 3 caught (frame 3); stopped, "−" twice back to 120 | no |

## Recaptured 2026-10-17 (2): hand-drawn Tuner and Metronome glyphs (iOS)

The three iOS captures were retaken under the same names, on the same simulators and in the same states. The build was
Debug, made from 041819b (`experiment/nav-glyphs`) with the Xcode 27.1 SDK: Tuner shows a hand-drawn tuning fork and
Metronome a hand-drawn pyramid metronome. Each image was compared with the previous one (HEAD) outside the navigation
and the status bar.

| File | How | Differs beyond the two glyphs |
|---|---|---|
| `ios-iphone17-autolisten.png` | retaken: iPhone 17 portrait, guitar, `e2-flat-20`, gate off | no |
| `ios-ipadpro-autolisten.png` | retaken: iPad Pro 13-inch (M5) portrait, cello, `e2-flat-20`, gate off | no |
| `ios-ipadpro-d2-byear.png` | retaken: iPad Pro portrait, guitar, 6-String, By Ear | no |
