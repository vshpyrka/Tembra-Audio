package audio.tembra.ui.timeline

/** The timeline's user-facing strings (contracts/timeline-screen.md "Copy"), kept as data so tests assert the exact text. */
object TimelineCopy {
    const val addTrack = "Add track"
    const val addAudio = "Add audio…"
    const val addAudioAsNewTrack = "Add audio as new track"
    const val split = "Split"
    const val undo = "Undo"
    const val redo = "Redo"
    const val grid = "Grid"
    const val fitProject = "Fit project"
    const val trackSettings = "Track settings"
    const val moveUp = "Move up"
    const val moveDown = "Move down"
    const val deleteTrack = "Delete track"
    const val deleteClip = "Delete clip"
    const val metronomeSettings = "Metronome settings"
    const val effects = "Effects"
    const val effectsLater = "Effects for this track arrive in a later version."
    const val stopPlaybackTitle = "Stop playback?"
    const val stopPlaybackText = "You are leaving the timeline while it is playing."
    const val stopAndLeave = "Stop and leave"
    const val keepPlaying = "Keep playing"
    const val couldNotStart = "Could not start playback"
    const val noTracksYet = "No tracks yet"
    const val addToBegin = "Add a track or audio to begin"

    /** "<n> file(s) could not be added: <name> (<reason>), …" */
    fun filesRefused(refusals: List<Pair<String, String>>): String {
        val files = if (refusals.size == 1) "1 file" else "${refusals.size} files"
        return "$files could not be added: " + refusals.joinToString { (name, reason) -> "$name ($reason)" }
    }
}
