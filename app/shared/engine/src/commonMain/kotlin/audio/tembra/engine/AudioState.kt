package audio.tembra.engine

/** What the engine reports through `tembra_audio_state`; at most one stream is open (data-model.md §3). */
enum class AudioState {
    Idle,
    Listening,
    Tone,
    Metronome,

    /** The output stream is open for a project, stopped or playing (feature 009, ABI revision 6). */
    Transport;

    companion object {
        /** Maps the C ABI value; anything unknown reads as [Idle]. */
        fun fromNative(value: Int): AudioState = entries.getOrNull(value) ?: Idle
    }
}
