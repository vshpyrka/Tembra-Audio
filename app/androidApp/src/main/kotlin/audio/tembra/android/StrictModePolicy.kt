package audio.tembra.android

/**
 * The debug guard of the main-thread audit (feature 012, contracts/skills-and-audit.md §C part 4): debug builds
 * install a `StrictMode` thread policy that logs every main-thread disk read, network call and slow call, so the
 * device lanes and a manual mixer session can be read with `adb logcat -s StrictMode`; release builds install nothing
 * and pay nothing. Framework-free so the rule itself is a JVM test; the Application supplies the framework call.
 */
object StrictModePolicy {
    /** Runs [install] when [debug]; returns whether it did. */
    fun install(debug: Boolean, install: () -> Unit): Boolean {
        if (!debug) return false
        install()
        return true
    }
}
