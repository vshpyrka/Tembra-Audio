# Research: Project Scaffold

**Feature**: `specs/001-project-scaffold/spec.md` | **Date**: 2026-09-23

Every unknown from the plan's Technical Context is resolved below. Sources were checked on
2026-09-23: the official JetBrains and Google documentation (through Context7 and the rendered
pages), Maven Central and Google Maven metadata, GitHub releases, and the source of JetBrains'
production `kotlinconf-app`, Google's `Fruitties` sample and the `Kotlin/KMP-App-Template`
(all cloned and inspected). The Maven Central *search* index was found to be stale and was not
used; repository `maven-metadata.xml` files were read directly.

## R1. Build system

**Decision**: Gradle 9.7.0 via the wrapper, Kotlin DSL, a single version catalog
(`gradle/libs.versions.toml`), and precompiled convention plugins in a `build-logic` composite
build. Gradle root is `app/`.

**Rationale**: Kotlin Multiplatform and the Android Gradle Plugin are Gradle plugins; there is
no alternative build system for this stack. Version catalogs are the user's explicit
requirement and JetBrains' and Google's documented practice. Convention plugins avoid
repeating target and compiler configuration across six modules and keep the configuration
cache intact (a `buildSrc` change would invalidate every module). Gradle 9.7.0 is the newest
version inside Kotlin 2.4.20's tested range (7.6.3–9.7.0).

**Alternatives considered**: Amper (JetBrains' experimental build tool) — not production-ready
and unsupported by the Android KMP library plugin; `buildSrc` — slower incremental builds;
`allprojects {}` blocks — break Gradle isolated projects.

## R2. Toolchain and dependency versions

**Decision** (all Apache-2.0 unless noted):

| Component | Version | Evidence |
|---|---|---|
| Kotlin / Kotlin Multiplatform plugin / Compose compiler plugin | 2.4.20 | latest stable on Maven Central; compatibility guide row: Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1, Xcode 26.4 |
| Gradle | 9.7.0 | upper bound of the tested range (9.7.1 is current but untested) |
| Android Gradle Plugin | 9.3.3 | newest patch of the 9.3 line; the tested range ends at 9.3.1 and 9.4.1 is outside it. Drop to 9.3.1 if 9.3.3 misbehaves |
| Compose Multiplatform | 1.12.1 | latest stable; maps to Jetpack Compose 1.12.1; supports iOS 14+. Its `compose.material3` accessor maps to `org.jetbrains.compose.material3:material3:1.9.0`, the last stable Material 3 release on the decoupled track (pinned explicitly in the catalog) |
| `org.jetbrains.androidx.lifecycle` (ViewModel, runtime-compose) | 2.11.0 | latest stable |
| `org.jetbrains.compose.material3:material3-window-size-class` | 1.12.1 | ships with Compose Multiplatform |
| kotlinx-coroutines | 1.11.0 | latest stable |
| kotlinx-serialization-json (test-only, for the theme parity test) | 1.11.0 | latest stable; no compiler plugin needed for `JsonElement` parsing |
| Metro (`dev.zacsweers.metro` plugin, `metrox-viewmodel-compose`) | 1.4.4 | latest release 2026-09-16 |
| androidx.activity:activity-compose | 1.13.0 | latest stable |
| androidx.compose.ui:ui-test-junit4-android, ui-test-manifest | 1.12.1 | matches Jetpack Compose 1.12.1 |
| androidx.test:runner | 1.7.0 | latest stable |
| Oboe (`com.google.oboe:oboe`, Google Maven, Prefab) | 1.11.0 | release 2026-09-15 |
| miniaudio (vendored header, MIT-0 / public domain) | 0.11.25 | release 2026-03-03 |
| dr_wav from dr_libs (vendored header, MIT-0 / public domain) | 0.14.5 | tag `wav-0.14.5` |
| Signalsmith Stretch (vendored headers, MIT) | 1.1.0 | tag 1.1.0 |
| Catch2 (test-only, BSL-1.0) | v3.16.0 | release 2026-08-25 |
| Android NDK | 29.0.14206865 | newest non-rc NDK in the SDK manager |
| CMake (from the Android SDK manager) | 3.31.6 | newest 3.x in the SDK manager; used for Android, iOS and host builds |
| Android compileSdk / targetSdk | 37 / 36 | Compose Multiplatform 1.12.1's Android artifacts (Jetpack Compose 1.12.1) refuse to compile against API 36 (`checkAndroidMainAarMetadata`), so compileSdk is 37 (`platforms;android-37.0`, installed 2026-09-24); targetSdk stays 36 |
| Android minSdk | 26 | spec assumption (Android 8.0, AAudio available) |
| iOS deployment target | 16.0 | spec assumption; Compose Multiplatform minimum is 14 |
| JDK | 21 (Temurin, installed) | AGP 9 requires 17+ |
| Xcode | 26.6 (installed) | compatibility guide tested 26.4; set `kotlin.apple.xcodeCompatibility.nowarn=true` |

**Rationale**: the user asked for the latest guidelines; the constitution demands a build that
compiles. Taking the newest Kotlin and Compose Multiplatform while holding Gradle and AGP at the
upper bound of JetBrains' tested range gives both. JetBrains' own production app (Kotlin 2.4.10,
AGP 9.1.0, Gradle 9.6.1, Compose Multiplatform 1.11.1) proves the same shape of stack works.

**Alternatives considered**: the exact `kotlinconf-app` set (one minor version older; kept as
the documented fallback if 2.4.20 + 1.12.1 fails to resolve); AGP 9.4.1 and Gradle 9.7.1
(current, but outside the tested range for no benefit); compileSdk 37 (`platforms;android-37.0`,
an extra download with no consumer in this feature).

## R3. Android target configuration for shared modules

**Decision**: every `:shared:*` module applies `com.android.kotlin.multiplatform.library` and
configures the target with the `android {}` block (AGP ≥ 8.12; `androidLibrary {}` is deprecated
since 9.1.0-alpha09): `namespace`, `compileSdk`, `minSdk`, `withHostTestBuilder {}` and
`withDeviceTestBuilder { sourceSetTreeName = "test" }` with `androidx.test.runner.AndroidJUnitRunner`.
Source sets are `androidMain`, `androidHostTest` (JVM) and `androidDeviceTest` (instrumented);
`sourceSetTreeName = "test"` makes `commonTest` visible to device tests, so Compose UI tests
written once run on both the Android device and the iOS simulator.

**Rationale**: this is Google's current plugin for KMP libraries (single variant, faster
configuration) and what `kotlinconf-app`, `Fruitties` and both JetBrains templates use.

**Alternatives considered**: legacy `com.android.library` + `androidTarget()` — deprecated path,
variant explosion; a `jvm()` desktop target to run Compose UI tests on the host — rejected for
this feature to avoid a third platform the product does not ship (Constitution VI); revisit if
device/simulator UI test time becomes a problem.

## R4. Native engine build

**Decision**:
- `app/engine/` is a standalone CMake project (`CMakeLists.txt`, `include/tembra_engine.h`,
  `src/`, `third_party/`, `tests/`). It builds `tembra_engine` as a static library on every
  platform and, on Android only, is wrapped in a shared library with the JNI shim.
- **Android**: module `:engine:android` (`app/engine/android`, plugin `com.android.library`)
  with `externalNativeBuild.cmake { path = "../CMakeLists.txt"; version = "3.31.6" }`,
  `ndkVersion = "29.0.14206865"`, `buildFeatures.prefab = true`, dependency
  `com.google.oboe:oboe:1.11.0`, ABIs `arm64-v8a` and `x86_64` (64-bit phones and the
  Apple-silicon/x86 emulator). `:shared:engine`'s `androidMain` depends on it. This shape is
  Google's documented recommendation because the KMP library plugin has no `externalNativeBuild`.
- **iOS**: `:shared:engine` registers two Gradle `Exec` tasks that run the SDK's CMake
  (`$ANDROID_HOME/cmake/3.31.6/bin/cmake`) with `-DCMAKE_SYSTEM_NAME=iOS`,
  `-DCMAKE_OSX_ARCHITECTURES=arm64`, `-DCMAKE_OSX_DEPLOYMENT_TARGET=16.0` and
  `-DCMAKE_OSX_SYSROOT=iphoneos` or `iphonesimulator`, producing `libtembra_engine.a` per
  target under `build/engine/<target>/`. The cinterop for each iOS target depends on its task and
  passes `extraOpts("-libraryPath", <dir>)`; the `.def` declares `staticLibraries =
  libtembra_engine.a` so the archive is embedded into the klib and linked into the framework.
- **Host tests**: `:shared:engine` registers `engineHostTest` (configure + build + `ctest` for
  `app/engine` on macOS with Catch2 v3.16.0 fetched by `FetchContent` pinned to the tag).

**Rationale**: one CMake project, one compiler toolchain per platform, one CMake binary (the
SDK's) for all three uses; no Homebrew dependency. Static linking on iOS matches the static
Kotlin framework. Prefab is the supported way to consume Oboe without vendoring its sources.

**Alternatives considered**: Kotlin/Native `androidNativeArm64` targets building the C++ —
possible but leaves Oboe/Prefab and the NDK toolchain unused; a hand-built XCFramework for the
engine — more moving parts than `staticLibraries` for a single static archive; Homebrew CMake —
second toolchain to install.

## R5. Vendored native dependencies

**Decision**: vendor the permissive header-only libraries the engine will use, each in its own
folder under `app/engine/third_party/<name>/` with its upstream `LICENSE` file: miniaudio
0.11.25 (`miniaudio.h`), dr_wav 0.14.5 (`dr_wav.h`), Signalsmith Stretch 1.1.0
(`signalsmith-stretch.h` and its `dsp/` headers). One translation unit
(`src/third_party_check.cpp`) compiles them with their implementation macros and reports their
version strings through `tembra_engine_describe()`. Oboe is not vendored (Prefab). KissFFT or
pffft are deferred to the tuner feature.

**Rationale**: the user asked for the native audio dependencies to be part of the scaffold; the
spec (FR-006) requires them compiled and linked without functionality. Compiling them in and
echoing their versions proves the toolchain handles them on both platforms.

**Alternatives considered**: git submodules — awkward with shallow clones and fresh checkouts;
CMake `FetchContent` for runtime libs — network at build time and unpinned licence files.

## R6. iOS app integration

**Decision**: direct integration. `:shared:ui` builds a static framework `TembraShared`
(`isStatic = true`, `binaries.framework`), exports nothing beyond its own API, and Xcode calls
`./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode` in a run-script build phase guarded
by `OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED`. The Xcode project is derived from
`Kotlin/KMP-App-Template` (Apache-2.0; attribution in the licence inventory): `Config.xcconfig`
with `TEAM_ID`, `BUNDLE_ID=audio.tembra.app`, `APP_NAME=Tembra Audio`; `IPHONEOS_DEPLOYMENT_TARGET
= 16.0`; `ENABLE_USER_SCRIPT_SANDBOXING = NO`; `Info.plist` with
`CADisableMinimumFrameDurationOnPhone = true`. Swift is two files: `iOSApp.swift` (creates the
Metro graph through `MainViewControllerKt`) and `ContentView.swift`
(`UIViewControllerRepresentable` around `MainViewController()`).

**Rationale**: the documented JetBrains path, used by every reference app; no CocoaPods, no
SwiftPM packaging step, nothing for Swift to do but host the Compose view controller.

**Alternatives considered**: SwiftPM/XCFramework export — for distributing a library, not for
an app in the same repository; CocoaPods integration — deprecated by JetBrains.

## R7. Test lanes

**Decision**:

| Lane | Task | Runs | Contains |
|---|---|---|---|
| Android host | `checkAndroidHost` (aggregates each `:shared:*` module's `androidHostTest` task) | JVM | `SemanticVersion`, parser, fake engine, ViewModel, `AppDirectories` (Android actual under Robolectric is **not** used; the Android actual is exercised on device), theme parity test |
| iOS simulator | `checkIos` (aggregates `iosSimulatorArm64Test`) | booted or auto-started simulator | everything in `commonTest` plus `iosTest` (`NativeAudioEngineTest`, `AppDirectories`) plus Compose UI tests |
| Android device | `checkAndroidDevice` (aggregates `connectedAndroidDeviceTest`-style tasks) | connected phone or emulator | Compose UI tests, `NativeAudioEngineTest`, `AppDirectories` |
| Engine | `engineHostTest` | macOS host | Catch2 tests for the C ABI |

`runComposeUiTest` tests use `org.jetbrains.compose.ui:ui-test` in `commonTest`; Android device
tests add `ui-test-junit4-android`, `ui-test-manifest` (debug) and the AndroidX test runner.
Exact underlying task names are confirmed during implementation and wired into the aggregate
tasks so the documented commands stay stable.

**Rationale**: matches the constitution (JVM and iOS simulator for shared logic; device or
emulator for behaviour that needs a real platform) and the spec's "one command per platform".

**Alternatives considered**: Robolectric for Compose UI tests on the JVM — extra dependency and
Android-only; Kotest — second DSL; Roborazzi screenshot tests — deferred until the design system
stabilises (spec assumption).

## R8. Dependency injection

**Decision**: Metro 1.4.4. One `AppGraph` interface in `:shared:ui` (`@DependencyGraph(AppScope::class)`)
extending `ViewModelGraph`, binding `AudioEngine` to `NativeAudioEngine` with
`@SingleIn(AppScope::class)` and exposing `metroViewModelFactory`. `PlaceholderViewModel` is
`@Inject`-constructed with `@ContributesIntoMap`/`@ViewModelKey` as in `kotlinconf-app`, obtained
in Compose with `metroViewModel()` under `LocalMetroViewModelFactory`. Graph creation:
`createGraphFactory<AppGraph.Factory>()` in the Android `Application` and in `MainViewController()`
for iOS.

**Rationale**: constitution constraint; compile-time validation; no KSP; JetBrains ships exactly
this in production.

**Alternatives considered**: Koin (fallback if Metro blocks a Kotlin upgrade), manual
`AppContainer` (last resort); both documented in `docs/research/kmp-code-architecture.md`.

## R9. Design system implementation

**Decision**: transcribe `design/stitch/theme.json` into one Kotlin file per token type under
`audio.tembra.ui.theme` (see `data-model.md`), applied through `TembraTheme` as Material 3
`darkColorScheme`, `Typography`, `Shapes`, plus composition locals for `DawColors`,
`TembraSpacing` and the three extra text styles. Window width classes come from
`material3-window-size-class` (`calculateWindowSizeClass()`), stable in Compose Multiplatform
1.12.1. Fonts are static TTF instances bundled as Compose resources under
`composeResources/font/`: Space Grotesk Medium/SemiBold/Bold from the upstream 2.0.0 release
(`fonts/ttf/static/`), Inter Regular/Medium/SemiBold from the Inter 4.1 release archive; both OFL
1.1 with licence files kept beside them. A JVM test compares the Kotlin tokens with `theme.json`.

**Rationale**: the design defines only a dark palette; tokens as code are the constitution's
requirement; a parity test is the cheapest guarantee against drift. Static font instances avoid
relying on variable-font axis support on both renderers.

**Alternatives considered**: generating the Kotlin tokens from `theme.json` at build time — a
Gradle task for a file that changes rarely; variable TTFs from `google/fonts` with
`FontVariation` — fewer files but a rendering risk on iOS; the beta `material3-adaptive`
artifact for size classes — unnecessary while only the width bucket is needed.

## R10. Contents of the domain and persistence shells

**Decision**: `:shared:core` ships `SemanticVersion` (parse, compare, `toString`), used by the
engine descriptor now and by the project schema version later. `:shared:data` ships
`AppDirectories` (`expect`/`actual` returning the platform documents and cache directories),
needed by every later persistence feature. Each has `commonTest` tests and, for
`AppDirectories`, device and simulator tests.

**Rationale**: the spec requires the module parts to exist with tests wired but forbids product
functionality; these two types are the smallest genuinely reusable pieces of their layers, so
the tests are real rather than placeholders.

**Alternatives considered**: empty modules with a trivial test — meets the letter of FR-008 but
tests nothing; `Paths` in `:shared:core` — would put platform access in the pure module.

## R11. Local environment and prerequisites

**Findings on this machine (2026-09-23)**: JDK 21 Temurin; Xcode 26.6 with iOS 26.5 SDKs but
**no simulator runtime downloaded** (`xcrun simctl runtime list` is empty); Android SDK with
`platforms;android-36`, `build-tools;36.0.0`/`37.0.0`, `platform-tools 37.0.1`, `cmdline-tools`;
**no NDK, no CMake, no emulator, no system images**; no system Gradle; `android` CLI
1.0.16261425 (update available); Homebrew present; about 23 GB free disk.

**Decision**: prerequisites are installed through the `android` CLI (`android sdk install
"ndk;29.0.14206865" "cmake;3.31.6"`) and Xcode (`xcodebuild -downloadPlatform iOS`, roughly
8 GB). The Gradle wrapper is bootstrapped once from the Gradle 9.7.0 distribution zip
(`gradle wrapper --gradle-version 9.7.0`) and committed. A connected Android phone replaces the
emulator for running, device tests and screenshots; the emulator (`emulator`,
`system-images;android-35;google_apis;arm64-v8a`, the newest arm64 image offered) is optional and
documented for desktop-mode window-resize checks. A real iOS device can run the app and provide
screenshots but cannot run Gradle's Kotlin/Native test task, so the simulator runtime is
mandatory for the iOS test lane.

## R12. Licence inventory

**Decision**: `app/THIRD_PARTY_LICENSES.md`, hand-maintained, one row per dependency
(coordinates or vendored path, version, licence, source URL). All runtime dependencies are
Apache-2.0, MIT, MIT-0/public domain or OFL 1.1; Catch2 (BSL-1.0) is test-only. Reviewed
against Constitution VI in every plan.

**Alternatives considered**: the AboutLibraries Gradle plugin — generates JSON for a licence
screen the app does not have yet.

## R13. Screenshot verification procedure

**Decision**: screenshots are stored under `specs/001-project-scaffold/screenshots/` named
`<platform>-<compact|wide>.png`. Android: `android screen capture --output <file>` against the connected device
or emulator, once in portrait phone width and once in a wide window (tablet, landscape, or
desktop-mode/free-form window). iOS: `xcrun simctl io <udid> screenshot <file>` on an iPhone
and an iPad simulator. The reviewer compares against `design/stitch/screens/*/screenshot.jpg`
and the palette in `theme.json` and records the result in the feature's checklist.

## R14. Naming

**Decision**: Kotlin packages `audio.tembra.<layer>` (`core`, `engine`, `data`, `ui`); Android
`applicationId` and iOS bundle identifier `audio.tembra.app`; iOS framework `TembraShared`;
native library `tembra_engine`; Gradle project paths `:shared:core`, `:shared:engine`,
`:shared:data`, `:shared:ui`, `:engine:android`, `:androidApp`.

**Rationale**: reverse-domain form of the product name "Tembra Audio" with no organisation
domain decided yet; easy to rename before the first public release since nothing is published.
