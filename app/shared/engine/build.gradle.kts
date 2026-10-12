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
    // Measurement builds (feature 007) compile the tracing facade into the engine; `-Ptembra.perf=release`
    // (the SC-007 baseline) keeps the measurement build type without it.
    val tracing = if (providers.gradleProperty("tembra.perf").orNull == "true") "ON" else "OFF"
    return tasks.register<Exec>("buildEngine${target.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Builds libtembra_engine.a for $target with CMake"
        // The generated names header lives in include/ (feature 007).
        dependsOn("generateTraceNames")
        inputs.property("tembraTracing", tracing)
        inputs.dir("$source/include")
        inputs.dir("$source/src")
        inputs.dir("$source/src/core")
        inputs.dir("$source/src/metronome")
        inputs.dir("$source/src/tuner")
        inputs.dir("$source/src/platform")
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
                "-DCMAKE_OSX_SYSROOT=$sysroot -DCMAKE_BUILD_TYPE=Release -DTEMBRA_TRACING=$tracing && \"$cmake\" --build \"$out\""
        )
    }
}

val engineIosArm64 = registerEngineBuild("iosArm64", "iphoneos")
val engineIosSimulatorArm64 = registerEngineBuild("iosSimulatorArm64", "iphonesimulator")

tasks.register<Exec>("engineHostTest") {
    group = "verification"
    description = "Configures, builds and runs the C++ engine's Catch2 tests on the macOS host"
    dependsOn("generateTraceNames")
    val cmake = cmakeBin.absolutePath
    val ninja = ninjaBin.absolutePath
    val ctest = ctestBin.absolutePath
    val source = engineSourceDir.absolutePath
    val out = layout.buildDirectory.dir("engine/host").get().asFile.absolutePath
    // Drop-in reference recordings checked on pitch by recordings_on_pitch_test (feature 003, research R5).
    val recordingsDir = rootDir.parentFile.resolve("app/shared/ui/src/commonMain/composeResources/files/tuner").absolutePath
    inputs.dir("$source/include")
    inputs.dir("$source/src")
    inputs.dir("$source/src/core")
    inputs.dir("$source/src/metronome")
    inputs.dir("$source/src/tuner")
    inputs.dir("$source/src/platform")
    inputs.dir("$source/tests")
    inputs.file("$source/CMakeLists.txt")
    inputs.dir(recordingsDir)
    outputs.dir(out)
    commandLine(
        "sh", "-c",
        "\"$cmake\" -S \"$source\" -B \"$out\" -G Ninja -DCMAKE_MAKE_PROGRAM=\"$ninja\" -DTEMBRA_BUILD_TESTS=ON " +
            // Tracing on in the test build: the shim test exercises os_signpost and the poison test the guard (feature 007).
            "-DTEMBRA_TRACING=ON -DTEMBRA_TUNER_RECORDINGS_DIR=\"$recordingsDir\" " +
            "&& \"$cmake\" --build \"$out\" && \"$ctest\" --test-dir \"$out\" --output-on-failure"
    )
}

// --- Measurement build switch (feature 007) -------------------------------------------------------
// `-Ptembra.perf=true` generates PerfBuild.PERF_ENABLED = true and the signpost-name table
// (PerfNames.kt) from perf-catalogue.txt; without it (or with any other value, such as the
// `release` of the SC-007 baseline) the constant is false and every name is "", so release and
// debug binaries carry none of the catalogue strings (FR-014, SC-007).
val perfEnabled: Boolean = providers.gradleProperty("tembra.perf").orNull == "true"
val perfCatalogueFile: File = layout.projectDirectory.file("perf-catalogue.txt").asFile
val perfGeneratedMain = layout.buildDirectory.dir("generated/perf/commonMain/kotlin")
val perfGeneratedTest = layout.buildDirectory.dir("generated/perf/commonTest/kotlin")

val generatePerfBuild = tasks.register("generatePerfBuild") {
    group = "build"
    description = "Generates PerfBuild.kt, PerfNames.kt and the test expectation from -Ptembra.perf and perf-catalogue.txt"
    val enabled = perfEnabled
    val catalogue = perfCatalogueFile
    val mainDir = perfGeneratedMain.get().asFile
    val testDir = perfGeneratedTest.get().asFile
    inputs.property("perfEnabled", enabled)
    if (catalogue.exists()) inputs.file(catalogue)
    outputs.dir(mainDir)
    outputs.dir(testDir)
    doLast {
        val pkgDir = mainDir.resolve("audio/tembra/engine/perf").apply { mkdirs() }
        val testPkgDir = testDir.resolve("audio/tembra/engine/perf").apply { mkdirs() }
        val header = "// Generated by :shared:engine generatePerfBuild (feature 007); do not edit.\npackage audio.tembra.engine.perf\n\n"
        pkgDir.resolve("PerfBuild.kt").writeText(
            header +
                "/** `true` only in measurement builds (`-Ptembra.perf=true`); every facade entry is inert otherwise. */\n" +
                "object PerfBuild {\n    const val PERF_ENABLED: Boolean = $enabled\n}\n"
        )
        testPkgDir.resolve("PerfBuildExpectation.kt").writeText(
            header + "/** What the Gradle property said for this test run. */\n" +
                "internal object PerfBuildExpectation {\n    const val PERF_ENABLED: Boolean = $enabled\n}\n"
        )
        if (catalogue.exists()) {
            // Lines: <ENUM_NAME> <family> <signpost.name>; family `counter` marks PerfCounter entries.
            val entries = catalogue.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line -> line.split(Regex("\\s+")).also { require(it.size == 3) { "perf-catalogue.txt: expected 3 fields in '$line'" } } }
            val sections = entries.filter { it[1] != "counter" }
            val counters = entries.filter { it[1] == "counter" }
            fun table(type: String, rows: List<List<String>>): String =
                "@PublishedApi\ninternal fun $type.signpostName(): String = when (this) {\n" +
                    rows.joinToString("") { "    $type.${it[0]} -> \"${if (enabled) it[2] else ""}\"\n" } + "}\n"
            pkgDir.resolve("PerfNames.kt").writeText(
                header + "/** Signpost names from perf-catalogue.txt; empty strings outside measurement builds. */\n" +
                    table("PerfSection", sections) + "\n" + table("PerfCounter", counters)
            )
            // The catalogue lines as the parity test sees them on every target (no file access in commonTest).
            testPkgDir.resolve("PerfCatalogueExpectation.kt").writeText(
                header + "/** perf-catalogue.txt as read by generatePerfBuild for this test run. */\n" +
                    "internal object PerfCatalogueExpectation {\n    val LINES: List<String> = listOf(\n" +
                    entries.joinToString("") { "        \"${it.joinToString(" ")}\",\n" } + "    )\n}\n"
            )
        }
    }
}

// The C header of the same names (engine/include/tembra_trace_names.h) is checked in; this task regenerates it with
// `-Ptembra.updateTraceNames=true` and otherwise fails the build when the checked-in file is stale.
val traceNamesHeader: File = engineSourceDir.resolve("include/tembra_trace_names.h")
val generateTraceNames = tasks.register("generateTraceNames") {
    group = "build"
    description = "Generates app/engine/include/tembra_trace_names.h from perf-catalogue.txt (fails when stale)"
    val catalogue = perfCatalogueFile
    val header = traceNamesHeader
    val update = providers.gradleProperty("tembra.updateTraceNames").isPresent
    inputs.file(catalogue)
    inputs.property("update", update)
    outputs.file(header)
    doLast {
        val entries = catalogue.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(Regex("\\s+")) }
        val text = buildString {
            append("/* Generated by :shared:engine generateTraceNames from app/shared/engine/perf-catalogue.txt; do not edit. */\n")
            append("#ifndef TEMBRA_TRACE_NAMES_H\n#define TEMBRA_TRACE_NAMES_H\n\n")
            append("/* Catalogue signpost names (feature 007). Use through TEMBRA_TRACE_SCOPE / TEMBRA_TRACE_COUNTER only:\n")
            append(" * with TEMBRA_TRACING off the macros expand to nothing and no name reaches the binary. */\n")
            for (e in entries) append("#define TEMBRA_TRACE_${e[0]} \"${e[2]}\"\n")
            append("\n/* X-macro over every entry: os_signpost needs its name as a literal, so the shim dispatches through this. */\n")
            append("#define TEMBRA_TRACE_FOR_EACH(X) \\\n")
            append(entries.joinToString(" \\\n") { "    X(${it[0]}, \"${it[2]}\")" })
            append("\n\n#define TEMBRA_TRACE_NAME_COUNT ${entries.size}\n\n#endif /* TEMBRA_TRACE_NAMES_H */\n")
        }
        if (update || !header.exists()) {
            header.writeText(text)
        } else if (header.readText() != text) {
            throw GradleException(
                "${header.path} is stale: run ./gradlew :shared:engine:generateTraceNames -Ptembra.updateTraceNames=true and commit it"
            )
        }
    }
}
tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin", ignoreCase = true) }.configureEach {
    dependsOn(generateTraceNames)
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generatePerfBuild.map { perfGeneratedMain.get() })
        }
        commonTest {
            kotlin.srcDir(generatePerfBuild.map { perfGeneratedTest.get() })
        }
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
