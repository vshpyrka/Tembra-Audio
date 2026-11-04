# Quickstart: validating large screens and desktop (feature 011)

Run from `app/` unless noted. Every Gradle command goes through `gradle-run` in agent sessions.

## 1. Targets

- **Android Desktop (Preview) emulator, primary** (once per machine; the Googlebook-era image from the SDK's preview
  channel, research "Hands-on check: Desktop (Preview)"):
  ```sh
  SDK=~/Library/Android/sdk
  yes | $SDK/cmdline-tools/latest/bin/sdkmanager --channel=3 --install "system-images;android-37.1;android-desktop;arm64-v8a"
  echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd -n Tembra_Desktop_API37 \
    -k "system-images;android-37.1;android-desktop;arm64-v8a" -d desktop_api37
  CFG=~/.android/avd/Tembra_Desktop_API37.avd/config.ini
  sed -i '' 's/^hw.keyboard=no/hw.keyboard=yes/' $CFG
  # the emulator wants about twice the data partition free on the host; 4G fits with 11 GB free
  grep -q '^disk.dataPartition.size' $CFG && sed -i '' 's/^disk.dataPartition.size=.*/disk.dataPartition.size=4G/' $CFG \
    || echo 'disk.dataPartition.size=4G' >> $CFG
  $SDK/emulator/emulator -avd Tembra_Desktop_API37 -port 5582 &      # add -no-window for headless runs
  $SDK/platform-tools/adb -s emulator-5582 wait-for-device shell am switch-user 10   # the desktop session is user 10
  ```
  A 1920×1200 dp desktop with a touchscreen, like a Googlebook; drive it with the Mac's mouse, trackpad and keyboard.
  Apps open in free-form windows by default (the app's first window is 1382×864 dp, landscape).
- **Android 14 desktop emulator, secondary** (the no-touchscreen case and the 16:9 screenshots; 4.2 GB image):
  ```sh
  yes | $SDK/cmdline-tools/latest/bin/sdkmanager --install "system-images;android-34;android-desktop;arm64-v8a"
  echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd -n Tembra_Desktop_API34 \
    -k "system-images;android-34;android-desktop;arm64-v8a" -d desktop_medium
  sed -i '' 's/^hw.keyboard=no/hw.keyboard=yes/' ~/.android/avd/Tembra_Desktop_API34.avd/config.ini
  $SDK/emulator/emulator -avd Tembra_Desktop_API34 -port 5580 &
  ```
  A 1920×1080 dp desktop without a touchscreen. Run one desktop emulator at a time next to the other targets (memory).
- Disk: the two images take about 9 GB; keep 10 GB free before creating them.
- **Owner's Android tablet in desktop mode** (physical; run conditions: `tools/apply-device-run-conditions.sh <serial>`).
- **iPad simulator** (`iPad Pro 13-inch (M5)`) with windowed apps on (Settings → Multitasking & Gestures → Windowed
  Apps), and an iPad if available. Pointer: the Mac's trackpad with "Capture Pointer"; keyboard: "Capture Keyboard".
- Phones and the foldables as in every feature (regression only).

## 2. Lanes

```sh
./gradlew checkAndroidHost
./gradlew checkIos -Ptembra.iosSimulator="iPad Pro 13-inch (M5)"
tools/gradlew-device.sh emulator-5582 checkAndroidDevice                      # Desktop (Preview), primary
tools/gradlew-device.sh emulator-5582 :androidApp:connectedDebugAndroidTest
tools/gradlew-device.sh emulator-5580 :androidApp:connectedDebugAndroidTest   # Android 14 desktop, secondary
tools/gradlew-device.sh <tablet-serial> checkAndroidDevice
cd iosApp && xcodebuild test -project iosApp.xcodeproj -scheme TembraDesktopTests \
  -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5)'
```

All green, zero warnings (`--warning-mode all`).

Results on branch `011-large-screens-desktop` (T135; sounds muted on every target: media stream silent, the Mac's
output muted for the emulators and simulators):

| Target | Lane | Result |
|---|---|---|
| macOS host | `checkAndroidHost`, `engineHostTest` | green, zero warnings (engine 204 tests) |
| iPhone 18 Pro simulator | `checkIos` | green (core 198, engine 91, data 121, ui 971) |
| iPhone Duo simulator | `checkIos` | green |
| Tembra_Desktop_API37 | `checkAndroidDevice` | 2 762 tests, green after the tooltip test's window fit |
| Tembra_Desktop_API37 | `:androidApp:connectedDebugAndroidTest` | 45/45 green, zero warnings |
| Tembra_Desktop_API34 | `:androidApp:connectedDebugAndroidTest` | 45/45 green, zero warnings (after bounded idle waits: an unbounded `waitForIdleSync` hung a playing run for hours on this image) |
| Tembra_Desktop_API34 | `checkAndroidDevice` (beyond §2's list) | not a lane for this image: its test activity opens in a 412 dp phone-sized window, and the wide layouts' mouse tests start outside it |
| Tembra_Desktop_API37, 2026-11-04 (after T138–T142) | `checkAndroidDevice --continue` | core 198, engine 95, data 121, ui 980 (2 skipped), green; one earlier run lost `NativeAudioEngineTest.theStreamProbeSeesAnOpenOutput` right after an emulator cold boot (the first output stream opened after the probe's wait) and the class passed 17/17 on its own |
| Tembra_Desktop_API37, 2026-11-04 | `:androidApp:connectedDebugAndroidTest` | 45/45 green, zero warnings (full-screen retry included) |
| Host, 2026-11-04 | `checkAndroidHost` | green, zero warnings (with `FullScreenRequestTest`, `TooltipPositionTest`, `ScrollbarThumbTest`) |
| Pixel_9_Pro_Fold emulator, 2026-11-04 | `checkAndroidDevice --continue` | core 198, data 121, ui 980 (4 skipped) green; engine 94/95: `theStreamProbeSeesAnOpenOutput` counted streams and lost to a previous test's stream closing as the tone's opened (feature 009 test race, also seen once on API37); it now tells streams apart by player, and the engine module passed 95/95 twice |
| Pixel_9_Pro_Fold emulator, 2026-11-04 | `:androidApp:connectedDebugAndroidTest` | 45 tests, 0 failed, 12 skipped (the desktop tests skip outside free-form windows) |
| Tembra_Desktop_API34, 2026-11-04 | `:androidApp:connectedDebugAndroidTest` | 45/45 green, zero warnings; a first run right after the emulator's snapshot boot found the morning's app window restored beside the test's ("expected one task, found 2") and passed after `am force-stop com.tembraaudio` |

One desktop emulator at a time: with both running (one windowed), one crashed twice on this Mac (2026-11-04).

## 3. Reach (User Story 1)

- `aapt2 dump badging androidApp/build/outputs/apk/release/*.apk | grep -i feature`: touchscreen and microphone listed as
  not required.
- After the release reaches Play: Play Console → Reach and devices → Device catalog → a Chromebook model without a
  touchscreen shows "Supported".
- Android 14 desktop emulator (no touchscreen), mouse and keyboard only: tune a string by ear (Tuner, E, 6), start the metronome at 96 BPM in 3/4,
  open the demo project and play, import a WAV through the picker, split a clip and drag one piece to another track,
  undo twice, rename and duplicate a project. Everything reachable without touch.
- Mixed input on the Desktop (Preview) emulator (it has a touchscreen, like a Googlebook): alternate taps (click with
  the emulator's touch mode) and mouse clicks on the same controls; both work, and touch never shows hover or tooltips.
- No-microphone check: the emulator always reports a microphone, so the automated proof is
  `MicrophoneAvailabilityTest` with a fake audio-device seam; by hand, on a device whose microphone is disabled or absent,
  Auto Listen shows "No microphone is available on this device" and asks for nothing.

## 4. Windows (User Story 2), by ear

1. Metronome at 120 BPM, running. Make fifty window changes: drag the window edges across narrow, medium and wide;
   maximise; restore; snap left and right (Meta+[ and Meta+]); Timeline menu → Full screen and Exit full screen;
   minimise and restore. Listen: no gap, no flam, no restart. Exit full screen returns the same size and place.
2. Repeat with the demo project playing; watch the playhead and the readout keep going.
3. Tuner in Auto Listen with a tone near the microphone: resizing keeps listening; minimising stops it ("Mic active"
   pill hides); restoring resumes it. Clicking another window keeps it listening.
4. Scroll the timeline to bar 9, zoom in, select a clip, open its menu, then resize across both widths: same bar at the
   left edge, same zoom, same selection, menu still on the clip.
5. Shrink the window as far as it goes: it stops at 360 × 480 dp and every screen remains usable.
5a. Focus a track header with Tab, click another app's window, click back: the same header has focus, audio never
    stopped.
5b. Move the window to a second display (the emulator's extended displays, or the tablet's external monitor) and back:
    the layout follows the new size and density, audio continues.
6. Resize the window by dragging its corner, close it with the caption's close button and relaunch from the launcher:
   the window reopens at the size and place it was left at. Closing the window ends the process that would host an
   instrumented test, and a size set with `am task resize` is never remembered (research hands-on check "Desktop
   (Preview)"), so this is an adb-scripted check on the emulators (by hand on the tablet); record what each target does:
   ```sh
   A() { adb -s emulator-5582 "$@"; }
   task() { A shell dumpsys activity activities | grep -m1 -oE '\* Task\{[0-9a-f]+ #[0-9]+ type=standard A=[0-9]+:com.tembraaudio' \
     | grep -oE '#[0-9]+' | tr -d '#'; }
   bounds() { A shell dumpsys activity activities | awk "/Task\{.*#$1 /{f=1} f&&/mBounds=/{print; exit}" \
     | grep -oE 'mBounds=Rect\([^)]*\)'; }                       # Rect(L, T - R, B) in px
   T=$(task); bounds $T
   A shell input mouse swipe <right-2> <bottom-2> <newRight> <newBottom> 1500     # drag the bottom-right corner
   A shell input mouse tap <right-58> <top+36>                    # the caption's close button (240 dpi)
   A shell am start --user current -n com.tembraaudio/audio.tembra.android.MainActivity
   bounds $(task)                                               # equals the dragged bounds
   ```
   Verified on Android 17 with `main`'s manifest: dragged to Rect(403, 180 - 1905, 1155), closed, relaunched at the
   same Rect.
7. First launch after `adb uninstall`: record the window's size and shape (expected: landscape, wide).
8. iPad: steps 1–4 in a resizable window, by dragging the window corner; rotation as well.

## 5. Pointer, keyboard and drops (User Stories 3–5)

- Hover every icon-only control on each destination (tooltips) and every other control (a hover state over exactly
  the area that clicks), right-click a clip, a track header and a project card (menus at the pointer), drag a clip with
  the mouse and press Escape mid-drag, scroll and Shift-scroll and Ctrl-scroll the timeline, pinch on the trackpad,
  wheel over the tempo dial and each slider. Watch the pointer shape (Android): hand over controls, text in fields,
  grab and grabbing on clips, busy while a long import runs.
- Scrollbars: wheel-scroll the timeline's tracks and time, the Projects grid, the tuner's and metronome's panes and the
  shortcut sheet: a scrollbar appears, fades after a second, and dragging it scrolls; touch-scrolling shows none.
- Keyboard: Ctrl+1–5, Ctrl+/ (sheet), Escape, Metronome Space/↑/↓/Shift/T, Tuner A/E/1–6/Space/L, Projects Ctrl+F and
  Ctrl+N, Timeline's keys; type in the Projects search and confirm nothing fires. Meta+/ on Android lists the app's
  shortcuts. On iPad, the menu bar lists the Command shortcuts.
- Drops: from the desktop's Files app drag one WAV onto a track lane (clip at the drop point), two files onto empty
  space (two tracks), three onto Projects (new project), a folder with WAVs and a text file (imported or refused per
  platform), a PDF alone ("No audio files were found"). Repeat from the iPad's Files app.
- Open with: in the desktop's Files app, right-click a WAV → Open with → Tembra Audio: a new project opens with it.
  Select a WAV and an MP3 and open both: one project, two tracks. With a project playing, opening a file asks first;
  Cancel leaves everything as it was. Repeat from the iPad's Files app (Share or Open in).

## 6. Desktop app quality guidelines self-check (SC-010)

On the Desktop (Preview) emulator with the release build, walk the guideline tests in scope and record pass or fail
with a screenshot each in the pull request: T-Scrollbar_Display, T-Hover_Parity, T-Desktop_Menus,
T-Request_Fullscreen_Mode, T-Keyboard_Navigation, T-Keyboard_Parity, T-Custom_Cursors, T-Cursor_Target_Size,
T-Drag_Drop_Batch (into the app), T-File_Picker, T-File_Handlers and T-Offline_Support (airplane mode: every screen
works, nothing waits on the network). The "Optimized for desktop" label itself is Google's to assign after review;
nothing in the app or the console requests it.

## 7. Performance

```sh
./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf
cd perf && uv run tracepulse run --device pixel6 --label 011-large-screens --no-build --apk … --test-apk …
```

The `windowing` journey records `shell.windowRelayout`, `shell.shortcutNavigate` and `timeline.contextMenu` with zero
xruns; the trial round fails if any of the three sections is missing. After the merge, a labelled session on each
available device and the Desktop (Preview) emulator.

## 8. Chromebook store screenshots (User Story 6)

The Chromebook slot takes 16:9 images, and the Desktop (Preview) display is 16:10 (2880×1800), so the screenshots come
from the Android 14 desktop emulator's 16:9 display. With the release build
(`tools/release-android.sh --serial emulator-5580`): maximise the window,
then capture `adb -s emulator-5580 exec-out screencap -p > shot.png` (3840×2160) of the demo project playing, the tuner
in By Ear, the metronome running and a library of four realistic projects; scale to 2560×1440
(`sips -z 1440 2560 shot.png`), convert to JPEG without alpha, and check: no debug chips, no test data, no added text.
Keep them with the Play upload kit (`play-store-listing/chromebook/`) and upload to Play Console's Chromebook slot.
