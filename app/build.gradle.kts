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

tasks.register("engineHostTest") {
    group = "verification"
    description = "C++ engine Catch2 tests on the macOS host"
    dependsOn(":shared:engine:engineHostTest")
}
