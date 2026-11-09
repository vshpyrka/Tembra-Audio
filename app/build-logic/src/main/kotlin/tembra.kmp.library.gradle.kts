// Convention plugin for every Kotlin Multiplatform module under app/shared.
// Targets: Android (com.android.kotlin.multiplatform.library, single variant), iosArm64, iosSimulatorArm64.
// Test lanes: androidHostTest (JVM), androidDeviceTest (connected device), iosSimulatorArm64Test.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("tembra.kotlin.warnings")
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
        // Library device-test APKs carry libtembra_engine.so; keep symbols instead of stripping (no NDK needed here).
        packaging {
            jniLibs.keepDebugSymbols.add("**/*.so")
        }
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
        // The instrumentation runner configured above must ship inside every device-test APK.
        matching { it.name == "androidDeviceTest" }.configureEach {
            dependencies {
                implementation(libs.findLibrary("androidx-test-runner").get())
            }
        }
    }

    jvmToolchain(21)
}

// Constitution II: dependencies point inward toward :shared:core. A violation fails configuration. The guard is on
// project dependencies: `:shared:core` may use the two Kotlin-only libraries the constitution's stack names for the
// document model (kotlinx.serialization, kotlinx.collections.immutable; feature 009), never a project, Compose or a
// platform API.
val declaredSuffixes = listOf("Implementation", "Api", "CompileOnly", "RuntimeOnly")
configurations.matching { c -> declaredSuffixes.any { c.name.endsWith(it) } && !c.name.contains("swiftPM", ignoreCase = true) }.configureEach {
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
// `-Ptembra.iosSimulator="<simctl device name or UDID>"` runs the iOS lane on that simulator
// (for example the iPad Pro 13-inch); without it the Kotlin plugin picks its default iPhone.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    val simulator = providers.gradleProperty("tembra.iosSimulator")
    if (simulator.isPresent) device.set(simulator)
    // Timing tests widen their bounds on GitHub's shared runners: the flag reaches the simulator process (the Kotlin
    // plugin runs the binary through `simctl spawn`, which forwards only SIMCTL_CHILD_-prefixed variables); `-Ptembra.ci=true` rehearses it locally.
    val ci = providers.gradleProperty("tembra.ci").orNull ?: System.getenv("GITHUB_ACTIONS")
    if (!ci.isNullOrEmpty()) {
        environment("GITHUB_ACTIONS", ci)
        environment("SIMCTL_CHILD_GITHUB_ACTIONS", ci)  // `simctl spawn` hands SIMCTL_CHILD_-prefixed variables to the process
    }
}
tasks.register("checkIos") {
    group = "verification"
    description = "Runs this module's tests on the iOS simulator (override with -Ptembra.iosSimulator)"
    dependsOn(tasks.matching { it.name == "iosSimulatorArm64Test" })
}
tasks.register("checkAndroidDevice") {
    group = "verification"
    description = "Runs this module's instrumented tests on the connected Android device"
    dependsOn(tasks.matching { it.name.startsWith("connected") && it.name.contains("DeviceTest") })
}
