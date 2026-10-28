package audio.tembra.ui.timeline

import audio.tembra.core.project.EditRejection
import audio.tembra.core.recording.TakeStopReason

/** The timeline's user-facing strings (contracts/timeline-screen.md "Copy"), kept as data so tests assert the exact text. */
object TimelineCopy {
    const val addTrack = "Add track"
    const val addAudio = "Add audio…"
    const val addAudioAsNewTrack = "Add audio as new track"
    const val split = "Split"
    const val undo = "Undo"
    const val redo = "Redo"
    const val snap = "Snap"
    const val snapToGrid = "Snap to grid"
    const val snapCaption = "Ruler taps, playhead drags, clip moves and splits land on these lines."
    const val overview = "Overview"

    /** The full toolbar's grid pill: "Snap 1/16" (spec FR-018, D1). */
    fun snapPill(gridLabel: String): String = "$snap $gridLabel"

    /** The overflow menu's grid entry: "Snap: 1/16" (spec FR-018, FR-055, D1). */
    fun snapEntry(gridLabel: String): String = "$snap: $gridLabel"

    /** The overflow menu's show/hide entry for the overview strip, checked while it shows (spec FR-015, FR-055; T151). */
    const val overviewStrip = "Overview strip"

    /** The tempo pill: "124 BPM", read as "Tempo 124 beats per minute" (spec FR-036). */
    fun tempoPill(bpm: Int): String = "$bpm BPM"
    fun tempoPillDescription(bpm: Int): String = "Tempo $bpm beats per minute"

    /** The metronome sheet's read-only tempo: "124 BPM, from the project" (spec FR-039, D1). */
    fun projectTempo(bpm: Int): String = "${tempoPill(bpm)}, $fromTheProject"

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
    const val takeTooShort = "The take was too short to keep"

    // Feature 010 (contracts/timeline-recording-ui.md "Copy").
    const val arm = "Arm for recording"
    const val inputLevel = "Input level"
    const val clipped = "Clipped, tap to reset"
    const val monitorInput = "Monitor input"
    const val monitorNeedsHeadphones = "Connect headphones to monitor the input"
    const val microphoneNeeded = "Microphone access is needed to record"
    const val openSettings = "Open settings"
    const val allowMicrophone = "Allow"
    const val countIn = "Count-in"
    const val countInOff = "Off"
    const val countInOneBar = "1 bar"
    const val countInTwoBars = "2 bars"
    const val recordRefused = "Not enough space to record (200 MB needed)"
    const val takeEndedCall = "Take ended: the call took the audio"
    const val takeEndedInput = "Take ended: the microphone changed"
    const val takeEndedLength = "Take ended: 30 minutes reached"
    const val takeEndedSpace = "Take ended: the device is out of space"
    const val takeEndedFailed = "Take ended: recording failed"
    const val takeEndedPermission = "Take ended: microphone access was revoked"
    const val takeEndedLeft = "Take kept"
    const val stopRecordingTitle = "Stop recording?"
    const val stopRecordingText = "The take is kept up to now."
    const val stopRecordingAndLeave = "Stop recording and leave"
    const val keepRecording = "Keep recording"

    /** The one-line notice for a take the engine or the platform ended (spec FR-021, FR-025). */
    fun takeEnded(reason: TakeStopReason): String = when (reason) {
        TakeStopReason.Interrupted -> takeEndedCall
        TakeStopReason.InputLost -> takeEndedInput
        TakeStopReason.MaxLength -> takeEndedLength
        TakeStopReason.DiskFull -> takeEndedSpace
        TakeStopReason.WriteFailed -> takeEndedFailed
        TakeStopReason.PermissionRevoked -> takeEndedPermission
        TakeStopReason.User, TakeStopReason.TransportClosed, TakeStopReason.Leaving -> takeEndedLeft
    }

    /** The one-line reason for a refused edit, or null for refusals that need no message (spec FR-033, FR-043). */
    fun rejected(reason: EditRejection): String = when (reason) {
        EditRejection.SpanOccupied -> spanOccupied
        EditRejection.TrackLimitReached -> trackLimit
        EditRejection.ClipLimitReached -> clipLimit
        EditRejection.SplitOutsideClip -> splitOutside
        EditRejection.SplitTooCloseToEdge -> splitTooClose
        EditRejection.ProjectNameOutOfRange, EditRejection.DescriptionTooLong, EditRejection.TrackNameTooLong -> nameTooLong
        EditRejection.TrackAtEnd -> ""
        EditRejection.TakeTooShort -> takeTooShort
        is EditRejection.UnknownTrack, is EditRejection.UnknownClip, is EditRejection.UnknownAsset -> ""
    }

    /** "<n> file(s) could not be added: <name> (<reason>), …" */
    fun filesRefused(refusals: List<Pair<String, String>>): String {
        val files = if (refusals.size == 1) "1 file" else "${refusals.size} files"
        return "$files could not be added: " + refusals.joinToString { (name, reason) -> "$name ($reason)" }
    }
}
