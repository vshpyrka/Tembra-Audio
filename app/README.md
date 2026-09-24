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
| `uiTest` (`:shared:ui`) | iOS simulator, Android device | `runComposeUiTest` screen tests (they need a real UI platform) |
| `iosTest`, `androidDeviceTest` | one platform | the real `NativeAudioEngine`, platform directories |
| `engine/tests` | macOS host | Catch2, offline, no audio device |

## Screenshots

```bash
android screen capture --output specs/<feature>/screenshots/android-compact.png   # add --device <serial> if several
xcrun simctl io "iPhone 17" screenshot specs/<feature>/screenshots/ios-compact.png
xcrun simctl io "iPad Pro 13-inch (M5)" screenshot specs/<feature>/screenshots/ios-wide.png
```

Wide Android capture: rotate to landscape (`adb shell settings put system user_rotation 1` after
`accelerometer_rotation 0`), capture, restore. Large fonts: `adb shell settings put system font_scale 2.0`
and `xcrun simctl ui "iPhone 17" content_size accessibility-extra-extra-extra-large`; restore to `1.0` /
`medium`. Compare against `design/stitch/theme.json` and `design/stitch/screens/*/screenshot.jpg`.

## Module map

```
app/
├── build-logic/            convention plugins: tembra.kmp.library, tembra.kmp.compose, tembra.android.app, tembra.android.native
├── engine/                 C++ engine (CMake). include/tembra_engine.h is the only header Kotlin sees (plain C ABI)
│   ├── src/                engine_info.cpp (descriptor), third_party_check.cpp (compiles vendored libs)
│   ├── third_party/        miniaudio, dr_wav, Signalsmith Stretch (+ licences)
│   ├── tests/              Catch2 tests
│   └── android/            :engine:android — com.android.library + CMake/NDK + Prefab Oboe → libtembra_engine.so + JNI shim
├── shared/
│   ├── core/               :shared:core   — pure Kotlin domain (no Compose, no platform APIs, no project dependencies)
│   ├── engine/             :shared:engine — AudioEngine facade: NativeAudioEngine (JNI / cinterop), FakeAudioEngine, parser
│   ├── data/               :shared:data   — persistence layer (AppDirectories for now)
│   └── ui/                 :shared:ui     — Compose screens, ViewModels, theme, Metro graph; iOS framework "TembraShared"
├── androidApp/             :androidApp — TembraApplication (Metro graph) + MainActivity
└── iosApp/                 Xcode project; two Swift files host MainViewController()
```

Dependencies point inward: apps → `:shared:ui` → `:shared:engine` / `:shared:data` → `:shared:core`.
A dependency in the wrong direction fails configuration (`tembra.kmp.library`).

## Where does X go?

| You are adding… | Put it in | Notes |
|---|---|---|
| a new screen | `:shared:ui`, package `audio.tembra.ui.<feature>` | one `ViewModel` per destination, `StateFlow<UiState>`; tests in `uiTest` (screen) and `commonTest` (ViewModel) |
| a domain rule, model or pure calculation | `:shared:core` | no Compose, no platform code; test on JVM and iOS |
| a persistence concern (files, database, preferences) | `:shared:data` | platform access behind an interface, wired in the platform Metro graphs |
| an engine command or telemetry | `engine/include/tembra_engine.h` + `engine/src`, then `:shared:engine` (`AudioEngine`, actuals, `FakeAudioEngine`) | plain C ABI, no callbacks into Kotlin, nothing Kotlin on the audio thread |
| platform SDK access (paths, OS services) | `androidMain` / `iosMain` of the owning module | `expect`/`actual` only for SDK access; business logic uses interfaces |
| a colour, size, font or shape | `:shared:ui` `audio.tembra.ui.theme` from `design/stitch/theme.json` | never a literal value in a screen; `ThemeJsonParityTest` guards drift |
| a third-party dependency | `gradle/libs.versions.toml` + `THIRD_PARTY_LICENSES.md` | permissive licence only (Constitution VI) |

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
- `./gradlew build` (the metadata-jar compilation used only for publishing libraries) prints Kotlin
  "KLIB loader: The same 'unique_name=…' found in more than one library" warnings. They come from
  Compose Multiplatform 1.12.1's own dependency graph, which still ships both `androidx.*` and
  `org.jetbrains.androidx.*` variants of savedstate, annotation and collection, and from Metro's runtime
  klib sharing the name `runtime` with Compose's. App compilations and all four test lanes are warning-free.
