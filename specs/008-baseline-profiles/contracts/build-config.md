# Contract: Gradle configuration for baseline profiles and shrinking

What each build file must end up expressing. Exact DSL is the implementer's; the observable behaviour is the contract.

## `app/gradle/libs.versions.toml`

- `androidx-profileinstaller = "1.4.1"` and `androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", … }`.
- Plugin alias `baselineprofile = { id = "androidx.baselineprofile", version.ref = "androidx-benchmark" }` (the plugin ships on the benchmark train; 1.5.0 is already the pinned version).

## `app/androidApp/build.gradle.kts`

- Applies `androidx.baselineprofile` (consumer) unconditionally (D1; fallback recorded there).
- `buildTypes.release`: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`. `perf` keeps `initWith(getByName("release"))` and therefore inherits all three.
- `baselineProfile { automaticGenerationDuringBuild = false; saveInSrc = true; mergeIntoMain = true; warnings { disabledVariants = false } }` — no `filter`.
- `androidComponents.beforeVariants`: disable the variants whose build type is `benchmarkRelease`, `benchmarkPerf` or `nonMinifiedPerf`; keep `nonMinifiedRelease` (the generation target), `release`, `perf`, `debug`.
- `dependencies { implementation(libs.androidx.profileinstaller) }`; under `providers.gradleProperty("tembra.perf").isPresent`: `baselineProfile(project(":perf-benchmarks"))`.
- Task `checkBaselineProfileCommitted`: fails when `src/main/generated/baselineProfiles/baseline-prof.txt` is missing or has no rule line, message naming `uv run tracepulse profile generate --device <id>`; wired through `androidComponents.onVariants` as a dependency of `merge<Variant>ArtProfile` for build types `release` and `perf` only.
- No other change to the module (the Metro graph, the activity and the services are untouched).

## `app/androidApp/proguard-rules.pro`

- Starts with a header comment stating the policy: a rule is added only when a device run on the shrunk build fails, with a comment naming the code path and the failing symptom; nothing speculative.
- Known candidates if a run fails (documented in the header, not pre-added): kotlinx.serialization keep rules for `audio.tembra.**` serializable classes; Room's `_Impl` constructors if the KMP constructor path falls back to reflection on Android.

## `app/perf-benchmarks/build.gradle.kts`

- Applies `androidx.baselineprofile` (producer): `baselineProfile { useConnectedDevices = true }` (no managed devices).
- `beforeVariants` enables `nonMinifiedRelease` in addition to `perf` and `release` (the plugin needs the matching test variant for generation).
- Dependencies unchanged (Macrobenchmark already carries `BaselineProfileRule`).

## `app/build-logic/src/main/kotlin/tembra.android.app.gradle.kts`

- Registers `checkAndroidHost` (group `verification`) depending on `testDebugUnitTest`, mirroring the KMP library convention's pattern-matched registration.

## `app/build.gradle.kts`

- `checkAndroidHost` additionally depends on `:androidApp:checkAndroidHost` (the other two lanes unchanged).

## Observable behaviour (what the quickstart checks)

1. `./gradlew :androidApp:assembleDebug` runs no `*ArtProfile*`, `*BaselineProfile*` or R8 task.
2. `./gradlew :androidApp:assembleRelease` fails with the generation message when the profile file is absent, and with it present produces an APK containing `assets/dexopt/baseline.prof`, with `classes*.dex` laid out by R8 (startup profile) and no shrinker warning in the log.
3. `./gradlew -Ptembra.perf=true :androidApp:assemblePerf` produces the same profile-carrying, shrunk APK (debug-signed, profileable) that `tracepulse run` installs.
4. `./gradlew -Ptembra.perf=true :androidApp:generateBaselineProfile` with `ANDROID_SERIAL` set runs only the `BaselineProfileRule` test on that device and writes the two files.
5. `./gradlew checkAndroidHost` runs `BaselineProfileCoverageTest`.
6. `./gradlew tasks --all` without `-Ptembra.perf` does not list `:perf-benchmarks` tasks (unchanged).
