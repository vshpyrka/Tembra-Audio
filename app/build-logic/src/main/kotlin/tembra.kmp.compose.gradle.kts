// Convention plugin for shared modules that contain Compose Multiplatform UI.
@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.compose.ComposeExtension

plugins {
    id("tembra.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.zacsweers.metro")
}

val composeDeps = extensions.getByType<ComposeExtension>().dependencies

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(composeDeps.runtime)
            implementation(composeDeps.foundation)
            implementation(composeDeps.material3)
            implementation(composeDeps.ui)
            implementation(composeDeps.components.resources)
        }
        // Compose UI tests need a real UI platform: they live in `uiTest`, which feeds the iOS simulator and the
        // Android device lanes but not the JVM host lane.
        val uiTest by creating {
            dependsOn(commonTest.get())
            dependencies {
                implementation(composeDeps.uiTest)
            }
        }
        iosTest.get().dependsOn(uiTest)
        matching { it.name == "androidDeviceTest" }.configureEach { dependsOn(uiTest) }
    }
}
