package audio.tembra.ui.platform

/**
 * What the Android foreground notification shows while audio keeps playing in the background
 * (contracts/timeline-audio-kotlin.md): "Playing" / the project name for the transport, "Metronome" / the tempo for
 * the standalone metronome. iOS shows nothing.
 */
data class PlaybackNotification(val title: String, val subtitle: String) {
    companion object {
        /** "Recording" / the project name while a take runs (feature 010, FR-021). */
        fun recording(projectName: String): PlaybackNotification = PlaybackNotification(RECORDING_TITLE, projectName)

        const val RECORDING_TITLE = "Recording"
    }
}
