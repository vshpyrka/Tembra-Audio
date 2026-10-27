// :androidApp — thin Android shell: Application (Metro graph) and MainActivity (Compose host).
plugins {
    id("tembra.android.app")
}

android {
    namespace = "audio.tembra.android"
    defaultConfig {
        applicationId = "audio.tembra.app"
        versionCode = 1
        versionName = "0.1.0"
    }
    // BuildConfig.DEBUG gates the developer-view launch argument (feature 002, FR-022).
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        // Shrunk and obfuscated since feature 008 (spec FR-005a, D3): R8 is what lays the startup profile's classes
        // out together, and it makes the app smaller. Keep rules live in proguard-rules.pro, each with its reason.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Measurement build (feature 007, D3): release optimisations, profileable, debug signing so the
        // pipeline can install it fresh. Macrobenchmark (:perf-benchmarks) targets this type; build it with
        // `-Ptembra.perf=true` so the generated PerfBuild/PerfNames and the engine's TEMBRA_TRACING match.
        create("perf") {
            initWith(getByName("release"))
            isProfileable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

// Baseline profile consumer (feature 008, contracts/build-config.md): generation is on demand
// (`uv run tracepulse profile generate`), never during a build; the two files are committed; one copy
// in src/main serves every variant; no filter, so the framework rules the journeys exercise stay in.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    mergeIntoMain = true
    warnings {
        // The three variants below are disabled on purpose; the plugin need not say so on every build.
        disabledVariants = false
    }
}

// A release-like build never ships without a committed profile (feature 008, FR-006, D9): the check runs before
// the ART-profile merge of the `release` and `perf` variants only — never for nonMinifiedRelease, which is the
// generation build that must succeed before any profile exists, and never for debug.
val checkBaselineProfileCommitted = tasks.register("checkBaselineProfileCommitted") {
    group = "verification"
    description = "Fails when src/main/generated/baselineProfiles/baseline-prof.txt is missing or has no rule"
    val profile = layout.projectDirectory.file("src/main/generated/baselineProfiles/baseline-prof.txt")
    inputs.files(profile).withPropertyName("baselineProfile")
    doLast {
        val file = profile.asFile
        val hasRule = file.isFile && file.useLines { lines -> lines.any { it.isNotBlank() && !it.startsWith("#") } }
        if (!hasRule) {
            throw GradleException(
                "no committed baseline profile: run `uv run tracepulse profile generate --device <id>` from app/perf " +
                    "(expected a rule in $file)"
            )
        }
    }
}

// The coverage test reads these two files at run time; without them as inputs Gradle serves the test from the
// cache when only a rule or the profile changed (seen 2026-10-12: a rule pointed at a missing class and the lane
// stayed green), which would make the check a no-op in the lane and in `profile generate`.
tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }.configureEach {
    inputs.files(
        layout.projectDirectory.file("baseline-profile-coverage.txt"),
        layout.projectDirectory.file("src/main/generated/baselineProfiles/baseline-prof.txt"),
    ).withPropertyName("baselineProfileCoverageInputs").withPathSensitivity(PathSensitivity.RELATIVE)
}

androidComponents {
    beforeVariants { variant ->
        // The plugin adds nonMinified<Type> and benchmark<Type> for every non-debuggable type. Only
        // nonMinifiedRelease is used (the generation target, D1); the rest would just configure.
        if (variant.buildType in setOf("benchmarkRelease", "benchmarkPerf", "nonMinifiedPerf")) {
            variant.enable = false
        }
    }
    onVariants { variant ->
        if (variant.buildType in setOf("release", "perf")) {
            val mergeTask = "merge${variant.name.replaceFirstChar { it.uppercase() }}ArtProfile"
            tasks.matching { it.name == mergeTask }.configureEach { dependsOn(checkBaselineProfileCommitted) }
        }
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(project(":shared:engine"))
    implementation(project(":shared:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.metrox.viewmodel.compose)
    // Metro generates AndroidAppGraph here, and the shared AppBindings expose the one DataStore<Preferences> (feature 004).
    implementation(libs.androidx.datastore.preferences.core)
    // AndroidMicrophonePermission suspends on the activity result (feature 003, research R6).
    implementation(libs.kotlinx.coroutines.core)
    // AndroidAudioFilePicker hands the importer an Okio source over the picked document (feature 009, research R11).
    implementation(libs.okio)
    // Writes the shipped baseline profile into ART's profile directory on the first launch, so a direct
    // `adb install` benefits (feature 008, FR-006) and the comparison's Partial(Require) state can rely on it (D4).
    implementation(libs.androidx.profileinstaller)
    // AndroidMicrophonePermissionTest (feature 003) and the feature 004 host and picker tests are instrumented tests of this module.
    androidTestImplementation(libs.androidx.test.runner)
    // AndroidBackgroundPlaybackHostTest (feature 004): ServiceTestRule binds the metronome foreground service.
    androidTestImplementation(libs.androidx.test.rules)
    // AndroidMicrophonePermissionTest answers the system's microphone prompt where the platform refuses the runner's
    // grant (ColorOS without "Disable permission monitoring", 2026-10-16).
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    // BaselineProfileCoverageTest (feature 008, FR-011): a JVM test over two text files of this module. The JUnit 4
    // variant is named because the Kotlin plugin's framework inference covers KMP commonTest, not a plain Android
    // module's unit tests (`kotlin.test.Test` stayed unresolved with `kotlin-test` alone).
    testImplementation(libs.kotlin.test.junit)
    // The producer of the profile is the Macrobenchmark module, which joins the build only for measurement
    // runs (settings.gradle.kts); the generation task exists only then (feature 008, D1).
    if (providers.gradleProperty("tembra.perf").isPresent) {
        baselineProfile(project(":perf-benchmarks"))
    }
}
