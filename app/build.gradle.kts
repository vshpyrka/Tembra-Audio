// Root build: aggregates the four test lanes so each is one documented command (quickstart §4).
val sharedModules = listOf(":shared:core", ":shared:engine", ":shared:data", ":shared:ui")

fun registerLane(name: String, description: String, extraModules: List<String> = emptyList()) {
    val laneTasks = (sharedModules + extraModules).map { "$it:$name" }
    tasks.register(name) {
        group = "verification"
        this.description = description
        dependsOn(laneTasks)
        doLast { println("$name ran: ${laneTasks.joinToString()}") }
    }
}

// The host lane also runs the app module's JVM tests: the baseline-profile coverage check (feature 008, FR-011).
registerLane("checkAndroidHost", "Android host (JVM) unit tests of every shared module and the app module",
             extraModules = listOf(":androidApp"))
registerLane("checkIos", "iOS simulator tests of every shared module")
registerLane("checkAndroidDevice", "Instrumented tests of every shared module on the connected Android device")

// One connected device is one resource: the engine's real-time tests (T043) read the audio-health counters over ten
// seconds of playback, and another module installing its test APK on the same device at the same time starves the
// clip streamer. The lock lets Gradle keep `org.gradle.parallel` and still run the instrumentation tasks one at a time.
abstract class AndroidDeviceLock : BuildService<BuildServiceParameters.None>

val androidDeviceLock = gradle.sharedServices.registerIfAbsent("androidDeviceLock", AndroidDeviceLock::class) {
    maxParallelUsages.set(1)
}

allprojects {
    tasks.configureEach {
        if (name.startsWith("connected")) usesService(androidDeviceLock)
    }
}

tasks.register("engineHostTest") {
    group = "verification"
    description = "C++ engine Catch2 tests on the macOS host"
    dependsOn(":shared:engine:engineHostTest")
}
