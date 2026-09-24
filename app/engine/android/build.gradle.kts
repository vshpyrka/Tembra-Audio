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
}

dependencies {
    implementation(libs.oboe)
}
