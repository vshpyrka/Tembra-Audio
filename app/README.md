# Tembra Audio — application code

Kotlin Multiplatform app for Android and iOS with a shared Compose Multiplatform UI on top of one
C++ real-time audio engine. This folder is the Gradle root. Governing rules:
`.specify/memory/constitution.md`; architecture decision: `docs/research/kmp-code-architecture.md`.

## Prerequisites (macOS)

| Need | Check | Install |
|---|---|---|
| JDK 21 | `java -version` | Temurin 21 |
| Xcode 26.x with an iOS simulator runtime | `xcodebuild -version`, `xcrun simctl runtime list` | `xcodebuild -downloadPlatform iOS` (about 8 GB) |
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
adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity
```

iOS (simulator; Xcode runs `./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode` in a build phase):

```bash
cd app/iosApp
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build build
xcrun simctl boot "iPhone 17"
xcrun simctl install "iPhone 17" "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app"
xcrun simctl launch "iPhone 17" audio.tembra.app
```

Or open `app/iosApp/iosApp.xcodeproj` in Xcode and press Run. Signing: `Configuration/Config.xcconfig`
(`TEAM_ID` empty is fine for the simulator).

## Test lanes

One command per lane; each aggregates every `:shared:*` module.

```bash
cd app
./gradlew checkAndroidHost     # JVM unit tests (domain, parser, ViewModel, theme.json parity)
./gradlew checkIos             # iOS simulator: all shared tests + Compose UI tests + native engine link
# add -Ptembra.iosSimulator="iPad Pro 13-inch (M5)" (any simctl name or UDID) to run the iOS lane on another simulator
# iPhone Duo (foldable): needs Xcode 27.1 beta beside the release Xcode and its iOS 27.1 runtime; build the app with
# DEVELOPER_DIR=/Applications/Xcode-27.1-beta.app/Contents/Developer so it fills the displays (docs/research/iphone-duo.md)
./gradlew checkAndroidDevice   # connected Android device: Compose UI tests + native engine link + AppDirectories
./gradlew engineHostTest       # C++ engine Catch2 tests on the macOS host (no audio device)
```

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
| `engine/tests` | macOS host | Catch2, offline, no audio device: `engine_info_test`, `seqlock_test`, `ring_buffer_test`, `band_pass_test`, `mpm_test` (accuracy and octave), `tuner_analyzer_test`, `tone_generator_test`, `sample_player_test`, `recordings_on_pitch_test`, `engine_core_state_test` (with `fake_audio_device.h`); metronome: `metronome_renderer_test` (ten-minute timing), `click_synth_test`, `click_sample_builder_test`, `custom_sound_loader_test` (with `wav_fixtures.h`), `triple_buffer_test` |

## Screenshots

```bash
android screen capture --output specs/<feature>/screenshots/android-compact.png   # add --device <serial> if several
xcrun simctl io "iPhone 17" screenshot specs/<feature>/screenshots/ios-compact.png
xcrun simctl io "iPad Pro 13-inch (M5)" screenshot specs/<feature>/screenshots/ios-wide.png
```

Wide Android capture: rotate to landscape (`adb shell settings put system user_rotation 1` after
`accelerometer_rotation 0`), capture, restore. Medium band (600 to 839 dp) on a phone: `adb shell wm density 380`
turns a 1440 px Pixel into a 606 dp window (`wm density reset` afterwards). Name captures
`<platform>-<compact|medium|expanded|short>-<destination>.png` and record devices, OS versions and outcomes of manual
checks in `specs/<feature>/screenshots/NOTES.md`. The iOS simulator bundle identifier carries the local `TEAM_ID`
(`audio.tembra.app<TEAM_ID>`); read it from the built `Info.plist` before `simctl launch`. Large fonts: `adb shell settings put system font_scale 2.0`
and `xcrun simctl ui "iPhone 17" content_size accessibility-extra-extra-extra-large`; restore to `1.0` /
`medium`. Compare against `design/stitch/theme.json` and `design/stitch/screens/*/screenshot.jpg`.

## Module map

```
app/
├── build-logic/            convention plugins: tembra.kmp.library, tembra.kmp.compose, tembra.android.app, tembra.android.native
├── engine/                 C++ engine (CMake). include/tembra_engine.h is the only header Kotlin sees (plain C ABI)
│   ├── src/                engine_info.cpp (descriptor), third_party_check.cpp (compiles vendored libs)
│   │   ├── core/           EngineCore state machine (owns the device and every renderer), miniaudio_impl.cpp
│   │   ├── metronome/      MetronomeRenderer, click synth, sample builder, custom sound loader, resampler, triple buffer, C ABI forwarders
│   │   ├── tuner/          C ABI forwarders, ring buffer, seqlock, band-pass, McLeod detector, smoother, analyzer, tone generator, sample player, WAV decoder
│   │   └── platform/       AudioDevice seam: null (host), synthetic (test signals), android/oboe, apple/remote_io
│   ├── third_party/        miniaudio, dr_wav, Signalsmith Stretch (+ licences)
│   ├── tests/              Catch2 tests
│   └── android/            :engine:android — com.android.library + CMake/NDK + Prefab Oboe → libtembra_engine.so + JNI shim
├── shared/
│   ├── core/               :shared:core   — pure Kotlin domain (no Compose, no platform APIs, no project dependencies)
│   ├── engine/             :shared:engine — AudioEngine facade (TunerAudio + MetronomeAudio): NativeAudioEngine (JNI / cinterop), FakeAudioEngine, parser
│   ├── data/               :shared:data   — persistence: AppDirectories, tuner/ DataStore preferences (TunerPreferences, TembraDataStore), metronome/ (MetronomePreferences, CustomClickSoundStore)
│   └── ui/                 :shared:ui     — Compose screens, ViewModels, theme, Metro graph; iOS framework "TembraShared"
│       ├── theme/          design tokens from design/stitch/theme.json: colours, type, shapes, spacing and chrome metrics, motion, TembraIcon registry (Material Symbols glyphs plus the project's own note figures and, since 2026-10-17, the navigation's hand-drawn tuning fork and metronome)
│       ├── designsystem/   chrome components: TembraTopBar (not shown by the shell since 2026-10-16), TembraNavigationBar and TembraNavigationRail (icon-only, on Material 3 Expressive's ShortNavigationBar and collapsed WideNavigationRail since 2026-10-17; NavigationItemIcon shows the label as a hover tooltip, InsetFocusRing gives Material's inset focus ring), TembraSupportingPane, EmptyState, PlaceholderDestination, TembraSegmentedControl, TembraToggle, TembraStatusPill, TembraGradientCard, TembraChipRow, TembraPill, TembraSlider (expect/actual: Material's slider with a custom thumb and track), FocusRing, FrameTick
│       ├── platform/       MicrophonePermission, BackgroundPlaybackHost, SoundFilePicker interfaces (+ IosMicrophonePermission, IosBackgroundPlaybackHost, IosSoundFilePicker in iosMain; the Android ones live in :androidApp)
│       ├── navigation/     the shell: Destination registry, ShellViewModel, LayoutMode, NavigationShell, Navigation 3 wiring (TembraNavDisplay), SupportingPaneSceneStrategy
│       ├── metronome/      MetronomeScreen + MetronomeViewModel, PlaybackPolicy, MetronomeSettingsPanel; components/ (TempoDial, TempoSlider, AccentCells, StyleRow, ClickSoundPicker, TapTempoButton, PlayPauseButton, MetronomeDefaults)
│       ├── timeline/ projects/ profile/   one package per destination (TimelineScreen, ...); each independent of the shell; home/ keeps only LibraryPane, which no destination declares since Home was removed (2026-10-17)
│       ├── tuner/          TunerScreen + TunerViewModel, ListeningPolicy, TunerMeter (one Canvas), NoiseGateSlider (the noise gate control over the live input level), By Ear cards, ReferenceSoundRepository, test-signal and noise-gate requests
│       └── devtools/       debug-only developer views (DesignTokenPreview behind a launch argument)
├── androidApp/             :androidApp — TembraApplication (Metro graph), MainActivity, AndroidMicrophonePermission + MicrophonePermissionHost, MetronomeForegroundService + AndroidBackgroundPlaybackHost, SoundFilePickerHost + AndroidSoundFilePicker
├── iosApp/                 Xcode project; two Swift files host MainViewController()
└── tools/                  fetch-material-symbols.sh (glyphs at a pinned commit); make-note-glyphs.py (the note-figure glyphs); make-nav-glyphs.py (the navigation's hand-drawn Tuner and Metronome glyphs and the metronome notification's status-bar icon; `--preview <dir>` writes a preview sheet); capture-tuner-states.sh (the tuner's test-signal screenshot matrix); capture-noise-gate.sh (the noise gate control on one target in one orientation, with a layout check on Android)
```

Dependencies point inward: apps → `:shared:ui` → `:shared:engine` / `:shared:data` → `:shared:core`.
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
| a reference recording | `shared/ui/src/commonMain/composeResources/files/tuner/<instrument>-<note>.wav` (`guitar-E2.wav`, `guitar-Fsharp3.wav`, `cello-C2.wav`) | PCM or float WAV, mono or stereo, any rate, at least 1 s; `engineHostTest` fails if it is more than 1 cent off its note; none ships |
| a design glyph that Material Symbols lacks | hand-written `ic_<name>_24.xml` under `composeResources/drawable/` with the header `<!-- Tembra design glyph "<name>" from <stitch screen>; hand-converted, do not edit -->`, then a `TembraIcon` value | 24 × 24 viewport, one path, `android:fillColor="#FFFFFFFF"`; `IconAssetsTest` checks it |
| a note-figure glyph (metronome subdivisions) | edit the figure in `tools/make-note-glyphs.py`, then `python3 tools/make-note-glyphs.py` (rewrites every `ic_note_*_24.xml`; `--preview <dir>` writes an HTML sheet and the SVG path JSON) and a `TembraIcon` value | generated, not hand-edited: head, stem, beam and flag proportions are shared constants so the figures stay one family |
| a tuner test signal | a row in `TestSignals` (`:shared:engine`) with its expected reading | debug launch argument `tembra.tuner.testSignal` (Android extra) / `--tembra-tuner-test-signal=` (iOS): a catalogue id, `E2:-20`, or `E2:0@-55` for a note at a level in the noise gate's measure; `tools/capture-tuner-states.sh` captures every entry; `TunerSignalEndToEndTest` asserts it on both platforms |
| a check of the tuner's noise gate on a device | nothing to add: launch a debug build with `tembra.tuner.noiseGate` (Android extra) / `--tembra-tuner-noise-gate=` (iOS), `off` or a level on the scale such as `-50` | the value stands for that launch only and is never stored; with a test signal at a level (`e2-55db`, `e2-45db`, or `NOTE:CENTS@LEVEL`) it shows the control below and above the gate; `tools/capture-noise-gate.sh` makes the three captures per target and orientation; `NoiseGateEndToEndTest` asserts the gate on both platforms |
| engine core shared by the tuner and the metronome (stream, render loop, command FIFO) | `engine/src/core/` (`engine_core.{h,cpp}`; `miniaudio_impl.cpp` is the one miniaudio translation unit) | features render from `EngineCore`'s callback; no allocation, lock, I/O or logging inside `render` |
| metronome scheduling or a click sound | `engine/src/metronome/` (`metronome_renderer`, `metronome_grid.h`, `triple_buffer.h`; sounds in `click_synth`, `click_sample_builder`, `custom_sound_loader`, `sound_resampler`), exported through `metronome_abi.cpp` | Catch2 tests beside the others in `engine/tests/`; sample-accurate ticks, sounds prepared off the audio thread |
| a metronome engine call from Kotlin | `:shared:engine` `MetronomeAudio` (with `ClickLevel`, `ClickSoundId`, `MetronomeGrid`, `TickSnapshot`, `CustomSoundResult`); `AudioEngine` implements it, `FakeAudioEngine` for tests | the ViewModel depends on `MetronomeAudio`, never on `AudioEngine`; events and status come from `AudioStatus` |
| metronome preferences or the custom click file | `:shared:data` package `metronome` (`MetronomePreferences` over the one DataStore, `CustomClickSoundStore` on Okio) | fakes live beside them; the picker writes to `stagingPath`, `commit()` moves it to `path` |
| a background-playback host or a file picker | interface in `:shared:ui` `audio.tembra.ui.platform` (`BackgroundPlaybackHost`, `SoundFilePicker`); Android in `:androidApp` (`MetronomeForegroundService`, `AndroidBackgroundPlaybackHost`, `SoundFilePickerHost` + `AndroidSoundFilePicker` attached by `MainActivity`); iOS in `:shared:ui` `iosMain` (`IosBackgroundPlaybackHost`, `IosSoundFilePicker`) | bound in `AndroidAppGraph` / `IosAppGraph`; host tests in `androidTest` of `:androidApp` and `iosTest` of `:shared:ui`; the OS picker and the lock-screen run are manual checks |
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
