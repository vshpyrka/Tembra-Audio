package audio.tembra.ui.transport

/** The transport bar's strings (contracts/timeline-screen.md), kept as data so tests assert the exact text. */
object TransportCopy {
    const val play = "Play"
    const val pause = "Pause"
    const val skipToStart = "Skip to start"
    const val stepBack = "Back one bar"
    const val stepForward = "Forward one bar"
    const val metronomeOn = "Turn metronome on"
    const val metronomeOff = "Turn metronome off"
    const val metronomeSettings = "Metronome settings"
    const val masterVolume = "Master volume"
    const val readout = "Position and length"
    /** The background-playback notification's title; its subtitle is the project name. */
    const val playing = "Playing"
    const val stateOn = "On"
    const val stateOff = "Off"

    /** "Master volume 80 percent". */
    fun masterVolumeState(percent: Int): String = "$percent percent"

    /** "80%": the percentage drawn beside the master slider (spec FR-007). */
    fun masterVolumePercent(percent: Int): String = "$percent%"
}
