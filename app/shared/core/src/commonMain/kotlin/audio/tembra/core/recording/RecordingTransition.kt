package audio.tembra.core.recording

/** One policy step: the next state, the next monitor state and the effects to run, in order. */
data class RecordingTransition(
    val state: RecordingState,
    val monitor: MonitorState,
    val effects: List<RecordingEffect> = emptyList(),
    /** True when the take that just ended must disarm the track once it is finalised (the last five reasons of the table). */
    val disarmAfterFinalise: Boolean = false,
)
