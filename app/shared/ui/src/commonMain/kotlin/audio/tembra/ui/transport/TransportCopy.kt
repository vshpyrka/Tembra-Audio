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

    // Feature 010 (contracts/timeline-recording-ui.md "Copy").
    const val record = "Record"
    const val recording = "Recording"
    /** The readout's position while the count-in runs: "Count-in 4". */
    fun countIn(beatsLeft: Int): String = "Count-in $beatsLeft"
    /** Record's accessible name while the count-in runs: "Count-in, 3 beats left". */
    fun countInBeatsLeft(beatsLeft: Int): String = if (beatsLeft == 1) "Count-in, 1 beat left" else "Count-in, $beatsLeft beats left"
}
