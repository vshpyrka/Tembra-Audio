# Quickstart: Metronome (feature 004)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contracts**: [contracts/](contracts/)

This guide proves the feature end to end. Every Gradle command below is run through the
`gradle-run` skill wrapper (Constitution VIII); the commands are shown in their plain form.

## 1. Build and launch

```bash
cd app
./gradlew :androidApp:installDebug --warning-mode all
adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity
```

```bash
cd app/iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build build
BUNDLE=$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app/Info.plist")
xcrun simctl boot "iPhone 17"; xcrun simctl install "iPhone 17" "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app"
xcrun simctl launch "iPhone 17" "$BUNDLE"
```

Expected: the app opens on Timeline (Home until 2026-10-17). Tap **Metronome** (the timer icon; the navigation items have no text labels since 2026-10-17): the Metronome item is marked (no top bar since 2026-10-16); the dial shows
120 BPM "Moderato (108-120)", 4/4, 1/4, four cells reading Accent, Click, Click, Click, "Standard"
selected, the sound pill "Classic Wood", the slider knob at 40 % and a play button. No transport
bar, no right-hand panel.

## 2. Automated tests (spec FR-033)

```bash
cd app
./gradlew engineHostTest          # Catch2: renderer timing over ten minutes, click synthesis levels, custom-sound preparation, triple buffer, engine core state, ABI 3
./gradlew checkAndroidHost        # JVM: metronome domain, ViewModel and policy, preferences, sound store (fake file system), tokens, icon assets, theme parity
./gradlew checkIos                # iOS simulator: the same commonTest classes + MetronomeScreenTest, MetronomeControlsTest, TempoDialTest, AccentCellsTest, ClickSoundPickerTest, TembraPillTest, TembraChipRowTest, NativeAudioEngineTest, MetronomeTimingEndToEndTest, IosAudioSessionTest, IosBackgroundPlaybackHostTest, IosSoundFilePickerTest, DataStore and file round trips
./gradlew checkIos "-Ptembra.iosSimulator=iPad Pro 13-inch (M5)"   # the same lane on the tablet simulator
./gradlew checkAndroidDevice      # connected device: uiTest classes + NativeAudioEngineTest + MetronomeTimingEndToEndTest + AndroidAudioFocusTest + BecomingNoisyReceiverTest + DataStore and file round trips
./gradlew :androidApp:connectedDebugAndroidTest   # AndroidBackgroundPlaybackHostTest, AndroidSoundFilePickerTest (and the feature 003 permission test)
```

Every lane ends green with zero warnings (`--warning-mode all`). What each test proves is in
[research.md](research.md) R15 and the contracts. The compose compiler report is produced with
`./gradlew :shared:ui:compileAndroidMain -PcomposeReports=true` (reports in
`app/shared/ui/build/compose_compiler/`) and every new composable is skippable; the exceptions are explained in
[research.md](research.md) R11.

## 3. Tempo, play and pause (User Story 1)

1. Press play: beat 1 (an accent) sounds at once, the button shows pause, the cells light in turn
   with the clicks. Press pause: silence at once, no pop.
2. Tap "+" and "−": 121, 120 …; hold "+": the tempo climbs and stops at 240 with "+" greyed.
   Drag the slider and the ring: whole numbers, the name changes at 60, 66, 76, 108, 120, 156,
   176 and 200 (the slower name on the boundary: 120 is still "Moderato (108-120)").
3. While running, drag the slider from 120 to 200: the spacing tightens from the next click, no
   skipped or doubled click (spec SC-002).
4. Timing: `MetronomeTimingEndToEndTest` measures five seconds on a device; the ten-minute
   drift proof is `metronome_renderer_test` (offline, exact to one sample). For a listening
   check record ten minutes at 240 BPM with the phone next to a DAW click at 240 BPM: no
   audible phase slip at the end (spec SC-001). The same device test asserts the start latency,
   play to the first click, at 100 ms or less (spec SC-007).

## 4. Bar shape, styles and sounds (User Stories 2, 4)

1. Select 7/8: seven cells, Accent then six Clicks, the subdivision chips read 1/8, 1/16, 1/32,
   3let with 1/8 selected. Running, the bar restarts at beat 1 on the next beat.
2. Back to 4/4, select 1/8: a soft tick between beats; the cells are unchanged.
3. Tap the second cell: Click → Sub, the style pills all deselect; tap it twice more: Accent, then
   Click. Choose "Downbeat": Accent, Sub, Sub, Sub and the pill selects.
4. 6/8 + Standard sounds A C C A C C; 4/4 + Syncopated sounds C A C A (spec worked examples).
5. Open the sound pill: Classic Wood ✓, 808 Rim, Digital Beep, Cowbell, Mechanical, Custom.
   Choose one while stopped: one preview click. Choose one while running: the next tick uses it.
   Listen for the three levels in each sound (Accent clearly louder, Sub clearly softer).
6. Debug builds only, the Syncopated readings (spec FR-037):
   ```bash
   adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity --es tembra.metronome.syncopation off-beat-ticks
   xcrun simctl launch "iPhone 17" "$BUNDLE" --tembra-metronome-syncopation=all-but-one
   ```
   With Syncopated and 1/8 selected: `off-beat-ticks` accents the ticks between beats;
   `all-but-one` accents every beat except beat 1. The cells keep showing the even-beat pattern.
   A release build ignores the argument.

## 5. Custom WAV (User Story 5)

1. Put a WAV on the device (Android: `adb push click.wav /sdcard/Download/`; simulator: drag the
   file onto the Files app or use `xcrun simctl addmedia`… for non-media files open Files › iCloud
   Drive from the Mac). Choose "Custom": the platform picker opens filtered to WAV; pick the file.
   Within two seconds the entry shows the file's name, the custom sound is selected and a preview
   click sounds (stopped) or the next tick uses it (running).
2. Test files in `fixtures/` (written by `python3 fixtures/make_fixtures.py fixtures`, standard library only; the
   engine's own tests build the same shapes in memory from `engine/tests/wav_fixtures.h`): `silence-then-click.wav` (100 ms of silence:
   the click lands on the beat, no late feel), `long-tail.wav` (a 3 s ring: at 40 BPM it rings,
   at 240 BPM × 1/16 every tick starts clean with the tail faded), `stereo-24bit-96k.wav`,
   `float32.wav`, `8bit-8k.wav` (all accepted), `silent.wav` ("That file has no sound in it."),
   `notes.wav`, a text file ("That file isn't a WAV file Tembra can read.").
3. Delete the original file, force-quit and relaunch: the custom sound still plays. Delete the
   app's copy (`adb shell run-as audio.tembra.app rm files/metronome/custom.wav`,
   or `Documents/metronome/custom.wav` in the simulator's data container) and relaunch: Classic Wood plays and the message "Your custom
   sound is missing…" shows once; the Custom entry shows the name greyed.
4. "Replace" on the filled Custom entry picks another file and replaces the first.

## 6. Tap tempo (User Story 3)

Tap once: no change, caption "Tap again". Tap again within two seconds: the dial takes the
interval; keep tapping at a steady 100 BPM: it settles on 100 ± 1 and the caption counts the taps.
Stop for three seconds: the caption returns to "Sync finger rhythm"; the next tap starts over.
Tap at 300 BPM: 240; at 30 BPM: 40. Running, the click adopts each new tempo on the next tick.

## 7. Background, calls, other apps, headphones (User Story 6, spec FR-027)

| Check | Android | iOS | Expected |
|---|---|---|---|
| Screen lock | start, press power, wait ten minutes | same | the click never stops; on unlock the button reads pause; Android shows the "Metronome · Running" notification when notifications are allowed and the active-app chip otherwise |
| App switch | start, go Home, open another app | same | keeps running; back in the app it is running |
| Notification Stop | tap Stop on the notification | — | stops; the app shows play |
| Incoming call | call the phone | call the phone | stops when the call rings; stays stopped after the call |
| Another app plays | start, open YouTube and play a video | same | the click fades (Android 12+) and stops; YouTube plays alone; the app shows play |
| Notification sound | receive a message | same | the click ducks briefly (Android) or continues (iOS); it does not stop |
| Headphones in | start on the speaker, plug in headphones | same | continues on the headphones, same tempo |
| Headphones out | unplug | unplug | stops, shows play (platform guidance; spec FR-027 revised) |
| Bluetooth | pair a speaker, start | same | runs; the beat light still matches the audible click (latency compensated) |
| Leave the destination | tap Tuner | same | stops; back on Metronome it shows play |
| Force quit | kill the app while running | same | silence; relaunch shows every setting kept and the metronome stopped |

Record devices and outcomes in `screenshots/NOTES.md`.

## 8. Layouts and screenshots (User Story 7, spec FR-034)

| Band | Android | iOS |
|---|---|---|
| compact (under 600 dp) | Pixel 6 Pro portrait | iPhone 17 simulator |
| expanded (840 dp and wider) | Pixel 6 Pro with `adb shell wm density 380` in landscape, or a tablet | iPad Pro 13-inch simulator, portrait or landscape |
| phone landscape (600 dp and wider, under 480 dp tall) | Pixel 6 Pro rotated (`adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1`; `user_rotation 0` afterwards) | iPhone 17 simulator rotated (Simulator menu Device › Rotate Left, or the AppleScript in `specs/003-instrument-tuner/screenshots/NOTES.md`) |

Capture into `specs/004-metronome/screenshots/` as
`<platform>-<compact|expanded|landscape>-<state>[-largefont].png` with states: `stopped`,
`running-beat1` (play, capture within the first beat; on Android `android screen capture` is
fast enough, otherwise use a 40 BPM tempo), `standard`, `downbeat`, `syncopated`, `7-8`,
`6-8-labels`, `picker-open`, `custom-filled`, `import-refused`, `tempo-40`, `tempo-240`, plus
`stopped-largefont` at compact (Android `adb shell settings put system font_scale 2.0`; iOS
Settings → Accessibility → Larger Text). Android: `android screen capture --output <file>`;
iOS: `xcrun simctl io "<device>" screenshot <file>` (landscape needs `sips -r -90`). Review
against `design/stitch/screens/metronome/phone/index.html` (rendered), `metronome/tablet/screenshot.jpg`
and `metronome/phone-landscape/screenshot.jpg`: chips, cells and bar heights, style pills, the
sound pill or tiles, the dial ring and gradient, the number and marking, the slider, the tap
card and the play button. Nothing clipped, overlapping or under the bottom bar at any size;
every label whole at the large font; 7/8 cells whole (wrapping where needed).

Beat-light timing (spec SC-006): on a wired route at 120 BPM, record the screen and the audio
together (Android: `adb shell screenrecord` while a DAW records the phone's line-out, or one phone
filming another; iOS: QuickTime screen and audio capture of the device), measure the offset between
the cell lighting and the click on five beats and log the values in `NOTES.md`; 30 ms is the limit.

## 9. Pointer, keyboard and screen reader

Desktop mode or a mouse: hover every chip, cell, pill, entry, stepper, the slider, the ring, the
tap card and the play button; tooltips on "−", "+" and play; the wheel over the dial changes the
tempo by one. Keyboard: Tab through in reading order; arrows ±1 and Page Up/Down ±10 on the dial
and the slider; Space/Enter on chips, cells, pills, tap and play. TalkBack / VoiceOver: the dial
announces "120 beats per minute, Moderato" and is adjustable; a cell announces "Beat 3, Sub"; the
play button "Start metronome" / "Stop metronome"; the tap button announces the tempo it set.

## 10. Troubleshooting

- **No click on Android but the button shows pause**: another app holds `AUDIOFOCUS_GAIN`
  exclusively; the app shows "Another app is using the audio right now." Stop that app.
- **Stops as soon as the screen locks (Android)**: the foreground service did not start; check
  `adb logcat -s MetronomeForegroundService` for `MissingForegroundServiceTypeException` or
  `ForegroundServiceStartNotAllowedException` (play must be pressed while the app is visible).
- **Stops when switching apps (iOS)**: `UIBackgroundModes` missing from `Info.plist`, or the
  session was not activated (`IosAudioSession` logs the error).
- **Beat light early on Bluetooth**: `reportOutputLatencyNanos` not called after the route change
  (iOS) or `calculateLatencyMillis` failing (Android); the value is logged at debug level.
- **Custom import "isn't a WAV"** for a real WAV: the file uses a compressed WAV codec (ADPCM,
  µ-law); convert to PCM.
- **`engineHostTest` timing failures**: run the renderer test alone
  (`ctest -R metronome_renderer`) and compare the reported frame offsets; a change to
  `framesPerTick` rounding shows up as a growing error across the ten-minute run.
