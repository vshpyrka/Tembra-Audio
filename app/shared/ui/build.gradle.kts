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
            implementation(libs.androidx.test.runner)
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
