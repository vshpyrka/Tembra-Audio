// Root build: aggregates the four test lanes so each is one documented command (quickstart §4).
val sharedModules = listOf(":shared:core", ":shared:engine", ":shared:data", ":shared:ui")

fun registerLane(name: String, description: String) {
    val laneTasks = sharedModules.map { "$it:$name" }
    tasks.register(name) {
        group = "verification"
        this.description = description
        dependsOn(laneTasks)
        doLast { println("$name ran: ${laneTasks.joinToString()}") }
    }
}

registerLane("checkAndroidHost", "Android host (JVM) unit tests of every shared module")
registerLane("checkIos", "iOS simulator tests of every shared module")
registerLane("checkAndroidDevice", "Instrumented tests of every shared module on the connected Android device")

tasks.register("engineHostTest") {
    group = "verification"
    description = "C++ engine Catch2 tests on the macOS host"
    dependsOn(":shared:engine:engineHostTest")
}
