// :shared:engine — the AudioEngine facade over the C++ engine (JNI on Android, cinterop on iOS).
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.CInteropProcess
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.util.Properties

plugins {
    id("tembra.kmp.library")
}

// --- Native toolchain (the Android SDK's CMake builds the engine for Android, iOS and the host) ---
val sdkDir: File = run {
    val local = rootProject.file("local.properties")
    val fromLocal = if (local.exists()) Properties().apply { local.inputStream().use { load(it) } }.getProperty("sdk.dir") else null
    File(fromLocal ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Android SDK not found: set sdk.dir in app/local.properties or ANDROID_HOME. See app/README.md § Prerequisites"))
}
val cmakeVersion = libs.versions.cmake.get()
val cmakeDir = sdkDir.resolve("cmake/$cmakeVersion/bin")
val cmakeBin = cmakeDir.resolve("cmake")
val ninjaBin = cmakeDir.resolve("ninja")
val ctestBin = cmakeDir.resolve("ctest")
val engineSourceDir = rootDir.resolve("engine")

fun registerEngineBuild(target: String, sysroot: String): TaskProvider<Exec> {
    val outDir = layout.buildDirectory.dir("engine/$target")
    // Copy every value into locals: the task's closures must not capture the build script object
    // (Gradle configuration cache requirement).
    val cmake = cmakeBin.absolutePath
    val ninja = ninjaBin.absolutePath
    val source = engineSourceDir.absolutePath
    val sdk = sdkDir.absolutePath
    val version = cmakeVersion
    return tasks.register<Exec>("buildEngine${target.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Builds libtembra_engine.a for $target with CMake"
        inputs.dir("$source/include")
        inputs.dir("$source/src")
        inputs.file("$source/CMakeLists.txt")
        outputs.file(outDir.map { it.file("libtembra_engine.a") })
        val out = outDir.get().asFile.absolutePath
        doFirst {
            check(File(cmake).exists()) {
                "Missing CMake $version under $sdk. Run: android sdk install \"cmake;$version\" (see app/README.md § Prerequisites)"
            }
        }
        commandLine(
            "sh", "-c",
            "\"$cmake\" -S \"$source\" -B \"$out\" -G Ninja -DCMAKE_MAKE_PROGRAM=\"$ninja\" " +
                "-DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=15.0 " +
                "-DCMAKE_OSX_SYSROOT=$sysroot -DCMAKE_BUILD_TYPE=Release && \"$cmake\" --build \"$out\""
        )
    }
}

val engineIosArm64 = registerEngineBuild("iosArm64", "iphoneos")
val engineIosSimulatorArm64 = registerEngineBuild("iosSimulatorArm64", "iphonesimulator")

tasks.register<Exec>("engineHostTest") {
    group = "verification"
    description = "Configures, builds and runs the C++ engine's Catch2 tests on the macOS host"
    val cmake = cmakeBin.absolutePath
    val ninja = ninjaBin.absolutePath
    val ctest = ctestBin.absolutePath
    val source = engineSourceDir.absolutePath
    val out = layout.buildDirectory.dir("engine/host").get().asFile.absolutePath
    inputs.dir("$source/include")
    inputs.dir("$source/src")
    inputs.dir("$source/tests")
    inputs.file("$source/CMakeLists.txt")
    outputs.dir(out)
    commandLine(
        "sh", "-c",
        "\"$cmake\" -S \"$source\" -B \"$out\" -G Ninja -DCMAKE_MAKE_PROGRAM=\"$ninja\" -DTEMBRA_BUILD_TESTS=ON " +
            "&& \"$cmake\" --build \"$out\" && \"$ctest\" --test-dir \"$out\" --output-on-failure"
    )
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(project(":engine:android"))
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        val libDir = layout.buildDirectory.dir("engine/$targetName")
        compilations.getByName("main").cinterops.create("tembra") {
            definitionFile.set(file("src/nativeInterop/cinterop/tembra_engine.def"))
            packageName("audio.tembra.engine.cinterop")
            includeDirs(engineSourceDir.resolve("include"))
            extraOpts("-libraryPath", libDir.get().asFile.absolutePath)
        }
    }
}

// The static archive is embedded into the cinterop klib, so it must be a declared input of the cinterop
// task: otherwise a rebuilt engine leaves a stale archive inside an up-to-date klib.
tasks.withType<CInteropProcess>().configureEach {
    val engineTask = when (konanTarget) {
        KonanTarget.IOS_ARM64 -> engineIosArm64
        KonanTarget.IOS_SIMULATOR_ARM64 -> engineIosSimulatorArm64
        else -> null
    }
    if (engineTask != null) {
        dependsOn(engineTask)
        inputs.files(engineTask.map { it.outputs.files })
    }
}
