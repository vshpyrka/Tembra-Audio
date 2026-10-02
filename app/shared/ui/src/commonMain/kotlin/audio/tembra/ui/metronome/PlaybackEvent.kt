package audio.tembra.ui.metronome

/** What happened to the metronome's playback (data-model.md §7, contracts/background-playback.md). */
sealed interface PlaybackEvent {
    data object PlayPressed : PlaybackEvent
    data object PausePressed : PlaybackEvent

    /** The user left the Metronome destination or the ViewModel was cleared. */
    data object ScreenHidden : PlaybackEvent

    /** A call, the assistant or another app took the audio. */
    data object AudioInterrupted : PlaybackEvent

    /** Headphones were unplugged. */
    data object OutputRouteLost : PlaybackEvent

    /** The stream was lost and not reopened. */
    data object DeviceLost : PlaybackEvent

    /** The user asked to stop from outside the app (the Android notification's Stop action). */
    data object StopRequested : PlaybackEvent

    data class StartFailed(val reason: StartFailure) : PlaybackEvent
}
