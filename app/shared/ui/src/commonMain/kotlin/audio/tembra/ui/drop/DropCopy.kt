package audio.tembra.ui.drop

import audio.tembra.core.metronome.TimeSignature
import kotlin.math.floor

/** The words of drops and files opened with the app (feature 011, spec FR-026, FR-026a). */
internal object DropCopy {
    const val newTrack = "New track"
    const val newProject = "New project"
    const val noAudio = "No audio files were found"

    /** "Add to Drums at bar 5": [trackName] is the hovered track's, the bar counts from 1. */
    fun addToTrack(trackName: String, preview: DropPreview.AddToTrack, signature: TimeSignature): String =
        "Add to $trackName at bar ${floor(preview.atBeats.value / signature.beats).toInt() + 1}"

    /** "No audio files were found", then what was dropped instead, as the refusal message names files. */
    fun noAudio(skipped: List<SkippedItem>): String =
        if (skipped.isEmpty()) noAudio else "$noAudio: " + skipped.joinToString { "${it.name} (${skipText(it.reason)})" }

    fun skipText(reason: SkipReason): String = when (reason) {
        SkipReason.NotAudio -> "not an audio file"
        SkipReason.UnreadableFolder -> "a folder this device does not let the app open"
        SkipReason.NestedFolder -> "a folder inside the dropped folder"
    }
}
