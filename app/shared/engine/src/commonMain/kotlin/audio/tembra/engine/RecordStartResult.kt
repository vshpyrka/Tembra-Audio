package audio.tembra.engine

/** Outcome of [TimelineAudio.recordStart]. */
sealed interface RecordStartResult {
    /** The pre-roll or the capture begins at the next block. */
    data object Started : RecordStartResult

    /** No track is armed (`TEMBRA_ERR_STATE`). */
    data object NotArmed : RecordStartResult

    /** A take already runs (`TEMBRA_ERR_STATE`). */
    data object TakeRunning : RecordStartResult

    /** The staging file could not be created (`TEMBRA_ERR_IO`). */
    data object FileFailed : RecordStartResult
}
