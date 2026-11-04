package audio.tembra.ui.shortcuts

/** What the Metronome's keys do (feature 011, spec FR-018); each returns true when it acted. */
interface MetronomeShortcuts {
    fun startStop(): Boolean

    /** [delta] BPM, kept within 40 to 240 by the metronome. */
    fun changeTempo(delta: Int): Boolean

    fun tapTempo(): Boolean
}
