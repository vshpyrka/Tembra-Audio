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
- **Preview release, required on the iPhone 14 before release** (final review I2, fixed 2026-09-30): once a preview
  click ends, the actual closes the output the preview opened (`tembra_tone_stop()` / `nativeToneStop()`) and then
  deactivates the session (iOS) or abandons focus (Android). The Pixel proves the close
  (`NativeAudioEngineTest.aPreviewReleasesItsFocusOnceTheClickEnds`: no started media player remains); the simulator
  test process has no output unit, so the iOS path has never run. On the iPhone 14: play music in another app, open
  the Metronome, pick a sound (a preview click, the music ducks or pauses), do not press play, and check that the
  music resumes within about a second and that the Control Centre shows no audio owned by Tembra.

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
| Leave the destination | iPhone 17 simulator | **Pass (simulator)** | Click running, tap Tuner (the microphone prompt was answered "Don't Allow"), back on Metronome: "Start metronome" |
| Force quit | Pixel 6 Pro | **Pass** | Tempo raised to 121, click running (`piid:1511 state:started`), `am force-stop`: no pid, no player, no service, no focus. Relaunch: 121 BPM kept, "Start metronome". Tempo set back to 120 |
| Force quit | iPhone 17 simulator | **Pass (simulator)** | Click running, `simctl terminate`: the process and its `coreaudiod` assertion gone; relaunch "Start metronome" (settings persistence is shown on the Pixel row) |
| Every row | iPhone 14 | **Needs the user** | The B12 Debug build is installed on the phone (bundle `audio.tembra.appP5QJJ5Y5V7`), but `devicectl device process launch` refused: "the device was not, or could not be, unlocked". Steps below |

The Pixel screen-lock run happened before the notification permission was granted (the Notification Stop row, later
in the same session), so it shows the click and the foreground service surviving the lock without the notification.

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

## User Story 7 (batch B13, T134), 2026-09-30: layouts, input, semantics, the full capture set

**Devices**: Pixel 6 Pro (Android 17, CP3A.260905.009, serial 19021FDEE001AH, debug APK from `:androidApp:assembleDebug`);
iPhone 17 simulator and iPad Pro 13-inch (M5) simulator (both iOS 26.5, Debug build from `xcodebuild -scheme iosApp`,
installed with `simctl install`). The Mac was unlocked throughout. The Pixel was driven with `adb shell input` on
`uiautomator dump` bounds; the simulators with real mouse events (a small `CGEvent` tool) at the positions of the app's
elements in the Simulator's accessibility tree (after a rotation that tree keeps its portrait frames, so the positions
were turned by 90°), and the Files picker by screenshot position.

**Bands**: compact is the Pixel in portrait (412 dp wide) and the iPhone 17 in portrait (402 pt). Landscape is the
Pixel with `user_rotation 1` (891 × 411 dp window) and the iPhone 17 rotated with Device › Orientation › Landscape
Left (874 × 402 pt). Expanded is the Pixel in landscape with `wm density 380` (1313 × 606 dp) and the iPad Pro in
landscape (1376 × 1032 pt). iOS landscape files were turned upright with `sips -r -90`. Afterwards the Pixel's density,
rotation (`user_rotation 0`, auto-rotate off as before) and font scale (1.0) were restored, the iPhone's text size set
back to `medium` and both simulators turned back to portrait.

### The set

Each state was captured in each band on each platform as `<platform>-<band>-<state>.png`:

| State | What it shows |
|---|---|
| `stopped`, `standard` | Launch state: 4/4, 1/4, Standard (A C C C), Classic Wood, 120 "Moderato (108-120)", play |
| `downbeat`, `syncopated` | The style pill selected and the cells rewritten (A S S S; C A C A) |
| `7-8` | Seven cells, 1/8 selected with the x/8 labels; they wrap (4 + 3 on the phones, 3 + 3 + 1 in the iPhone's landscape column, 4 + 3 on the iPad and the Pixel's expanded band) |
| `6-8-labels` | 6/8: the subdivision labels read 1/8, 1/16, 1/32, 3let; A C C A C C |
| `picker-open` | Compact and landscape: the sound menu open, Classic Wood checked. Expanded: the "Click timbre" tile grid (there is no menu there) |
| `import-refused` | `notes.wav` picked: "That file isn't a WAV file Tembra can read." in the snackbar, the previous sound kept |
| `custom-filled` | `silence-then-click.wav` imported and selected: the menu's Custom entry (compact, landscape) or the Custom tile (expanded) names it, with the check and the Replace button |
| `tempo-40`, `tempo-240` | The ends: "Largo (40-60)" with "−" greyed and the empty ring; "Prestissimo (200-240)", "+" greyed, the full ring |
| `running-beat1` | Running at 120 BPM with beat 1 lit (the `secondary` outline on the Accent cell's bar box) and the pause glyph. The capture loop checked the pixel at that outline and kept the first frame where it was lit |
| `stopped-largefont` (compact only) | Android `font_scale 2.0`; iOS `content_size accessibility-extra-extra-extra-large` (the app relaunched to read it). `…-largefont-scrolled` shows the rest of the column |

Counts: Android 12 states × 3 bands + 2 large-font = 38; iOS the same, 38. The compact files from User Stories 1 to 5
were retaken in this set; that includes the two iOS User Story 1 captures noted as stale above, now taken with the Mac
unlocked (rotated gradient, filled glyphs). B11's missing expanded Custom tile is `ios-expanded-custom-filled.png`
and `android-expanded-custom-filled.png`.

### Found and fixed while capturing

- **Phone landscape shrank the dial until the readout broke.** On the Pixel the landscape pane is about 290 dp tall;
  the contract's order (dial card, then the tap and play row) left the dial under 160 dp, so the pane scrolled the tap
  and play row half out of view and the number shrank to "1". The pane now sizes the dial both ways and takes the
  larger: the stacked order, or the dial card beside a 176 dp column holding tap over play (what both phones use).
  Covered by `MetronomeScreenTest.aPhoneHeldSidewaysKeepsTheTransportInViewWithAReadableDial` (892 × 330 dp).
- **"TIME SIGNAT…" at the largest font.** The section header kept the caption on the same line and cut the label.
  It now wraps the caption under the label (Pixel, `font_scale 2.0`); the large-font test reads the section labels too.
- **"ACCEN" in the iPhone's landscape column (7/8).** The cells wrapped into two rows of four even where only three
  fit. They now wrap into as many balanced rows as the labels need, and a label steps its size down rather than lose
  letters. Covered by `AccentCellsTest.aNarrowColumnWrapsIntoAsManyRowsAsTheLabelsNeed`.

### Against the Stitch screens

- **Tablet** (`metronome/tablet/screenshot.jpg` against `ios-expanded-*` and `android-expanded-*`): the settings
  column a third of the width with the four cards in the design's order and the sound picker as the two-column "Click
  timbre" grid (selected tile tinted with the `secondary` border, the Custom tile with a "+"); the dial card fills the
  right column with its discs, the 420 dp-capped dial centred and the slider at its foot; the tap and play card under
  it with the row centred. No right-hand panel. Matches.
- **Phone landscape** (`metronome/phone-landscape/screenshot.jpg`): the rail, the narrow settings column with the
  signatures in two rows of three and the sound pill last. Differs on purpose: the design puts the tap and play row
  under a small dial card; on a real phone that leaves no room for the readout, so tap and play stand beside the dial
  (see above). The marking under the number wraps onto two lines in landscape on both phones ("Moderato" /
  "(108-120)", in every `android-landscape-*.png` and `ios-landscape-*.png`, e.g. `android-landscape-stopped.png`);
  the design shows it on one line. It is whole, not clipped: the readout is kept between the steppers on the smaller
  landscape dial, and the marking wraps rather than cuts (the same wrap shows on the phones at the largest font,
  `android-compact-stopped-largefont-scrolled.png`).
- **Phone** (`metronome/phone/`): unchanged from the earlier stories; the retaken compact set matches the notes above.

### Quickstart §9 (pointer, keyboard, screen reader)

- **Keyboard** (iPhone 17 simulator, Mac keyboard): Tab moves a visible `primary` ring through the screen and the
  navigation bar. The reading order (signatures, subdivisions, cells, style pills, sound, "−", dial, "+", slider, tap,
  play), the arrows ±1 and Page Up/Down ±10 on the dial and the slider, and Enter on a chip are asserted on the
  simulator lane by `MetronomeScreenTest.tabFollowsTheReadingOrder`, `theFocusedSliderTakesTheArrowAndPageKeys`,
  `enterSelectsTheFocusedChip` and `TempoDialTest.theFocusedDialTakesTheArrowAndPageKeys` /
  `tabVisitsTheSteppersAroundTheDial`. The Pixel lane declares no keyboard mode, so these skip there.
- **Pointer: needs the user.** Neither the Pixel (no pointer; `input mouse` does not hover) nor the simulators
  (no hover) can show hover. Hover is asserted through injected interaction sources for the cell, pill, play button,
  dial track, tap card, slider thumb, steppers and tiles, and the play button's tooltip ("Start metronome") in
  `MetronomeScreenTest.thePlayButtonsTooltipIsItsDescription`. By hand: with a mouse (Android desktop mode or a
  Bluetooth mouse on the Pixel), hover every chip, cell, pill, tile, stepper, the slider, the dial ring, tap and play;
  the "−", "+" and play tooltips should appear; the wheel over the dial moves the tempo by one per notch.
- **Screen readers: needs the user.** TalkBack and VoiceOver were not driven. The first build's accessibility tree
  (the Simulator's, which VoiceOver reads) repeated the drawn labels: "Beat 1, ACCENT", "Click sound, Classic Wood,
  Classic Wood", "Tap tempo, TAP TEMPO, Sync finger rhythm". Fixed in B13 fix round 1: the drawn labels are kept out
  of the merged nodes, and the tree now reads "Beat 1" … "Beat 4" (the level is the state), "Click sound, Classic
  Wood", "Tap tempo" (the caption is the state), "Start metronome", "Decrease tempo" / "Increase tempo", and the dial
  and the slider as sliders; both announce "120 beats per minute, Moderato" (asserted in `MetronomeScreenTest`). By
  hand: TalkBack on the Pixel and VoiceOver on the iPhone 14:
  the dial should say "120 beats per minute, Moderato" and adjust by swipe up/down; a cell "Beat 3, Click"; play
  "Start metronome" / "Stop metronome"; after two taps on Tap tempo the button announces "2 taps, … beats per minute".

### Beat-light timing (spec SC-006): needs the user

Not measured. It needs the screen and the audio recorded against one clock on a wired route, which cannot be set up
remotely.

Setup (Pixel 6 Pro; repeat on the iPhone 14 with a Lightning to 3.5 mm adapter): a USB-C to 3.5 mm adapter from the
Pixel into a line input of an audio interface or a laptop's line-in; a second phone filming the Pixel's screen in
240 fps slow motion with its microphone next to a small speaker fed from the same line-out (or the Pixel's own
speaker muted and the interface's monitor output), so the film carries both the picture and the click. At 240 fps one
frame is 4.2 ms, well under the 30 ms limit; `adb shell screenrecord` is not used because it records at the display's
variable rate without the audio.

Steps: open Metronome, 4/4, Classic Wood, 120 BPM, and press play. Film 10 s. Open the film in Audacity (File ›
Import › Audio for the waveform) and in a frame-stepping player (QuickTime Player with the arrow keys, or `ffmpeg -i
film.mov -vf showinfo` for frame times). For five beats, note the time of the first frame in which the cell's
`secondary` outline appears and the time of the click's onset in the waveform, and log each offset (light minus
click) here. Each must be within ±30 ms (spec SC-006). A constant offset in one direction points at the output
latency compensation (research R7); a spread points at the frame loop.

## Polish (batch B14, T138–T141), 2026-09-30

### Compose compiler report (T138)

`./gradlew :shared:ui:compileAndroidMain -PcomposeReports=true --rerun-tasks --warning-mode all` (through the
`gradle-run` wrapper; strong skipping, intrinsic remember and pausable composition on). Report files:
`app/shared/ui/build/compose_compiler/tembra_shared:ui-composables.txt`, `…-composables.csv`, `…-classes.txt` and
`android/main/tembra_shared:ui-module.json` (build output, not committed). Every named restartable composable of
`:shared:ui` is `restartable skippable` (85 of 85, measured again on the final tree after the final review's fix wave,
now including `TapTempoFor`), the feature's 35 among them (32 in `metronome` and `metronome.components`, plus
`TembraPill`, `TembraChipRow` and `Chip`); no metronome parameter is `unstable`
(the module's five `unstable` parameters are feature 003's `TuningPreset` and `TargetString`). The runtime-checked
parameters (`MetronomeUiState`, `AccentPattern`, `MetronomeCallbacks`, `BeatTelemetry`, `ClickSound`,
`CustomSoundState`, `List<ChipSpec>`, the ViewModel) are explained one by one in research.md R11 ("Stability outcome
(T138)"); no code change was needed.

### Skill review (T139)

Routed through `using-chrisbanes-skills`; loaded `kotlin-concurrency-and-flow`, `kotlin-control-flow`,
`kotlin-api-design`, `compose-state-and-effects`, `compose-performance`, `compose-component-design`,
`compose-focus-navigation`, `compose-animations` and `compose-ui-testing-patterns`. The main sources were read in full;
the tests and the smaller model files were checked against the same clusters and by a signal scan (stored scopes,
`runBlocking`, `!!`, broad catches, `else` over closed types, raw threads, `init` work; one top-level type per file).
The earlier batch reviews' deferred minors were taken up here.

Fixed: 3 (F1–F3). Deferred with a reason: 3 (D4–D6). Everything else: no finding.

- **F1** The dial readout was three extra screen-reader stops (B13 review, spec FR-030). The texts now carry
  `hideFromAccessibility()`. RED: `TempoDialTest.theReadoutIsNotASeparateScreenReaderStop` failed on the simulator;
  GREEN after the change. On the Pixel the uiautomator tree no longer lists "120", "BPM" or "Moderato (108-120)" (the
  dial node still has its state). On the iPhone 17 simulator the macOS accessibility bridge still lists the three
  texts, so whether VoiceOver skips them is part of the VoiceOver run left for the user (User Story 7).
- **F2** Tab order was asserted in the Stacked form only (B13 review). Wide and Landscape passes were added; a
  mutation (two sound tiles swapped in the expected order) makes the Wide test fail.
- **F3** The tap and play code was duplicated in `LandscapePane` (B13 review). One private `TapTempoFor`.
- **D4** iOS start ignores a failed audio-session activation: no seam to test it, feature 003 does the same; follow-up.
- **D5** The ViewModel reloads a selected custom sound after a failed pick: harmless and covers the catch-all path.
- **D6** The click-sound entry's `dashed`/`muted`/`enabled` flags and long names in the 160 dp menu: semantic states
  of an `internal` tile, names ellipsised with the full name in the description; accepted.

| File | Cluster | Outcome |
|---|---|---|
| `androidApp/src/androidTest/java/audio/tembra/android/TestSoundProvider.java` | kotlin-concurrency-and-flow (test shape) | No finding |
| `androidApp/src/androidTest/kotlin/audio/tembra/android/AndroidBackgroundPlaybackHostTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `androidApp/src/androidTest/kotlin/audio/tembra/android/AndroidSoundFilePickerTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `androidApp/src/main/kotlin/audio/tembra/android/AndroidAppGraph.kt` | kotlin-api-design | No finding |
| `androidApp/src/main/kotlin/audio/tembra/android/AndroidBackgroundPlaybackHost.kt` | kotlin-concurrency-and-flow | No finding: process-wide replay-0 `SharedFlow` justified in KDoc; state under one lock |
| `androidApp/src/main/kotlin/audio/tembra/android/AndroidSoundFilePicker.kt` | kotlin-api-design | No finding |
| `androidApp/src/main/kotlin/audio/tembra/android/ForegroundState.kt` | kotlin-api-design | No finding |
| `androidApp/src/main/kotlin/audio/tembra/android/MainActivity.kt` | kotlin-api-design | No finding |
| `androidApp/src/main/kotlin/audio/tembra/android/MetronomeForegroundService.kt` | kotlin-control-flow | No finding: `else` over an open intent action |
| `androidApp/src/main/kotlin/audio/tembra/android/MetronomeServiceBinder.kt` | kotlin-api-design | No finding |
| `androidApp/src/main/kotlin/audio/tembra/android/SoundFilePickerHost.kt` | kotlin-concurrency-and-flow | No finding: main-thread `CompletableDeferred` handoff |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/AccentPattern.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/BeatLevel.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/BeatUnit.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/BuiltInClickSound.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/ClickSound.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/CustomSoundRef.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/MetronomeSettings.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/MetronomeStyle.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/Subdivision.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/SyncopationReading.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/TapTempoSequence.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/Tempo.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/TempoMarking.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/TickGrid.kt` | kotlin-api-design | No finding |
| `core/src/commonMain/kotlin/audio/tembra/core/metronome/TimeSignature.kt` | kotlin-api-design | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/AccentPatternTest.kt` | kotlin-api-design (test) | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/ClickSoundTest.kt` | kotlin-api-design (test) | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/MetronomeStyleTest.kt` | kotlin-api-design (test) | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/SubdivisionTest.kt` | kotlin-api-design (test) | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/TapTempoSequenceTest.kt` | kotlin-api-design (test) | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/TempoMarkingTest.kt` | kotlin-api-design (test) | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/TempoTest.kt` | kotlin-api-design (test) | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/TickGridTest.kt` | kotlin-api-design (test) | No finding |
| `core/src/commonTest/kotlin/audio/tembra/core/metronome/TimeSignatureTest.kt` | kotlin-api-design (test) | No finding |
| `data/src/androidDeviceTest/kotlin/audio/tembra/data/metronome/CustomClickSoundStoreFileTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `data/src/androidDeviceTest/kotlin/audio/tembra/data/metronome/DataStoreMetronomePreferencesTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `data/src/commonMain/kotlin/audio/tembra/data/metronome/CustomClickSoundStore.kt` | kotlin-api-design, kotlin-concurrency-and-flow | No finding |
| `data/src/commonMain/kotlin/audio/tembra/data/metronome/DataStoreMetronomePreferences.kt` | kotlin-api-design, kotlin-concurrency-and-flow | No finding |
| `data/src/commonMain/kotlin/audio/tembra/data/metronome/FakeCustomClickSoundStore.kt` | kotlin-api-design, kotlin-concurrency-and-flow | No finding |
| `data/src/commonMain/kotlin/audio/tembra/data/metronome/FakeMetronomePreferences.kt` | kotlin-api-design, kotlin-concurrency-and-flow | No finding |
| `data/src/commonMain/kotlin/audio/tembra/data/metronome/MetronomePreferenceKeys.kt` | kotlin-api-design, kotlin-concurrency-and-flow | No finding |
| `data/src/commonMain/kotlin/audio/tembra/data/metronome/MetronomePreferences.kt` | kotlin-api-design, kotlin-concurrency-and-flow | No finding |
| `data/src/commonMain/kotlin/audio/tembra/data/metronome/MetronomePreferencesSnapshot.kt` | kotlin-control-flow | No finding: `else` decodes an open stored string |
| `data/src/commonMain/kotlin/audio/tembra/data/metronome/OkioCustomClickSoundStore.kt` | kotlin-concurrency-and-flow | Observation: `createDirectories` in `init` is one small I/O call when the graph builds the store; accepted (every other operation is on `Dispatchers.IO`) |
| `data/src/commonMain/kotlin/audio/tembra/data/tuner/TembraDataStore.kt` | kotlin-api-design, kotlin-concurrency-and-flow | No finding |
| `data/src/commonTest/kotlin/audio/tembra/data/metronome/FakeAppDirectories.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `data/src/commonTest/kotlin/audio/tembra/data/metronome/FakeCustomClickSoundStoreTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `data/src/commonTest/kotlin/audio/tembra/data/metronome/FakeMetronomePreferencesTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `data/src/commonTest/kotlin/audio/tembra/data/metronome/MetronomePreferencesSnapshotTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `data/src/commonTest/kotlin/audio/tembra/data/metronome/OkioCustomClickSoundStoreTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `data/src/iosTest/kotlin/audio/tembra/data/metronome/CustomClickSoundStoreFileTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `data/src/iosTest/kotlin/audio/tembra/data/metronome/DataStoreMetronomePreferencesTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `engine/src/androidDeviceTest/kotlin/audio/tembra/engine/AndroidAudioFocusTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `engine/src/androidDeviceTest/kotlin/audio/tembra/engine/BecomingNoisyReceiverTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `engine/src/androidDeviceTest/kotlin/audio/tembra/engine/MetronomeTimingEndToEndTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `engine/src/androidDeviceTest/kotlin/audio/tembra/engine/NativeAudioEngineTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `engine/src/androidMain/kotlin/audio/tembra/engine/AndroidAudioFocus.kt` | kotlin-concurrency-and-flow, kotlin-control-flow | No finding: `else -> Unit` is over an open platform `Int` |
| `engine/src/androidMain/kotlin/audio/tembra/engine/BecomingNoisyReceiver.kt` | kotlin-api-design | No finding |
| `engine/src/androidMain/kotlin/audio/tembra/engine/FocusType.kt` | kotlin-api-design | No finding |
| `engine/src/androidMain/kotlin/audio/tembra/engine/NativeAudioEngine.android.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/AudioEngine.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/AudioResult.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/AudioState.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/AudioStatus.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/ClickLevel.kt` | kotlin-control-flow | No finding: `else` maps an open native code |
| `engine/src/commonMain/kotlin/audio/tembra/engine/ClickSoundId.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/CustomSoundInfo.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/CustomSoundResult.kt` | kotlin-control-flow | No finding: `else` maps an open native code |
| `engine/src/commonMain/kotlin/audio/tembra/engine/FakeAudioEngine.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/MetronomeAudio.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/MetronomeGrid.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/TickSnapshot.kt` | kotlin-api-design | No finding |
| `engine/src/commonMain/kotlin/audio/tembra/engine/TunerAudio.kt` | kotlin-api-design | No finding |
| `engine/src/commonTest/kotlin/audio/tembra/engine/FakeAudioEngineTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `engine/src/iosMain/kotlin/audio/tembra/engine/IosAudioSession.kt` | kotlin-api-design | No finding |
| `engine/src/iosMain/kotlin/audio/tembra/engine/NativeAudioEngine.ios.kt` | kotlin-api-design (platform seam) | Deferred (D4): `startMetronome`/`previewClick` ignore a failed `activateForPlayback()`, as the feature 003 tone and listening paths do; `IosAudioSession` is an `object` with no seam to force the failure in the simulator (Constitution III), and the engine start reports its own error when the output cannot open. Follow-up: a session seam shared with the tuner paths |
| `engine/src/iosTest/kotlin/audio/tembra/engine/IosAudioSessionTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `engine/src/iosTest/kotlin/audio/tembra/engine/MetronomeTimingEndToEndTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `engine/src/iosTest/kotlin/audio/tembra/engine/NativeAudioEngineTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `ui/src/androidDeviceTest/kotlin/audio/tembra/ui/navigation/AndroidBackTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/IconAssetsTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/App.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/CardDiscs.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/ChipAccent.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/ChipSpec.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/FocusRing.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/FrameTick.kt` | compose-state-and-effects, compose-performance | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraChipRow.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraGradientCard.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/designsystem/TembraPill.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/di/AppBindings.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/di/AppGraph.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/BeatTelemetry.kt` | compose-state-and-effects | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/CustomSoundState.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/LocalMetronomeDebug.kt` | compose-state-and-effects | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeCallbacks.kt` | compose-state-and-effects | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeCopy.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeDebugRequest.kt` | compose-state-and-effects | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeLayout.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeMessage.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeScreen.kt` | compose-state-and-effects | No finding: wiring seam; effects keyed on the ViewModel; stateless `MetronomeScreenContent` takes state and callbacks |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeScreenContent.kt` | compose-component-design, compose-state-and-effects | **Fixed (F3)**: the tap button wiring was written twice (`TransportRow` and the landscape tap-over-play column); now one private `TapTempoFor(state, callbacks, modifier)` |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeSettingsPanel.kt` | compose-state-and-effects | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeUiState.kt` | compose-state-and-effects | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/MetronomeViewModel.kt` | kotlin-concurrency-and-flow, kotlin-control-flow | Deferred (D5): a picker failure (`SoundPickResult.Failed`) still reloads a selected custom sound from its copy, although the engine was not touched; harmless (idempotent, off the main thread) and the reload also covers the catch-all path where the engine state is unknown. Otherwise no finding: named jobs owned by `viewModelScope`, `Channel` for one-consumer messages, cancellation rethrown, exhaustive sealed mappings |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/PlaybackEvent.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/PlaybackPolicy.kt` | kotlin-control-flow | No finding: exhaustive `when` on state and event, no `else` |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/PlaybackState.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/PlaybackTransition.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/StartFailure.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/TapCaption.kt` | kotlin-control-flow | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/AccentCell.kt` | compose-performance, compose-component-design | No change: the lit state and glow are read in the draw phase; the radial glow brush is built per drawn frame while running (UI thread only, never the audio thread); accepted |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/AccentCells.kt` | compose-performance | No finding: the frame tick is read in the draw lambdas only |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/ClickSoundGlyphs.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/ClickSoundPicker.kt` | compose-component-design | Deferred (D6): long custom names ellipsise in the 160 dp menu (the entry's description carries the whole name; the width is the design's); accepted |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/ClickSoundPickerLayout.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/ClickSoundTile.kt` | compose-component-design | Deferred (D6): `dashed`, `muted`, `enabled` are the Custom entry's semantic states (empty, unavailable, importing), not caller shape flags; the tile is `internal` to the picker. Accepted, revisit if the transport reuses the tile |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/MetronomeDefaults.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/PlayPauseButton.kt` | compose-animations, compose-performance | No finding: `rememberInfiniteTransition` for the breathing glow, read in `drawBehind`, static under reduce motion |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/SectionLabel.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/StyleRow.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/SubdivisionChips.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/TapTempoButton.kt` | compose-state-and-effects, compose-animations | No finding: per-tap `Animatable` flash in the draw phase; tap time from a user-event coroutine |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/TempoDial.kt` | compose-focus-navigation, compose-component-design | No finding: one focusable slider node between the steppers, keys consumed only when handled, `center` slot |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/TempoDialGeometry.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/TempoReadout.kt` | compose-component-design (semantics) | **Fixed (F1)**: "120", "BPM" and the marking were three extra screen-reader stops after the dial; each text now carries `hideFromAccessibility()` (kept in the test tree) |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/TempoSlider.kt` | compose-focus-navigation | No finding: Page keys in `onPreviewKeyEvent`, consumed only when handled |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/TempoStepper.kt` | compose-state-and-effects | No finding: the hold repeat is a user-event job in `rememberCoroutineScope`, cancelled on release, disable and dispose |
| `ui/src/commonMain/kotlin/audio/tembra/ui/metronome/components/TimeSignatureChips.kt` | compose-component-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/navigation/NavigationShell.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/platform/BackgroundPlaybackHost.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/platform/SoundFilePicker.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/platform/SoundPickFailure.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/platform/SoundPickResult.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/theme/LinearGlow.kt` | compose-performance (tokens) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/theme/LocalTembraTypographyExtras.kt` | compose-performance (tokens) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraGradients.kt` | compose-performance (tokens) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraIcon.kt` | compose-performance (tokens) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraSpacing.kt` | compose-performance (tokens) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraTypeScale.kt` | compose-performance (tokens) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraTypography.kt` | compose-performance (tokens) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraTypographyExtras.kt` | compose-performance (tokens) | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/tuner/TunerMeter.kt` | kotlin-api-design | No finding |
| `ui/src/commonMain/kotlin/audio/tembra/ui/tuner/TunerUiState.kt` | kotlin-api-design | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/metronome/MetronomeDebugRequestTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/metronome/MetronomeLayoutTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/metronome/MetronomeUiStateTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/metronome/MetronomeViewModelTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/metronome/PlaybackPolicyTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/metronome/components/TempoDialGeometryTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/platform/FakeBackgroundPlaybackHost.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/platform/FakeBackgroundPlaybackHostTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/platform/FakeSoundFilePicker.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/platform/FakeSoundFilePickerTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `ui/src/commonTest/kotlin/audio/tembra/ui/theme/MetronomeTokensTest.kt` | kotlin-api-design (test) | No finding |
| `ui/src/iosMain/kotlin/audio/tembra/ui/IosAppGraph.kt` | kotlin-api-design | No finding |
| `ui/src/iosMain/kotlin/audio/tembra/ui/MainViewController.kt` | kotlin-api-design | No finding |
| `ui/src/iosMain/kotlin/audio/tembra/ui/platform/IosBackgroundPlaybackHost.kt` | kotlin-api-design | No finding |
| `ui/src/iosMain/kotlin/audio/tembra/ui/platform/IosDocumentPickerDelegate.kt` | kotlin-api-design | No finding |
| `ui/src/iosMain/kotlin/audio/tembra/ui/platform/IosSoundFilePicker.kt` | kotlin-concurrency-and-flow, kotlin-api-design | No finding |
| `ui/src/iosTest/kotlin/audio/tembra/ui/platform/IosBackgroundPlaybackHostTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `ui/src/iosTest/kotlin/audio/tembra/ui/platform/IosSoundFilePickerTest.kt` | kotlin-concurrency-and-flow (test shape) | No finding |
| `ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraChipRowTest.kt` | compose-ui-testing-patterns | No finding |
| `ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraGradientCardTest.kt` | compose-ui-testing-patterns | No finding |
| `ui/src/uiTest/kotlin/audio/tembra/ui/designsystem/TembraPillTest.kt` | compose-ui-testing-patterns | No finding |
| `ui/src/uiTest/kotlin/audio/tembra/ui/metronome/AccentCellsTest.kt` | compose-ui-testing-patterns | No finding |
| `ui/src/uiTest/kotlin/audio/tembra/ui/metronome/ClickSoundPickerTest.kt` | compose-ui-testing-patterns | No finding |
| `ui/src/uiTest/kotlin/audio/tembra/ui/metronome/MetronomeControlsTest.kt` | compose-ui-testing-patterns | No finding |
| `ui/src/uiTest/kotlin/audio/tembra/ui/metronome/MetronomeScreenTest.kt` | compose-ui-testing-patterns, compose-focus-navigation | **Fixed (F2)**: Tab order was asserted in Stacked only; `assertTabOrder` now also runs the Wide (1024 × 768, sound tiles row by row) and Landscape (892 × 330, tap over play) forms; a swapped-tile mutation makes the Wide test fail |
| `ui/src/uiTest/kotlin/audio/tembra/ui/metronome/ScriptedBeatTelemetry.kt` | compose-ui-testing-patterns | No finding |
| `ui/src/uiTest/kotlin/audio/tembra/ui/metronome/TempoDialTest.kt` | compose-ui-testing-patterns | **Fixed (F1)**: `theReadoutIsNotASeparateScreenReaderStop` (RED on the simulator before F1, GREEN after) |
| `ui/src/uiTest/kotlin/audio/tembra/ui/navigation/NavigationShellTest.kt` | compose-ui-testing-patterns | No finding |

### Final lanes (T140)

Pixel 6 Pro (19021FDEE001AH, Android 17) with `ANDROID_SERIAL` set (the emulator 127.0.0.1:5556 was attached too),
the app force-stopped first; iPhone 17 and iPad Pro 13-inch (M5) simulators, iOS 26.5. Every lane through the
`gradle-run` wrapper with `--warning-mode all` (and `--rerun`/`--rerun-tasks` so the tests executed); every lane green,
no warning fingerprint.

| Lane | Tests | Result |
|---|---|---|
| `engineHostTest` | 107 Catch2 cases | 107 passed |
| `checkAndroidHost` | 353 (core 76, engine 31, data 30, ui 216) | all passed |
| `checkIos` (iPhone 17) | 582 (core 76, engine 51, data 39, ui 416) | all passed |
| `checkIos -Ptembra.iosSimulator="iPad Pro 13-inch (M5)"` | 582 (same split) | all passed |
| `checkAndroidDevice` (Pixel 6 Pro) | 579 (core 76, engine 55, data 39, ui 409) | all passed |
| `:androidApp:connectedDebugAndroidTest` (Pixel 6 Pro) | 17 | all passed |
| `:androidApp:assembleDebug` | build | built, no warning |

Known and out of scope: `:androidApp:lintDebug` (feature 001's `themes.xml:7` NewApi and two launcher-icon warnings);
Xcode's one `libicu.icudtl_dat.o` linker warning from the Kotlin/Native runtime.

### Quickstart walk (T141)

Pixel 6 Pro (debug build reinstalled after `connectedDebugAndroidTest`), iPhone 17 simulator, iPad Pro 13-inch (M5)
simulator, all on the B14 build.

- §1: Pixel and iPhone open on Home; Metronome shows 120 "Moderato (108-120)", 4/4, 1/4, Accent Click Click Click,
  Standard, "Classic Wood", the play button, no transport bar or right-hand panel. Matches.
- §2: the lanes above. The compose report command in the quickstart named a task that does not exist
  (`compileDebugKotlinAndroid`); corrected to `compileAndroidMain`, and the lane lists gained `MetronomeControlsTest`,
  `IosSoundFilePickerTest` and the iPad run.
- §3: play shows pause and lights the cells in turn (Pixel, iPhone); "+" gives 121 "Allegro (120-156)"; pause shows play.
- §4: 7/8 gives seven cells wrapped 4 + 3 and the chips 1/8, 1/16, 1/32, 3let; back to 4/4 gives four.
- §5: `silence-then-click.wav` pushed to Download and picked in DocumentsUI: the pill reads "silence-then-click" at
  once and the copy is `files/metronome/custom.wav` (11564 bytes). The quickstart's path
  (`files/../documents/metronome/custom.wav`) and its fixture source (a `--write` flag the engine test does not have,
  `notes.txt`) were wrong; corrected to `files/metronome/custom.wav`, `Documents/metronome/custom.wav` on iOS, and
  `fixtures/make_fixtures.py` with `notes.wav`.
- §6: four taps over adb settle the dial (107 with adb's latency) and the caption returns to "Sync finger rhythm"
  three seconds after the last tap.
- §7: unchanged since User Story 6 (the rows that need a call, another app or headphones remain with the user).
- §8: Pixel landscape shows the settings column beside the dial card with tap over play (the "beside" arrangement);
  the iPad shows the Wide form in portrait too, so the quickstart now says "portrait or landscape".
- §9: keyboard order proven by the three Tab-order tests; hover and the screen-reader runs remain with the user.

## Follow-ups after the final review, 2026-09-30

The final whole-branch review found four Important issues, fixed in 98d3233..3350e61 (iOS latency kept on a refresh,
a preview's stream closed on both platforms, a focus denial shown as the focus message, iOS route loss for any output
that is not the built-in speaker or receiver). It left these as follow-ups:

- Debounce persistence: every tap and every dial or slider value writes DataStore at once (`MetronomeViewModel.persist`);
  conflate or delay the write so only the settled value reaches disk.
- A custom copy that vanishes before a rate change makes the engine fall back to Classic Wood silently (`engine_core.cpp`,
  reopening at a new rate) while Kotlin still shows Custom until the next `screenVisible(true)`; surface it through
  `selectSound` returning `ERR_STATE` or a small ABI read of the selected sound.
- `MetronomeRenderer::startVoice` overwrites a still-releasing second voice; unreachable with the shipped grids (pinned by
  the Catch2 check from 8045ab9); chain voices if transport adds faster grids or tick bursts.
- The iOS timing and preview device tests never execute their measurement in the bare simulator process (no output
  unit): run `checkIos` against the iPhone 14, or an XCTest host with an output unit, before a release and record the
  numbers beside the Pixel's.
- `PlaybackState.Starting` is set and cleared inside one synchronous call, so its policy rows guard re-entrancy only;
  document that or keep the state policy-internal.
- `MetronomeAudio.readTick` is main-thread-only because each actual reuses one scratch buffer; pass a per-caller scratch
  before the transport feature adds a second reader.
- Decoding a 64 MB 8-bit multichannel WAV can transiently need several times the file size (float conversion before
  the downmix): lower the cap or decode in a streaming pass.
- `loadCustomSound` decodes under the engine's command lock, so a slider drag or the poll's latency refresh can block
  for about a second during an import: decode outside the lock.
- `IosAudioSession.activateForPlayback()`'s result is ignored by the metronome start (the engine reports its own error
  when the unit cannot open).
- Pre-existing and outside this feature: Oboe `openInput` closes the device without clearing `EngineCore::outputOpen_`
  (feature 003); the navigation bar labels break mid-word at the largest font scale (feature 002);
  `:androidApp:lintDebug` fails on `themes.xml:7` NewApi from feature 001.

### Still needs the user

- iPhone 14 (the final build is installed; the phone was locked when the launch was attempted): every User Story 6 row,
  the preview release path (no orange microphone dot after a stop; another app's audio resumes after auditioning a
  click), and the iOS device-lane measurements above.
- An incoming call (a real call, or the emulator's console: the emulator attached over adb alone exposes no console, so
  `emu gsm call` does nothing there), wired and USB-C headphones, Bluetooth, TalkBack and VoiceOver (including whether
  VoiceOver still speaks "120", "BPM" and the marking as separate stops), hover with a pointer, the SC-006 beat-light
  timing recording, and the listening checks (click quality and levels, onset alignment of an imported WAV).

## Design iteration D1 (2026-10-01): the compact bar-shape card, one-row accents, note glyphs, play above tap

T144–T147. The three settings cards pushed the dial half off a phone in portrait; the compact (Stacked) layout now
holds the time-signature menu, the four note-figure subdivision tiles, the accent cells in one row and the style pills
in one card (`metronome.bar`), the sound pill under it, and the dial card takes the height left above the pinned tap
and play row (ring 160–300 dp; the column scrolls only when 160 dp cannot be kept). Landscape puts play above tap
beside the dial card; the play glow reaches 8 dp (was 24 dp). Wide and Landscape keep the chips, now with the same
note glyphs as icons.

Devices: Pixel 6 Pro, Android 17 (1440 × 3120 px, 560 dpi = 411 × 891 dp), and the iPhone 17 and iPad Pro 13-inch
(M5) simulators, iOS 26.5. Build of the working tree after the T145 changes (base c06d027).

| Capture | What it shows |
|---|---|
| `android-compact-stopped.png` | 4/4, stopped: the card (4/4 pill, quarter-note tile selected, four labelled cells, style row), Classic Wood, the whole dial card with its slider, Tap Tempo and play, no scrolling |
| `android-compact-7-8.png` | 7/8 chosen from the menu: seven 44 dp-plus cells in one row with no labels (the bars show the levels), eighth-note glyphs on the tiles, everything in view |
| `android-compact-running-beat1.png` | 4/4 running, beat 1 lit (secondary outline), pause glyph, the 8 dp glow clear of the Tap Tempo card |
| `android-landscape-stopped.png` | Pixel held sideways: settings column with the chips (note glyphs as chip icons), dial card, play above Tap Tempo beside it |
| `ios-compact-stopped.png` | iPhone 17, 4/4, stopped: as on the Pixel, all in view |
| `ios-compact-7-8.png` | iPhone 17, 7/8: one row of seven unlabelled cells, all in view |
| `ios-landscape-stopped.png` | iPhone 17 Landscape Left (rotated upright with `sips -r -90`): play above Tap Tempo beside the dial card |
| `ios-expanded-stopped.png` | iPad Pro landscape: unchanged Wide layout, the subdivision chips now with the note glyphs |

Measured on the devices (Pixel from `uiautomator dump` bounds at 3.5 px/dp; iPhone from the capture at 3 px/dp, the
stepper edges ± 1 dp):

- Screen host (between the app header and the navigation bar): Pixel 411 × 706 dp, iPhone 17 402 × 658 dp. The
  `MetronomeScreenTest` compact cases use 411 × 700 and 402 × 652 (a few dp stricter); a 360 × 640 phone is modelled
  as 360 × 449 (the Pixel's 191 dp of chrome), where the column scrolls and the dial keeps 160 dp.
- Dial ring: Pixel 253 dp at 4/4 and 273 dp at 7/8 (the unlabelled cells are shorter); iPhone 17 about 201 dp at 4/4
  and 220 dp at 7/8. Before D1 the dial was 270 dp wide and its lower half was off screen on both phones.
- The keyboard path works on the iPhone simulator: Tab reaches the signature pill (focus ring), Return opens the menu,
  Down focuses 4/4, Return selects it (used to reset the persisted 7/8 for `ios-compact-stopped.png`).

Not captured: `android-compact-running-beat1` exists only for Android (no iOS running capture was asked for); the
landscape and expanded 7/8 and running sets were not retaken (their composition is unchanged apart from the chip
glyphs and the landscape transport order).

Observations:

- At the iPhone's 201 dp ring (and in the Pixel's landscape pane) the readout's "Moderato (108-120)" used to set on
  two lines between the steppers; fixed in fix round 1 (below): the marking stays on one line and drops the range.
- At 2× font the compact 4/4 cells are narrower than "ACCENT" too, so they drop their labels and stay in one row
  (per the revised FR-028); the column scrolls there with the dial at 300 dp (its minimum grows with the font scale so
  the readout stays whole).

### Lanes (T146)

`checkAndroidHost` (core 76, engine 31, data 30, ui 218), `checkIos` on the iPhone 17 and on the iPad Pro 13-inch (M5)
(each: core 76, engine 53, data 39, ui 435), `checkAndroidDevice` on the Pixel 6 Pro (core 76, engine 56, data 39,
ui 428) and `:androidApp:assembleDebug`: all green, zero warnings with `--warning-mode all`. Keyboard tests run on iOS
and return by declaration on the Android device lane.

### Skill review (T147)

`compose-component-design`, `compose-focus-navigation` and `compose-ui-testing-patterns` over `TimeSignatureMenu.kt`,
`SubdivisionTiles.kt`, `AccentCells.kt` (and `AccentCell.kt`), `MetronomeSettingsPanel.kt`,
`MetronomeScreenContent.kt`:

- Fixed while implementing: the cell label is an optional slot (`label: (@Composable () -> Unit)?`, default
  `AccentCellLabel(level)`) rather than a Boolean show-label flag; the panel takes the `MetronomeLayout` it serves
  instead of the `pickerLayout` + `signatureColumns` pair (three forms, one enum); the cell gap is a design-value
  parameter; every new component applies its `modifier` at its root; the new tiles and menu reuse the chip, pill and
  sound-menu colours, the focus ring and the 44 dp minimum; tab order reads the menu, the tiles left to right, the
  cells, the pills, the sound, the dial, the slider, then tap and play (play then tap in landscape, as drawn).
- Deviations from the contracts, kept deliberately: `MetronomeSettingsPanel(state, callbacks, telemetry, layout,
  modifier)` (contract lists `pickerLayout`, `signatureColumns`); `AccentCell` gains the `label` slot and `AccentCells`
  a `gap` parameter; the compact card draws no "BAR SHAPE" heading (the Stitch variant has none); since fix round 1
  its semantics carry the name "Bar shape" as a traversal group (contracts/metronome-screen.md).
- Deferred: the Stacked column keeps `verticalScroll` attached and reports a zero scroll range when everything fits
  (switching the modifier on and off would feed a measured size back into composition); the tests assert the range.
  `TembraSpacing.tempoDialMaxWidth` (270 dp) is no longer read by the screen (the compact cap is
  `MetronomeDefaults.CompactDialMax`, 300 dp, per the revised contract) and `TembraIcon.MusicNote` is no longer drawn
  by the metronome; both stay until data-model §9 drops them. The menu trigger's drawn "4/4" is kept out of the
  accessibility tree (the description names it), so tests assert the description, not the drawn text.

### Fix round 1 (2026-10-01, review I1–I3, M3, M5)

- **One-line marking** (I1, ruling in contracts/design-system-additions.md): `TempoReadout` measures "Moderato
  (108-120)" against the width the readout is given (a `BoxWithConstraints` constraint read with a `TextMeasurer`,
  nothing written back) and shows the name alone when the full string does not fit; one line, no wrap, a step-down
  size only for a name too wide at the largest fonts. Retaken: `ios-compact-stopped.png` (iPhone 17, 4/4, ring about
  201 dp: "Moderato" on one line) and `android-landscape-stopped.png` (Pixel held sideways: "Moderato" on one line).
  The Pixel portrait ring (253 dp) still fits the full "Moderato (108-120)". `TempoReadoutTest` covers both widths.
- **Bar-shape name** (I2): the card's semantics read "Bar shape" and group its controls (`isTraversalGroup`), without
  merging them; asserted in `theCompactCardKeepsOnlyTheStyleLabel`.
- **Landscape at 2× font** (I3): `theLargestFontKeepsTheLandscapeChipsWholeWithPlayAboveTap` (892 × 330 dp host, the
  Pixel's pane) checks the section labels, the six signature chips, the four subdivision chips with their glyphs and
  the style pills whole inside the column, no overlap among the settings controls, the dial, slider, tap and play in
  the window, play above tap, and the one-line marking. The host is wider than a phone held upright, so it reads bounds
  rather than visibility, like the other landscape layout tests.
- **Minors**: `IconAssetsTest` covers the eight `ic_note_*` drawables (bundled, 24 dp, literal white fill, no platform
  references, the generator named in the header); the signature pill and the sound pill expose "Collapsed" /
  "Expanded" as their state (`theTriggerReadsCollapsedOrExpanded`).
- Lanes: `checkAndroidHost` (core 76, engine 31, data 30, ui 219), `checkIos` on the iPhone 17 and on the iPad Pro
  13-inch (M5) (each: core 76, engine 53, data 39, ui 439), `checkAndroidDevice` on the Pixel 6 Pro (core 76, engine 56,
  data 39, ui 432), `:androidApp:assembleDebug`: green, zero warnings. The Pixel's auto-rotate had been switched on
  since the first D1 run, so device tests composed in landscape (the navigation rail grew by the cutout inset and three
  tuner tests failed); the lane ran with rotation locked to portrait, and auto-rotate was switched back on afterwards.

### T150 and T152: phone landscape (2026-10-01)

The landscape layout went through four rulings the same day (T150: the compact card in a 240–344 dp column; T152:
the dial block at the right edge, then a 3:4 split, then 1:3 with a landscape-only style menu, finally **2:3**). What
ships is the last one: the settings column is 2⁄5 of the content width (at least 240 dp × font scale) and holds the
same compact card as portrait (signature pill + tiles, the cells in one row 2 dp apart, the style row, the sound
pill); the dial pane is the remaining 3⁄5, the dial card spans it with the ring centred and sized from the height
(height − the tap/play row − the slider − gaps − `sm` card padding, within 160–300 dp), and the tap and play row
spans the pane under it, 48 dp tall up to a 300 dp pane and 64 dp from 420 dp. The "larger dial" rule is withdrawn;
`StyleMenu` from the 1:3 ruling was removed again before shipping. Also: `TapTempoButton` takes a `minHeight` and
keeps its title and caption on one line each (step-down size), `PlayPauseButton` takes a `diameter`, and the
readout sets "BPM" under the number on the smallest rings so "120" never clips (it showed "1" on a 160 dp ring).

Captures retaken: `android-landscape-stopped.png`, `android-landscape-7-8.png` (Pixel 6 Pro, Android 17, rotated
with auto-rotate off), `ios-landscape-stopped.png`, `ios-landscape-7-8.png` (iPhone 17 simulator, iOS 26.5,
Landscape Left, rotated upright with `sips`).

Measured on the devices (Pixel from `uiautomator dump`, iPhone from the capture):

| | Pixel 6 Pro | iPhone 17 |
|---|---|---|
| Screen host (window less the rail, header, safe areas) | 785 × 303 dp | 686 × 308 dp |
| Content (inside the page margin) | 753 × 287 dp | 654 × 292 dp |
| Settings column (2⁄5) | 302 dp | 261 dp |
| Dial pane | 439 dp | 381 dp |
| Ring | ≈163 dp | ≈168 dp |
| Tap/play row | 48 dp | 48 dp |

**Not met on the devices** (reported, not hidden): the 2⁄5 rule was sized against the full windows (891 and 874 dp,
giving 344 and 337 dp columns, where `MetronomeScreenTest` shows everything fitting without scrolling). The real
content is narrower because of the navigation rail and the safe areas, so on the devices the column is 302 / 261 dp:
7/8 needs 336 dp for seven 44 dp cells and wraps into two rows of labelled cells; on the Pixel the style pills wrap
under one another; on the iPhone the tiles wrap under the signature pill; on both the sound pill (and at 7/8 the
style row) is below the fold, so the settings column scrolls. The dial pane (ring, slider, tap, play) is whole on
both. `landscapeOnTheDeviceHostsKeepsTheDialPaneWhole` pins this at the measured hosts. The iOS landscape menu did
not open from the hardware keyboard in landscape (it does in portrait), so the 7/8 captures were set in portrait and
rotated; not investigated further.

Lanes: `checkAndroidHost` (core 76, engine 31, data 30, ui 219), `checkIos` on the iPhone 17 and on the iPad Pro
13-inch (M5) (each core 76, engine 53, data 39, ui 443), `checkAndroidDevice` on the Pixel 6 Pro (core 76, engine 56,
data 39, ui 436; rotation locked for the run, auto-rotate switched back on after), `:androidApp:assembleDebug`: green,
zero warnings.

### T152 sixth check: the landscape card that fits the real column (2026-10-01)

With the 2:3 split kept, the landscape settings card now differs from portrait so it fits the measured column
(302 dp × 287 dp on the Pixel 6 Pro, 261 dp × 292 dp on the iPhone 17) without scrolling: row 1 the time-signature
menu and the new `StyleMenu` pill ("Standard ▾", announced "Style, Standard", Collapsed/Expanded), row 2 the four
subdivision tiles spread across the width, row 3 the accent cells as 44 dp bar boxes without labels
(`AccentCellsForm.BarOnly`: the level shows in the bar and is announced by the cell's state; one row when each keeps
44 dp, otherwise two rows at 44 dp, 4 dp apart), then the sound pill. `TimeSignatureMenu` and `StyleMenu` share
`MenuPill`. The dial pane is unchanged. Portrait is unchanged.

Measured on the Pixel 6 Pro (`uiautomator dump`, 3.5 px/dp): the card is 164 dp tall at 4/4 and 212 dp at 7/8 (4 + 3
cells); with the sound pill under it 220 dp and 268 dp, inside the 287 dp content height, so the column does not
scroll. The iPhone 17's card has the same rows (pills 172 dp wide in a 245 dp inner width, 4/4 cells one row, 7/8 two
rows of four and three), so the same heights; `landscapeOnTheDeviceHostsShowsTheWholeCardAndDialPane` asserts the
whole card and the sound pill without scrolling at both measured content sizes and both signatures.

Captures retaken: `android-landscape-stopped.png` (4/4) and `android-landscape-7-8.png` (Pixel 6 Pro, Android 17).
**Not retaken:** `ios-landscape-stopped.png` and `ios-landscape-7-8.png` still show the previous 2:3 card: the Mac's
console was locked (`IOConsoleLocked`) during this round, so the Simulator could not be rotated or driven from the
keyboard (`simctl` has no rotation); they need retaking once the session is unlocked.

Lanes: `checkAndroidHost` (core 76, engine 31, data 30, ui 219), `checkIos` on the iPhone 17 and on the iPad Pro
13-inch (M5) (each core 76, engine 53, data 39, ui 446), `checkAndroidDevice` on the Pixel 6 Pro (core 76, engine 56,
data 39, ui 439; rotation locked for the run, auto-rotate restored), `:androidApp:assembleDebug`: green, zero warnings.

