// Convention plugin for the Android native build-helper module (:engine:android): a plain Android
// library whose only job is to compile the C++ engine with CMake/NDK (Constitution II allows such
// modules below :shared:engine). Prefab is enabled so Oboe is consumed from Google Maven.
plugins {
    id("com.android.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun catalogVersion(alias: String): String = libs.findVersion(alias).get().requiredVersion

android {
    compileSdk = catalogVersion("android-compileSdk").toInt()
    ndkVersion = catalogVersion("android-ndk")

    defaultConfig {
        minSdk = catalogVersion("android-minSdk").toInt()
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            version = catalogVersion("cmake")
        }
    }

    buildFeatures {
        prefab = true
    }
}
