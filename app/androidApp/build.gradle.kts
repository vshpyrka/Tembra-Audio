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
        release {
            isMinifyEnabled = false
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

androidComponents {
    beforeVariants { variant ->
        // The plugin adds nonMinified<Type> and benchmark<Type> for every non-debuggable type. Only
        // nonMinifiedRelease is used (the generation target, D1); the rest would just configure.
        if (variant.buildType in setOf("benchmarkRelease", "benchmarkPerf", "nonMinifiedPerf")) {
            variant.enable = false
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
    // Writes the shipped baseline profile into ART's profile directory on the first launch, so a direct
    // `adb install` benefits (feature 008, FR-006) and the comparison's Partial(Require) state can rely on it (D4).
    implementation(libs.androidx.profileinstaller)
    // AndroidMicrophonePermissionTest (feature 003) and the feature 004 host and picker tests are instrumented tests of this module.
    androidTestImplementation(libs.androidx.test.runner)
    // AndroidBackgroundPlaybackHostTest (feature 004): ServiceTestRule binds the metronome foreground service.
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.core)
    // The producer of the profile is the Macrobenchmark module, which joins the build only for measurement
    // runs (settings.gradle.kts); the generation task exists only then (feature 008, D1).
    if (providers.gradleProperty("tembra.perf").isPresent) {
        baselineProfile(project(":perf-benchmarks"))
    }
}
