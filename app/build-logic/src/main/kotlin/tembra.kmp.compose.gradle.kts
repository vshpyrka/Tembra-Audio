// Convention plugin for shared modules that contain Compose Multiplatform UI.
plugins {
    id("tembra.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.zacsweers.metro")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = libs.findLibrary(alias).get()

kotlin {
    // Compose resources are packaged as Android assets. AGP's KMP library plugin (9.3.3) exposes an assets source to
    // the Compose plugin only when Android resources are enabled; without this the module's fonts, drawables and files
    // never reach the app or the device-test APK (found in feature 002; feature 001's fonts fell back silently).
    android {
        androidResources {
            enable = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(lib("compose-runtime"))
            implementation(lib("compose-foundation"))
            implementation(lib("compose-material3"))
            implementation(lib("compose-ui"))
            implementation(lib("compose-components-resources"))
        }
        // Compose UI tests need a real UI platform: they live in `uiTest`, which feeds the iOS simulator and the
        // Android device lanes but not the JVM host lane.
        val uiTest = create("uiTest")
        uiTest.dependsOn(commonTest.get())
        uiTest.dependencies {
            implementation(lib("compose-ui-test"))
        }
        iosTest.get().dependsOn(uiTest)
        matching { it.name == "androidDeviceTest" }.configureEach { dependsOn(uiTest) }
    }
}
