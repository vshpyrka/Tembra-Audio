package audio.tembra.ui.platform

/**
 * What the Android foreground notification shows while audio keeps playing in the background
 * (contracts/timeline-audio-kotlin.md): "Playing" / the project name for the transport, "Metronome" / the tempo for
 * the standalone metronome. iOS shows nothing.
 */
data class PlaybackNotification(val title: String, val subtitle: String)
