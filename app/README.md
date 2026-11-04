# Tembra Audio — application code

Kotlin Multiplatform app for Android and iOS with a shared Compose Multiplatform UI on top of one
C++ real-time audio engine. This folder is the Gradle root. Governing rules:
`.specify/memory/constitution.md`; architecture decision: `docs/research/kmp-code-architecture.md`.

## Prerequisites (macOS)

| Need | Check | Install |
|---|---|---|
| JDK 21 | `java -version` | Temurin 21 |
| Xcode 27.1 with an iOS simulator runtime (the iOS 27 iPhone and the iPhone Duo need its SDK) | `xcodebuild -version`, `xcrun simctl runtime list` | `xcodebuild -downloadPlatform iOS` (about 8 GB) |
| Android SDK: platform 37, build-tools 36, platform-tools | `android sdk list` | `android sdk install "platforms;android-37.0" "build-tools;36.0.0" platform-tools` |
| Android NDK 29 and CMake 3.31.6 (also used for iOS and host builds) | `ls ~/Library/Android/sdk/ndk/29.0.14206865 ~/Library/Android/sdk/cmake/3.31.6` | `android sdk install "ndk;29.0.14206865" "cmake;3.31.6"` |
| `android` CLI | `android --version` | see the Android CLI docs; `android update` when prompted |
| A device: Android phone with USB debugging (or an emulator); iOS simulator (a physical iPhone works for running and screenshots, not for the Kotlin test lane) | `adb devices`, `xcrun simctl list devices available` | |

Point Gradle at the SDK once (git-ignored file):

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > app/local.properties
```

Gradle itself is the committed wrapper (`./gradlew`, Gradle 9.7.0). A missing NDK or CMake fails
configuration with a message that names the exact `android sdk install` command.

## Build and run

Android (connected device or emulator):

```bash
cd app
./gradlew :androidApp:installDebug
adb shell am start -n com.tembraaudio/audio.tembra.android.MainActivity
```

iOS (simulator; Xcode runs `./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode` in a build phase):

```bash
cd app/iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' -derivedDataPath build build
xcrun simctl boot "iPhone 18 Pro"
xcrun simctl install "iPhone 18 Pro" "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app"
xcrun simctl launch "iPhone 18 Pro" com.tembraaudio
```

Or open `app/iosApp/iosApp.xcodeproj` in Xcode and press Run. Signing: `Configuration/Config.xcconfig`
(`TEAM_ID` empty is fine for the simulator).

Android desktop windows (feature 011, `specs/011-large-screens-desktop/quickstart.md` §1): two desktop emulators, run
one at a time next to the other targets. The primary is the Android 17 "Desktop (Preview)" image (a Googlebook-like
1920×1200 dp desktop with a touchscreen; it is on the SDK's preview channel only); the secondary is the Android 14
desktop image (1920×1080 dp, no touchscreen: the device Google Play filtered the app away from before 011, and the
16:9 display the Chromebook store screenshots come from).

```bash
SDK=~/Library/Android/sdk
# Primary: Tembra_Desktop_API37
yes | $SDK/cmdline-tools/latest/bin/sdkmanager --channel=3 --install "system-images;android-37.1;android-desktop;arm64-v8a"
echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd -n Tembra_Desktop_API37 \
  -k "system-images;android-37.1;android-desktop;arm64-v8a" -d desktop_api37
CFG=~/.android/avd/Tembra_Desktop_API37.avd/config.ini
sed -i '' 's/^hw.keyboard=no/hw.keyboard=yes/' $CFG
# The emulator wants about twice the data partition free on the host ("Not enough space to create userdata
# partition"): 4G boots with 10–12 GB free, 6G does not.
grep -q '^disk.dataPartition.size' $CFG && sed -i '' 's/^disk.dataPartition.size=.*/disk.dataPartition.size=4G/' $CFG \
  || echo 'disk.dataPartition.size=4G' >> $CFG
$SDK/emulator/emulator -avd Tembra_Desktop_API37 -port 5582 &            # add -no-window for headless runs
# The image runs a headless system user: the desktop session is user 10. After every cold boot:
adb -s emulator-5582 wait-for-device shell am switch-user 10
adb -s emulator-5582 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s emulator-5582 shell am start --user current -n com.tembraaudio/audio.tembra.android.MainActivity

# Secondary: Tembra_Desktop_API34 (no touchscreen)
yes | $SDK/cmdline-tools/latest/bin/sdkmanager --install "system-images;android-34;android-desktop;arm64-v8a"
echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd -n Tembra_Desktop_API34 \
  -k "system-images;android-34;android-desktop;arm64-v8a" -d desktop_medium
sed -i '' 's/^hw.keyboard=no/hw.keyboard=yes/' ~/.android/avd/Tembra_Desktop_API34.avd/config.ini
$SDK/emulator/emulator -avd Tembra_Desktop_API34 -port 5580 &
```

Apps open in free-form windows on both. `adb shell am task resize <task> <l> <t> <r> <b>` resizes a window without
recreating the activity (it is not clamped to the app's minimum and the system does not remember it); a mouse drag of
the window's corner (`adb shell input mouse swipe …`) is clamped and remembered. The images take about 9 GB.

Release builds, the way users get the app. Debug builds are debuggable on Android and link an unoptimised Kotlin
framework on iOS, so Compose runs far slower there; judge smoothness on these only:

```bash
cd app
tools/release-android.sh   # assembleRelease, signed with the debug key, compiled from the baseline profile, on every adb target
tools/release-ios.sh       # Release configuration on every connected iPhone/iPad and every booted simulator
```

Both install over a debug or perf build in place and launch the app. `--serial`/`--device` pick one target,
`--no-build` reuses the last build, `--clean` uninstalls first (which deletes the app's projects and settings);
`--help` lists the rest. A physical iPhone needs a team: `TEAM_ID` in `Config.xcconfig`, `--team-id` or
`TEMBRA_TEAM_ID`. When a target runs a newer iOS than the default Xcode's SDK (an iOS 27 phone, the iPhone Duo), the
iOS script switches to the Xcode whose SDK covers it.

Device runs on one Android target (feature 010): `tools/gradlew-device.sh <serial> <gradle arguments…>` runs the
wrapper with `ANDROID_SERIAL` set, so `tools/gradlew-device.sh 19021FDEE001AH checkAndroidDevice` runs the device
lane on the Pixel 6 Pro alone. Before any lane, capture or perf round on a physical device apply the owner's run
conditions with `tools/apply-device-run-conditions.sh <serial>` (the screen awake, brightness at its floor, the
music stream silent, do-not-disturb on; the OPPO refuses the brightness settings and is dimmed by hand). The device
UI tests run behind the black `Theme.Tembra.DeviceTest` window. The only step that makes a sound is the loopback
alignment check of feature 010's quickstart §8, announced before it starts.

Run conditions for every device run (feature 010 quickstart §2): brightness at its floor (`tools/apply-device-run-conditions.sh` sets `screen_brightness_mode 0` and `screen_brightness 0`), the music stream silent (`media volume --stream 3 --set 0`), do-not-disturb on (`cmd notification set_dnd on`), the device UI tests behind the black `Theme.Tembra.DeviceTest` window. The iPhone 14 is dimmed by `PerfJourneyTestCase`; its ringer and volume are set by hand, and the Mac stays muted. The loopback check is the only step allowed to sound; run it with a cable where possible.

## Test lanes

One command per lane; each aggregates every `:shared:*` module.

```bash
cd app
./gradlew checkAndroidHost     # JVM unit tests (domain, parser, ViewModel, theme.json parity)
./gradlew checkIos             # iOS simulator: all shared tests + Compose UI tests + native engine link
# add -Ptembra.iosSimulator="iPad Pro 13-inch (M5)" (any simctl name or UDID) to run the iOS lane on another simulator
# iPhone Duo (foldable): needs Xcode 27.1 and an iOS 27.1 simulator runtime; an app built with an older SDK runs
# pillarboxed on the inner display (docs/research/iphone-duo.md)
./gradlew checkAndroidDevice   # connected Android device: Compose UI tests + native engine link + AppDirectories
./gradlew engineHostTest       # C++ engine Catch2 tests on the macOS host (no audio device)

# One Android target while others stay attached (ANDROID_SERIAL): the desktop emulators (feature 011)
tools/gradlew-device.sh emulator-5582 checkAndroidDevice                      # Desktop (Preview), primary
tools/gradlew-device.sh emulator-5582 :androidApp:connectedDebugAndroidTest   # freeform window, mouse and key tests
tools/gradlew-device.sh emulator-5580 :androidApp:connectedDebugAndroidTest   # Android 14 desktop, no touchscreen
# iPad desktop behaviour (menu commands, keys, pointer, window minimum): the TembraDesktopTests XCUITest target
cd iosApp && xcodebuild test -project iosApp.xcodeproj -scheme TembraDesktopTests \
  -destination 'platform=iOS Simulator,name=iPad Pro 13-inch (M5)'
```

The `:androidApp` desktop tests (`androidTest/.../desktop/`) skip with a visible reason on a device without free-form
window management. On `Tembra_Desktop_API37`, run `adb -s emulator-5582 shell am switch-user 10` after a cold boot
before any lane: instrumentation runs as the current user.

Prove the lanes can fail: change an expected value in
`shared/core/src/commonTest/kotlin/audio/tembra/core/version/SemanticVersionTest.kt`, run
`checkAndroidHost` and `checkIos`; each reports exactly one failing test. Revert.

Where tests live:

| Source set | Runs on | Contents |
|---|---|---|
| `commonTest` | JVM host, iOS simulator, Android device | pure logic, ViewModels with `FakeAudioEngine` |
| `androidHostTest` | JVM host only | tests that read repository files (`ThemeJsonParityTest`) |
| `uiTest` (`:shared:ui`) | iOS simulator, Android device | `runComposeUiTest` screen tests (they need a real UI platform): `NavigationShellTest`, `TembraNavigationBarTest`, `TembraNavigationRailTest`, `TembraTopBarTest`, `TembraSupportingPaneTest`, `EmptyStateTest`, `ReduceMotionTest`, `RememberLayoutModeTest`, `AppContentTest`, `DesignTokenPreviewTest`; tuner: `TunerScreenTest`, `TunerScreenByEarTest`, `TunerScreenPlayingTest`, `TunerScreenDeniedTest`, `TunerScreenLayoutTest`, `TunerMeterTest`, `TunerMeterLayoutTest`, `TembraSegmentedControlTest`, `TembraToggleTest`, `TembraStatusPillTest`, `TembraGradientCardTest`; metronome: `MetronomeScreenTest`, `MetronomeControlsTest`, `TempoDialTest`, `AccentCellsTest`, `ClickSoundPickerTest`, `TembraChipRowTest`, `TembraPillTest` |
| `iosTest`, `androidDeviceTest` | one platform | the real `NativeAudioEngine` (descriptor, tone round trip, idle reading, `TunerSignalEndToEndTest`: every test signal through the real engine), `IosAudioSessionTest` / `AndroidAudioFocusTest`, `DataStoreTunerPreferencesTest`, platform directories; metronome: `MetronomeTimingEndToEndTest` (start latency and tick spacing through the real engine), `BecomingNoisyReceiverTest`, `DataStoreMetronomePreferencesTest`, `CustomClickSoundStoreFileTest`; `IosMicrophonePermissionTest`, `IosBackgroundPlaybackHostTest`, `IosSoundFilePickerTest` (`:shared:ui`); `AndroidBackTest` (system back through the shell, Espresso) |
| `androidTest` (`:androidApp`) | Android device | `AndroidMicrophonePermissionTest`: the activity-hosted permission implementation; `AndroidBackgroundPlaybackHostTest` (the metronome foreground service) and `AndroidSoundFilePickerTest` (the WAV picker, through `TestSoundProvider`); run with `./gradlew :androidApp:connectedDebugAndroidTest` |
| `androidHostTest` (`:shared:ui`) | JVM host only | `IconAssetsTest` (glyph files normalised, pinned commit, licence bundled) |
| `commonTest` (feature 009) | JVM host, iOS simulator, Android device | `:shared:core` `project` and `timeline`: the document, the editor, the history, the playback policy, the library query, the viewport, ruler labels, snap, clip hits, time formats; `:shared:data` `projects`: the codec and its golden `fixtures/demo-project.json`, migrations, the validator, the Okio repository on `FakeFileSystem`, the importer, overviews, autosave, the demo installer, and `timeline`: the overview strip's device preference; `:shared:ui`: `ProjectSessionTest`, `ArrangementMapperTest`, `MetronomeBridgeTest`, `ShellViewModelGuardTest`, `TimelineViewModelTest`, `TrackSettingsViewModelTest`, `ProjectsViewModelTest`, `RelativeTimeTest`, `ProjectsLayoutTest`, `TimelineLayoutTest`, `TransportUiStateTest` |
| `uiTest` (feature 009) | iOS simulator, Android device | `TransportBarTest`, `TimelineRulerTest`, `PlayheadOverlayTest`, `TrackLaneTest`, `TrackLaneGestureTest`, `TimelineScreenTest`, `TrackHeaderTest`, `GainReadoutTest`, `InstrumentLabelMenuTest`, `GridPickerTest`, `SnapSheetTest`, `OverviewStripTest`, `MasterVolumePopupTest`, `TempoDialogTest`, `MetronomeSettingsSheetTest`, `TimelineKeyboardTest`, `TimelineSemanticsTest`, `TimelineLargeFontTest`, `TrackSettingsScreenTest`, `TrackSettingsInlineRowTest`, `ProjectsScreenTest`, `ProjectCardTest`, `ProjectOverviewChartTest`, `ProjectsKeyboardTest`, `TembraMenuTest`, `TembraTooltipTest`, `TembraTextFieldTest`, `TembraDialogTest`, `TembraCardTest`, `StopAndLeaveDialogTest` |
| `iosTest`, `androidDeviceTest` (feature 009) | one platform | `NativeAudioEngineTransportTest` and `TransportTimingEndToEndTest` (the real transport: sources, arrangement, play, seek, pause, the inspector and the overview builder; the bare simulator test process has no output unit and skips the playback checks), `IosAudioFilePickerTest`, `AudioFilePickerHostTest` and `AndroidBackgroundPlaybackHostTest` (`:androidApp`) |
| `engine/tests` | macOS host | Catch2, offline, no audio device: `engine_info_test`, `seqlock_test`, `ring_buffer_test`, `band_pass_test`, `mpm_test` (accuracy and octave), `tuner_analyzer_test`, `tone_generator_test`, `sample_player_test`, `recordings_on_pitch_test`, `engine_core_state_test` (with `fake_audio_device.h`); metronome: `metronome_renderer_test` (ten-minute timing), `click_synth_test`, `click_sample_builder_test`, `custom_sound_loader_test` (with `wav_fixtures.h`), `triple_buffer_test` |

## CI and dependency updates

GitHub Actions runs the lanes that need no device, and Renovate keeps the version catalog current: it opens a weekly
batch of update pull requests and merges each one whose checks all pass.

| Job (`.github/workflows/ci.yml`) | Runner | Runs on | Command (from `app/`) |
|---|---|---|---|
| Android host tests and release build | `ubuntu-24.04` | every push, every pull request to `main` | `checkAndroidHost :androidApp:assembleRelease` |
| iOS simulator and C++ engine tests | `macos-26` (or `vars.CI_MACOS_RUNNER`) | every pull request to `main` | `checkIos engineHostTest` on the newest iPhone simulator |
| Renovate config | `ubuntu-24.04` | every push, every pull request to `main` | `renovate-config-validator --strict` |
| Google Play testing (`.github/workflows/play-closed-testing.yml`) | `ubuntu-24.04` | every push to `main` that changes the Android app, or by hand | `checkAndroidHost :androidApp:bundleRelease`, then signs with the upload key, releases on Google Play's internal testing track and deletes the run's bundle |

The Play workflow, its keyless Google Cloud trust, the upload key in Secret Manager and the one-time setup
(`tools/play/setup-wizard.sh`) are described in [`docs/google-play-publishing.md`](../docs/google-play-publishing.md).
Until its repository variables exist, the workflow only builds and tests.

Both Gradle jobs pass `-Ptembra.warningsAsErrors=true --warning-mode fail`, so a Kotlin compiler warning or a Gradle
deprecation fails CI; without the property a local build still only reports warnings. A Sunday evening run on `main`
checks `main` as merged and refreshes the caches that pull requests read. The Android device lanes, Compose UI tests
on Android, baseline-profile generation and performance rounds stay on the Mac with its devices.

### What Renovate does

The config is `.github/renovate.json5`; Renovate runs from `.github/workflows/renovate.yml`.

- **When.** New pull requests open on Mondays before 07:00 Kyiv time, for releases at least three days old. Renovate
  runs daily, after each CI run on one of its branches, and after each push to `main`.
- **What.** The version catalog, the Gradle wrapper (the wrapper jar and scripts are not regenerated, only
  `distributionUrl`), and the actions in the workflows. Not the perf pipeline's Python packages, the JDK version or
  the runner images: those change by hand.
- **Groups.** One pull request per group: *Kotlin and Compose* (Kotlin and its compiler plugins, Compose Multiplatform,
  the JetBrains androidx ports, androidx Compose, Metro), *kotlinx*, *Android Gradle Plugin and Gradle*, *AndroidX
  runtime and Okio*, *Android test and benchmark*, *CI workflows*. A major update gets its own pull request. Oboe has
  no group.
- **Merging.** Renovate squash-merges a pull request once every check on it has passed, and never merges while a check
  is pending or has failed; a failed pull request stays open for you. A pull request that is only behind `main` is
  merged without a rebase (a rebase would re-run the macOS job), so two groups can meet for the first time on `main`;
  the push to `main` runs the Android job on the combination. Renovate merges one pull request per run, and each merge
  triggers the next run.
- **Pinned lines.** Versions that are already pre-releases (JetBrains Material 3 `1.12.0-alpha03`, androidx Material 3
  `1.5.0-beta01`) only move along their own `x.y.z`. The androidx Compose artifacts stay on the Jetpack line that
  Compose Multiplatform is built on (`1.12.x`, Material 3 `1.5.x`); move both patterns in `renovate.json5` forward when
  a Compose Multiplatform minor update lands. kotlinx-datetime never takes the `-0.6.x-compat` artifacts.

After an automerged update that changes code on the startup path or a journey, regenerate the baseline profile and run
a performance round as usual (`perf/README.md`).

### One-time GitHub setup

1. **Token.** Create a classic personal access token (Settings → Developer settings → Personal access tokens →
   Tokens (classic)) with the `repo` and `workflow` scopes, and give it an expiry you will notice. Renovate decides
   whether to merge by reading the pull request's checks, and Renovate's GitHub documentation lists no Checks access
   for fine-grained tokens, so use a classic one. Renovate acts as you: its pull requests and merges appear under your
   account, with commits authored by `Renovate Bot`.
2. **Secret.** Repository → Settings → Secrets and variables → Actions → New repository secret: `RENOVATE_TOKEN`.
   Until it exists the Renovate workflow only prints a notice.
3. **Actions.** Settings → Actions → General: if you restrict which actions may run, allow `actions/checkout`,
   `actions/setup-java`, `actions/cache`, `gradle/actions` and `renovatebot/github-action`, and for the Play
   workflow `actions/upload-artifact`, `actions/download-artifact` and `google-github-actions/auth`. The workflows declare
   their own `contents: read` permission, so the default workflow permission does not matter.
4. **Merging.** Settings → General → Pull Requests: keep **Allow squash merging** on, and turn on **Automatically
   delete head branches**. Leave **Allow auto-merge** as it is; Renovate does not use it.
5. **Branch protection (optional).** Nothing here depends on it; a private repository gets protected branches only
   on GitHub Pro or above. If you protect `main`, require the three checks above and do not require an approving
   review: Renovate opens pull requests with your token, you cannot approve your own pull request, and Renovate
   could never merge. Administrators bypass branch protection by default, which keeps your direct pushes to `main`
   working; Renovate merges as you, so its merges bypass the rule too, and its own reading of the checks stays the
   gate.
6. **First run.** Actions → Renovate → Run workflow. It opens the *Dependency Dashboard* issue. Outside the Monday
   window, updates wait there; tick one to have the next run open it now.

### Cost

On a private repository GitHub bills macOS minutes at about ten times the Linux rate ($0.062 against $0.006 per
minute; GitHub Free includes $12 a month, Pro $18). The iOS job is nearly all of CI's cost: it runs on every pull
request, again whenever Renovate updates one, and on Sunday; the Actions usage page shows the real figure after the
first week. To run
it on your own Mac for free, register a self-hosted runner (Settings → Actions → Runners → New self-hosted runner,
macOS ARM64) with Xcode, the Android SDK and `ANDROID_HOME` set in the runner's `.env`, then set the repository
variable `CI_MACOS_RUNNER` to `self-hosted`. The Mac must be awake for the job to run, and it builds the pull request's
dependencies on that machine, as a local build would.

## Screenshots

```bash
android screen capture --output specs/<feature>/screenshots/android-compact.png   # add --device <serial> if several
xcrun simctl io "iPhone 18 Pro" screenshot specs/<feature>/screenshots/ios-compact.png
xcrun simctl io "iPad Pro 13-inch (M5)" screenshot specs/<feature>/screenshots/ios-wide.png
```

Wide Android capture: rotate to landscape (`adb shell settings put system user_rotation 1` after
`accelerometer_rotation 0`), capture, restore. Medium band (600 to 839 dp) on a phone: `adb shell wm density 380`
turns a 1440 px Pixel into a 606 dp window (`wm density reset` afterwards). Name captures
`<platform>-<compact|medium|expanded|short>-<destination>.png` and record devices, OS versions and outcomes of manual
checks in `specs/<feature>/screenshots/NOTES.md`. The iOS simulator bundle identifier carries the local `TEAM_ID`
(`com.tembraaudio<TEAM_ID>`); read it from the built `Info.plist` before `simctl launch`. Large fonts: `adb shell settings put system font_scale 2.0`
and `xcrun simctl ui "iPhone 18 Pro" content_size accessibility-extra-extra-extra-large`; restore to `1.0` /
`medium`. Compare against `design/stitch/theme.json` and `design/stitch/screens/*/screenshot.jpg`.

## Module map

```
app/
├── build-logic/            convention plugins: tembra.kmp.library, tembra.kmp.compose, tembra.android.app, tembra.android.native
├── engine/                 C++ engine (CMake). include/tembra_engine.h is the only header Kotlin sees (plain C ABI)
│   ├── src/                engine_info.cpp (descriptor), third_party_check.cpp (compiles vendored libs)
│   │   ├── core/           EngineCore state machine (owns the device and every renderer), miniaudio_impl.cpp
│   │   ├── metronome/      MetronomeRenderer, click synth, sample builder, custom sound loader, resampler, triple buffer, C ABI forwarders
│   │   ├── transport/      feature 009: TempoMap, TransportClock, TransportRenderer (the project's callback: clips → strips → bus → the anchored click), TransportRecord, C ABI forwarders
│   │   ├── timeline/       feature 009: AudioDecoder (miniaudio's WAV/FLAC/MP3), ClipSource + SpscFifo, DiskStreamer (the reader thread), ArrangementSnapshot, OverviewBuilder (.peaks), C ABI forwarders
│   │   ├── mixer/          feature 009: GainRamp, TrackStrip, MasterBus (the shape features 010 and later extend); feature 010: MonitorStrip (the armed track's input through its own strip into the master bus)
│   │   ├── capture/        feature 010: CaptureSource (the callback's side: per-block peak, clip latch, alignment discard, the ordered input fifo), TakeRecorder (the "tembra-recorder" thread: fifo → 24-bit WAV, header refreshed each second, 256-frame peak buckets, the length and free-space limits), WavWriter, PeakRing, CaptureRecord, C ABI forwarders (`capture_abi.cpp`)
│   │   ├── tuner/          C ABI forwarders, ring buffer, seqlock, band-pass, McLeod detector, smoother, analyzer, tone generator, sample player, WAV decoder
│   │   └── platform/       AudioDevice seam: null (host), synthetic (test signals), android/oboe, apple/remote_io
│   ├── third_party/        miniaudio, dr_wav, Signalsmith Stretch (+ licences)
│   ├── tests/              Catch2 tests
│   └── android/            :engine:android — com.android.library + CMake/NDK + Prefab Oboe → libtembra_engine.so + JNI shim
├── shared/
│   ├── core/               :shared:core   — pure Kotlin domain (no Compose, no platform APIs, no project dependencies; kotlinx.serialization + collections.immutable only): metronome/, tuner/, project/ (the document, ids, edits + ProjectEditor, EditHistory, TempoMap, PlaybackPolicy, LibraryQuery), timeline/ (TimelineViewport, RulerLabels, Snap, ClipHit, TimeFormat)
│   ├── engine/             :shared:engine — AudioEngine facade (TunerAudio + MetronomeAudio + TimelineAudio): NativeAudioEngine (JNI / cinterop, ArrangementBuffer on Android), FakeAudioEngine, parser, perf/ (PerfTrace facade, catalogue enums, AudioHealth)
│   ├── data/               :shared:data   — persistence: AppDirectories, tuner/ DataStore preferences (TunerPreferences, TembraDataStore), metronome/ (MetronomePreferences, CustomClickSoundStore), timeline/ (TimelinePreferences: whether this device shows the overview strip, `timeline.overviewStrip`), projects/ (ProjectRepository over Okio project folders + library.json, the ProjectDocumentCodec, AudioImporter, WaveformOverview, AutosaveScheduler, DemoProjectInstaller; depends on :shared:engine for the offline inspector and overview builder)
│   └── ui/                 :shared:ui     — Compose screens, ViewModels, theme, Metro graph; iOS framework "TembraShared"
│       ├── theme/          design tokens from design/stitch/theme.json: colours, type, shapes, spacing and chrome metrics, motion, TembraIcon registry (Material Symbols glyphs plus the project's own note figures and, since 2026-10-17, the navigation's hand-drawn tuning fork and metronome)
│       ├── designsystem/   chrome components: TembraTopBar (not shown by the shell since 2026-10-16), TembraNavigationBar and TembraNavigationRail (icon-only, on Material 3 Expressive's ShortNavigationBar and collapsed WideNavigationRail since 2026-10-17; NavigationItemIcon shows the label as a hover tooltip, InsetFocusRing gives Material's inset focus ring), TembraSupportingPane, EmptyState, PlaceholderDestination, TembraSegmentedControl, TembraToggle, TembraStatusPill, TembraGradientCard, TembraChipRow, TembraPill, TembraSlider (expect/actual: Material's slider with a custom thumb and track), FocusRing, FrameTick
│       ├── platform/       MicrophonePermission, BackgroundPlaybackHost (+ PlaybackNotification), AudioFilePicker interfaces (+ IosMicrophonePermission, IosBackgroundPlaybackHost, IosAudioFilePicker in iosMain; the Android ones live in :androidApp); feature 010: AudioRouteMonitor (interface with `route: StateFlow<AudioRoute>`), FakeAudioRouteMonitor, IosAudioRouteMonitor in iosMain (AVAudioSession route and route-change notification); the Android binding AndroidAudioRouteMonitor lives in :androidApp (AudioManager devices + AudioDeviceCallback)
│       ├── session/        feature 009: ProjectSession (the open project, its history, autosave, engine binding and playback policy; the shell's PlaybackGuard), TransportTelemetry, DialogRequest, AppStartup; feature 010: RecordingController (the recording session beside ProjectSession: armed track, monitor state, phase, permission flow, route observation, take staging and adoption, the growing take's overview; every transition is a row of `RecordingPolicy` in `:shared:core`)
│       ├── transport/      feature 009: TransportBar and its parts (TimeReadout reads the telemetry in draw, MasterVolumeSlider, MasterVolumePopup (the phone bar's speaker button and pop-up slider, D1), MetronomeToggle, TransportButton)
│       ├── tracksettings/  feature 009: TrackSettingsViewModel (one track's fields as session edits, the name draft carried through ProjectSession.settingsDraft across 840 dp), TrackSettingsForm (+ GainSlider, EffectsPlaceholder), the nested TrackSettingsScreen (TrackSettingsKey) and the expanded layout's TrackSettingsInlineRow
│       ├── shortcuts/      feature 011: ShortcutCatalogue (every key, one source), KeyChord (the one matching rule), ShortcutDispatcher, the shell's and destinations' key handlers, ShortcutSheet, OverlayStack (close-overlay), ShortcutRegistry (the iPad menu's targets); iosMain IosShortcutBridge, androidMain systemShortcutGroups
│       ├── drop/           feature 011: DropPlanner, AudioDropState + Modifier.audioDropTarget, DroppedFilesReader (Android adapter, IosDroppedFilesReader), OpenedFiles (IosOpenedFiles), ProjectFromAudio
│       ├── navigation/     the shell: Destination registry, ShellViewModel, LayoutMode, NavigationShell, Navigation 3 wiring (TembraNavDisplay), SupportingPaneSceneStrategy
│       ├── metronome/      MetronomeScreen + MetronomeViewModel, PlaybackPolicy, MetronomeSettingsPanel; components/ (TempoDial, TempoSlider, AccentCells, StyleRow, ClickSoundPicker, TapTempoButton, PlayPauseButton, MetronomeDefaults)
│       ├── timeline/ projects/ profile/   one package per destination; feature 009 fills timeline/ (TimelineScreen + TimelineViewModel over ProjectSession, TimelineKeyHandling; components/ with the Canvas ruler, TrackLane + ClipPainter, PlayheadOverlay, ViewportGestures, the toolbar with its TempoPill, headers, menus, TempoDialog, MetronomeSettingsSheet (the signature since D1), SnapSheet, and the OverviewStrip, the always-visible zoom control, inset from the window's sides and hidden or shown from the toolbar's overflow menu) and projects/ (ProjectsViewModel over the repository's index and the app-scoped LibraryViewStateHolder, ProjectsScreen; components/ with ProjectCard, ProjectOverviewChart, LibrarySearchField, LibraryFilterChips, SortMenu, RelativeTime and the four dialogs); each independent of the shell; home/ keeps only LibraryPane, which no destination declares since Home was removed (2026-10-17)
│       ├── tuner/          TunerScreen + TunerViewModel, ListeningPolicy, TunerMeter (one Canvas), NoiseGateSlider (the noise gate control over the live input level), By Ear cards, ReferenceSoundRepository, test-signal and noise-gate requests
│       └── devtools/       debug-only developer views (DesignTokenPreview behind a launch argument)
├── androidApp/             :androidApp — TembraApplication (Metro graph), MainActivity, AndroidMicrophonePermission + MicrophonePermissionHost, PlaybackForegroundService + AndroidBackgroundPlaybackHost (the metronome and the transport), AudioFilePickerHost + AndroidAudioFilePicker (OpenDocument / OpenMultipleDocuments)
├── iosApp/                 Xcode project; two Swift files host MainViewController()
└── tools/                  fetch-material-symbols.sh (glyphs at a pinned commit); make-note-glyphs.py (the note-figure glyphs); make-nav-glyphs.py (the navigation's hand-drawn Tuner and Metronome glyphs and the metronome notification's status-bar icon; `--preview <dir>` writes a preview sheet); make-demo-audio.py (the demo project's four loops under shared/ui/src/commonMain/composeResources/files/demo/, regenerated with `uv run --with numpy python tools/make-demo-audio.py`); capture-tuner-states.sh (the tuner's test-signal screenshot matrix); capture-noise-gate.sh (the noise gate control on one target in one orientation, with a layout check on Android); gradlew-device.sh (the wrapper against one Android serial, feature 010); apply-device-run-conditions.sh (brightness floor, music stream silent, do-not-disturb, screen awake on one Android serial); measure-take-alignment.py (a loopback take's offset to the beat, or the monitoring delay with `--monitor`)
```

Dependencies point inward: apps → `:shared:ui` → `:shared:data` → `:shared:engine` → `:shared:core` (`:shared:data` uses the engine's offline audio tools since feature 009).
A dependency in the wrong direction fails configuration (`tembra.kmp.library`).

## Where does X go?

| You are adding… | Put it in | Notes |
|---|---|---|
| a new screen | `:shared:ui`, package `audio.tembra.ui.<feature>` | one `ViewModel` per destination, `StateFlow<UiState>`; tests in `uiTest` (screen) and `commonTest` (ViewModel) |
| a new navigation destination | `audio.tembra.ui.navigation.Destination` (enum value: id, label, title, `TembraIcon`, supporting pane, placeholder sentence) + a screen composable in its own package + the branch in `DestinationContent` | the exhaustive `when` fails to compile until mapped; the label is the icon's accessible name (the bar and rail draw icons only), the bar and rail need nothing else; the first enum value is the start destination |
| an icon | `tools/fetch-material-symbols.sh [--fill] <glyph>` then a `TembraIcon` value (`--fill` for the filled variant) | Material Symbols Outlined only, bundled as normalised vector XML; screens use `TembraIcons.vector(TembraIcon.X)`, never `Res.drawable` |
| a chrome component (bar, rail, panel, top bar, empty state) | `audio.tembra.ui.designsystem` | stateless, every value from `TembraTheme`, semantics and `testTag` on every control |
| a developer-only view | `audio.tembra.ui.devtools` + a `DeveloperView` value | opened only through the debug launch argument (`tembra.developer.view` / `--tembra-developer-view=`), never from the UI |
| a domain rule, model or pure calculation | `:shared:core` | no Compose, no platform code; test on JVM and iOS |
| a persistence concern (files, database, preferences) | `:shared:data` | platform access behind an interface, wired in the platform Metro graphs |
| an engine command or telemetry | `engine/include/tembra_engine.h` + `engine/src`, then `:shared:engine` (`AudioEngine`, actuals, `FakeAudioEngine`) | plain C ABI, no callbacks into Kotlin, nothing Kotlin on the audio thread |
| platform SDK access (paths, OS services) | `androidMain` / `iosMain` of the owning module | `expect`/`actual` only for SDK access; business logic uses interfaces |
| a colour, size, font or shape | `:shared:ui` `audio.tembra.ui.theme` from `design/stitch/theme.json` | never a literal value in a screen; `ThemeJsonParityTest` guards drift |
| a third-party dependency | `gradle/libs.versions.toml` + `THIRD_PARTY_LICENSES.md` | permissive licence only (Constitution VI) |
| a platform permission | interface in `:shared:ui` `audio.tembra.ui.platform`; Android implementation in `:androidApp` (it needs the activity result registry), iOS implementation in `:shared:ui` `iosMain` | inject through Metro, `Fake…Permission` in `commonTest`; the OS prompt itself is a manual check |
| a preference | `:shared:data`, package `tuner` (or the feature's): keys as constants, a snapshot data class with `decode`/`encode`, an interface with a `Flow` and `suspend fun update`, a `Fake…Preferences`, the DataStore implementation over `TembraDataStore.preferences` | one DataStore file per process (`tembra.preferences_pb`); unknown stored values decode to defaults |
| a baseline-profile change (a new primary destination, journey or main interaction) | `uv run tracepulse profile generate --device pixel6` from `app/perf/`, then commit `androidApp/src/main/generated/baselineProfiles/{baseline-prof.txt,startup-prof.txt}`; a new destination the journeys visit goes into `androidApp/baseline-profile-coverage.txt` first | the host lane's `BaselineProfileCoverageTest` fails until the profile covers every rule; release-like builds refuse to ship without a committed profile; details in `app/perf/README.md` § Baseline profiles |
| a keep rule for the shrunk release build | `androidApp/proguard-rules.pro`, with a comment naming the code path and the symptom a device run showed | only after a journey or a screenshot on the shrunk `perf` build fails; never `-dontwarn` wildcards |
| a performance round or session | `app/perf/` (`uv run tracepulse run --device <id> --label <feature> [--rounds 5]`), config `app/tracepulse.yml`, records on the `perf-history` branch | user tooling: it calls `./gradlew --console=plain -Ptembra.perf=true` itself; an agent builds through `gradle-run` and passes `--no-build --apk … --test-apk …`; raw traces stay in `app/perf/data/` (git-ignored) |
| a benchmark journey | `perf-benchmarks/src/main/kotlin/audio/tembra/perf/journeys/` (UiAutomator over `PerfAnchors`) + a benchmark class; `iosApp/TembraPerfTests/Journeys/` + a test case; the name in `app/tracepulse.yml` | `:perf-benchmarks` is included only with `-Ptembra.perf=true`; anchors are `testTag`s listed in `audio.tembra.ui.perf.PerfAnchors`, mirrored in both drivers |
| a named performance section or counter | `app/shared/engine/perf-catalogue.txt` + `PerfSection`/`PerfCounter`, then `PerfTrace.section { }` around non-composable work (or `TEMBRA_TRACE_SCOPE` in the engine's non-real-time code) | the only source of names; `generateTraceNames` keeps `engine/include/tembra_trace_names.h` in step; never on the audio callback (the poison test proves it); every feature instruments itself (constitution) |
| the demo project's audio | `shared/ui/src/commonMain/composeResources/files/demo/{drums,bass,keys,pad}.wav`, written by `uv run --with numpy python tools/make-demo-audio.py` (feature 009, research R18) | generated, never hand-edited: 16 bars of 4/4 at 124 BPM (1 486 452 frames at 48 kHz, 16-bit); the installer reads them through `DemoAudioSource` |
| a reference recording | `shared/ui/src/commonMain/composeResources/files/tuner/<instrument>-<note>.wav` (`guitar-E2.wav`, `guitar-Fsharp3.wav`, `cello-C2.wav`) | PCM or float WAV, mono or stereo, any rate, at least 1 s; `engineHostTest` fails if it is more than 1 cent off its note; none ships |
| a design glyph that Material Symbols lacks | hand-written `ic_<name>_24.xml` under `composeResources/drawable/` with the header `<!-- Tembra design glyph "<name>" from <stitch screen>; hand-converted, do not edit -->`, then a `TembraIcon` value | 24 × 24 viewport, one path, `android:fillColor="#FFFFFFFF"`; `IconAssetsTest` checks it |
| a note-figure glyph (metronome subdivisions) | edit the figure in `tools/make-note-glyphs.py`, then `python3 tools/make-note-glyphs.py` (rewrites every `ic_note_*_24.xml`; `--preview <dir>` writes an HTML sheet and the SVG path JSON) and a `TembraIcon` value | generated, not hand-edited: head, stem, beam and flag proportions are shared constants so the figures stay one family |
| a tuner test signal | a row in `TestSignals` (`:shared:engine`) with its expected reading | debug launch argument `tembra.tuner.testSignal` (Android extra) / `--tembra-tuner-test-signal=` (iOS): a catalogue id, `E2:-20`, or `E2:0@-55` for a note at a level in the noise gate's measure; `tools/capture-tuner-states.sh` captures every entry; `TunerSignalEndToEndTest` asserts it on both platforms |
| a check of the tuner's noise gate on a device | nothing to add: launch a debug build with `tembra.tuner.noiseGate` (Android extra) / `--tembra-tuner-noise-gate=` (iOS), `off` or a level on the scale such as `-50` | the value stands for that launch only and is never stored; with a test signal at a level (`e2-55db`, `e2-45db`, or `NOTE:CENTS@LEVEL`) it shows the control below and above the gate; `tools/capture-noise-gate.sh` makes the three captures per target and orientation; `NoiseGateEndToEndTest` asserts the gate on both platforms |
| microphone capture into a take (feature 010) | `engine/src/capture/` (`capture_source` on the audio callback; `take_recorder`, `wav_writer`, `peak_ring` on the recorder thread), exported through `capture_abi.cpp`; the monitor path in `engine/src/mixer/monitor_strip.*` | Catch2 beside the others (`capture_source_test`, `take_recorder_test`, `wav_writer_test`, `peak_ring_test`, `monitor_strip_test`); the callback side is in `TEMBRA_RT_SOURCES`, so it is compiled with the allocation and locking poison; the input block is read inside the output callback, never a second callback (`engine/README.md` § Recording) |
| a take's lifecycle (arm, record, stop, adopt, recover) | `:shared:ui` `audio.tembra.ui.session.RecordingController`, with the transition table `RecordingPolicy` in `:shared:core` and take staging/adoption in `:shared:data` `projects` | effects run against `TimelineAudio`, `MicrophonePermission`, `AudioRouteMonitor`, `BackgroundPlaybackHost` and `ProjectRepository`; the finished take reaches the document as one `RecordTake` edit through `RecordingHost` |
| an input route query (built-in mic, wired, Bluetooth, USB) | interface `AudioRouteMonitor` in `:shared:ui` `audio.tembra.ui.platform`; Android binding `AndroidAudioRouteMonitor` in `:androidApp`, iOS binding `IosAudioRouteMonitor` in `:shared:ui` `iosMain` | bound in `AndroidAppGraph` / `IosAppGraph`; `FakeAudioRouteMonitor` in tests |
| a run on one Android device, or its run conditions (feature 010) | `tools/gradlew-device.sh <serial> <gradle arguments…>`, `tools/apply-device-run-conditions.sh <serial>` | see § Build and run |
| the loopback alignment check of a recorded take | `tools/measure-take-alignment.py take.wav --bpm <bpm>` (`--monitor` for the monitoring delay) | standard library only; the one step of feature 010 that makes a sound (quickstart §8) |
| engine core shared by the tuner and the metronome (stream, render loop, command FIFO) | `engine/src/core/` (`engine_core.{h,cpp}`; `miniaudio_impl.cpp` is the one miniaudio translation unit) | features render from `EngineCore`'s callback; no allocation, lock, I/O or logging inside `render` |
| metronome scheduling or a click sound | `engine/src/metronome/` (`metronome_renderer`, `metronome_grid.h`, `triple_buffer.h`; sounds in `click_synth`, `click_sample_builder`, `custom_sound_loader`, `sound_resampler`), exported through `metronome_abi.cpp` | Catch2 tests beside the others in `engine/tests/`; sample-accurate ticks, sounds prepared off the audio thread |
| a metronome engine call from Kotlin | `:shared:engine` `MetronomeAudio` (with `ClickLevel`, `ClickSoundId`, `MetronomeGrid`, `TickSnapshot`, `CustomSoundResult`); `AudioEngine` implements it, `FakeAudioEngine` for tests | the ViewModel depends on `MetronomeAudio`, never on `AudioEngine`; events and status come from `AudioStatus` |
| metronome preferences or the custom click file | `:shared:data` package `metronome` (`MetronomePreferences` over the one DataStore, `CustomClickSoundStore` on Okio) | fakes live beside them; the picker writes to `stagingPath`, `commit()` moves it to `path` |
| a background-playback host or a file picker | interface in `:shared:ui` `audio.tembra.ui.platform` (`BackgroundPlaybackHost`, `SoundFilePicker`); Android in `:androidApp` (`MetronomeForegroundService`, `AndroidBackgroundPlaybackHost`, `SoundFilePickerHost` + `AndroidSoundFilePicker` attached by `MainActivity`); iOS in `:shared:ui` `iosMain` (`IosBackgroundPlaybackHost`, `IosSoundFilePicker`) | bound in `AndroidAppGraph` / `IosAppGraph`; host tests in `androidTest` of `:androidApp` and `iosTest` of `:shared:ui`; the OS picker and the lock-screen run are manual checks |
| a keyboard shortcut (feature 011) | one entry in `audio.tembra.ui.shortcuts.ShortcutCatalogue` (chord, scope, `ShortcutAction`, label in `ShortcutCopy`) + its branch in `ShortcutDispatcher` + the scope's target interface | the exhaustive `when` fails to compile until routed; the sheet, Android's Meta+/ list and the iPad menu bar read the catalogue; a primary chord is ⌘ on iPad and served by the menu (its first one per entry), so Compose leaves it alone there; overlays register with `RegisterOverlay` so close-overlay (Escape, Ctrl/⌘+.) reaches them |
| a drop zone or an "open with" type (feature 011) | `audio.tembra.ui.drop`: a `DropZone` and its rule in `DropPlanner`, an `AudioDropState` + `Modifier.audioDropTarget` on the layout; Android types in `AndroidDroppedFilesReader` / the manifest's `VIEW` filter, iOS in `IosDroppedFilesReader` / `Info.plist`'s document types | plans are common code (`DropPlannerTest`); the readers stay thin; iOS gives no drop position with Compose Multiplatform 1.12 (research R12) |
| pointer behaviour on a control (feature 011) | `audio.tembra.ui.designsystem`: `onSecondaryClick` + `PointerMenuAnchor` for a menu at the pointer, `PointerShapes` (grab, grabbing, busy) or `PointerIcon.Hand` before `clickable`, `hoverArea` for the hover tint, `tembraScrollbar` with a `ScrollbarAdapter` on a scrolling container | the hover tint covers the whole clickable area (`PointerAreaTest`); wheel rules live in `ViewportGestures` |
| a window behaviour (full screen, minimum size, relayout) (feature 011) | `audio.tembra.ui.platform.WindowControls` (`AndroidWindowControls` attached by `MainActivity`, `IosWindowControls`); the minimum in the manifest's `<layout>` and `WindowMinimum.swift`; focus and place across layouts with `focusRestoringRoot` / `focusIdentity` | desktop tests in `androidApp/src/androidTest/.../desktop/` run on `Tembra_Desktop_API37` (they skip without free-form windows); `TembraDesktopTests` on an iPad simulator |
| a reusable metronome control (dial, chip row, accent cells, sound picker) | generic pieces in `audio.tembra.ui.designsystem` (`TembraChipRow`, `TembraPill`, `TembraGradientCard` discs); metronome-specific ones in `audio.tembra.ui.metronome.components` (`internal`) | stateless, `modifier` first optional parameter, primitives in, callbacks out; every value from `TembraTheme` (metronome tokens included) |

## Conventions

- Test first (red, green, refactor); every behaviour has a test where it can fail.
- One top-level type per file, named after it; nested types only for private details.
- `internal` by default; feature packages under each module.
- No wildcard imports, no `TODO` without an issue reference, zero compiler warnings.
- Kotlin never runs on the audio callback; Kotlin ↔ C++ only through the C ABI.
- All musical time in beats (nothing to store yet; the rule starts with the next feature).

## Known toolchain notes

- Android 17 devices need Espresso ≥ 3.7.0 for Compose UI tests (pinned in the catalog).
- Compose keeps clickables unfocusable in touch mode; the keyboard test switches input mode first.
- Xcode prints one linker warning about `libicu.icudtl_dat.o` in the Kotlin/Native runtime being built
  for a newer simulator version; it comes from the Kotlin toolchain, not this code.
- AGP's KMP library plugin exposes an Android assets source only when `androidResources.enable = true`; the Compose
  convention plugin sets it, otherwise the module's compose resources (fonts, drawables, files) silently never reach
  the app or the device-test APK (feature 002).
- `./gradlew build` (the metadata-jar compilation used only for publishing libraries) prints Kotlin
  "KLIB loader: The same 'unique_name=…' found in more than one library" warnings. They come from
  Compose Multiplatform 1.12.1's own dependency graph, which still ships both `androidx.*` and
  `org.jetbrains.androidx.*` variants of savedstate, annotation and collection, and from Metro's runtime
  klib sharing the name `runtime` with Compose's. App compilations and all four test lanes are warning-free.
