// Convention plugin for the Macrobenchmark test module (:perf-benchmarks, feature 007): the test plugin and the
// baseline-profile producer (feature 008) come from this build's classpath, so they share one AGP instance with
// the app module's convention plugin. A plugin that compiles against AGP cannot be applied from a module script
// here (it would load in the root classloader, where AGP is absent); the module's own configuration stays in its
// build script.
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
    id("tembra.kotlin.warnings")
}
