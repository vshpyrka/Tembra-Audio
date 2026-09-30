# Feature 004 metronome: screenshot and device notes

## User Story 1 (batch B8, T098), 2026-09-30

**Devices**: Pixel 6 Pro (Android 17, serial 19021FDEE001AH, 1440 × 3120, debug build through
`:androidApp:installDebug`); iPhone 17 simulator (iOS 26.5, Debug build from `xcodebuild -scheme iosApp`).

| File | What it shows |
|---|---|
| `android-compact-stopped.png` | Metronome destination at launch: 120 BPM, "Moderato (108-120)", A C C C cells, play button |
| `android-compact-running-beat1.png` | Running at 120 BPM with beat 1 lit (secondary outline on the Accent bar box), pause glyph |
| `android-compact-tempo-240.png` | Running at 240 after holding "+": "Prestissimo (200-240)", full ring, "+" greyed, beat 2 lit |
| `android-compact-tempo-40.png` | 40 BPM after dragging the slider to its start: "Largo (40-60)", empty ring |
| `ios-compact-stopped.png` | The same stopped screen on the simulator |
| `ios-compact-running-beat1.png` | Running at 120 BPM with beat 1 lit, pause glyph |
| `ios-compact-tempo-240.png` | 240 BPM, "+" greyed |
| `ios-compact-tempo-40.png` | 40 BPM, "−" greyed |

### Quickstart §3, steps 1–3, by observation

- **Play and pause.** Pixel: play starts the engine stream (`dumpsys audio` lists the app's AAudio player as
  `started`) and the foreground service (`MetronomeForegroundService`, `isForeground=true`, type media playback);
  pause stops both at once (no started player, no service). The click itself was not listened to in this session:
  the run was driven over adb, so audibility and "no pop" are left for the user's listening check (quickstart §3.1).
- **Beat light.** A burst of screen captures at 120 BPM caught the outline moving cell 2 → cell 3 → cell 1 in turn
  on both platforms; only one cell is outlined at a time, and none while stopped.
- **Steppers.** "+" gives 121 "Allegro (120-156)", "−" back to 120 "Moderato (108-120)" (the slower name on the
  boundary). Holding "+" for 1.5 s on the Pixel went 120 → 134 (one step at 400 ms, then every 80 ms); holding
  longer stops at 240 with "+" greyed. The same at 40 with "−" greyed on the simulator.
- **Slider and ring.** Dragging the slider to its start gives 40 "Largo (40-60)". A drag on the ring from 12 o'clock
  to 3 o'clock gives 105 (a quarter of the 275° track), a shorter one 74 "Adagio (66-76)"; a drag that starts in the
  centre does nothing (UI test), so the column still scrolls.
- **Tempo change while running (step 3).** The new grid is sent on every change and applied by the engine from its
  next block (research R1, `metronome_renderer_test`); the cells kept lighting in turn at the new spacing. The
  "no skipped or doubled click" part is an ear check left for the user.
- The simulator run was driven through macOS Accessibility: System Events `click at` lands on the app's
  accessibility element under the point (the Simulator exposes the Compose semantics tree), so a slider click
  acts as an increment rather than a jump; the steppers were used for 40 and 240 there.

### Against the Stitch phone screen (`design/stitch/screens/metronome/phone/`)

Matches: the dial card's radial glow and vertical overlay, the 275° ring with round caps and the eight tick marks,
the steppers sitting on the ring at 9 and 3 o'clock, "120" with "BPM" in `secondary` and the marking in
`tertiary`, the slider's grey 6 dp track with the `primary` thumb and "40" / "240" end labels, the cells (Accent
bar in `secondary` with its pink glow, Click bars in `primary`, labels under them), the tap-tempo card and the
round `secondaryContainer` play button with its pink glow.

Differences, to settle later:
- The time-signature, subdivision, style and sound sections are not there yet (User Stories 2 and 4), so the
  column has empty space above the pinned tap and play row.
- The play button's glow is clipped by the bottom navigation bar at its lower edge (as in the design screenshot).

### Fix round 1 (2026-09-30)

- **Ring gradient.** The first captures drew `tempoArc` from the dial box's top-left to its bottom-right, unrotated.
  The design's SVG carries the same top-left → bottom-right gradient but is drawn turned −90°, so on screen it runs
  from bottom-left (`primary`) to top-right (`secondary`): the fill starts warm, pink-lavender, at 12 o'clock and
  cools to violet towards its end, as in `design/stitch/screens/metronome/phone/screenshot.jpg`. The earlier note
  blamed the sweep length; that was wrong (110° at 120 BPM is the spec's formula and the design's dash is only the
  mock's value). The dial now turns the brush with the SVG; `android-compact-stopped.png` and
  `android-compact-running-beat1.png` were retaken and match the design's colouring.
- **Glyphs.** The play and pause glyphs are now the filled Material Symbols (`PlayArrowFilled`, `PauseFilled`,
  fetched with `tools/fetch-material-symbols.sh --fill`), as the design draws them; checked on the Pixel captures.
- **iOS retake.** The iOS captures could not be retaken: the Mac's screen locked during the run, so the Simulator
  window could not be driven (no window in the accessibility tree) to reach the Metronome destination.
  `ios-compact-stopped.png` and `ios-compact-running-beat1.png` still show the first build (outlined glyph, unrotated
  gradient). The rotated gradient and the filled glyph are covered on the simulator by `TempoDialTest.
  theRingGradientRunsFromBottomLeftToTopRight` and `MetronomeControlsTest.thePlayButtonUsesTheFilledGlyphs`; the two
  iOS captures are to be retaken when the Mac is unlocked.

## User Story 2 (batch B9, T106), 2026-09-30

**Devices**: Pixel 6 Pro (Android 17, serial 19021FDEE001AH, 411 dp wide, debug APK from `:androidApp:assembleDebug`);
iPhone 17 simulator (iOS 26.5, 402 pt wide, Debug build from `xcodebuild -scheme iosApp`). Both runs were launched with
the debug argument (`--es tembra.metronome.syncopation <id>` / `--tembra-metronome-syncopation=<id>`), which also opens
the app on the Metronome destination. The simulator was driven through System Events `click at` on the Compose
accessibility elements (the Mac was unlocked this time).

| File | What it shows |
|---|---|
| `<platform>-compact-standard.png` | Launch state: 4/4 and 1/4 selected, "Common & Complex" caption, A C C C, "Standard" selected |
| `<platform>-compact-downbeat.png` | After "Downbeat": A S S S and the Downbeat pill selected |
| `<platform>-compact-syncopated.png` | Syncopated + 1/8, running (pause glyph), relaunched with `off-beat-ticks` (Android) / `all-but-one` (iOS): the cells keep the even-beat pattern C A C A |
| `<platform>-compact-7-8.png` | 7/8 + Standard: the subdivision chips read 1/8, 1/16, 1/32, 3let with 1/8 selected; seven cells A C C C C C C in two rows (4 + 3) |
| `<platform>-compact-6-8-labels.png` | 6/8 + Standard: A C C A C C, x/8 subdivision labels; two rows of three on the Pixel, one row on the iPhone 17 |

### Quickstart §4, steps 1–4 and 6, by observation

- **Step 1.** 7/8 gives seven cells, Accent then six Clicks, and the x/8 labels with 1/8 selected (both platforms).
  Running across a signature change was not listened to; the grid is sent at once and the engine maps the position
  (research R1, `metronome_renderer_test`).
- **Step 2.** 1/8 on 4/4 leaves the cells unchanged (the grid is `ASCSCSCS`, `MetronomeViewModelTest`); the soft tick
  between beats is an ear check left for the user.
- **Step 3.** Pixel: from Downbeat (A S S S), tapping cell 2 gave A A S S and every style pill deselected; Syncopated
  then rewrote the cells to C A C A and selected its pill. Simulator: "Downbeat" gave A S S S with the pill selected.
- **Step 4.** 6/8 + Standard shows A C C A C C; 4/4 + Syncopated shows C A C A. What they sound like is left for the
  listening check.
- **Step 6 (debug readings).** `off-beat-ticks` (Pixel) and `all-but-one` (both) were each launched with Syncopated and
  1/8 stored and played: the app opened on the Metronome, the settings were restored, playback started and the cells
  kept the even-beat pattern C A C A. The grids themselves (`SASASASA` for off-beat ticks with 1/8, `SAAA` for all but
  one with 1/4) are asserted by `MetronomeViewModelTest`; the audible difference is an ear check for the user. A
  release build never reads the argument (`BuildConfig.DEBUG` in `MainActivity`, `#if DEBUG` in `ContentView.swift`).
- Once, the Pixel opened on Home rather than the Metronome: the start was sent right after `adb install -r`, while
  the freshly installed process was still coming up. Three later force-stop + start runs with the extra all opened on
  the Metronome.

### Found and fixed on the Pixel: truncated "ACCENT" labels

The first 6/8 and 7/8 captures on the Pixel kept every cell in one row (seven 46 dp cells fit the 363 dp row at the
44 dp target), so the "ACCENT" label was clipped to "ACCE". `AccentCells` now also floors a cell at its widest level
label ("ACCENT" in bold, measured with the theme's `microLabel`) plus its padding, so 6/8 and 7/8 wrap on the Pixel and
large fonts wrap earlier, while the iPhone 17's wider row still fits 6/8 in one row. The test is
`AccentCellsTest.cellsWrapBeforeTheirLabelsWouldClip` (RED on the simulator before the fix).

### Against the Stitch phone screen (`design/stitch/screens/metronome/phone/screenshot.jpg`)

Matches: the section order and cards (`surfaceContainerLow`, 12 dp corners, 8 dp padding); "TIME SIGNATURE" with the
"Common & Complex" caption on the right; six equal chips in one row with 4/4 in `secondaryContainer`; four
subdivision chips with the note glyph and 1/4 in `primaryContainer`; the cells card with the "STYLE" row under a seam
and the three pills, Standard in `secondaryContainer`.

Differences, to settle later:
- The style row is taller than the design's (the pills keep a 44 dp target, the design's buttons are about 28 dp), so
  the gap between the cells and the row reads larger.
- The design draws beat 3 as SUB in its 4/4 illustration; Standard is A C C C by the spec (FR-010, research table).
- The sound pill ("Classic Wood") between the cells card and the dial is not there yet (User Story 4).
- The caption uses the tabular-parameter face at the section label's 10 sp size (the design's `text-[10px]`).
- The two iOS User Story 1 captures noted as stale above (`ios-compact-stopped.png`, `ios-compact-running-beat1.png`)
  were not retaken in this batch.

## User Stories 3 and 4 (batch B10, T111 and T117), 2026-09-30

**Devices**: Pixel 6 Pro (Android 17, serial 19021FDEE001AH, debug APK from `:androidApp:assembleDebug`); iPhone 17
simulator (iOS 26.5, Debug build from `xcodebuild -scheme iosApp`). Both were launched with the debug argument, which
opens the app on the Metronome. The Pixel was driven with `adb shell input tap` (tap loops run inside the device shell
so the intervals are steady) and read with `uiautomator dump`; the simulator with System Events `click at` on the
Compose accessibility elements (the Mac was unlocked).

| File | What it shows |
|---|---|
| `<platform>-compact-picker-open.png` | 4/4 stopped, the sound pill "Classic Wood" under the cells card and its menu open: Classic Wood (primary, check), 808 Rim, Digital Beep, Cowbell, Mechanical, Custom with a "+" |

### Quickstart §6 (tap tempo), by observation

- **One tap**: tempo unchanged, caption "Tap again" (Pixel).
- **A steady run**: ten taps every ~550 ms on the Pixel (0.5 s sleep plus the `input tap` cost) settled on 109 BPM,
  six taps every ~710 ms on the simulator (AppleScript clicks) gave 84 BPM with the caption "6 taps"; both match the
  intervals the drivers produce. A steady 100 BPM by finger is the user's check.
- **Found and fixed on the Pixel**: ten taps read "9 taps" because the sequence keeps the last nine taps for the
  average. The caption now counts every tap of the run (`MetronomeViewModelTest.theCaptionCountsEveryTapOfALongRun`,
  RED before the fix); twelve taps read "12 taps" on the Pixel afterwards.
- **Fast and slow**: taps as fast as `input tap` allows gave 240; three taps 1.9 s apart gave 40.
- **Rest**: three seconds after the last tap the caption reads "Sync finger rhythm" again and the next tap starts over
  ("Tap again") on both platforms.
- **Running**: taps while running changed the tempo without a restart (the play button kept "Stop metronome"; the
  grid is re-sent, `MetronomeViewModelTest.aSecondTapSetsTheTempoThroughTheSettingsPath`). Whether the click adopts the
  new tempo on the next tick is an ear check for the user.
- **Flash**: the 120 ms `primary` flash is verified by `MetronomeScreenTest` with the frozen clock (and absent under
  a zero `MotionDurationScale`); it is too short to catch in a device screenshot.

### Quickstart §4 step 5 (sounds), by observation

- The menu lists the six entries in the spec's order with the current one checked (both platforms, captures above).
- Stopped: choosing 808 Rim closed the menu and the pill read "808 Rim" (both); the engine calls
  `selectSound(Rim808)` then `previewClick(Click)` (`MetronomeViewModelTest`).
- Running: choosing Cowbell kept the click running ("Stop metronome") and the pill read "Cowbell" (both).
- Not listened to (driven over adb and AppleScript): the preview click, the swap on the next tick, and the three levels
  of each sound (Accent clearly louder, Sub clearly softer) are left for the user's ear check.
- Custom with nothing imported opens the platform WAV picker; preparing and selecting the picked file is User Story 5.

### Against the Stitch phone screen

Matches: the pill between the cells card and the dial card, centred, `surfaceContainerHigh`, fully rounded, with the
`graphic_eq` glyph in `primary`, the name in `labelMedium` and the `expand_more` chevron in `outline`; the 160 dp menu
on `surfaceContainerHighest` with 12 dp corners, the current entry in `primary` with a check and the others in
`onSurfaceVariant`. Differences: the menu's rows are Material's 48 dp (the design's `py-1.5` rows are about 32 dp), so
the menu is taller; it opens above the pill on both phones (Material positions it);
the design's list ("808 Click", "Studio Rim", no Custom) is replaced by the spec's (FR-021).

## User Story 5 (batch B11, T123), 2026-09-30

**Devices**: Pixel 6 Pro (Android 17, serial 19021FDEE001AH, debug APK from `:androidApp:assembleDebug`, app data
cleared first); iPhone 17 simulator (iOS 26.5, Debug build from `xcodebuild -scheme iosApp`, reinstalled). Both were
launched with the debug argument that opens the Metronome. The Pixel was driven with `adb shell input tap` on
`uiautomator dump` bounds. On the simulator the Compose elements were clicked through System Events, and the document
picker (a remote view that System Events cannot press) with real mouse events posted by a small `CGEvent` tool. The
Mac was unlocked.

**Fixtures**: `specs/004-metronome/fixtures/` (written by `make_fixtures.py` there, standard library only; the engine
test has no `--write` flag): `silence-then-click.wav` (48 kHz 16-bit mono, 100 ms silence then a 20 ms decaying
1.8 kHz sine), `long-tail.wav` (22.05 kHz, a 3 s 440 Hz ring), `stereo-24bit-96k.wav`, `float32.wav` (IEEE float,
48 kHz), `8bit-8k.wav`, `silent.wav` (0.5 s of zeros) and `notes.wav` (a text file). Pushed to the Pixel with
`adb push … /sdcard/Download/` (DocumentsUI › Downloads lists them straight away), and copied on the simulator into the
Files app's local storage (`…/data/Containers/Shared/AppGroup/<group.com.apple.FileProvider.LocalStorage>/File Provider
Storage/`), which the picker shows as Browse › On My iPhone.

| File | What it shows |
|---|---|
| `<platform>-compact-import-refused.png` | Classic Wood still selected after picking `notes.wav`; the snackbar reads "That file isn't a WAV file Tembra can read." |
| `<platform>-compact-custom-filled.png` | `silence-then-click` imported and selected: the pill names it; in the menu the Custom entry reads the name (ellipsised in the 160 dp menu), `primary` with a check, and the `swap_horiz` Replace button |

### Quickstart §5, by observation (both platforms unless noted)

- **Step 1**: "Custom" with nothing imported opened the platform picker (DocumentsUI on the Pixel, the Files sheet on
  the simulator). Picking `silence-then-click.wav` named the pill and the Custom entry "silence-then-click" within the
  1.5 s the driver waited; the app's copy appeared at `files/metronome/custom.wav` (Pixel) and
  `Documents/metronome/custom.wav` (simulator).
- **Step 2**: `8bit-8k`, `float32`, `stereo-24bit-96k` and `long-tail` were each accepted (the pill took each name;
  Pixel all four, simulator the first three). `silent.wav` showed "That file has no sound in it." and `notes.wav` "That file
  isn't a WAV file Tembra can read.", each once, with Classic Wood kept and no `custom.wav` left behind (Pixel,
  `run-as ls`). Whether the click lands on the beat, the 240 BPM × 1/16 tail fade and the full ring at 40 BPM are ear
  checks left for the user (the engine's `metronome_renderer_test.cpp` and `custom_sound_loader_test.cpp` cover them).
- **Step 3**: after deleting the original from Downloads / On My iPhone, a force-stop and relaunch still showed the
  custom sound selected, and the menu's Custom entry was filled with Replace. After deleting the app's copy
  (`adb shell run-as audio.tembra.app rm files/metronome/custom.wav`; `rm` in the simulator's data container) and
  relaunching: the pill read "Classic Wood", the snackbar "Your custom sound is missing, so Classic Wood is playing."
  showed once, and the menu's Custom entry read the old name with a "+". A second relaunch showed no message.
  Choosing that entry opened the picker again, and `long-tail.wav` was imported through it (Pixel).
- **Step 4**: "Replace" on the filled entry opened the picker and each new file replaced the name and the app's copy
  (the copy's size followed the file). On the Pixel a replace while running kept the click running ("Stop metronome"),
  and the pill took the new name.

Differences and notes:
- The 160 dp menu ellipsises long names ("silence-t…") because the check and the Replace button share the row. The
  pill and the entry's content description carry the full name.
- The dashed empty tile, the greyed unavailable tile and the Replace tooltip are in the expanded layout's Tiles, which
  these compact captures do not show. `ClickSoundPickerTest` covers them.

### Fix round 1: a refused Replace keeps the previous sound (Pixel, 2026-09-30)

The import now prepares the staged file first and commits it only when the engine accepts it; a refusal removes the
staged file only. Re-checked on the Pixel 6 Pro (fix build installed over the B11 one, app data kept) with `float32`
as the selected custom sound (`files/metronome/custom.wav`, 15416 bytes, md5 `d8f11a6c…`):

- **Replace with `notes.wav`**: the snackbar read "That file isn't a WAV file Tembra can read." once; the pill still read
  "float32"; `custom.wav` kept its size, timestamp and md5, and no `custom.wav.tmp` was left
  (`run-as ls` / `md5sum`). Capture: `android-compact-replace-refused.png`.
- **Force-stop and relaunch**: the pill read "float32" and no "missing" message appeared (checked twice, 3 s apart).
- **Replace with `silent.wav`**: "That file has no sound in it." once, "float32" kept, the copy untouched.
- **Replace with `8bit-8k.wav`** (accepted): the pill took "8bit-8k" and `custom.wav` became the 844-byte file, so the
  new order still commits an accepted file.
- Whether the previous sound is what plays after a refused Replace (the engine reload) is an ear check left for the
  user; `MetronomeViewModelTest` asserts the reload and reselect calls.

| File | What it shows |
|---|---|
| `android-compact-replace-refused.png` | `float32` still selected after replacing it with `notes.wav`; the snackbar reads "That file isn't a WAV file Tembra can read." |

## User Story 6 (batch B12, T126), 2026-09-30: background, calls, other apps, headphones

**Devices**: Pixel 6 Pro (Android 17, build CP3A.260905.009, serial 19021FDEE001AH, debug APK from
`:androidApp:assembleDebug`); Android emulator `Pixel_9_Pro_Fold` (`sdk_gphone64_arm64`, Android 16 / API 36, adb
`127.0.0.1:5556` through an ssh tunnel); iPhone 17 simulator (iOS 26.5, Debug build from `xcodebuild -scheme iosApp`);
iPhone 14 "Victor's iPhone" (iOS 26.5.2, Debug device build, installed with `xcrun devicectl device install app`).

**Probes.** Pixel: `dumpsys activity services audio.tembra.app` (the `MetronomeForegroundService` record,
`isForeground=true`, `types=0x00000002` = media playback), the top of `dumpsys audio`'s focus stack, the app uid's
`AudioPlaybackConfiguration` (`state:started`), `pidof`, and the button's content description ("Stop metronome" while
running, "Start metronome" when stopped) from the layout tree. Simulator: the button through the accessibility tree,
the process's CPU time (`ps -o stat,time`) and `runningboardd`'s assertions for the app (`MediaPlayback`). On the
simulator neither the host `coreaudiod` assertion nor the runningboard assertions end when the click stops (see the note
below the table), so they show only that the process is not suspended; the running state comes from the button on
return.

| Row | Device | Outcome | Evidence |
|---|---|---|---|
| Screen lock, ten minutes | Pixel 6 Pro | **Pass** | `KEYCODE_SLEEP` at 16:55:49; at +5 s (`mWakefulness=Dozing`), +5 min and +10 min the same player (`piid:1439 state:started`), the same pid, focus on top and the service in the foreground; after `KEYCODE_WAKEUP` the button read "Stop metronome". The +10 min read showed `mWakefulness=Awake` (something lit the screen near the end); the +5 s and +5 min reads were taken dozing |
| Screen lock, ten minutes | iPhone 17 simulator | **Pass (simulator)** | Device › Lock at 17:01; probed every minute to 17:11; after Device › Home the button read "Stop metronome" (same process, pid 52665). The simulator does not model lock-screen suspension faithfully: repeat on the iPhone 14 |
| App switch | Pixel 6 Pro | **Pass** | Home, then Settings for 60 s (`topResumedActivity` = Settings): service foreground, player `state:started`, focus on top; back in the app "Stop metronome" |
| App switch | iPhone 17 simulator | **Pass (simulator)** | Settings for 30 s, and again for 40 s: on return "Stop metronome"; in the background the process kept running (CPU time 1:53.96 → 1:54.02 in 5 s, never stopped) with runningboard's `MediaPlayback` assertion |
| Notification Stop | Pixel 6 Pro | **Failed, fixed in this batch, then Pass** | Failed first: the app did not declare `POST_NOTIFICATIONS`, so on Android 13+ the service's notification was never posted (no record in `dumpsys notification`; `pm grant` did nothing, appops `allow` did nothing). Fix: the manifest declares the permission (never requested at runtime). With it granted (`pm grant audio.tembra.app android.permission.POST_NOTIFICATIONS`, standing in for the user's Settings toggle) the "Metronome · Running" notification shows the Stop action (`android-notification-stop.png`). Tapping Stop over the app and, again, from Home: service gone, focus abandoned, no player, notification removed; the app then read "Start metronome" |
| Incoming call | Android emulator | **Needs the user** | Not exercised. The emulator's console is not reachable from this Mac (only adb is tunnelled; `adb emu avd name` exits 1, `adb emu gsm call` silently does nothing, `mCallState=0`), and placing a call inside the AVD was refused by the session's permission policy. Steps below |
| Incoming call | Pixel 6 Pro, iPhone 14 | **Needs the user** | Real calls are not placed by the agent. Steps below |
| Another app plays (YouTube) | Pixel 6 Pro | **Pass** | Click running, then a YouTube video (`am start -a VIEW -d https://www.youtube.com/watch?v=jNQXAC9IVRw com.google.android.youtube`): YouTube requested `GAIN` at 17:15:06.992, the app abandoned its focus at 17:15:07.142; the service and the app's player were gone, YouTube's player `state:started` alone on top of the focus stack. Back in the app after 10 s "Start metronome"; nothing restarted |
| Notification sound | Pixel 6 Pro | **Pass (ear check open)** | Ringer switched from VIBRATE to NORMAL for the check and back afterwards. `cmd notification post` on the `shell_cmd` channel (importance 3, default sound): the notification player (uid 10242) reached `state:started`; the click's player (`piid:1439`) stayed `state:started`, focus unchanged, "Stop metronome" after. Whether the click ducked audibly is an ear check |
| Headphones in | Pixel 6 Pro, iPhone 14 | **Needs the user** | No headphones can be plugged in remotely |
| Headphones out | Pixel 6 Pro | **Needs the user** | The shell cannot send `android.media.AUDIO_BECOMING_NOISY` (`SecurityException: Permission Denial: not allowed to send broadcast ... uid=2000`). The mapping is covered by the device tests (`BecomingNoisyReceiverTest`, `NativeAudioEngineTest`: delivery → `OutputRouteLost`) and `MetronomeViewModelTest.anOutputRouteLossStops` |
| Bluetooth speaker | Pixel 6 Pro, iPhone 14 | **Needs the user** | No speaker to pair |
| Leave the destination | Pixel 6 Pro | **Pass** | Click running, tap Tuner: 2 s later no service, no focus, no player; back on Metronome "Start metronome". (The Tuner raised the microphone prompt; answered "Only this time".) |
| Leave the destination | iPhone 17 simulator | **Pass** | Click running, tap Tuner (the microphone prompt was answered "Don't Allow"), back on Metronome: "Start metronome" |
| Force quit | Pixel 6 Pro | **Pass** | Tempo raised to 121, click running (`piid:1511 state:started`), `am force-stop`: no pid, no player, no service, no focus. Relaunch: 121 BPM kept, "Start metronome". Tempo set back to 120 |
| Force quit | iPhone 17 simulator | **Pass** | Click running, `simctl terminate`: the process and its `coreaudiod` assertion gone; relaunch "Start metronome" (settings persistence is shown on the Pixel row) |
| Every row | iPhone 14 | **Needs the user** | The B12 Debug build is installed on the phone (bundle `audio.tembra.appP5QJJ5Y5V7`), but `devicectl device process launch` refused: "the device was not, or could not be, unlocked". Steps below |

Simulator note: pressing play takes runningboard `AudioRecording` and `MediaPlayback` assertions for the app
(`9097-64356-20023`, `-20024` in a fresh process) and a stop does not release them (checked 8 s after the stop; in the
first process they were still held 25 minutes and two stops later). The engine's output path is output-only RemoteIO
disposed on close and the session is deactivated on stop, so this is most likely the simulator's host audio device
staying open per process. On the iPhone 14 check after a stop that no orange microphone dot shows and that the app
suspends in the background (below).

### Rows that need the user

Android, on the Pixel 6 Pro with the B12 build (installed):

1. **Notifications**: Settings › Apps › Tembra Audio › Notifications › allow (the app never asks).
2. **Incoming call**: Metronome › play; call the Pixel from another phone. Expected: the click stops when it rings and
   the app reads play; after hanging up it stays stopped.
3. **Incoming call on the emulator** (where its console is reachable, i.e. the emulator runs on this Mac):
   `adb -s <emulator> install -r app/androidApp/build/outputs/apk/debug/androidApp-debug.apk`; open Metronome and
   press play; `adb -s <emulator> emu gsm call 5551234`; check the button reads "Start metronome" and
   `adb -s <emulator> shell dumpsys activity services audio.tembra.app` lists no `MetronomeForegroundService`;
   `adb -s <emulator> emu gsm cancel 5551234`; wait ten seconds; the click is still stopped.
4. **Headphones in**: play on the speaker, plug in wired or USB-C headphones. Expected: the click continues in the
   headphones at the same tempo, the button still reads pause.
5. **Headphones out**: unplug them while it plays. Expected: it stops and the app reads play.
6. **Bluetooth**: pair a speaker, play at 120 BPM. Expected: it runs, and the beat light matches the audible click.
7. Notification sound by ear: with the ringer on, have a message arrive while it plays. Expected: the click dips briefly
   and continues.

iOS, on the iPhone 14 (unlock the phone, then from the repository root):

```sh
xcrun devicectl device process launch --device 48CD2B09-42B3-5156-9DD4-658E30BD0C1C audio.tembra.appP5QJJ5Y5V7
```

(To rebuild first: `cd app/iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug
-destination 'generic/platform=iOS' -derivedDataPath build-device -allowProvisioningUpdates build`, then
`xcrun devicectl device install app --device 48CD2B09-42B3-5156-9DD4-658E30BD0C1C "build-device/Build/Products/Debug-iphoneos/Tembra Audio.app"`,
and delete `build-device/`.) Open Metronome and run each row of quickstart §7: screen lock ten minutes (the click never
stops, pause on unlock), app switch, an incoming call (stops when it rings, stays stopped after), YouTube (stops,
YouTube plays alone), a message sound (the click continues), headphones in (continues) and out (stops), a Bluetooth
speaker (runs, light matches), leave the destination (stops), force quit (silence; relaunch keeps the settings, stopped).
After a stop, check that no orange microphone dot is shown and that the app is suspended in the background (the
simulator note above).
