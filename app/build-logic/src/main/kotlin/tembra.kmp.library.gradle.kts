// Convention plugin for every Kotlin Multiplatform module under app/shared.
// Targets: Android (com.android.kotlin.multiplatform.library, single variant), iosArm64, iosSimulatorArm64.
// Test lanes: androidHostTest (JVM), androidDeviceTest (connected device), iosSimulatorArm64Test.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun catalogVersion(alias: String): String = libs.findVersion(alias).get().requiredVersion

// :shared:core -> audio.tembra.core, :shared:ui -> audio.tembra.ui
val moduleNamespace = "audio.tembra" + project.path.removePrefix(":shared").replace(':', '.')

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = moduleNamespace
        compileSdk = catalogVersion("android-compileSdk").toInt()
        minSdk = catalogVersion("android-minSdk").toInt()
        compilerOptions.jvmTarget = JvmTarget.JVM_17
        withHostTestBuilder {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.findLibrary("kotlin-test").get())
            implementation(libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }

    jvmToolchain(21)
}

// Constitution II: dependencies point inward toward :shared:core. A violation fails configuration.
configurations.configureEach {
    dependencies.configureEach {
        if (this is ProjectDependency && this.path != project.path) {
            val depPath = this.path
            if (project.path == ":shared:core") {
                throw GradleException(
                    "':shared:core' must stay pure Kotlin; remove the dependency on '$depPath' (Constitution II)"
                )
            }
            if ((project.path == ":shared:engine" || project.path == ":shared:data") && depPath == ":shared:ui") {
                throw GradleException(
                    "'${project.path}' must not depend on ':shared:ui' (dependencies point inward, Constitution II)"
                )
            }
        }
    }
}

// Per-module lane tasks; the root build aggregates them. Names of the underlying AGP/KGP test tasks
// are matched by pattern so the documented commands never change when a plugin renames a task.
tasks.register("checkAndroidHost") {
    group = "verification"
    description = "Runs this module's Android host (JVM) unit tests"
    dependsOn(tasks.withType<Test>().matching { it.name.contains("HostTest", ignoreCase = true) })
}
tasks.register("checkIos") {
    group = "verification"
    description = "Runs this module's tests on the iOS simulator"
    dependsOn(tasks.matching { it.name == "iosSimulatorArm64Test" })
}
tasks.register("checkAndroidDevice") {
    group = "verification"
    description = "Runs this module's instrumented tests on the connected Android device"
    dependsOn(tasks.matching { it.name.startsWith("connected") && it.name.contains("DeviceTest") })
}
