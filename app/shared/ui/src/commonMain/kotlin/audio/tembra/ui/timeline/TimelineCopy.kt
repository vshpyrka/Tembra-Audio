package audio.tembra.ui.timeline

import audio.tembra.core.project.EditRejection

/** The timeline's user-facing strings (contracts/timeline-screen.md "Copy"), kept as data so tests assert the exact text. */
object TimelineCopy {
    const val addTrack = "Add track"
    const val addAudio = "Add audio…"
    const val addAudioAsNewTrack = "Add audio as new track"
    const val split = "Split"
    const val undo = "Undo"
    const val redo = "Redo"
    const val grid = "Grid"
    const val snapToGrid = "Snap to grid"
    const val snapCaption = "Ruler taps, playhead drags, clip moves and splits land on these lines."
    const val fitProject = "Fit project"
    const val zoomIn = "Zoom in"
    const val zoomOut = "Zoom out"
    const val overview = "Overview"

    /** The zoom buttons' level: "Zoom 2.5×, fit the project" (spec FR-015, D1). */
    fun zoomLevel(label: String): String = "Zoom $label, fit the project"

    /** The overview strip's state: "bars 1 to 32 in view". */
    fun barsInView(first: Int, last: Int): String = "bars $first to $last in view"
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

    const val pickFailed = "The file could not be opened"
    const val tempo = "Tempo"
    const val tempoDown = "Tempo down"
    const val tempoUp = "Tempo up"
    const val done = "Done"
    const val fromTheProject = "from the project"
    const val customSoundFromMetronome = "Custom click sounds are imported from the Metronome"
    const val spanOccupied = "Another clip is in the way"
    const val trackLimit = "A project holds at most 64 tracks"
    const val clipLimit = "A project holds at most 1024 clips"
    const val splitOutside = "Move the playhead inside the clip to split it"
    const val splitTooClose = "The playhead is too close to the clip's edge"
    const val nameTooLong = "The name is too long"

    /** The one-line reason for a refused edit, or null for refusals that need no message (spec FR-033, FR-043). */
    fun rejected(reason: EditRejection): String = when (reason) {
        EditRejection.SpanOccupied -> spanOccupied
        EditRejection.TrackLimitReached -> trackLimit
        EditRejection.ClipLimitReached -> clipLimit
        EditRejection.SplitOutsideClip -> splitOutside
        EditRejection.SplitTooCloseToEdge -> splitTooClose
        EditRejection.ProjectNameOutOfRange, EditRejection.DescriptionTooLong, EditRejection.TrackNameTooLong -> nameTooLong
        EditRejection.TrackAtEnd -> ""
        is EditRejection.UnknownTrack, is EditRejection.UnknownClip, is EditRejection.UnknownAsset -> ""
    }

    /** "<n> file(s) could not be added: <name> (<reason>), …" */
    fun filesRefused(refusals: List<Pair<String, String>>): String {
        val files = if (refusals.size == 1) "1 file" else "${refusals.size} files"
        return "$files could not be added: " + refusals.joinToString { (name, reason) -> "$name ($reason)" }
    }
}
