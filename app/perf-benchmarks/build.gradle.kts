// :perf-benchmarks — Jetpack Macrobenchmark journeys over the app's perf anchors (feature 007, D2).
// Included in the build only with `-Ptembra.perf=true` (settings.gradle.kts); depends on nothing in the
// module graph and nothing depends on it. Build: `./gradlew -Ptembra.perf=true :perf-benchmarks:assemblePerf`.
// Since feature 008 the module takes `com.android.test` and the baseline-profile producer (D1) from the
// `tembra.android.benchmarks` convention plugin: `BaselineProfileGenerator` drives the journeys on the connected
// device named by ANDROID_SERIAL when `:androidApp:generateBaselineProfile` runs.
plugins {
    id("tembra.android.benchmarks")
}

android {
    namespace = "audio.tembra.perf"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Suppress the emulator and debuggable warnings only where the pipeline runs on real devices.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        // Mirrors the app's `perf` build type (release optimisations, profileable, debug signing).
        create("perf") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        // The release baseline for SC-007 (`tracepulse run --build-type release`); a test module has no
        // release type of its own in AGP 9, so it is created here.
        maybeCreate("release").apply {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    targetProjectPath = ":androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
    beforeVariants { variant ->
        // nonMinifiedRelease is the profile-generation variant the plugin pairs with the app's (feature 008).
        variant.enable = variant.buildType in setOf("perf", "release", "nonMinifiedRelease")
    }
}

// No managed device: generation runs on the attached Pixel (API 33+), selected through ANDROID_SERIAL (D7).
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.tracing.ktx)
}
