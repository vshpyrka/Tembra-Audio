package audio.tembra.core.recording

/**
 * Input monitoring as the toggle shows it (spec FR-016, FR-017): off, on, or remembered on while the output is the
 * built-in speaker, where it must not sound (the toggle shows suspended and a tap explains).
 */
enum class MonitorState {
    Off,
    On,
    SuspendedSpeaker,
}
