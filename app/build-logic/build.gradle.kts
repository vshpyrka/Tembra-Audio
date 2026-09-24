plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.metro.gradle.plugin)
}

// Gradle generates type-safe accessors for every task type of the applied plugins, including Metro's graph
// analysis tasks, whose declarations carry Metro's opt-in markers; opting in keeps the build-logic compile
// warning-free. Convention plugins never call those APIs themselves.
kotlin {
    compilerOptions {
        optIn.add("dev.zacsweers.metro.gradle.DelicateMetroGradleApi")
        optIn.add("dev.zacsweers.metro.gradle.DangerousMetroGradleApi")
        optIn.add("dev.zacsweers.metro.gradle.RequiresIdeSupport")
    }
}
