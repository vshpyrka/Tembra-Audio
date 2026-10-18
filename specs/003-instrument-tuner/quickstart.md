# Quickstart: Instrument Tuner

**Feature**: `003-instrument-tuner` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Everything runs from `app/` with the committed Gradle wrapper. Prerequisites are unchanged from
features 001 and 002 (`app/README.md`): JDK 21, Xcode 26 with an iOS simulator runtime, Android SDK
platform 37 with NDK 29 and CMake 3.31.6, the `android` CLI. New for this feature: a device or
simulator with a working microphone (the iOS simulator uses the Mac's input device; an Android
emulator forwards the host microphone when created with audio input enabled) and something that
makes a steady note: a guitar, a cello, or a reference tone played from another device.

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

Expected: the app opens on Timeline (Home until 2026-10-17). Tap **Tuner** (the music-note icon; the navigation items have no text labels since 2026-10-17): the Tuner item is marked (no top bar since 2026-10-16), the selectors show
Guitar, 6-String, Auto Listen, and the platform's microphone prompt appears at once (first launch).
Allow it: the pill "MIC ACTIVE" appears and the meter shows "Listening".

## 2. Automated tests (spec FR-028)

```bash
cd app
./gradlew engineHostTest          # Catch2: MPM accuracy and octave tests, analyzer, tone, sample player, recordings on pitch, ABI 2
./gradlew checkAndroidHost        # JVM: core domain, ListeningPolicy, TunerViewModel, preferences, tokens, icon assets, theme parity
./gradlew checkIos                # iOS simulator: the same commonTest classes + TunerScreenTest, TunerMeterTest, component tests, NativeAudioEngineTest, DataStore round trip
./gradlew checkAndroidDevice      # connected device: uiTest classes + NativeAudioEngineTest + TunerSignalEndToEndTest + AndroidAudioFocusTest + DataStore round trip + AndroidBackTest
./gradlew :androidApp:connectedDebugAndroidTest   # AndroidMicrophonePermissionTest (the app module's instrumented test)
```

Every lane ends green with zero warnings (`--warning-mode all`). What each test proves is listed in
[research.md](research.md) R12 and the contracts. Listening tests inside `NativeAudioEngineTest` run
only when the test process holds the microphone permission: grant it to the test package with
`adb shell pm grant audio.tembra.ui.test android.permission.RECORD_AUDIO` before `checkAndroidDevice`
(the instrumentation runner's package is printed by the task); on the simulator the test process
inherits the Mac's microphone access.

## 3. Auto Listen (User Story 1)

1. With Auto Listen active, play the low E of a standard-tuned guitar (or a steady 82.4 Hz tone).
   Within a quarter of a second the meter shows **E2**, the live frequency (about "82.4 Hz"), the
   chip "Reference 82.4 Hz", the needle centred, the pill **IN TUNE** lit.
2. Tune the string down slightly: the needle leans left, **Flat** and its arrow light up, the pill
   goes unlit. Tune up past the note: **Sharp** lights. Tune more than 50 cents down: the note reads
   **D#2** and the needle jumps to the sharp end (chromatic behaviour, spec edge cases).
3. Play the cello C (65.4 Hz) or a 7-string's B1 (61.7 Hz): the octave is right (C2 / B1, not C3 / B2).
4. Stop playing: within half a second the meter rests ("Listening", needle centred, pill unlit).
5. Hum, whistle above E5 or stay silent: the meter rests; it never shows a wrong note with the pill lit.

Offline proof of accuracy, response time and octave correctness is `engineHostTest` (SC-001 to SC-003);
this section is the on-device confirmation with a real instrument (SC-004).

### Test signals (debug builds, no instrument or microphone needed)

A launch argument feeds a generated tone through the real engine in place of the microphone (spec
FR-032, research R16). The app starts on Tuner in Auto Listen with the pill **TEST SIGNAL** and never
asks for the microphone. Use a catalogue id or `NOTE:CENTS`:

```bash
# Android (debug build)
adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity --es tembra.tuner.testSignal e2-flat-20
adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity --es tembra.tuner.testSignal "E2:+20"
# iOS simulator (BUNDLE from §1)
xcrun simctl launch "iPhone 17" "$BUNDLE" --tembra-tuner-test-signal=e2-flat-20
xcrun simctl launch "iPhone 17" "$BUNDLE" "--tembra-tuner-test-signal=E2:+20"
```

Catalogue ids and their expected readings are the table in [data-model.md](data-model.md) §9
(`e2-in-tune`, `e2-flat-20`, `e2-sharp-20`, `e2-flat-45`, `a2-sharp-40`, `d3-flat-5`, `g3-in-tune`,
`b3-in-tune`, `e4-in-tune`, `c2-in-tune`, `b1-in-tune`, `a1-in-tune`, `e5-in-tune`, `out-of-range-2khz`).
Expected on screen: `e2-in-tune` → E2, needle centred, IN TUNE lit; `e2-flat-20` → needle 36° left,
Flat lit; `e2-flat-45` → needle 81° left; `d3-flat-5` → Flat lit, pill unlit; `out-of-range-2khz` →
"Listening". The automated form of this check is `TunerSignalEndToEndTest` (§2), which asserts every
entry's frequency within 1 cent on the real engine of both platforms. Release builds ignore the argument.

## 4. Instrument, string count and tuning presets (User Story 2)

1. Tap **By Ear**, then **Cello**: the string-count selector beside the preset picker disappears
   and four cards C2 G2 D3 A3 show numerals IV to I and 65.4 / 98.0 / 146.8 / 220.0 Hz. Tap
   **Auto Listen**: no string-count selector is shown for either instrument.
2. Tap **Guitar**, **By Ear**, then **7-String** beside the picker, then the picker: "Standard
   7-String" and "Drop A" are listed; choose Drop A: the lowest card reads VII, A1, 55.0 Hz.
3. Choose 6-String and open the picker: seven presets from Standard to Open D; choose Drop D: card VI
   reads D2 73.4 Hz.
4. Kill and relaunch the app, open Tuner: Guitar, 6-String, Drop D, By Ear and the loop toggle are
   as you left them (DataStore file `<preferences dir>/tembra.preferences_pb`).

## 5. By Ear, loop tone and drop-in recordings (User Stories 3 and 4)

1. In By Ear with **Loop Tone** active, tap **Audition** on E2: a clean tone sounds at once through
   the current output route, the card is outlined in the accent colour, its button reads **Playing**,
   the pill reads **TONE OUT**. It keeps sounding for a minute or more with no gap or click.
2. Tap **Audition** on A2: E2 stops, A2 starts, no overlap, no gap you can hear. Tap **Playing**: silence,
   no click.
3. Switch Loop Tone off and audition D3: the tone lasts about two seconds and the card returns to
   Audition by itself. Switch the toggle on during a one-shot: the tone keeps repeating.
4. While a tone plays: change preset, instrument, or switch to Auto Listen, or press Home: the tone stops
   and never restarts on return.
5. Drop-in recording: put a WAV of the guitar low E at `shared/ui/src/commonMain/composeResources/files/tuner/guitar-E2.wav`
   (see [contracts/reference-sounds.md](contracts/reference-sounds.md) for the naming rule), rebuild:
   auditioning E2 plays the recording, looping seamlessly; every other string still plays the synthesized
   tone. `./gradlew engineHostTest` now checks that file's pitch and fails if it is more than 1 cent off.
   Remove the file before committing unless it is meant to ship.

## 6. Microphone permission (User Story 5)

Reset and drive the permission states:

```bash
# Android
adb shell pm revoke audio.tembra.app android.permission.RECORD_AUDIO     # back to "not determined" on the next launch
# iOS simulator (BUNDLE from §1)
xcrun simctl privacy "iPhone 17" reset microphone "$BUNDLE"
xcrun simctl privacy "iPhone 17" grant microphone "$BUNDLE"
xcrun simctl privacy "iPhone 17" revoke microphone "$BUNDLE"
```

1. Reset, launch, open Tuner: the prompt appears. **Deny**: the meter is replaced by "Microphone
   needed", the sentence, and **Allow microphone**; no pill. By Ear still auditions.
2. Android: tap Allow microphone → the prompt appears again (rationale state); deny again → the OS
   stops asking; tap Allow microphone → the app's settings page opens. iOS: Allow microphone opens
   the settings page directly.
3. Grant in settings, return to the app: listening starts by itself, pill MIC ACTIVE.
4. While listening, revoke in settings (Android drops the app; iOS restarts it) and return: the denied
   panel is shown, never a frozen meter.

## 7. Leaving, backgrounding, the OS microphone indicator, interruptions (spec FR-007, FR-016, SC-007)

1. While listening, watch the OS indicator: Android's green microphone dot in the status bar, the iOS
   orange dot. Tap **Timeline**: the dot disappears within a second. Return to Tuner: it comes back.
2. Press Home or lock the device while listening: the dot disappears. Return: listening resumes.
3. Switch to By Ear: the dot disappears and stays off while tones play. Switch back: on again.
4. Privacy backstop check (developer only): pause the app in the debugger for six seconds while
   listening: the engine stops capture by itself (the OS dot goes off); on resume the meter restarts.
5. Interruptions: Android emulator `adb emu gsm call 5551234` (or a real call) while listening →
   the pill disappears; hang up → listening resumes. Play music in another app, then audition a
   tone: the music ducks (Android) or pauses (iOS) and resumes when the tone stops. Unplug
   headphones during a tone: the tone stops (iOS) or continues on the speaker (Android), the card
   never stays stuck on Playing.

## 8. Layouts and screenshots (User Story 6, spec FR-029)

| Band | Android | iOS |
|---|---|---|
| compact (under 600 dp) | Pixel 6 Pro portrait | iPhone 17 simulator |
| expanded (840 dp and wider) | Pixel 6 Pro with `adb shell wm density 380` in landscape, or a tablet | iPad Pro 13-inch simulator landscape |
| phone landscape (600 dp and wider, under 480 dp tall) | Pixel 6 Pro rotated (`adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1`; `user_rotation 0` afterwards) | iPhone 17 simulator rotated (Simulator menu Device › Rotate Left, or the AppleScript in `screenshots/NOTES.md`) |

Capture both modes at both widths on both platforms, both modes in phone landscape (six-string guitar
and, for By Ear, also the seven-string guitar and the cello) as `<platform>-landscape-<autoListen|byEar>[-<preset>].png`,
the denied state at compact, and both modes at
the largest system font size on compact (Android: `adb shell settings put system font_scale 2.0`;
iOS: Settings → Accessibility → Larger Text), into `specs/003-instrument-tuner/screenshots/` as
`<platform>-<compact|expanded>-<autoListen|byEar|denied>[-largefont].png`; record devices and
findings in `screenshots/NOTES.md`. Android: `android screen capture --output <file>`; iOS:
`xcrun simctl io "<device>" screenshot <file>` (portrait captures need `sips -r -90` for landscape).
State matrix (spec FR-029, SC-012): `app/tools/capture-tuner-states.sh --android` and
`app/tools/capture-tuner-states.sh --ios "iPhone 17"` launch the debug app once per catalogue id,
wait two seconds and capture `<platform>-compact-autoListen-<id>.png` into the screenshots folder.
Review each against the expected reading in [data-model.md](data-model.md) §9: needle angle
(1.8° per cent), note name and octave, pill lit only for the in-tune entries, the highlighted end
label on the offset entries, and the resting state for `out-of-range-2khz`. Record the table of
expected versus seen in `screenshots/NOTES.md`.

Review against `design/stitch/screens/instrument-tuner/phone/index.html` (rendered) and the tablet screenshots: selectors, pill, gradient card, gauge geometry, note sizes, cards, picker, toggle. Nothing
clipped, overlapping or under the bottom bar at any size; every label whole at the large font.
Review the phone-landscape captures against `design/stitch/screens/tuner-landscape-auto-listen-b/screenshot.jpg`
and `tuner-landscape-by-ear-g/screenshot.jpg` (`-e` for seven strings): the settings column holds the
header with the pill, the selectors and (By Ear) the picker and toggle; the meter fits its card
without scrolling; every card is whole in two rows.

## 9. Pointer, keyboard and screen reader

Attach a mouse and keyboard (Android desktop mode or the simulator with hardware keyboard): hover
every segment, the picker, the toggle and every card button (visible hover state); Tab through them
in reading order (visible focus ring); Space/Enter activates. TalkBack / VoiceOver: the meter announces
"Tuner meter, E2, 4 cents sharp" and "Listening" when resting; segments announce their label and
"selected"; the toggle announces on/off; a card button announces "Audition E2" and, when sounding,
"Stop E2" with the card "Playing". All automated in `TunerScreenTest` and `TunerMeterTest` except the
screen-reader voice itself.

## 10. Troubleshooting

- `tembra_tuner_start` returns `TEMBRA_ERR_DEVICE` (logged by the ViewModel in debug): the permission
  is missing (§6) or another app holds an exclusive input; Oboe falls back to shared mode by itself.
- No reading on an Android emulator: create the AVD with the host microphone enabled
  (`android emulator create … ` then Extended controls → Microphone → "Virtual microphone uses host audio input").
- The iOS simulator hears nothing: check the Mac's input device in System Settings → Sound.
- Needle jitters on a real string: expected during the first 100 ms of a pluck; steady thereafter
  (SC-006 is measured on a steady tone).
- A test signal shows nothing: the argument is read only by debug builds; check `BuildConfig.DEBUG`
  (Android) or the Debug configuration (iOS) and the spelling of the id (`data-model.md` §9).
