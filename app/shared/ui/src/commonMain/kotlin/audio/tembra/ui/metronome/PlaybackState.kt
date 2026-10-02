package audio.tembra.ui.metronome

/** Where the metronome's playback lifecycle is (data-model.md §7). */
enum class PlaybackState {
    Stopped,

    /** The background host is acquired and the engine start is pending (under 100 ms). */
    Starting,

    Running,
}
