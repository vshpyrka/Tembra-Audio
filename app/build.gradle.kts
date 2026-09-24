// Root build: aggregates the four test lanes so each is one documented command (quickstart §4).
val sharedModules = listOf(":shared:core", ":shared:engine", ":shared:data", ":shared:ui")

fun registerLane(name: String, description: String) = tasks.register(name) {
    group = "verification"
    this.description = description
    dependsOn(sharedModules.map { "$it:$name" })
    doLast { println("$name ran: ${sharedModules.joinToString { "$it:$name" }}") }
}

registerLane("checkAndroidHost", "Android host (JVM) unit tests of every shared module")
registerLane("checkIos", "iOS simulator tests of every shared module")
registerLane("checkAndroidDevice", "Instrumented tests of every shared module on the connected Android device")

tasks.register("engineHostTest") {
    group = "verification"
    description = "C++ engine Catch2 tests on the macOS host"
    dependsOn(":shared:engine:engineHostTest")
}
