package audio.tembra.ui.metronome

/**
 * The metronome's playback lifecycle as a pure function (data-model.md §7, contracts/background-playback.md): play
 * starts only from [PlaybackState.Stopped]; every interruption, route or device loss, external stop, pause or leaving
 * the screen stops a running metronome; a failed start stops with the message that names why. Nothing ever restarts by
 * itself (spec FR-027).
 */
object PlaybackPolicy {
    fun onEvent(state: PlaybackState, event: PlaybackEvent): PlaybackTransition = when (state) {
        PlaybackState.Stopped -> when (event) {
            PlaybackEvent.PlayPressed -> PlaybackTransition.Start
            PlaybackEvent.PausePressed,
            PlaybackEvent.ScreenHidden,
            PlaybackEvent.AudioInterrupted,
            PlaybackEvent.OutputRouteLost,
            PlaybackEvent.DeviceLost,
            PlaybackEvent.StopRequested,
            is PlaybackEvent.StartFailed,
            -> PlaybackTransition.Ignore
        }
        PlaybackState.Starting -> when (event) {
            is PlaybackEvent.StartFailed -> PlaybackTransition.Stop(messageFor(event.reason))
            PlaybackEvent.PlayPressed,
            PlaybackEvent.PausePressed,
            PlaybackEvent.ScreenHidden,
            PlaybackEvent.AudioInterrupted,
            PlaybackEvent.OutputRouteLost,
            PlaybackEvent.DeviceLost,
            PlaybackEvent.StopRequested,
            -> PlaybackTransition.Ignore
        }
        PlaybackState.Running -> when (event) {
            PlaybackEvent.PausePressed,
            PlaybackEvent.ScreenHidden,
            PlaybackEvent.AudioInterrupted,
            PlaybackEvent.OutputRouteLost,
            PlaybackEvent.DeviceLost,
            PlaybackEvent.StopRequested,
            -> PlaybackTransition.Stop(message = null)
            PlaybackEvent.PlayPressed,
            is PlaybackEvent.StartFailed,
            -> PlaybackTransition.Ignore
        }
    }

    private fun messageFor(reason: StartFailure): MetronomeMessage = when (reason) {
        StartFailure.HostRefused -> MetronomeMessage.FocusDenied
        StartFailure.EngineError -> MetronomeMessage.DeviceUnavailable
    }
}
