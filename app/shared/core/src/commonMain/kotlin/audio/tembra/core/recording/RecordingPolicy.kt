package audio.tembra.core.recording

import audio.tembra.core.project.Beats

/**
 * The recording policy table of contracts/recording-session.md as a pure, exhaustive function (feature 010; spec
 * FR-001, FR-008, FR-009, FR-011, FR-016 to FR-023), the sibling of `PlaybackPolicy`: a subject `when` over the event,
 * then over the state, with no `else` (`kotlin-control-flow`). The controller feeds it the facts the policy cannot see
 * (the permission, the speaker, the remembered toggle, whether a disarm is pending) and runs the effects.
 */
object RecordingPolicy {

    /**
     * One step. [monitorRemembered] is the device's toggle value; [isSpeaker] the current output; [disarmPending]
     * whether the running take's end must disarm (carried from the transition that ended it).
     */
    fun onEvent(
        state: RecordingState,
        monitor: MonitorState,
        event: RecordingEvent,
        monitorRemembered: Boolean,
        isSpeaker: Boolean,
        disarmPending: Boolean = false,
    ): RecordingTransition = when (event) {
        is RecordingEvent.Arm -> arm(state, monitor, event)
        RecordingEvent.Disarm -> disarm(state, monitor)
        is RecordingEvent.InputOpened -> when (state) {
            is RecordingState.Arming -> {
                val monitorOn = monitorRemembered && !isSpeaker
                RecordingTransition(
                    RecordingState.Armed(state.trackId),
                    if (monitorOn) MonitorState.On else if (monitorRemembered) MonitorState.SuspendedSpeaker else MonitorState.Off,
                    if (monitorOn) listOf(RecordingEffect.SetMonitor(true)) else emptyList(),
                )
            }
            RecordingState.Disarmed, is RecordingState.Armed, is RecordingState.PreRoll, is RecordingState.Recording, is RecordingState.Finalising -> stay(state, monitor)
        }
        RecordingEvent.InputFailed -> when (state) {
            is RecordingState.Arming -> RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.Notify(TakeStopReason.InputLost)))
            RecordingState.Disarmed, is RecordingState.Armed, is RecordingState.PreRoll, is RecordingState.Recording, is RecordingState.Finalising -> stay(state, monitor)
        }
        is RecordingEvent.Record -> when (state) {
            is RecordingState.Armed -> {
                val countIn = if (event.playing) 0 else event.countInBeats
                val next: RecordingState = if (countIn > 0) RecordingState.PreRoll(state.trackId, event.takeId, countIn) else RecordingState.Recording(state.trackId, event.takeId, Beats.Zero, 0L)
                RecordingTransition(next, monitor, listOf(RecordingEffect.StartTake(countIn), RecordingEffect.KeepAlive(recording = true)))
            }
            is RecordingState.PreRoll -> cancelPreRoll(state, monitor)
            is RecordingState.Recording -> RecordingTransition(RecordingState.Finalising(state.trackId, state.takeId), monitor, listOf(RecordingEffect.StopTake(TakeStopReason.User)))
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Finalising -> stay(state, monitor)
        }
        RecordingEvent.Stop -> when (state) {
            is RecordingState.Recording -> RecordingTransition(RecordingState.Finalising(state.trackId, state.takeId), monitor, listOf(RecordingEffect.StopTake(TakeStopReason.User)))
            is RecordingState.PreRoll -> cancelPreRoll(state, monitor)
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.Finalising -> stay(state, monitor)
        }
        RecordingEvent.Cancel -> when (state) {
            is RecordingState.PreRoll -> cancelPreRoll(state, monitor)
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.Recording, is RecordingState.Finalising -> stay(state, monitor)
        }
        is RecordingEvent.PreRollTick -> when (state) {
            is RecordingState.PreRoll -> RecordingTransition(state.copy(beatsLeft = event.beatsLeft), monitor)
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.Recording, is RecordingState.Finalising -> stay(state, monitor)
        }
        is RecordingEvent.TakeStarted -> when (state) {
            is RecordingState.PreRoll -> RecordingTransition(RecordingState.Recording(state.trackId, state.takeId, event.startBeats, event.since), monitor)
            is RecordingState.Recording -> RecordingTransition(state.copy(startBeats = event.startBeats, since = event.since), monitor)
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.Finalising -> stay(state, monitor)
        }
        is RecordingEvent.TakeEnded -> endTake(state, monitor, event.reason, disarm = false)
        RecordingEvent.TakeFinalised -> when (state) {
            is RecordingState.Finalising ->
                if (disarmPending) {
                    RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.KeepAlive(recording = false), RecordingEffect.CloseInput))
                } else {
                    RecordingTransition(RecordingState.Armed(state.trackId), monitor, listOf(RecordingEffect.KeepAlive(recording = false)))
                }
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.PreRoll, is RecordingState.Recording -> stay(state, monitor)
        }
        RecordingEvent.PermissionDenied -> when (state) {
            is RecordingState.Arming -> RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.ShowDenied))
            RecordingState.Disarmed, is RecordingState.Armed, is RecordingState.PreRoll, is RecordingState.Recording, is RecordingState.Finalising -> stay(state, monitor)
        }
        RecordingEvent.PermissionRevoked -> when (state) {
            is RecordingState.Recording -> endTake(state, monitor, TakeStopReason.PermissionRevoked, disarm = true)
            is RecordingState.PreRoll -> cancelAndDisarm()
            is RecordingState.Arming, is RecordingState.Armed -> RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CloseInput, RecordingEffect.ShowDenied))
            RecordingState.Disarmed, is RecordingState.Finalising -> stay(state, monitor)
        }
        RecordingEvent.InputChanged -> when (state) {
            is RecordingState.Recording -> endTake(state, monitor, TakeStopReason.InputLost, disarm = false)
            is RecordingState.PreRoll -> cancelPreRoll(state, monitor)
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.Finalising -> stay(state, monitor)
        }
        is RecordingEvent.OutputChanged -> when (state) {
            RecordingState.Disarmed -> stay(state, if (event.isSpeaker && monitorRemembered) MonitorState.SuspendedSpeaker else if (monitorRemembered) MonitorState.On else MonitorState.Off)
            is RecordingState.Arming, is RecordingState.Armed, is RecordingState.PreRoll, is RecordingState.Recording, is RecordingState.Finalising ->
                if (event.isSpeaker) {
                    RecordingTransition(state, if (monitorRemembered) MonitorState.SuspendedSpeaker else MonitorState.Off, if (monitor == MonitorState.On) listOf(RecordingEffect.SetMonitor(false)) else emptyList())
                } else if (monitorRemembered) {
                    RecordingTransition(state, MonitorState.On, if (monitor != MonitorState.On) listOf(RecordingEffect.SetMonitor(true)) else emptyList())
                } else {
                    stay(state, MonitorState.Off)
                }
        }
        RecordingEvent.Interrupted -> when (state) {
            is RecordingState.Recording -> endTake(state, monitor, TakeStopReason.Interrupted, disarm = false)
            is RecordingState.PreRoll -> cancelPreRoll(state, monitor)
            RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.Finalising -> stay(state, monitor)
        }
        is RecordingEvent.TrackDeleted -> if (state.armedTrack == event.trackId) leaveArmed(state, monitor, TakeStopReason.User) else stay(state, monitor)
        RecordingEvent.ProjectClosing -> leaveArmed(state, monitor, TakeStopReason.Leaving)
        RecordingEvent.HostHidden -> leaveArmed(state, monitor, TakeStopReason.Leaving)
        RecordingEvent.TransportClosed -> leaveArmed(state, monitor, TakeStopReason.TransportClosed)
        is RecordingEvent.MonitorToggle -> when (state) {
            RecordingState.Disarmed, is RecordingState.Arming -> toggleRemembered(state, monitor, monitorRemembered, event.isSpeaker, applyToEngine = false)
            is RecordingState.Armed, is RecordingState.PreRoll, is RecordingState.Recording, is RecordingState.Finalising ->
                toggleRemembered(state, monitor, monitorRemembered, event.isSpeaker, applyToEngine = true)
        }
    }

    private fun stay(state: RecordingState, monitor: MonitorState): RecordingTransition = RecordingTransition(state, monitor)

    private fun arm(state: RecordingState, monitor: MonitorState, event: RecordingEvent.Arm): RecordingTransition = when (state) {
        RecordingState.Disarmed -> when (event.permission) {
            RecordingEvent.PermissionStatus.Granted -> RecordingTransition(RecordingState.Arming(event.trackId), monitor, listOf(RecordingEffect.OpenInput, RecordingEffect.KeepAlive(recording = false)))
            RecordingEvent.PermissionStatus.NotDetermined, RecordingEvent.PermissionStatus.DeniedCanAsk -> RecordingTransition(state, monitor, listOf(RecordingEffect.RequestPermission))
            RecordingEvent.PermissionStatus.DeniedOpenSettings -> RecordingTransition(state, monitor, listOf(RecordingEffect.ShowDenied))
        }
        is RecordingState.Armed ->
            if (state.trackId == event.trackId) {
                RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CloseInput))
            } else {
                RecordingTransition(RecordingState.Arming(event.trackId), monitor, listOf(RecordingEffect.CloseInput, RecordingEffect.OpenInput))
            }
        // Arming and disarming are unavailable while a take runs (FR-018); a second arm while opening is ignored.
        is RecordingState.Arming, is RecordingState.PreRoll, is RecordingState.Recording, is RecordingState.Finalising -> stay(state, monitor)
    }

    private fun disarm(state: RecordingState, monitor: MonitorState): RecordingTransition = when (state) {
        is RecordingState.Armed, is RecordingState.Arming -> RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CloseInput))
        RecordingState.Disarmed, is RecordingState.PreRoll, is RecordingState.Recording, is RecordingState.Finalising -> stay(state, monitor)
    }

    private fun cancelPreRoll(state: RecordingState.PreRoll, monitor: MonitorState): RecordingTransition =
        RecordingTransition(RecordingState.Armed(state.trackId), monitor, listOf(RecordingEffect.CancelTake, RecordingEffect.KeepAlive(recording = false)))

    private fun cancelAndDisarm(): RecordingTransition {
        return RecordingTransition(
            RecordingState.Disarmed,
            MonitorState.Off,
            listOf(RecordingEffect.CancelTake, RecordingEffect.KeepAlive(recording = false), RecordingEffect.CloseInput, RecordingEffect.ShowDenied),
        )
    }

    /** A take ends for [reason]: Finalising, the stop and the notice; [disarm] when the track must also be disarmed afterwards. */
    private fun endTake(state: RecordingState, monitor: MonitorState, reason: TakeStopReason, disarm: Boolean): RecordingTransition = when (state) {
        is RecordingState.Recording -> RecordingTransition(
            RecordingState.Finalising(state.trackId, state.takeId),
            monitor,
            listOf(RecordingEffect.StopTake(reason), RecordingEffect.Notify(reason)),
            disarmAfterFinalise = disarm,
        )
        RecordingState.Disarmed, is RecordingState.Arming, is RecordingState.Armed, is RecordingState.PreRoll, is RecordingState.Finalising -> stay(state, monitor)
    }

    /** The session leaves the armed state behind (a deleted track, the project closing, the host hidden, the transport closed). */
    private fun leaveArmed(state: RecordingState, monitor: MonitorState, reason: TakeStopReason): RecordingTransition = when (state) {
        RecordingState.Disarmed -> stay(state, monitor)
        is RecordingState.Arming, is RecordingState.Armed -> RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CloseInput))
        is RecordingState.PreRoll -> RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CancelTake, RecordingEffect.KeepAlive(recording = false), RecordingEffect.CloseInput))
        is RecordingState.Recording -> endTake(state, monitor, reason, disarm = true)
        is RecordingState.Finalising -> RecordingTransition(state, monitor, emptyList(), disarmAfterFinalise = true)
    }

    private fun toggleRemembered(state: RecordingState, monitor: MonitorState, remembered: Boolean, isSpeaker: Boolean, applyToEngine: Boolean): RecordingTransition {
        if (isSpeaker) return RecordingTransition(state, if (remembered) MonitorState.SuspendedSpeaker else MonitorState.Off, listOf(RecordingEffect.ShowMonitorNeedsHeadphones))
        val next = !remembered
        val effects = mutableListOf<RecordingEffect>(RecordingEffect.RememberMonitor(next))
        if (applyToEngine) effects += RecordingEffect.SetMonitor(next)
        return RecordingTransition(state, if (next) MonitorState.On else MonitorState.Off, effects)
    }
}
