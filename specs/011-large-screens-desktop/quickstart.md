# Quickstart: validating large screens and desktop (feature 011)

Run from `app/` unless noted. Every Gradle command goes through `gradle-run` in agent sessions.

## 1. Targets

- **Android desktop emulator** (once per machine; 4.2 GB image, research hands-on check):
  ```sh
  SDK=~/Library/Android/sdk
  yes | $SDK/cmdline-tools/latest/bin/sdkmanager --install "system-images;android-34;android-desktop;arm64-v8a"
  echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd -n Tembra_Desktop_API34 \
    -k "system-images;android-34;android-desktop;arm64-v8a" -d desktop_medium
  sed -i '' 's/^hw.keyboard=no/hw.keyboard=yes/' ~/.android/avd/Tembra_Desktop_API34.avd/config.ini
  $SDK/emulator/emulator -avd Tembra_Desktop_API34 -port 5580 &      # add -no-window for headless runs
  ```
  It is a 1920×1080 dp desktop without a touchscreen; drive it with the Mac's mouse, trackpad and keyboard.
- **Owner's Android tablet in desktop mode** (physical; run conditions: `tools/apply-device-run-conditions.sh <serial>`).
- **iPad simulator** (`iPad Pro 13-inch (M5)`) with windowed apps on (Settings → Multitasking & Gestures → Windowed
  Apps), and an iPad if available. Pointer: the Mac's trackpad with "Capture Pointer"; keyboard: "Capture Keyboard".
- Phones and the foldables as in every feature (regression only).

## 2. Lanes

```sh
./gradlew checkAndroidHost
./gradlew checkIos -Ptembra.iosSimulator="iPad Pro 13-inch (M5)"
tools/gradlew-device.sh emulator-5580 checkAndroidDevice
tools/gradlew-device.sh emulator-5580 :androidApp:connectedDebugAndroidTest
tools/gradlew-device.sh <tablet-serial> checkAndroidDevice
cd iosApp && xcodebuild test -project iosApp.xcodeproj -scheme TembraDesktopTests \
  -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5)'
```

All green, zero warnings (`--warning-mode all`).

## 3. Reach (User Story 1)

- `aapt2 dump badging androidApp/build/outputs/apk/release/*.apk | grep -i feature`: touchscreen and microphone listed as
  not required.
- After the release reaches Play: Play Console → Reach and devices → Device catalog → a Chromebook model without a
  touchscreen shows "Supported".
- Desktop emulator, mouse and keyboard only: tune a string by ear (Tuner, E, 6), start the metronome at 96 BPM in 3/4,
  open the demo project and play, import a WAV through the picker, split a clip and drag one piece to another track,
  undo twice, rename and duplicate a project. Everything reachable without touch.
- No-microphone check: the emulator always reports a microphone, so the automated proof is
  `MicrophoneAvailabilityTest` with a fake audio-device seam; by hand, on a device whose microphone is disabled or absent,
  Auto Listen shows "No microphone is available on this device" and asks for nothing.

## 4. Windows (User Story 2), by ear

1. Metronome at 120 BPM, running. Drag the window edges across narrow, medium and wide twenty times; maximise; restore;
   snap left and right (Meta+[ and Meta+]); minimise and restore. Listen: no gap, no flam, no restart.
2. Repeat with the demo project playing; watch the playhead and the readout keep going.
3. Tuner in Auto Listen with a tone near the microphone: resizing keeps listening; minimising stops it ("Mic active"
   pill hides); restoring resumes it. Clicking another window keeps it listening.
4. Scroll the timeline to bar 9, zoom in, select a clip, open its menu, then resize across both widths: same bar at the
   left edge, same zoom, same selection, menu still on the clip.
5. Shrink the window as far as it goes: it stops at 360 × 480 dp and every screen remains usable.
6. Quit and relaunch: the window reopens at its last size and place (record what the platform does: research R3).
7. First launch after `adb uninstall`: record the window's size and shape (expected: landscape, wide).
8. iPad: steps 1–4 in a resizable window, by dragging the window corner; rotation as well.

## 5. Pointer, keyboard and drops (User Stories 3–5)

- Hover every icon-only control on each destination (tooltips), right-click a clip, a track header and a project card
  (menus at the pointer), drag a clip with the mouse and press Escape mid-drag, scroll and Shift-scroll and Ctrl-scroll
  the timeline, pinch on the trackpad, wheel over the tempo dial and each slider. Watch the pointer shape (Android).
- Keyboard: Ctrl+1–5, Ctrl+/ (sheet), Escape, Metronome Space/↑/↓/Shift/T, Tuner A/E/1–6/Space/L, Projects Ctrl+F and
  Ctrl+N, Timeline's keys; type in the Projects search and confirm nothing fires. Meta+/ on Android lists the app's
  shortcuts. On iPad, the menu bar lists the Command shortcuts.
- Drops: from the desktop's Files app drag one WAV onto a track lane (clip at the drop point), two files onto empty
  space (two tracks), three onto Projects (new project), a folder with WAVs and a text file (imported or refused per
  platform), a PDF alone ("No audio files were found"). Repeat from the iPad's Files app.

## 6. Performance

```sh
./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf
cd perf && uv run tracepulse run --device pixel6 --label 011-large-screens --no-build --apk … --test-apk …
```

The `windowing` journey records `shell.windowRelayout`, `shell.shortcutNavigate` and `timeline.contextMenu` with zero
xruns; after the merge, a labelled session on each available device and the desktop emulator.

## 7. Chromebook store screenshots (User Story 6)

On the desktop emulator with the release build (`tools/release-android.sh --serial emulator-5580`): maximise the window,
then capture `adb -s emulator-5580 exec-out screencap -p > shot.png` (3840×2160) of the demo project playing, the tuner
in By Ear, the metronome running and a library of four realistic projects; scale to 2560×1440
(`sips -z 1440 2560 shot.png`), convert to JPEG without alpha, and check: no debug chips, no test data, no added text.
Keep them with the Play upload kit (`play-store-listing/chromebook/`) and upload to Play Console's Chromebook slot.
