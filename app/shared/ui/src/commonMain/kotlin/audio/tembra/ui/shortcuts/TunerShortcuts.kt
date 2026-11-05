package audio.tembra.ui.shortcuts

/** What the Tuner's keys do (feature 011, spec FR-018a); each returns true when it acted. */
interface TunerShortcuts {
    fun autoListen(): Boolean

    fun byEar(): Boolean

    /** The string numbered [number] on its card: 1 is string I, the highest. */
    fun playString(number: Int): Boolean

    fun stopTone(): Boolean

    fun toggleLoop(): Boolean
}
