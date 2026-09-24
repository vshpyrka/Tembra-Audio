// Convention plugin for the Android native build-helper module (:engine:android): a plain Android
// library whose only job is to compile the C++ engine with CMake/NDK (Constitution II allows such
// modules below :shared:engine). Prefab is enabled so Oboe is consumed from Google Maven.
import java.util.Properties

plugins {
    id("com.android.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun catalogVersion(alias: String): String = libs.findVersion(alias).get().requiredVersion

// Actionable prerequisite check (spec US1 scenario 3): fail early with the fix instead of an NDK stack trace.
val sdkDir: File = run {
    val local = rootProject.file("local.properties")
    val fromLocal = if (local.exists()) Properties().apply { local.inputStream().use { load(it) } }.getProperty("sdk.dir") else null
    File(fromLocal ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Android SDK not found: set sdk.dir in app/local.properties or ANDROID_HOME. See app/README.md § Prerequisites"))
}
val requiredNdk = catalogVersion("android-ndk")
val requiredCmake = catalogVersion("cmake")
val missing = listOfNotNull(
    "NDK $requiredNdk".takeUnless { sdkDir.resolve("ndk/$requiredNdk").isDirectory },
    "CMake $requiredCmake".takeUnless { sdkDir.resolve("cmake/$requiredCmake").isDirectory },
)
if (missing.isNotEmpty()) {
    throw GradleException(
        "Missing Android ${missing.joinToString(" and ")} under $sdkDir. " +
            "Run: android sdk install \"ndk;$requiredNdk\" \"cmake;$requiredCmake\" (see app/README.md § Prerequisites)"
    )
}

android {
    compileSdk = catalogVersion("android-compileSdk").toInt()
    ndkVersion = requiredNdk

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
