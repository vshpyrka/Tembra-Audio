plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.metro.gradle.plugin)
    // The Baseline Profile plugin must share AGP's classloader (feature 008); the modules apply it by id.
    implementation(libs.baselineprofile.gradle.plugin)
}

// Gradle generates type-safe accessors for every task type of the applied plugins, including Metro's graph
// analysis tasks, which are marked @ExperimentalMetroGradleApi; opting in keeps the build-logic compile
// warning-free. Convention plugins never call those APIs themselves.
kotlin {
    compilerOptions {
        optIn.add("dev.zacsweers.metro.gradle.ExperimentalMetroGradleApi")
    }
}
