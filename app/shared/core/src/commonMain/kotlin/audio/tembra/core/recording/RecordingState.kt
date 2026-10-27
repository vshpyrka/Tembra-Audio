package audio.tembra.core.recording

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId

/**
 * The recording phase the session publishes (feature 010, data-model.md §2), the sibling of `PlaybackState`: which
 * track is armed, whether the input is opening, a count-in pre-roll runs, a take captures, or the take is being
 * finalised. [takeId] is the staging token of the running take.
 */
sealed interface RecordingState {
    data object Disarmed : RecordingState

    /** The input is opening for [trackId] (the permission was granted). */
    data class Arming(val trackId: TrackId) : RecordingState

    data class Armed(val trackId: TrackId) : RecordingState

    /** The count-in runs; [beatsLeft] counts down from bars × beats per bar to 1. */
    data class PreRoll(val trackId: TrackId, val takeId: String, val beatsLeft: Int) : RecordingState

    /** A take captures from [startBeats]; [since] is the epoch millisecond it began. */
    data class Recording(val trackId: TrackId, val takeId: String, val startBeats: Beats, val since: Long) : RecordingState

    /** The take ended; the file is being adopted into the document. */
    data class Finalising(val trackId: TrackId, val takeId: String) : RecordingState

    /** The armed track of every state but [Disarmed]. */
    val armedTrack: TrackId?
        get() = when (this) {
            Disarmed -> null
            is Arming -> trackId
            is Armed -> trackId
            is PreRoll -> trackId
            is Recording -> trackId
            is Finalising -> trackId
        }

    /** True while a count-in or a take runs (FR-018 makes the structural edits unavailable). */
    val takeRunning: Boolean
        get() = when (this) {
            Disarmed, is Arming, is Armed -> false
            is PreRoll, is Recording, is Finalising -> true
        }
}
