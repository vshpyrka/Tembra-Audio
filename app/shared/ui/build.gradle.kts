// :shared:ui — Compose Multiplatform screens, ViewModels, Metro graph and the iOS framework "TembraShared".
plugins {
    id("tembra.kmp.compose")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "TembraShared"
            isStatic = true
            binaryOption("bundleId", "audio.tembra.shared")
            // Size optimisation for shipped binaries only; Kotlin/Native ignores it for debug builds.
            if (buildType == org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE) {
                binaryOption("smallBinary", "true")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:engine"))
            implementation(project(":shared:data"))
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.metrox.viewmodel.compose)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.compose.ui.test.junit4.android)
            implementation(libs.androidx.compose.ui.test.manifest)
            // Compose UI tests idle through Espresso; older Espresso uses a hidden InputManager API removed in Android 17.
            implementation(libs.androidx.test.espresso.core)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "audio.tembra.ui.resources"
}

// ThemeJsonParityTest reads design/stitch/theme.json from the repository root.
tasks.withType<Test>().configureEach {
    systemProperty("tembra.repoRoot", rootDir.parentFile.absolutePath)
}

// Compose Multiplatform 1.12.1 registers this task for the device-test component but never sets its output
// (that component has no assets source); the main variant's resources reach the test APK through the library.
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }.configureEach { enabled = false }
