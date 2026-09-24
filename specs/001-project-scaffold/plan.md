# Implementation Plan: Project Scaffold

**Branch**: `001-project-scaffold` | **Date**: 2026-09-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-project-scaffold/spec.md`

## Summary

Create the Kotlin Multiplatform codebase that every later feature builds on: a Gradle project
rooted at `app/` with a version catalog and convention plugins; four shared modules
(`:shared:core`, `:shared:engine`, `:shared:data`, `:shared:ui`) plus thin Android and iOS apps;
one CMake-built C++ engine with a plain C ABI, compiled for Android (through a small
`com.android.library` module with Prefab-provided Oboe) and iOS (static library embedded through
cinterop), linked into both apps and reachable from shared Kotlin through an `AudioEngine`
facade that only reports the engine's name, version and compiled libraries; the Stitch design
system transcribed into a shared Compose Multiplatform theme with a parity test against
`design/stitch/theme.json`; a single placeholder screen (title, counter button, engine caption,
token preview) that adapts to compact and wide windows and supports hover, tooltip and keyboard;
Metro dependency injection; and test lanes on the Android host, the Android device, the iOS
simulator and the C++ host, each behind one documented Gradle command. No product functionality.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (Kotlin Multiplatform, Kotlin/Native for iOS); C++17 for
the engine; Swift 5 for the two-file iOS shell.

**Primary Dependencies**: Gradle 9.7.0, Android Gradle Plugin 9.3.3 (`com.android.application`,
`com.android.library`, `com.android.kotlin.multiplatform.library`), Compose Multiplatform 1.12.1
(Material 3, `material3-window-size-class`, resources, `ui-test`), `org.jetbrains.androidx.lifecycle`
2.11.0 (ViewModel, runtime-compose), kotlinx-coroutines 1.11.0, Metro 1.4.4, androidx.activity
1.13.0; native: Oboe 1.11.0 (Prefab), miniaudio 0.11.25, dr_wav 0.14.5, Signalsmith Stretch
1.1.0 (vendored headers), Catch2 3.16.0 (tests); NDK 29.0.14206865, CMake 3.31.6. Full table
with evidence in [research.md](research.md) R2.

**Storage**: N/A (no persistence in this feature; `:shared:data` only exposes platform
directories).

**Testing**: `kotlin.test` + `kotlinx-coroutines-test` in `commonTest`; Compose Multiplatform
`runComposeUiTest`; Android host tests (JVM), Android device tests (`androidx.test` runner,
`ui-test-junit4-android`), iOS simulator tests (`iosSimulatorArm64Test`); Catch2 via CTest for
C++; kotlinx-serialization-json (test-only) for the theme parity test.

**Target Platform**: Android 8.0+ (API 26, compile/target SDK 36, ABIs arm64-v8a and x86_64);
iOS 16.0+ (iosArm64 devices, iosSimulatorArm64). Development host: macOS with Xcode 26.6 and
JDK 21.

**Project Type**: mobile app (Kotlin Multiplatform shared code, native shells) with an embedded
native library.

**Performance Goals**: fresh clone to running app on both platforms in under 15 minutes; each
test lane under 10 minutes; placeholder screen renders at the display refresh rate with no
dropped frames while pressing the button (no measurable work happens).

**Constraints**: no Kotlin on any audio thread (there is none yet); no audio device opened, no
permissions requested; every visual value from design tokens; one type per file; dependencies
only from the decided stack with permissive licences; zero compiler or lint warnings; iOS
framework static and symbol-stripped. Coverage measurement is deferred: this feature has no
tooling to compute module coverage, so Constitution III's "no coverage drop" rule is applied by
review of the test list until Kover is introduced with the audio-spine feature, where the first
real domain logic lands.

**Scale/Scope**: 6 Gradle modules + 1 CMake project + 1 Xcode project; about 40 Kotlin files,
6 C/C++ files, 2 Swift files; one screen; roughly 25 automated tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Real-Time Engine Isolation | The engine exposes a plain C ABI with four descriptor functions, no callbacks into Kotlin, no audio thread, no allocation on any hot path. Kotlin calls it only through `NativeAudioEngine` on ordinary threads. | PASS |
| II. Shared Kotlin Control Plane, Compose UI | Module graph is exactly `:shared:core`, `:shared:engine`, `:shared:data`, `:shared:ui` + `:androidApp` + `iosApp`; dependencies point inward (verified by Gradle: `:shared:core` declares no project dependencies). The single screen uses a multiplatform `ViewModel` exposing one `StateFlow<PlaceholderUiState>`; screens never touch the engine; `expect`/`actual` only in `NativeAudioEngine` and `AppDirectories`. No telemetry exists yet. | PASS |
| III. Test-First | Every task in Phase 2 is written test-first; four lanes defined (R7); Kotlin tests use `FakeAudioEngine`; the real engine is linked only in `iosTest` and `androidDeviceTest`; C++ has its own Catch2 target. Enforced when tasks are generated and reviewed. | PASS |
| IV. Adaptive, Input-Agnostic UI | Layout keyed on `WindowSizeClass` width (compact vs wide); Material 3 `TooltipBox` and hover states on the button; keyboard activation and visible focus; 44 dp minimum; all values from `TembraTheme`; screenshots captured with `android screen capture` and `simctl` at both widths and reviewed against the Stitch reference; semantics and test tags on every control. | PASS |
| V. Document Model in Beats | No document, tempo or musical time in this feature; nothing stored in samples. | PASS (not exercised) |
| VI. Simplicity | Only the dependencies in R2; vendored native libraries are required by the spec (FR-006) and echo their versions instead of adding functionality; `:shared:core` and `:shared:data` contain one small reusable type each rather than speculative layers; licence inventory lists every dependency as permissive (Catch2 BSL-1.0 is test-only). | PASS |
| VII. Code Organisation | One type per file in every Kotlin module (token types, locals, ViewModel, state, screen, facade, parser, fake); feature package `placeholder`; `internal` by default. | PASS |
| Technology & Architecture Constraints | Stack matches `docs/research/kmp-code-architecture.md` (versions refreshed in R2); design tokens from `design/stitch/theme.json`; product name "Tembra Audio". Navigation 3, Room, DataStore, Okio are intentionally **not** added yet (no consumer). | PASS |
| Development Workflow & Quality Gates | This is the first item of the fixed build order; the definition of done maps to the four test lanes plus screenshots; CI configuration deferred per spec assumption, commands are pipeline-ready. | PASS |

**Post-design re-check (after Phase 1)**: unchanged. The one structural addition beyond the
constitution's named module list is `:engine:android`; it is a build-mechanics module, not a
Kotlin layer, and is justified in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-scaffold/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R14 with evidence
├── data-model.md        # Phase 1: design tokens, placeholder state, engine descriptor
├── quickstart.md        # Phase 1: prerequisites, build/run/test/screenshot commands
├── contracts/
│   ├── engine-c-abi.md          # tembra_engine.h subset
│   ├── audio-engine-facade.md   # AudioEngine, EngineDescriptor, Fake, parser
│   ├── design-system-api.md     # TembraTheme and token preview
│   └── placeholder-screen.md    # screen, ViewModel, semantics, test tags
├── checklists/requirements.md
├── screenshots/         # created during implementation (android/ios × compact/wide)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/                                    Gradle root
├── settings.gradle.kts                 includeBuild("build-logic"); include(":shared:core", ":shared:engine",
│                                       ":shared:data", ":shared:ui", ":engine:android", ":androidApp")
├── build.gradle.kts                    aggregate tasks: checkAndroidHost, checkIos, checkAndroidDevice, engineHostTest
├── gradle.properties                   configuration cache, build cache, parallel, Kotlin/Native flags, JVM args
├── gradle/libs.versions.toml           every version and library (R2)
├── gradle/wrapper/                     Gradle 9.7.0 wrapper
├── gradlew, gradlew.bat
├── local.properties                    sdk.dir (git-ignored)
├── THIRD_PARTY_LICENSES.md             licence inventory (R12)
├── README.md                           module map, where-does-X-go, commands (FR-011)
├── build-logic/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── tembra.kmp.library.gradle.kts    KMP + Android KMP library plugin, iOS targets, kotlin.test
│       ├── tembra.kmp.compose.gradle.kts    adds Compose Multiplatform + compiler plugin + ui-test
│       └── tembra.android.app.gradle.kts    Android application defaults
├── engine/                             C++ engine (CMake project)
│   ├── CMakeLists.txt                  static lib tembra_engine; option TEMBRA_BUILD_TESTS
│   ├── include/tembra_engine.h         C ABI (contracts/engine-c-abi.md)
│   ├── src/engine_info.cpp             name/version/describe
│   ├── src/third_party_check.cpp       compiles vendored headers, exposes their version strings
│   ├── third_party/{miniaudio,dr_libs,signalsmith-stretch}/  vendored headers + LICENSE files
│   ├── tests/CMakeLists.txt            Catch2 v3.16.0 via FetchContent
│   ├── tests/engine_info_test.cpp
│   └── android/                        :engine:android (com.android.library)
│       ├── build.gradle.kts            externalNativeBuild (CMake 3.31.6), NDK 29, prefab, Oboe 1.11.0, ABIs
│       ├── src/main/AndroidManifest.xml
│       └── src/main/cpp/CMakeLists.txt + tembra_jni.cpp   shared lib libtembra_engine.so wrapping the static lib
├── shared/
│   ├── core/                           :shared:core (pure Kotlin)
│   │   └── src/commonMain/kotlin/audio/tembra/core/version/SemanticVersion.kt
│   │       src/commonTest/kotlin/audio/tembra/core/version/SemanticVersionTest.kt
│   ├── engine/                         :shared:engine
│   │   ├── build.gradle.kts            cinterops per iOS target, cmake Exec tasks, engineHostTest
│   │   ├── src/nativeInterop/cinterop/tembra_engine.def
│   │   ├── src/commonMain/kotlin/audio/tembra/engine/{AudioEngine,EngineDescriptor,NativeAudioEngine,
│   │   │                                              FakeAudioEngine,EngineDescriptorParser}.kt
│   │   ├── src/androidMain/kotlin/audio/tembra/engine/NativeAudioEngine.android.kt   (JNI externals)
│   │   ├── src/iosMain/kotlin/audio/tembra/engine/NativeAudioEngine.ios.kt           (cinterop)
│   │   ├── src/commonTest/kotlin/audio/tembra/engine/{EngineDescriptorParserTest,FakeAudioEngineTest}.kt
│   │   ├── src/iosTest/kotlin/audio/tembra/engine/NativeAudioEngineTest.kt
│   │   └── src/androidDeviceTest/kotlin/audio/tembra/engine/NativeAudioEngineTest.kt
│   ├── data/                           :shared:data
│   │   └── src/{commonMain,androidMain,iosMain}/kotlin/audio/tembra/data/AppDirectories*.kt
│   │       src/{commonTest,iosTest,androidDeviceTest}/kotlin/audio/tembra/data/AppDirectoriesTest.kt
│   └── ui/                             :shared:ui (Compose, ViewModels, DI, iOS framework "TembraShared")
│       ├── build.gradle.kts            tembra.kmp.compose, Metro plugin, framework config, device-test deps
│       ├── src/commonMain/composeResources/font/   SpaceGrotesk-{Medium,SemiBold,Bold}.ttf, Inter-{Regular,Medium,SemiBold}.ttf, OFL.txt
│       ├── src/commonMain/kotlin/audio/tembra/ui/App.kt
│       ├── src/commonMain/kotlin/audio/tembra/ui/di/{AppGraph,AppBindings}.kt
│       ├── src/commonMain/kotlin/audio/tembra/ui/theme/{TembraTheme,TembraColorScheme,DawColors,LocalDawColors,
│       │       TembraTypography,TembraTypographyExtras,LocalTembraTypographyExtras,TembraFonts,TembraShapes,
│       │       TembraSpacing,LocalTembraSpacing,DesignTokenPreview}.kt
│       ├── src/commonMain/kotlin/audio/tembra/ui/placeholder/{PlaceholderUiState,PlaceholderViewModel,
│       │       PlaceholderScreen,PlaceholderScreenContent}.kt
│       ├── src/iosMain/kotlin/audio/tembra/ui/MainViewController.kt
│       ├── src/androidHostTest/kotlin/audio/tembra/ui/theme/ThemeJsonParityTest.kt
│       └── src/commonTest/kotlin/audio/tembra/ui/{placeholder/PlaceholderScreenTest,placeholder/PlaceholderViewModelTest,
│               theme/DesignTokenPreviewTest}.kt
├── androidApp/                         :androidApp
│   ├── build.gradle.kts                tembra.android.app; applicationId audio.tembra.app
│   └── src/main/{AndroidManifest.xml, kotlin/audio/tembra/android/{TembraApplication,MainActivity}.kt}
└── iosApp/                             Xcode project (from KMP-App-Template, adapted)
    ├── Configuration/Config.xcconfig   TEAM_ID, BUNDLE_ID=audio.tembra.app, APP_NAME=Tembra Audio
    ├── iosApp.xcodeproj/               run-script: ./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode
    └── iosApp/{iOSApp.swift, ContentView.swift, Info.plist, Assets.xcassets}
```

**Structure Decision**: the module graph from `docs/research/kmp-code-architecture.md` §2 and
`app/README.md`, realised at scaffold scope. `:shared:core` and `:shared:data` each hold one
small reusable type (R10). `:engine:android` exists only because Google's KMP library plugin
has no CMake support (R4). No navigation, transport bar, persistence libraries or feature
packages beyond `placeholder` are created (spec FR-012).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `:engine:android` Gradle module in addition to the constitution's named module list | `com.android.kotlin.multiplatform.library` has no `externalNativeBuild`; Google's documented recommendation is a standalone `com.android.library` module that builds the CMake project and is consumed from `androidMain`. Prefab (Oboe) also requires an Android library module. | Building the C++ with Kotlin/Native `androidNative*` targets leaves Oboe/Prefab and the NDK toolchain unusable; putting CMake into `:androidApp` would make `:shared:engine` depend on the app, reversing the dependency direction. |
| Vendored third-party headers compiled without being used (R5) | Spec FR-006 and the user's request require the native audio dependencies to be part of the build now so the audio-spine feature does not discover toolchain problems late. | Deferring them would satisfy Principle VI more literally but violates the spec; they are compiled in one translation unit and only their version strings are exposed. |
