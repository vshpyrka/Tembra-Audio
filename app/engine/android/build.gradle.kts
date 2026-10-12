// :engine:android — builds the C++ engine for Android with CMake/NDK and wraps it in libtembra_engine.so
// together with the JNI shim. Google's KMP library plugin has no externalNativeBuild, hence this module.
// Consumed only by :shared:engine's androidMain.
plugins {
    id("tembra.android.native")
}

android {
    namespace = "audio.tembra.engine.android"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    // Measurement builds (feature 007): `-Ptembra.perf=true` compiles the tracing facade into the
    // engine (`=release`, the SC-007 baseline, does not). The property, not the app's `perf` build
    // type, drives it because :shared:engine (a single-variant KMP library) sits between this module
    // and the app.
    if (providers.gradleProperty("tembra.perf").orNull == "true") {
        defaultConfig.externalNativeBuild.cmake.arguments += "-DTEMBRA_TRACING=ON"
    }
}

dependencies {
    implementation(libs.oboe)
}
