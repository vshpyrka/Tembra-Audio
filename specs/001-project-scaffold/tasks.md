---

description: "Task list for the project scaffold (001)"
---

# Tasks: Project Scaffold

**Input**: Design documents from `/specs/001-project-scaffold/`

**Prerequisites**: plan.md, spec.md, research.md (R1–R14), data-model.md, contracts/ (engine-c-abi, audio-engine-facade, design-system-api, placeholder-screen), quickstart.md

**Tests**: REQUIRED. Constitution III (Test-First) is non-negotiable and the spec's User Story 2 demands tests on both platforms. Every test task is written first and MUST fail before the implementation task that follows it. Build-configuration tasks have no unit test; their "red" is a failing build and their "green" is a successful one.

**Organization**: Tasks are grouped by user story. Phases run in the order Setup → Foundational → US1 → US2 → US4 → US3 → US5 → Polish. US4 (P2) is scheduled before US3 (P2) because US3's ViewModel consumes the `AudioEngine`/`EngineDescriptor` types that US4 creates; both are P2 so the priority order is preserved.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 build & launch, US2 tests on both platforms, US3 design system rendered, US4 native engine link, US5 documentation
- Include exact file paths in descriptions

## Path Conventions

Gradle root is `app/`. Kotlin modules: `app/shared/{core,engine,data,ui}` (`:shared:*`), `app/androidApp` (`:androidApp`), `app/engine/android` (`:engine:android`). CMake project: `app/engine`. Xcode project: `app/iosApp`. Packages `audio.tembra.<layer>`. One type per file (Constitution VII).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Toolchain prerequisites and the Gradle skeleton every module plugs into (research R1, R2, R11).

- [X] T001 Install native toolchain into the Android SDK: run `android sdk install "ndk;29.0.14206865" "cmake;3.31.6"` and verify `platforms/android-36` and `build-tools/36.0.0` are listed by `android sdk list`; record the commands in `specs/001-project-scaffold/quickstart.md` §1 if they differ
- [X] T002 [P] Download the iOS simulator runtime with `xcodebuild -downloadPlatform iOS` and verify `xcrun simctl runtime list` shows an iOS runtime and `xcrun simctl list devices available` shows an iPhone 17 device (create one with `xcrun simctl create` if none exists)
- [X] T003 [P] Bootstrap the Gradle 9.7.0 wrapper: download `gradle-9.7.0-bin.zip` to the scratch directory, run its `bin/gradle wrapper --gradle-version 9.7.0 --distribution-type bin` inside `app/`, producing `app/gradlew`, `app/gradlew.bat`, `app/gradle/wrapper/gradle-wrapper.jar`, `app/gradle/wrapper/gradle-wrapper.properties`
- [X] T004 [P] Create `app/gradle/libs.versions.toml` with every version from research R2 (`kotlin = "2.4.20"`, `agp = "9.3.3"`, `compose-multiplatform = "1.12.1"`, `androidx-lifecycle = "2.11.0"`, `kotlinx-coroutines = "1.11.0"`, `kotlinx-serialization = "1.11.0"`, `metro = "1.4.4"`, `androidx-activity = "1.13.0"`, `androidx-compose = "1.12.1"`, `androidx-test-runner = "1.7.0"`, `oboe = "1.11.0"`, `android-compileSdk = "37"`, `android-targetSdk = "36"`, `android-minSdk = "26"`, `android-ndk = "29.0.14206865"`, `cmake = "3.31.6"`), the `[libraries]` entries for lifecycle-viewmodel-compose, lifecycle-runtime-compose, kotlinx-coroutines-core, kotlinx-coroutines-test, kotlinx-serialization-json, kotlin-test, metrox-viewmodel-compose, androidx-activity-compose, androidx-compose-ui-test-junit4-android, androidx-compose-ui-test-manifest, androidx-test-runner, oboe, and the `[plugins]` entries kotlinMultiplatform, kotlinAndroid, composeCompiler (`org.jetbrains.kotlin.plugin.compose`), composeMultiplatform (`org.jetbrains.compose`), androidApplication, androidLibrary, androidKmpLibrary (`com.android.kotlin.multiplatform.library`), metro (`dev.zacsweers.metro`)
- [X] T005 [P] Create `app/settings.gradle.kts` (`rootProject.name = "tembra"`, `pluginManagement` and `dependencyResolutionManagement` repositories `google { content { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }`, `mavenCentral()`, `gradlePluginPortal()`; `includeBuild("build-logic")`; `include(":shared:core", ":shared:engine", ":shared:data", ":shared:ui", ":engine:android", ":androidApp")`) and `app/gradle.properties` (`org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8`, `kotlin.daemon.jvmargs=-Xmx4G`, `org.gradle.configuration-cache=true`, `org.gradle.caching=true`, `org.gradle.parallel=true`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.native.binary.gc=cms`, `kotlin.incremental.native=true`, `kotlin.apple.xcodeCompatibility.nowarn=true`, `kotlin.code.style=official`)
- [X] T006 [P] Create the `build-logic` composite build: `app/build-logic/settings.gradle.kts` (repositories as above, `rootProject.name = "build-logic"`), `app/build-logic/build.gradle.kts` (`kotlin-dsl` plugin; `implementation` of the Kotlin, Android, Compose Multiplatform and Metro Gradle plugin artifacts referenced through the version catalog via `files(libs.javaClass.superclass.protectionDomain.codeSource.location)` or explicit coordinates), and three precompiled scripts: `app/build-logic/src/main/kotlin/tembra.kmp.library.gradle.kts` (applies `org.jetbrains.kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`; `android {}` block with namespace from the project path, `compileSdk`/`minSdk` from the catalog, `withHostTestBuilder {}`, `withDeviceTestBuilder { sourceSetTreeName = "test" }.configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`; targets `iosArm64()`, `iosSimulatorArm64()`; `commonTest` depends on `kotlin("test")` and `kotlinx-coroutines-test`; JVM toolchain 21), `app/build-logic/src/main/kotlin/tembra.kmp.compose.gradle.kts` (applies `tembra.kmp.library`, `org.jetbrains.compose`, `org.jetbrains.kotlin.plugin.compose`; adds `compose.runtime`, `compose.foundation`, `compose.material3`, `compose.components.resources`, `compose.ui` to `commonMain`, `compose.uiTest` to `commonTest`), `app/build-logic/src/main/kotlin/tembra.android.app.gradle.kts` (applies `com.android.application`, `org.jetbrains.kotlin.android`, Compose plugins; `compileSdk`/`minSdk`/`targetSdk` from the catalog; JVM target 21)
- [X] T007 [P] Extend `.gitignore` with `app/engine/build/`, `app/**/.cxx/`, `app/build-logic/build/`, `app/build-logic/.gradle/`, `app/iosApp/build/`, `app/iosApp/**/xcuserdata/`, `app/iosApp/**/DerivedData/`, `*.xcuserstate` (keep existing entries)
- [X] T008 Create `app/local.properties` with `sdk.dir=/Users/<you>/Library/Android/sdk` (already git-ignored) and verify the skeleton evaluates: `cd app && ./gradlew help --configuration-cache` completes with `BUILD SUCCESSFUL` and no warnings (the six module directories may be empty stubs with a one-line `build.gradle.kts` at this point)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The six modules, the C++ engine project with its first C ABI function, and the Android/iOS native build wiring. Until this phase compiles on both platforms no story can be demonstrated (research R3, R4, R6).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T009 [P] Create `app/shared/core/build.gradle.kts` applying `tembra.kmp.library` only (no project dependencies; the module MUST stay pure Kotlin) and the empty source-set directories `app/shared/core/src/commonMain/kotlin/audio/tembra/core/` and `app/shared/core/src/commonTest/kotlin/audio/tembra/core/`
- [X] T010 [P] Create `app/shared/data/build.gradle.kts` applying `tembra.kmp.library` with `commonMain` depending on `project(":shared:core")` and `androidMain` on `androidx.activity:activity-compose`-free Android APIs only (no extra libraries), plus the source-set directories `src/commonMain`, `src/androidMain`, `src/iosMain`, `src/commonTest`, `src/iosTest`, `src/androidDeviceTest` under `audio/tembra/data/`
- [X] T011 [P] Write the Catch2 test `app/engine/tests/engine_info_test.cpp` asserting `tembra_engine_abi_version() == TEMBRA_ENGINE_ABI_VERSION` and `TEMBRA_ENGINE_ABI_VERSION == 1u`, and `app/engine/tests/CMakeLists.txt` fetching Catch2 `v3.16.0` with `FetchContent` (pinned `GIT_TAG v3.16.0`), defining executable `tembra_engine_tests`, linking `tembra_engine_core` and `Catch2::Catch2WithMain`, and registering it with `catch_discover_tests` (RED: the build fails because the header and library do not exist yet)
- [X] T012 Create the CMake project: `app/engine/CMakeLists.txt` (`cmake_minimum_required(VERSION 3.22)`, `project(tembra_engine LANGUAGES C CXX)`, C++17, `add_library(tembra_engine_core STATIC src/engine_info.cpp)`, `set_target_properties(tembra_engine_core PROPERTIES OUTPUT_NAME tembra_engine)` (so iOS still emits `libtembra_engine.a`, matching the `.def`), `target_include_directories(tembra_engine_core PUBLIC include)`, `if(ANDROID) find_package(oboe REQUIRED CONFIG); target_link_libraries(tembra_engine_core PUBLIC oboe::oboe) endif()`, `option(TEMBRA_BUILD_TESTS "Build Catch2 tests" OFF)`, `if(TEMBRA_BUILD_TESTS) enable_testing(); add_subdirectory(tests) endif()`), the header `app/engine/include/tembra_engine.h` exactly as in `contracts/engine-c-abi.md` (all four declarations, `TEMBRA_ENGINE_ABI_VERSION 1u`), and `app/engine/src/engine_info.cpp` implementing only `tembra_engine_abi_version()` returning `TEMBRA_ENGINE_ABI_VERSION` (the other three functions are declared but left unimplemented until US4; the test target only references `abi_version`)
- [X] T013 Run the host test build for the engine from the SDK's CMake: `$HOME/Library/Android/sdk/cmake/3.31.6/bin/cmake -S app/engine -B app/engine/build/host -G Ninja -DTEMBRA_BUILD_TESTS=ON && cmake --build app/engine/build/host && ctest --test-dir app/engine/build/host --output-on-failure` (GREEN: 1 test passes); record the exact commands in `app/engine/README.md`
- [X] T014 Create the Android native module `:engine:android`: `app/engine/android/build.gradle.kts` (plugin `com.android.library`; `namespace = "audio.tembra.engine.cinterop"`; `compileSdk`/`minSdk` from the catalog; `ndkVersion = libs.versions.android.ndk.get()`; `externalNativeBuild.cmake { path = file("src/main/cpp/CMakeLists.txt"); version = libs.versions.cmake.get() }`; `defaultConfig.ndk.abiFilters += listOf("arm64-v8a", "x86_64")`; `buildFeatures.prefab = true`; `dependencies { implementation(libs.oboe) }`), `app/engine/android/src/main/AndroidManifest.xml` (empty `<manifest/>`), `app/engine/android/src/main/cpp/CMakeLists.txt` (`add_subdirectory(../../../.. tembra_engine_core_build)` pointing at `app/engine`, which links Oboe itself under `if(ANDROID)` per T012; `add_library(tembra_engine SHARED tembra_jni.cpp)`; `target_link_libraries(tembra_engine PRIVATE tembra_engine_core log)`), and `app/engine/android/src/main/cpp/tembra_jni.cpp` exposing `extern "C" JNIEXPORT jint JNICALL Java_audio_tembra_engine_NativeAudioEngine_nativeAbiVersion(JNIEnv*, jobject)` calling `tembra_engine_abi_version()`
- [X] T015 Create `app/shared/engine/build.gradle.kts` applying `tembra.kmp.library`: `commonMain` depends on `project(":shared:core")` and `kotlinx-coroutines-core`; `androidMain` depends on `project(":engine:android")`; register `Exec` tasks `buildEngineIosArm64` and `buildEngineIosSimulatorArm64` that run the SDK CMake (`$sdkDir/cmake/3.31.6/bin/cmake`) with `-S <repo>/app/engine -B <module>/build/engine/<target> -G Ninja -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 -DCMAKE_OSX_SYSROOT=<iphoneos|iphonesimulator> -DCMAKE_BUILD_TYPE=Release` followed by `cmake --build` (declare `app/engine/include`, `app/engine/src`, `app/engine/CMakeLists.txt` as inputs and the `.a` as output for caching); for each iOS target add `compilations.getByName("main").cinterops.create("tembra_engine") { definitionFile.set(file("src/nativeInterop/cinterop/tembra_engine.def")); packageName("audio.tembra.engine.cinterop"); includeDirs("<repo>/app/engine/include"); extraOpts("-libraryPath", "<module>/build/engine/<target>") }` and make the cinterop task `dependsOn` the matching Exec task; register `engineHostTest` (Exec running the three commands from T013)
- [X] T016 [P] Create `app/shared/engine/src/nativeInterop/cinterop/tembra_engine.def` with `headers = tembra_engine.h`, `headerFilter = tembra_engine.h`, `package = audio.tembra.engine.cinterop`, `staticLibraries = libtembra_engine.a`
- [X] T017 [P] Create `app/shared/ui/build.gradle.kts` applying `tembra.kmp.compose` and `dev.zacsweers.metro`: `commonMain` depends on `project(":shared:core")`, `project(":shared:engine")`, `project(":shared:data")`, `libs.androidx.lifecycle.viewmodel.compose`, `libs.androidx.lifecycle.runtime.compose`, `libs.metrox.viewmodel.compose`, `compose.material3`, no window-size-class artifact (the width bucket is computed from `BoxWithConstraints`, see `WindowWidth.kt`); `androidDeviceTest` depends on `libs.androidx.compose.ui.test.junit4.android`, `libs.androidx.test.runner`, and `androidx.compose.ui:ui-test-manifest` via the debug configuration; `androidHostTest` depends on `libs.kotlinx.serialization.json`; `listOf(iosArm64(), iosSimulatorArm64()).forEach { it.binaries.framework { baseName = "TembraShared"; isStatic = true; freeCompilerArgs += "-Xbinary=strip=true" } }`; `compose.resources { publicResClass = false; packageOfResClass = "audio.tembra.ui.resources" }`; pass the repository root to host tests with `tasks.withType<Test>().configureEach { systemProperty("tembra.repoRoot", rootDir.parentFile.absolutePath) }`
- [X] T018 [P] Create `app/androidApp/build.gradle.kts` applying `tembra.android.app` and `dev.zacsweers.metro`: `namespace = "audio.tembra.android"`, `defaultConfig { applicationId = "audio.tembra.app"; versionCode = 1; versionName = "0.1.0" }`, `buildTypes.release { isMinifyEnabled = false }`, dependencies `project(":shared:ui")`, `libs.androidx.activity.compose`, `libs.metrox.viewmodel.compose`
- [X] T019 Enforce Constitution II dependency direction in `app/build-logic/src/main/kotlin/tembra.kmp.library.gradle.kts`: inside `configurations.configureEach { dependencies.configureEach { … } }` throw `GradleException("':shared:core' must stay pure Kotlin; remove the dependency on <path> (Constitution II)")` when `project.path == ":shared:core"` declares any `ProjectDependency`, and `GradleException("'<path>' must not depend on ':shared:ui' (dependencies point inward)")` when `:shared:engine` or `:shared:data` depends on `:shared:ui`; verify RED by temporarily adding `implementation(project(":shared:data"))` to `app/shared/core/build.gradle.kts` (the build fails with the message), then remove it (GREEN)
- [X] T020 Create the root `app/build.gradle.kts` registering aggregate lifecycle tasks: `checkAndroidHost` (dependsOn every `:shared:*` module's Android host unit-test task, discovered as the `testDebugHostTest`/`testHostTest`-style task the KMP library plugin creates; confirm the exact name with `./gradlew :shared:core:tasks --all | grep -i test` and hard-code it), `checkIos` (dependsOn `:shared:*:iosSimulatorArm64Test`), `checkAndroidDevice` (dependsOn the connected device-test task of each `:shared:*` module, likewise discovered), and `engineHostTest` (dependsOn `:shared:engine:engineHostTest`); each aggregate MUST print the task names it ran in `doLast`
- [X] T021 Verify the foundation compiles for Android: `cd app && ./gradlew :shared:ui:assembleDebug :androidApp:assembleDebug --warning-mode all` completes with `BUILD SUCCESSFUL`, `libtembra_engine.so` exists for `arm64-v8a` and `x86_64` under `app/engine/android/build/intermediates/`, and the output contains zero `warning:` lines; fix configuration until it does
- [X] T022 Verify the foundation compiles for iOS: `cd app && ./gradlew :shared:ui:linkDebugFrameworkIosSimulatorArm64` completes with `BUILD SUCCESSFUL`, `app/shared/engine/build/engine/iosSimulatorArm64/libtembra_engine.a` exists, and `nm` on the framework binary lists `tembra_engine_abi_version`

**Checkpoint**: Both platforms compile from one Gradle invocation each; the C ABI's first function is reachable from Kotlin/Native and via JNI. User story implementation can begin.

---

## Phase 3: User Story 1 - Build and launch on both platforms (Priority: P1) 🎯 MVP

**Goal**: A contributor runs one documented command per platform and the app opens on an Android device or emulator and on the iOS simulator, showing a first version of the placeholder screen (product name only; the full screen arrives in US3).

**Independent Test**: From a fresh clone with prerequisites, `cd app && ./gradlew :androidApp:installDebug && android run --activity audio.tembra.android.MainActivity` shows "Tembra Audio" on the device; `xcodebuild ... build` + `xcrun simctl install/launch` shows the same on the simulator (quickstart §2–§3).

### Implementation for User Story 1

- [X] T023 [P] [US1] Create the Metro graph skeleton in `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/di/AppGraph.kt` (`interface AppGraph : ViewModelGraph` with `val metroViewModelFactory` inherited; no bindings yet) and `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/di/AppBindings.kt` (`@ContributesTo(AppScope::class) @BindingContainer object AppBindings` with no providers yet), following the `kotlinconf-app` pattern recorded in `docs/research/kmp-reference-repos.md`
- [X] T024 [P] [US1] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/App.kt`: `@Composable fun App(appGraph: AppGraph)` that provides `LocalMetroViewModelFactory provides appGraph.metroViewModelFactory` and, for this story, renders `MaterialTheme { Scaffold { Text("Tembra Audio", modifier = Modifier.testTag("placeholder.title")) } }` (replaced by `TembraTheme { PlaceholderScreen() }` in US3)
- [X] T025 [P] [US1] Create the iOS entry point `app/shared/ui/src/iosMain/kotlin/audio/tembra/ui/MainViewController.kt`: `@DependencyGraph(AppScope::class) interface IosAppGraph : AppGraph` (own file `IosAppGraph.kt` in the same package) and `fun MainViewController(): UIViewController = ComposeUIViewController { App(appGraph) }` where `appGraph` is a top-level `private val appGraph = createGraph<IosAppGraph>()`
- [X] T026 [P] [US1] Create the Android app: `app/androidApp/src/main/kotlin/audio/tembra/android/AndroidAppGraph.kt` (`@DependencyGraph(AppScope::class) interface AndroidAppGraph : AppGraph { @DependencyGraph.Factory fun interface Factory { fun create(@Provides context: Context): AndroidAppGraph } }`), `app/androidApp/src/main/kotlin/audio/tembra/android/TembraApplication.kt` (`class TembraApplication : Application()` holding `val appGraph = createGraphFactory<AndroidAppGraph.Factory>().create(this)`), `app/androidApp/src/main/kotlin/audio/tembra/android/MainActivity.kt` (`ComponentActivity`, `enableEdgeToEdge()`, `setContent { App((application as TembraApplication).appGraph) }`), `app/androidApp/src/main/AndroidManifest.xml` (application name, label "Tembra Audio", `MainActivity` exported with MAIN/LAUNCHER intent filter, `android:theme="@style/Theme.Tembra"`, no permissions), `app/androidApp/src/main/res/values/strings.xml`, `app/androidApp/src/main/res/values/themes.xml` (`Theme.Tembra` parent `android:Theme.Material.NoActionBar` with `windowBackground` `#131316` and `android:windowLightStatusBar` `false`), and a minimal adaptive launcher icon under `app/androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` with `drawable/ic_launcher_foreground.xml` and `values/colors.xml` background `#0F0F12`
- [X] T027 [US1] Create the Xcode project from the JetBrains template (Apache-2.0, attribution added in T064): `app/iosApp/Configuration/Config.xcconfig` (`TEAM_ID=`, `BUNDLE_ID=audio.tembra.app`, `APP_NAME=Tembra Audio`), `app/iosApp/iosApp.xcodeproj/project.pbxproj` adapted from `Kotlin/KMP-App-Template` with scheme/target `iosApp`, `IPHONEOS_DEPLOYMENT_TARGET = 16.0`, `ENABLE_USER_SCRIPT_SANDBOXING = NO`, `FRAMEWORK_SEARCH_PATHS = "$(SRCROOT)/../shared/ui/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"`, `OTHER_LDFLAGS = -framework TembraShared`, and the run-script phase `if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then exit 0; fi; cd "$SRCROOT/.."; ./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode`; `app/iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/IDEWorkspaceChecks.plist`; `app/iosApp/iosApp/iOSApp.swift` (`@main struct iOSApp: App { var body: some Scene { WindowGroup { ContentView() } } }`); `app/iosApp/iosApp/ContentView.swift` (`ComposeView: UIViewControllerRepresentable` returning `MainViewControllerKt.MainViewController()`, `ContentView` applying `.ignoresSafeArea()`); `app/iosApp/iosApp/Info.plist` with `CADisableMinimumFrameDurationOnPhone = true`, `UIUserInterfaceStyle = Dark`, `UILaunchScreen = {}`, `UISupportedInterfaceOrientations` for iPhone and iPad; `app/iosApp/iosApp/Assets.xcassets` with an `AppIcon` and `AccentColor` (`#A78BFA`)
- [X] T028 [US1] Add an actionable prerequisite check to `app/build-logic/src/main/kotlin/tembra.kmp.library.gradle.kts` (or a `checkNativeToolchain` task wired before native tasks) that fails configuration with `Missing Android NDK 29.0.14206865 or CMake 3.31.6 under $sdkDir. See app/README.md § Prerequisites` when either directory is absent, and verify by temporarily renaming the NDK directory (spec US1 scenario 3), then restoring it
- [X] T029 [US1] Launch on Android: `cd app && ./gradlew :androidApp:installDebug && android run --activity audio.tembra.android.MainActivity` (or `adb shell am start -n audio.tembra.app/audio.tembra.android.MainActivity`) against the connected phone or emulator; confirm the title renders and no crash appears in `adb logcat`; record the wall-clock time from clean clone in `specs/001-project-scaffold/checklists/requirements.md` notes against SC-001
- [X] T030 [US1] Launch on iOS: `cd app/iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build build` then `xcrun simctl boot "iPhone 17"`, `xcrun simctl install booted "build/Build/Products/Debug-iphonesimulator/Tembra Audio.app"`, `xcrun simctl launch booted audio.tembra.app`; confirm the title renders; fix `Config.xcconfig`/pbxproj until the run-script builds the framework without a manual step

**Checkpoint**: Both apps launch to a titled screen from the documented commands (quickstart §2–§3 true as written for the title).

---

## Phase 4: User Story 2 - Run the automated tests on both platforms (Priority: P1)

**Goal**: One documented Gradle command per lane runs all applicable tests and a seeded failure is reported; the first real shared-logic tests (`SemanticVersion`, `AppDirectories`) exist and pass on the Android host, the Android device and the iOS simulator.

**Independent Test**: `cd app && ./gradlew checkAndroidHost checkIos checkAndroidDevice engineHostTest` all end `BUILD SUCCESSFUL` with non-zero test counts; breaking one assertion makes `checkAndroidHost` and `checkIos` fail with the test name (quickstart §4).

### Tests for User Story 2 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T031 [P] [US2] Write `app/shared/core/src/commonTest/kotlin/audio/tembra/core/version/SemanticVersionTest.kt`: parses `"0.1.0"` to `SemanticVersion(0, 1, 0)`; `toString()` round-trips; ordering `0.9.9 < 1.0.0 < 1.0.1 < 1.1.0`; `parse` rejects `""`, `"1.2"`, `"1.2.3.4"`, `"a.b.c"`, `"-1.0.0"` with `IllegalArgumentException`; `parseOrNull` returns null for the same inputs (RED: class missing)
- [ ] T032 [P] [US2] Write `app/shared/data/src/iosTest/kotlin/audio/tembra/data/AppDirectoriesTest.kt` and `app/shared/data/src/androidDeviceTest/kotlin/audio/tembra/data/AppDirectoriesTest.kt`: `documents` and `cache` are non-empty absolute paths, differ from each other, and the directories exist or can be created; the Android test obtains the `Context` from `InstrumentationRegistry.getInstrumentation().targetContext` (RED: types missing)

### Implementation for User Story 2

- [ ] T033 [P] [US2] Implement `app/shared/core/src/commonMain/kotlin/audio/tembra/core/version/SemanticVersion.kt`: `data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion>` with `require` non-negative components, `override fun toString() = "$major.$minor.$patch"`, `compareTo` by major, minor, patch, and `companion object { fun parse(text: String): SemanticVersion; fun parseOrNull(text: String): SemanticVersion? }` accepting exactly three dot-separated non-negative integers (GREEN for T031)
- [ ] T034 [P] [US2] Implement `AppDirectories`: `app/shared/data/src/commonMain/kotlin/audio/tembra/data/AppDirectories.kt` (`interface AppDirectories { val documents: String; val cache: String }`), `app/shared/data/src/androidMain/kotlin/audio/tembra/data/AndroidAppDirectories.kt` (`class AndroidAppDirectories(context: Context) : AppDirectories` using `context.filesDir` and `context.cacheDir`), `app/shared/data/src/iosMain/kotlin/audio/tembra/data/IosAppDirectories.kt` (`class IosAppDirectories : AppDirectories` using `NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory | NSCachesDirectory, NSUserDomainMask, null, true, null)?.path`) (GREEN for T032)
- [ ] T035 [US2] Bind `AppDirectories` in Metro: add `@Provides @SingleIn(AppScope::class) fun appDirectories(): AppDirectories = IosAppDirectories()` to `app/shared/ui/src/iosMain/kotlin/audio/tembra/ui/IosAppGraph.kt` and `@Provides @SingleIn(AppScope::class) fun appDirectories(context: Context): AppDirectories = AndroidAppDirectories(context)` to `app/androidApp/src/main/kotlin/audio/tembra/android/AndroidAppGraph.kt`; expose `val appDirectories: AppDirectories` on `AppGraph` so the graph compiles the binding on both platforms
- [ ] T036 [US2] Run `cd app && ./gradlew checkAndroidHost` (expects `SemanticVersionTest` green on the JVM) and `./gradlew checkIos` (expects `SemanticVersionTest` and `AppDirectoriesTest` green on the simulator); fix the aggregate task names in `app/build.gradle.kts` (T020) if a module's test task was not picked up, and make `checkIos` boot the default simulator when none is running (Kotlin/Native does this automatically; document any needed `kotlin.native.ios.simulator` property)
- [ ] T037 [US2] Run `cd app && ./gradlew checkAndroidDevice` against the connected phone or emulator (expects `AppDirectoriesTest` green) and `./gradlew engineHostTest` (expects the Catch2 test green); confirm `--warning-mode all` output has zero warnings for all four lanes
- [ ] T038 [US2] Perform the seeded-failure check from quickstart §4: change the expected patch in `SemanticVersionTest` from `0` to `9`, run `checkAndroidHost` and `checkIos`, confirm each reports exactly one failing test named `SemanticVersionTest`, then revert; note the observed output format in `specs/001-project-scaffold/checklists/requirements.md`

**Checkpoint**: Four green lanes, each behind one documented command, on the Android host, the Android device, the iOS simulator and the C++ host.

---

## Phase 5: User Story 4 - Confirm the native audio component is part of the build (Priority: P2)

**Goal**: The engine reports its name, version and compiled libraries through the C ABI; Kotlin exposes it through the `AudioEngine` facade; the link is asserted by tests on the Android device and the iOS simulator; no audio device is touched.

**Independent Test**: `./gradlew :shared:engine:iosSimulatorArm64Test` and the module's connected device test pass `NativeAudioEngineTest`; `engineHostTest` passes the extended Catch2 suite (quickstart §6).

### Tests for User Story 4 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T039 [P] [US4] Extend `app/engine/tests/engine_info_test.cpp`: `tembra_engine_name()` equals `"Tembra Engine"`; `tembra_engine_version()` matches `^\d+\.\d+\.\d+$` and equals `"0.1.0"`; `tembra_engine_describe()` starts with `"Tembra Engine 0.1.0 ("`, ends with `")"`, and contains `"miniaudio 0.11.25"`, `"dr_wav 0.14.5"`, `"signalsmith-stretch 1.1.0"`; on `__ANDROID__` it also contains `"oboe 1.11.0"` (RED: link errors, functions unimplemented)
- [ ] T040 [P] [US4] Write `app/shared/engine/src/commonTest/kotlin/audio/tembra/engine/EngineDescriptorParserTest.kt`: parsing `"Tembra Engine 0.1.0 (oboe 1.11.0; miniaudio 0.11.25; dr_wav 0.14.5; signalsmith-stretch 1.1.0)"` yields `name == "Tembra Engine"`, `version == SemanticVersion(0, 1, 0)`, `compiledLibraries == listOf("oboe 1.11.0", "miniaudio 0.11.25", "dr_wav 0.14.5", "signalsmith-stretch 1.1.0")`, `rawDescription` equals the input; `"Tembra Engine 0.1.0 ()"` yields an empty list; missing `(`, missing `)`, and a non-semantic version each throw `IllegalArgumentException` (RED)
- [ ] T041 [P] [US4] Write `app/shared/engine/src/commonTest/kotlin/audio/tembra/engine/FakeAudioEngineTest.kt`: default descriptor is `Tembra Engine 0.0.0` with empty libraries; a configured descriptor is returned unchanged; `describeCalls` increments per call (RED)
- [ ] T042 [P] [US4] Write `app/shared/engine/src/iosTest/kotlin/audio/tembra/engine/NativeAudioEngineTest.kt` and `app/shared/engine/src/androidDeviceTest/kotlin/audio/tembra/engine/NativeAudioEngineTest.kt`: `NativeAudioEngine().describe()` has `name == "Tembra Engine"`, `version == SemanticVersion(0, 1, 0)`, `compiledLibraries` contains entries starting with `miniaudio`, `dr_wav`, `signalsmith-stretch`, and on Android also `oboe`; calling `describe()` twice returns equal descriptors (RED: actuals missing)

### Implementation for User Story 4

- [ ] T043 [P] [US4] Vendor the header-only libraries with licences: `app/engine/third_party/miniaudio/miniaudio.h` + `LICENSE` from miniaudio 0.11.25, `app/engine/third_party/dr_libs/dr_wav.h` + `LICENSE` from tag `wav-0.14.5`, `app/engine/third_party/signalsmith-stretch/signalsmith-stretch.h` + `dsp/` headers + `LICENSE.txt` from tag 1.1.0; add `app/engine/third_party/README.md` listing each library, version, upstream URL and licence
- [ ] T044 [US4] Implement `app/engine/src/third_party_check.cpp`: defines `MINIAUDIO_IMPLEMENTATION` and `DR_WAV_IMPLEMENTATION` in this single translation unit, includes the three vendored headers, and exposes `const char* tembra_third_party_versions(void)` (internal header `app/engine/src/third_party_versions.h`) returning `"miniaudio " MA_VERSION_STRING "; dr_wav " DRWAV_VERSION_STRING "; signalsmith-stretch 1.1.0"` (verify the exact macro names in the vendored headers; fall back to literal strings if a macro is absent); add the file and `third_party/` include paths to `app/engine/CMakeLists.txt`
- [ ] T045 [US4] Implement `tembra_engine_name`, `tembra_engine_version` and `tembra_engine_describe` in `app/engine/src/engine_info.cpp`: name `"Tembra Engine"`, version `"0.1.0"` (single `TEMBRA_ENGINE_VERSION` macro), describe assembled once into a `static const std::string` as `"<name> <version> (" + platform_libs + third_party + ")"` where `platform_libs` is `"oboe " + oboe::Version::Text + "; "` under `#ifdef __ANDROID__` (include `<oboe/Oboe.h>`; Oboe is already linked into `tembra_engine_core` under `if(ANDROID)` by T012) and empty otherwise; rerun T013's commands (GREEN for T039 on the host, without the oboe entry)
- [ ] T046 [US4] Extend the JNI shim `app/engine/android/src/main/cpp/tembra_jni.cpp` with `nativeName`, `nativeVersion`, `nativeDescribe` returning `env->NewStringUTF(...)` of the three C functions under the class `audio.tembra.engine.NativeAudioEngine`; rebuild `:engine:android` and confirm `nm -D` on `libtembra_engine.so` (arm64-v8a) lists the four `Java_audio_tembra_engine_NativeAudioEngine_native*` symbols
- [ ] T047 [P] [US4] Create the Kotlin facade files in `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine/`: `AudioEngine.kt` (`interface AudioEngine { fun describe(): EngineDescriptor }`), `EngineDescriptor.kt` (`data class EngineDescriptor(val name: String, val version: SemanticVersion, val compiledLibraries: List<String>, val rawDescription: String)` where `name` MUST be exactly `Tembra Engine` for the real engine and `compiledLibraries` is empty on the fake), `EngineDescriptorParser.kt` (`internal object EngineDescriptorParser { fun parse(raw: String): EngineDescriptor }` splitting `"<name> <version> (<libs>)"`, libraries separated by `"; "`, `SemanticVersion.parse` for the version, `IllegalArgumentException` on any malformed input), `FakeAudioEngine.kt` (as in `contracts/audio-engine-facade.md`) (GREEN for T040, T041)
- [ ] T048 [US4] Create `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine/NativeAudioEngine.kt` (`expect class NativeAudioEngine() : AudioEngine`), `app/shared/engine/src/androidMain/kotlin/audio/tembra/engine/NativeAudioEngine.android.kt` (`actual class NativeAudioEngine actual constructor() : AudioEngine` with `private external fun nativeDescribe(): String` (+ `nativeName`, `nativeVersion`, `nativeAbiVersion`), `companion object { init { System.loadLibrary("tembra_engine") } }`, `describe() = EngineDescriptorParser.parse(nativeDescribe())`), and `app/shared/engine/src/iosMain/kotlin/audio/tembra/engine/NativeAudioEngine.ios.kt` (`actual class NativeAudioEngine actual constructor() : AudioEngine` calling `audio.tembra.engine.cinterop.tembra_engine_describe()?.toKString()` and parsing) (GREEN for T042 after T049)
- [ ] T049 [US4] Bind the engine in Metro: add `@Provides @SingleIn(AppScope::class) fun audioEngine(): AudioEngine = NativeAudioEngine()` to `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/di/AppBindings.kt` and `val audioEngine: AudioEngine` to `AppGraph`; run `cd app && ./gradlew :shared:engine:iosSimulatorArm64Test` and `./gradlew checkAndroidDevice` (GREEN for T042 on both platforms) and `./gradlew engineHostTest`
- [ ] T050 [US4] Verify the no-audio constraint: `app/androidApp/src/main/AndroidManifest.xml` and the merged manifest (`app/androidApp/build/intermediates/merged_manifests/debug/AndroidManifest.xml`) contain no `<uses-permission>`; `app/iosApp/iosApp/Info.plist` has no `NSMicrophoneUsageDescription`; `grep -rn "AAudio\|oboe::AudioStream\|AVAudioEngine\|AVAudioSession" app/engine/src` returns nothing but the version reference; record the checks in `specs/001-project-scaffold/checklists/requirements.md`

**Checkpoint**: The native engine is compiled for both platforms, linked, and its descriptor is asserted by tests on the Android device, the iOS simulator and the C++ host.

---

## Phase 6: User Story 3 - See the shared design system rendered (Priority: P2)

**Goal**: The Stitch tokens exist once as `TembraTheme`, verified against `theme.json`; the placeholder screen (title, counter button, engine caption, token preview) adapts to compact and wide windows and supports hover, tooltip and keyboard; screenshots on both platforms are captured and reviewed.

**Independent Test**: Launch either app: swatches match `theme.json`, the counter increments on tap/click/keyboard, the tooltip appears on hover, nothing overlaps at phone and tablet widths; `ThemeJsonParityTest`, `DesignTokenPreviewTest`, `PlaceholderViewModelTest`, `PlaceholderScreenTest` are green (quickstart §5, §7).

### Tests for User Story 3 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T051 [P] [US3] Write `app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/ThemeJsonParityTest.kt`: loads `design/stitch/theme.json` from `System.getProperty("tembra.repoRoot")` with `kotlinx.serialization.json.Json.parseToJsonElement`; for every key in `namedColors` asserts the matching `TembraColorScheme.dark` property (snake_case → camelCase, `on_primary` → `onPrimary`) equals the hex (exact `Color` equality via `toArgb()`); for every key in `dawSemanticColors` except `_note` and `clip_glow` asserts the matching `DawColors` property (`grid_overlay_faint` and `grid_overlay` compare with alpha `0x1F` and `0x33`); for every `typography` entry asserts family name (`Space Grotesk`/`Inter`), `fontSize` px == sp value, `lineHeight` px == sp value, `fontWeight` == `FontWeight(weight)`, and `fontFeatureSettings` contains `tnum` for `tabular-timecode` and `tabular-param`; asserts `rounded` (sm 4, DEFAULT 8, md 12, lg 16, xl 24 dp) and `spacing` (xs 4, sm 8, md 12, lg 20, xl 28, gutter 12, margin 16) and `touchTargetMin == 44`; fails if any file key has no Kotlin counterpart (RED: theme types missing)
- [ ] T052 [P] [US3] Write `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/theme/DesignTokenPreviewTest.kt` using `runComposeUiTest`: inside `TembraTheme { DesignTokenPreview() }` a node with `testTag("swatch.primary")` exists with content description `primary #cebdff`, `swatch.playhead` exists with `playhead #C084FC`, `type.tabularTimecode` exists, and the number of nodes whose tag starts with `swatch.` equals 47 + 16 (RED)
- [ ] T053 [P] [US3] Write `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/placeholder/PlaceholderViewModelTest.kt` with `runTest` and `Dispatchers.setMain(StandardTestDispatcher())`: initial `uiState.value.pressCount == 0`; `onButtonPressed()` three times gives `3`; `uiState.value.engine` equals the `FakeAudioEngine`'s descriptor; the fake's `describeCalls == 1` after construction and unchanged after presses (RED)
- [ ] T054 [P] [US3] Write `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/placeholder/PlaceholderScreenTest.kt` using `runComposeUiTest` on `PlaceholderScreenContent(state, onButtonPressed)` inside `TembraTheme`: `placeholder.title` shows `Tembra Audio`; `placeholder.counter` shows `Pressed 0 times`; after `onNodeWithTag("placeholder.button").performClick()` the callback fired once; with `state.copy(pressCount = 3)` the counter shows `Pressed 3 times`; `placeholder.engineCaption` shows the fake descriptor's `rawDescription`; `placeholder.button` has content description `Count up` and a height/width of at least 44 dp (`assertHeightIsAtLeast(44.dp)`); keyboard: `onNodeWithTag("placeholder.button").requestFocus()` then `performKeyInput { pressKey(Key.Spacebar) }` fires the callback once; hover: `performMouseInput { enter(center); moveTo(center) }` then `mainClock.advanceTimeBy(1_500)` makes `onNodeWithText("Adds one to the counter")` displayed; layout: rendering with `windowWidth = WindowWidth.Compact` yields a node tagged `placeholder.layout.compact` and with `WindowWidth.Expanded` yields `placeholder.layout.wide`; `WindowWidth.fromWidth(599.dp) == Compact`, `fromWidth(600.dp) == Medium`, `fromWidth(840.dp) == Expanded`; integrated: `PlaceholderScreen(viewModel = PlaceholderViewModel(FakeAudioEngine()))` shows `Pressed 1 times` after one click (RED)

### Implementation for User Story 3

- [ ] T055 [P] [US3] Add the fonts: download Space Grotesk 2.0.0 static TTFs `SpaceGrotesk-Medium.ttf`, `SpaceGrotesk-SemiBold.ttf`, `SpaceGrotesk-Bold.ttf` (upstream `fonts/ttf/static/`) and Inter 4.1 static TTFs `Inter-Regular.ttf`, `Inter-Medium.ttf`, `Inter-SemiBold.ttf` (from the `Inter-4.1.zip` release, `extras/ttf/` or equivalent static folder) into `app/shared/ui/src/commonMain/composeResources/font/` (lower-case file names required by Compose resources: `space_grotesk_medium.ttf`, `space_grotesk_semibold.ttf`, `space_grotesk_bold.ttf`, `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`), and their OFL texts into `app/shared/ui/src/commonMain/composeResources/files/OFL-SpaceGrotesk.txt` and `OFL-Inter.txt`
- [ ] T056 [P] [US3] Create the colour token files in `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/`: `TembraColorScheme.kt` (`object TembraColorScheme { val dark: ColorScheme = darkColorScheme(...) }` with all 47 values from data-model §1.1), `DawColors.kt` (`@Immutable data class DawColors` with the 18 properties from data-model §1.2; `gridOverlayFaint = Color(0x1FA855F7)`, `gridOverlay = Color(0x33C084FC)`, `clipGlow = Color(0x40A78BFA)` plus `val clipGlowBlur = 12.dp`; `companion object { val Default }`), `LocalDawColors.kt` (`val LocalDawColors = staticCompositionLocalOf { DawColors.Default }`)
- [ ] T057 [P] [US3] Create the typography, shape and spacing token files in the same package: `TembraFonts.kt` (`object TembraFonts { @Composable fun spaceGrotesk(): FontFamily; @Composable fun inter(): FontFamily }` built from `Font(Res.font.space_grotesk_medium, FontWeight.Medium)` etc.), `TembraTypography.kt` (`object TembraTypography { @Composable fun material(): Typography }` mapping the 12 styles per data-model §1.3 to `headlineLarge/Medium/Small`, `bodyLarge/Medium/Small`, `labelLarge/Medium/Small`), `TembraTypographyExtras.kt` (`@Immutable data class TembraTypographyExtras(val headlineLgMobile: TextStyle, val tabularTimecode: TextStyle, val tabularParam: TextStyle)` with `fontFeatureSettings = "tnum"` on the two tabular styles), `LocalTembraTypographyExtras.kt`, `TembraShapes.kt` (`object TembraShapes { val material = Shapes(extraSmall = RoundedCornerShape(4.dp), small = 8.dp, medium = 12.dp, large = 16.dp, extraLarge = 24.dp) }`), `WindowWidth.kt` (`enum class WindowWidth { Compact, Medium, Expanded }` with `companion object { fun fromWidth(width: Dp): WindowWidth }` using the 600 dp and 840 dp breakpoints and `val isWide: Boolean`), `TembraSpacing.kt` (`@Immutable data class TembraSpacing(val xs: Dp = 4.dp, val sm = 8.dp, val md = 12.dp, val lg = 20.dp, val xl = 28.dp, val gutter = 12.dp, val pageMargin = 16.dp, val touchTargetMin = 44.dp)`), `LocalTembraSpacing.kt`
- [ ] T058 [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/TembraTheme.kt`: `@Composable fun TembraTheme(content)` applying `MaterialTheme(colorScheme = TembraColorScheme.dark, typography = TembraTypography.material(), shapes = TembraShapes.material)` inside `CompositionLocalProvider(LocalDawColors, LocalTembraSpacing, LocalTembraTypographyExtras)`, and `object TembraTheme` with the six read-only accessors from `contracts/design-system-api.md`; run `cd app && ./gradlew checkAndroidHost` (GREEN for T051)
- [ ] T059 [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/theme/DesignTokenPreview.kt`: renders every `ColorScheme` role and every `DawColors` value as a labelled swatch (`Modifier.testTag("swatch.$name").semantics { contentDescription = "$name $hex" }`, 44 dp square with `surfaceLevel2Border` outline, label in `labelSm`) plus one sample line per text style (`testTag("type.$name")`), using `FlowRow` so it wraps into a grid on wide windows and a single column on compact ones; run `./gradlew checkIos` (GREEN for T052)
- [ ] T060 [P] [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/placeholder/PlaceholderUiState.kt` (`data class PlaceholderUiState(val pressCount: Int = 0, val engine: EngineDescriptor)`; `pressCount` MUST never be negative) and `PlaceholderViewModel.kt` (`@Inject @ContributesIntoMap(AppScope::class) @ViewModelKey(PlaceholderViewModel::class) class PlaceholderViewModel(engine: AudioEngine) : ViewModel()` with `private val _uiState = MutableStateFlow(PlaceholderUiState(engine = engine.describe()))`, `val uiState: StateFlow<PlaceholderUiState>`, `fun onButtonPressed() { _uiState.update { it.copy(pressCount = it.pressCount + 1) } }`) (GREEN for T053)
- [ ] T061 [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/placeholder/PlaceholderScreenContent.kt`: stateless composable per `contracts/placeholder-screen.md` with signature `PlaceholderScreenContent(state, onButtonPressed, windowWidth: WindowWidth, modifier)`: `Scaffold` with content padding; `windowWidth == WindowWidth.Compact` selects the single column with `headlineLgMobile` and root `testTag("placeholder.layout.compact")`, otherwise a centred `widthIn(max = 720.dp)` column with `headlineLg` and root `testTag("placeholder.layout.wide")`; title (`placeholder.title`), a row with a Material 3 `Button` labelled `Count up` wrapped in `TooltipBox(PlainTooltip { Text("Adds one to the counter") })` with `Modifier.testTag("placeholder.button").sizeIn(minHeight = spacing.touchTargetMin).semantics { contentDescription = "Count up" }` and the counter text `Pressed $n times` in `tabularParam`-derived style with `testTag("placeholder.counter")` and `stateDescription = "$n"`, the engine caption (`bodySm`, `onSurfaceVariant`, `testTag("placeholder.engineCaption")`), and `DesignTokenPreview` in a `verticalScroll` column (`testTag("placeholder.tokenPreview")`); no literal colours, sizes or fonts outside `TembraTheme` accessors (GREEN for T054)
- [ ] T062 [US3] Create `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/placeholder/PlaceholderScreen.kt` (`@Composable fun PlaceholderScreen(viewModel: PlaceholderViewModel = metroViewModel())` collecting `uiState` with `collectAsStateWithLifecycle()` and delegating to `PlaceholderScreenContent` with `windowWidth = WindowWidth.fromWidth(maxWidth)` measured by a `BoxWithConstraints` root) and replace the body of `App()` in `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/App.kt` with `TembraTheme { PlaceholderScreen() }` inside the `LocalMetroViewModelFactory` provider; rebuild and relaunch on both platforms (T029, T030 commands) and confirm the caption shows the real descriptor
- [ ] T063 [US3] Manual verification and screenshots per quickstart §5 and §7: on the Android device tap the button three times, hover with a mouse or trackpad if available (desktop mode, emulator, or a device with a pointer) to see the tooltip, Tab to the button and press Space, rotate or resize to a wide window (include a 600 dp-wide window where the device or emulator allows it; medium width renders as wide) and once with Android font scale 200 %; on the iOS simulator repeat on iPhone 17 and iPad Pro 13-inch, once with Larger Accessibility Sizes enabled; capture `specs/001-project-scaffold/screenshots/android-compact.png`, `android-wide.png` (`android screen capture --output <file>`), `ios-compact.png`, `ios-wide.png` (`xcrun simctl io booted screenshot <file>`); compare each with `design/stitch/theme.json` and `design/stitch/screens/*/screenshot.jpg`; record any overlap or colour mismatch as a defect and fix before ticking the checklist

**Checkpoint**: The design system is code, verified against its source file and on screen; the placeholder screen is complete on both platforms with screenshots stored.

---

## Phase 7: User Story 5 - Understand where things go (Priority: P3)

**Goal**: A newcomer answers the four "where does X go" questions and runs every command from the README alone; the agent guidance file carries the same commands.

**Independent Test**: Hand `app/README.md` to someone unfamiliar with the project; they locate where a screen, a domain rule, a persistence concern and an engine command belong, and every command in it works when pasted (quickstart §8).

### Implementation for User Story 5

- [ ] T064 [P] [US5] Rewrite `app/README.md`: prerequisites (from quickstart §1, including the `android sdk install` and `xcodebuild -downloadPlatform iOS` commands and the disk-space note), the module map with a "Where does X go?" table (new screen → `:shared:ui` feature package; domain rule → `:shared:core`; persistence → `:shared:data`; engine command → `app/engine/include/tembra_engine.h` then `:shared:engine` facade; platform glue → `androidMain`/`iosMain` only for SDK access), build/run commands for both platforms, the four test-lane commands, the seeded-failure check, the screenshot procedure, the dependency-direction rule, and the coding conventions (one type per file, `internal` by default, tokens only from `TembraTheme`, TDD); credit `Kotlin/KMP-App-Template` (Apache-2.0) as the origin of the Xcode project
- [ ] T065 [P] [US5] Create `app/engine/README.md` describing the CMake project, the C ABI rule (plain C, no callbacks, static storage), how the Android shared library and the iOS static libraries are produced, and the host test commands from T013
- [ ] T066 [US5] Update `CLAUDE.md`: replace the "There is no application code yet" paragraph with the actual layout and record the build, run and test commands (`./gradlew :androidApp:installDebug`, the `xcodebuild` invocation, `checkAndroidHost`, `checkIos`, `checkAndroidDevice`, `engineHostTest`, `android screen capture --output`), keeping the rest of the file unchanged
- [ ] T067 [US5] Dry-run the documentation: from a second clone into a temporary directory, follow `app/README.md` literally on this machine (skipping the already-installed prerequisites) and time it; fix any command that does not work as written; note the elapsed time against SC-001 and the outcome of the four "where does X go" questions against SC-006 in `specs/001-project-scaffold/checklists/requirements.md`

**Checkpoint**: Documentation is sufficient on its own for a fresh contributor.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Licence inventory, hygiene gates and the final end-to-end validation of quickstart.md.

- [ ] T068 [P] Create `app/THIRD_PARTY_LICENSES.md` with one row per dependency (Gradle coordinates or vendored path, version, licence, source URL): Kotlin stdlib and kotlinx libraries, Compose Multiplatform and AndroidX artifacts, Metro, Oboe, miniaudio, dr_wav, Signalsmith Stretch, Catch2 (test-only, BSL-1.0), Space Grotesk and Inter (OFL-1.1), the `Kotlin/KMP-App-Template` Xcode project (Apache-2.0); every runtime row MUST be Apache-2.0, MIT, MIT-0/public domain or OFL-1.1
- [ ] T069 [P] Audit Constitution VII across `app/shared` and `app/androidApp`: run `grep -rln "^\(internal \|public \)\?\(data \|sealed \|enum \|value \)\?\(class\|interface\|object\) " --include=*.kt app/shared app/androidApp` and confirm every file declares exactly one top-level type named after the file; confirm no `import .*\.\*` wildcard imports and no `TODO` without an issue reference; run `grep -rn -E 'Color\(0x|[0-9]\.dp\b|[0-9]\.sp\b|FontFamily\(|FontWeight\([0-9]' --include=*.kt app/shared/ui/src/commonMain/kotlin | grep -v '/ui/theme/'` and require no output (SC-004: no literal visual values outside the theme package); fix violations
- [ ] T070 Run the hygiene gate: `cd app && ./gradlew clean build -x check --warning-mode all` and confirm zero `warning:`/`w:` lines and zero deprecation notices; run `./gradlew checkAndroidHost checkIos checkAndroidDevice engineHostTest` once more from a clean build; confirm the iOS framework is static and stripped (`file` and `nm -U | wc -l` on `TembraShared` compared with an unstripped build)
- [ ] T071 Execute `specs/001-project-scaffold/quickstart.md` sections 1–9 end to end on this machine, recording the wall-clock duration of each of the four test lanes against SC-002, tick every satisfied item in `specs/001-project-scaffold/checklists/requirements.md`, and list any remaining deviation with its reason
- [ ] T072 Commit the feature on branch `001-project-scaffold` in logical groups (setup and build-logic; engine and native wiring; shared modules and tests; UI and design system; apps; docs and licences), each message ending with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`, leaving the working tree clean

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies; T001–T007 can run in parallel, T008 needs all of them
- **Foundational (Phase 2)**: depends on Setup; BLOCKS every user story. Internal order: T009, T010, T011 in parallel → T012 → T013 → T014 → T015 (needs T012 for the header path) with T016, T017, T018 in parallel → T019 (dependency-direction check) → T020 → T021 → T022
- **US1 (Phase 3)**: depends on Foundational; T023–T026 in parallel → T027 → T028 → T029, T030
- **US2 (Phase 4)**: depends on Foundational and on US1's Metro graph files (T023, T025, T026) for T035; tests T031, T032 in parallel → T033, T034 in parallel → T035 → T036 → T037 → T038
- **US4 (Phase 5)**: depends on Foundational and US2's `SemanticVersion` (T033) and lanes (T036, T037); tests T039–T042 in parallel → T043 → T044 → T045 → T046 → T047 → T048 → T049 → T050
- **US3 (Phase 6)**: depends on US4's `AudioEngine`, `EngineDescriptor`, `FakeAudioEngine` (T047) and on US1's `App()` (T024); tests T051–T054 in parallel → T055, T056, T057 in parallel → T058 → T059 → T060 → T061 → T062 → T063
- **US5 (Phase 7)**: depends on US1–US4 being demonstrable so the commands are real; T064, T065 in parallel → T066 → T067
- **Polish (Phase 8)**: depends on all stories; T068, T069 in parallel → T070 → T071 → T072

### User Story Dependencies

- **US1 (P1)**: only Foundational
- **US2 (P1)**: Foundational plus the three Metro graph files from US1 (for the `AppDirectories` binding). Everything else in US2 is independent of US1
- **US4 (P2)**: US2's `SemanticVersion` and the four test lanes
- **US3 (P2)**: US4's facade types and US1's `App()` entry point
- **US5 (P3)**: all previous stories, documentation only

### Within Each User Story

- Test tasks are written first and MUST fail (compile error or assertion) before the implementation task that turns them green; the task text names which test each implementation task satisfies
- Token/model files before the theme and screen that use them; facade types before actuals; actuals before DI bindings; bindings before the app relaunch
- A story is complete when its checkpoint holds on both platforms

### Parallel Opportunities

- Setup: T002 (iOS runtime download, long) alongside T003–T007
- Foundational: T009, T010, T011; then T016, T017, T018 while T015 is written
- US1: T023, T024, T025, T026 are four independent files
- US2: T031 with T032; T033 with T034
- US4: T039, T040, T041, T042 (four test files); T043 with T047
- US3: T051, T052, T053, T054 (four test files); T055, T056, T057 (fonts, colours, typography/shapes/spacing)
- US5: T064 with T065
- Polish: T068 with T069

---

## Parallel Example: User Story 3

```bash
# Write all four US3 tests together (they must fail first):
Task: "ThemeJsonParityTest in app/shared/ui/src/androidHostTest/kotlin/audio/tembra/ui/theme/ThemeJsonParityTest.kt"
Task: "DesignTokenPreviewTest in app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/theme/DesignTokenPreviewTest.kt"
Task: "PlaceholderViewModelTest in app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/placeholder/PlaceholderViewModelTest.kt"
Task: "PlaceholderScreenTest in app/shared/ui/src/commonTest/kotlin/audio/tembra/ui/placeholder/PlaceholderScreenTest.kt"

# Then the three independent token groups together:
Task: "Fonts and OFL files under app/shared/ui/src/commonMain/composeResources/"
Task: "TembraColorScheme.kt, DawColors.kt, LocalDawColors.kt"
Task: "TembraFonts.kt, TembraTypography.kt, TembraTypographyExtras.kt, LocalTembraTypographyExtras.kt, TembraShapes.kt, TembraSpacing.kt, LocalTembraSpacing.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (install NDK/CMake, download the iOS runtime, wrapper, catalog, build-logic)
2. Complete Phase 2: Foundational (six modules, engine with `tembra_engine_abi_version`, both platforms compile)
3. Complete Phase 3: US1 (apps launch to the title on device/emulator and simulator)
4. **STOP and VALIDATE**: quickstart §2–§3 for the title screen
5. Demo: the first Kotlin Multiplatform build of Tembra Audio running on both platforms

### Incremental Delivery

1. Setup + Foundational → both platforms compile with the native library linked
2. US1 → apps launch (MVP)
3. US2 → four green test lanes and a proven red-to-green loop
4. US4 → engine descriptor through JNI and cinterop, asserted on device and simulator
5. US3 → design system as code, parity-tested, placeholder screen with screenshots
6. US5 → README and CLAUDE.md complete
7. Polish → licences, hygiene gate, quickstart run-through, commits

### Parallel Team Strategy

With two people after Foundational: one takes US1 then US3 (apps and UI), the other takes US2 then US4 (test lanes and engine). The hand-off points are T023–T026 (graph files, needed by T035) and T047 (facade types, needed by T060).

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Every test task states the assertions; every implementation task names the test it makes green
- Exact Gradle task names produced by the Android KMP library plugin (host/device test tasks) are discovered in T020 and then hard-coded into the aggregate tasks so the documented commands never change
- Commit after each checkpoint at minimum (T072 lists the groups)
- Avoid: literal colours or sizes outside `audio.tembra.ui.theme`, nested public types, Kotlin tests linking the real engine outside `iosTest`/`androidDeviceTest`, any dependency not in `libs.versions.toml`
