package audio.tembra.core.recording

/** The side effects the controller performs after a policy step (data-model.md §2); the list may be empty. */
sealed interface RecordingEffect {
    data object OpenInput : RecordingEffect

    data object CloseInput : RecordingEffect

    /** `recordStart` with the count-in in beats (0 = none). */
    data class StartTake(val countInBeats: Int) : RecordingEffect

    /** `recordStop`, then the adoption; [reason] is kept for the take info and the notice. */
    data class StopTake(val reason: TakeStopReason) : RecordingEffect

    /** `recordCancel`: the pre-roll ends with nothing recorded. */
    data object CancelTake : RecordingEffect

    /** `setMonitor(enabled)` on the engine for the armed track. */
    data class SetMonitor(val enabled: Boolean) : RecordingEffect

    /** Remember the toggle's value on the device. */
    data class RememberMonitor(val enabled: Boolean) : RecordingEffect

    data object RequestPermission : RecordingEffect

    /** The "microphone needed" message with the settings action. */
    data object ShowDenied : RecordingEffect

    /** The "headphones needed" message. */
    data object ShowMonitorNeedsHeadphones : RecordingEffect

    /** The "take ended: reason" notice. */
    data class Notify(val reason: TakeStopReason) : RecordingEffect

    /** `keepAlive(recording)` while a take runs, the playing notification or a release otherwise. */
    data class KeepAlive(val recording: Boolean) : RecordingEffect
}
