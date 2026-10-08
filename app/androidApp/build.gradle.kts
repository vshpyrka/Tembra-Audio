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
    // AndroidMicrophonePermissionTest (feature 003) and the feature 004 host and picker tests are instrumented tests of this module.
    androidTestImplementation(libs.androidx.test.runner)
    // AndroidBackgroundPlaybackHostTest (feature 004): ServiceTestRule binds the metronome foreground service.
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
