// Convention plugin for the thin Android application module.
plugins {
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.zacsweers.metro")
    // Baseline profile consumer (feature 008, D1): applied here because the plugin compiles against AGP and
    // must load in the same classloader; the module's build script holds the `baselineProfile { }` configuration.
    id("androidx.baselineprofile")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun catalogVersion(alias: String): String = libs.findVersion(alias).get().requiredVersion

android {
    compileSdk = catalogVersion("android-compileSdk").toInt()
    ndkVersion = catalogVersion("android-ndk")
    defaultConfig {
        minSdk = catalogVersion("android-minSdk").toInt()
        targetSdk = catalogVersion("android-targetSdk").toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// The app module's host lane (feature 008, FR-011): its JVM unit tests, which hold the baseline-profile coverage
// check; the root `checkAndroidHost` aggregates it beside the shared modules'. Matched by pattern like the KMP
// convention's registration, so the documented command survives a task rename.
tasks.register("checkAndroidHost") {
    group = "verification"
    description = "Runs this module's Android host (JVM) unit tests"
    dependsOn(tasks.withType<Test>().matching { it.name == "testDebugUnitTest" })
}
