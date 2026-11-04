package audio.tembra.core.recording

import audio.tembra.core.project.Beats
import audio.tembra.core.project.TrackId
import audio.tembra.core.recording.RecordingEvent.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every row of the state machine in contracts/recording-session.md (feature 010; spec FR-001, FR-008, FR-009, FR-011, FR-016 to FR-023). */
class RecordingPolicyTest {

    private val t = TrackId("track-1")
    private val u = TrackId("track-2")
    private val armed = RecordingState.Armed(t)
    private val preRoll = RecordingState.PreRoll(t, "take-1", 4)
    private val recording = RecordingState.Recording(t, "take-1", Beats(4.0), 1000L)
    private val finalising = RecordingState.Finalising(t, "take-1")

    private fun step(
        state: RecordingState,
        event: RecordingEvent,
        monitor: MonitorState = MonitorState.Off,
        remembered: Boolean = false,
        speaker: Boolean = false,
        disarmPending: Boolean = false,
    ): RecordingTransition = RecordingPolicy.onEvent(state, monitor, event, remembered, speaker, disarmPending)

    @Test
    fun armFromDisarmedFollowsThePermission() {
        assertEquals(
            RecordingTransition(RecordingState.Arming(t), MonitorState.Off, listOf(RecordingEffect.OpenInput, RecordingEffect.KeepAlive(false))),
            step(RecordingState.Disarmed, RecordingEvent.Arm(t, PermissionStatus.Granted)),
        )
        for (status in listOf(PermissionStatus.NotDetermined, PermissionStatus.DeniedCanAsk)) {
            assertEquals(
                RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.RequestPermission)),
                step(RecordingState.Disarmed, RecordingEvent.Arm(t, status)),
                "$status",
            )
        }
        assertEquals(
            RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.ShowDenied)),
            step(RecordingState.Disarmed, RecordingEvent.Arm(t, PermissionStatus.DeniedOpenSettings)),
        )
    }

    @Test
    fun armingCompletesWithTheRememberedMonitorUnlessTheSpeakerIsTheOutput() {
        assertEquals(RecordingTransition(armed, MonitorState.Off), step(RecordingState.Arming(t), RecordingEvent.InputOpened(1, 48000)))
        assertEquals(
            RecordingTransition(armed, MonitorState.On, listOf(RecordingEffect.SetMonitor(true))),
            step(RecordingState.Arming(t), RecordingEvent.InputOpened(1, 48000), remembered = true),
        )
        assertEquals(
            RecordingTransition(armed, MonitorState.SuspendedSpeaker),
            step(RecordingState.Arming(t), RecordingEvent.InputOpened(1, 48000), remembered = true, speaker = true),
        )
        assertEquals(
            RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.Notify(TakeStopReason.InputLost))),
            step(RecordingState.Arming(t), RecordingEvent.InputFailed),
        )
        assertEquals(
            RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.ShowDenied)),
            step(RecordingState.Arming(t), RecordingEvent.PermissionDenied),
        )
    }

    @Test
    fun aTapOnTheLitToggleDisarmsAndAnotherTrackReArms() {
        assertEquals(
            RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CloseInput)),
            step(armed, RecordingEvent.Arm(t, PermissionStatus.Granted), monitor = MonitorState.On),
        )
        assertEquals(
            RecordingTransition(RecordingState.Arming(u), MonitorState.On, listOf(RecordingEffect.CloseInput, RecordingEffect.OpenInput)),
            step(armed, RecordingEvent.Arm(u, PermissionStatus.Granted), monitor = MonitorState.On),
        )
        assertEquals(
            RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CloseInput)),
            step(armed, RecordingEvent.Disarm, monitor = MonitorState.On),
        )
        // A second arm while the input opens is ignored.
        assertEquals(RecordingTransition(RecordingState.Arming(t), MonitorState.Off), step(RecordingState.Arming(t), RecordingEvent.Arm(u, PermissionStatus.Granted)))
    }

    @Test
    fun recordFromArmedPreRollsWhenStoppedWithACountInAndRecordsAtOnceOtherwise() {
        assertEquals(
            RecordingTransition(preRoll, MonitorState.Off, listOf(RecordingEffect.StartTake(4), RecordingEffect.KeepAlive(true))),
            step(armed, RecordingEvent.Record("take-1", countInBeats = 4, playing = false)),
        )
        val immediate = RecordingState.Recording(t, "take-1", Beats.Zero, 0L)
        assertEquals(
            RecordingTransition(immediate, MonitorState.Off, listOf(RecordingEffect.StartTake(0), RecordingEffect.KeepAlive(true))),
            step(armed, RecordingEvent.Record("take-1", countInBeats = 0, playing = false)),
        )
        assertEquals(
            RecordingTransition(immediate, MonitorState.Off, listOf(RecordingEffect.StartTake(0), RecordingEffect.KeepAlive(true))),
            step(armed, RecordingEvent.Record("take-1", countInBeats = 4, playing = true)),
        )
        // Record never reaches the policy while Disarmed or Arming (the ViewModel arms first); it is ignored.
        assertEquals(RecordingTransition(RecordingState.Disarmed, MonitorState.Off), step(RecordingState.Disarmed, RecordingEvent.Record("x", 0, false)))
        assertEquals(RecordingTransition(RecordingState.Arming(t), MonitorState.Off), step(RecordingState.Arming(t), RecordingEvent.Record("x", 0, false)))
    }

    @Test
    fun thePreRollTicksStartsOrIsCancelledByEveryCancellingEvent() {
        assertEquals(RecordingTransition(preRoll.copy(beatsLeft = 2), MonitorState.Off), step(preRoll, RecordingEvent.PreRollTick(2)))
        assertEquals(
            RecordingTransition(RecordingState.Recording(t, "take-1", Beats(4.0), 1000L), MonitorState.Off),
            step(preRoll, RecordingEvent.TakeStarted(Beats(4.0), 1000L)),
        )
        val cancelled = RecordingTransition(armed, MonitorState.Off, listOf(RecordingEffect.CancelTake, RecordingEffect.KeepAlive(false)))
        for (event in listOf(RecordingEvent.Cancel, RecordingEvent.Stop, RecordingEvent.Record("take-2", 4, false), RecordingEvent.Interrupted, RecordingEvent.InputChanged)) {
            assertEquals(cancelled, step(preRoll, event), "$event")
        }
        // Leaving during a count-in cancels without a dialog and disarms (FR-022).
        for (event in listOf(RecordingEvent.HostHidden, RecordingEvent.ProjectClosing, RecordingEvent.TransportClosed, RecordingEvent.TrackDeleted(t))) {
            assertEquals(
                RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CancelTake, RecordingEffect.KeepAlive(false), RecordingEffect.CloseInput)),
                step(preRoll, event),
                "$event",
            )
        }
        // Arming and disarming are unavailable while the count-in runs (FR-018).
        assertEquals(RecordingTransition(preRoll, MonitorState.Off), step(preRoll, RecordingEvent.Arm(u, PermissionStatus.Granted)))
        assertEquals(RecordingTransition(preRoll, MonitorState.Off), step(preRoll, RecordingEvent.Disarm))
    }

    @Test
    fun stopOrRecordWhileRecordingFinalisesTheTake() {
        val stopped = RecordingTransition(finalising, MonitorState.On, listOf(RecordingEffect.StopTake(TakeStopReason.User)))
        assertEquals(stopped, step(recording, RecordingEvent.Stop, monitor = MonitorState.On))
        assertEquals(stopped, step(recording, RecordingEvent.Record("take-2", 4, true), monitor = MonitorState.On))
        assertEquals(RecordingTransition(recording, MonitorState.On), step(recording, RecordingEvent.Cancel, monitor = MonitorState.On))
    }

    @Test
    fun everyEndingEventWhileRecordingFinalisesWithItsReason() {
        for (reason in listOf(TakeStopReason.MaxLength, TakeStopReason.DiskFull, TakeStopReason.InputLost, TakeStopReason.WriteFailed)) {
            assertEquals(
                RecordingTransition(finalising, MonitorState.Off, listOf(RecordingEffect.StopTake(reason), RecordingEffect.Notify(reason))),
                step(recording, RecordingEvent.TakeEnded(reason)),
                "$reason",
            )
        }
        assertEquals(
            RecordingTransition(finalising, MonitorState.Off, listOf(RecordingEffect.StopTake(TakeStopReason.Interrupted), RecordingEffect.Notify(TakeStopReason.Interrupted))),
            step(recording, RecordingEvent.Interrupted),
        )
        assertEquals(
            RecordingTransition(finalising, MonitorState.Off, listOf(RecordingEffect.StopTake(TakeStopReason.InputLost), RecordingEffect.Notify(TakeStopReason.InputLost))),
            step(recording, RecordingEvent.InputChanged),
        )
        // The five reasons that also disarm once the take is finalised.
        val disarming = mapOf(
            RecordingEvent.PermissionRevoked to TakeStopReason.PermissionRevoked,
            RecordingEvent.TrackDeleted(t) to TakeStopReason.User,
            RecordingEvent.ProjectClosing to TakeStopReason.Leaving,
            RecordingEvent.HostHidden to TakeStopReason.Leaving,
            RecordingEvent.TransportClosed to TakeStopReason.TransportClosed,
        )
        for ((event, reason) in disarming) {
            assertEquals(
                RecordingTransition(finalising, MonitorState.Off, listOf(RecordingEffect.StopTake(reason), RecordingEffect.Notify(reason)), disarmAfterFinalise = true),
                step(recording, event),
                "$event",
            )
        }
        // Another track's deletion changes nothing.
        assertEquals(RecordingTransition(recording, MonitorState.Off), step(recording, RecordingEvent.TrackDeleted(u)))
    }

    @Test
    fun finalisedReturnsToArmedOrDisarmsWhenADisarmIsPending() {
        assertEquals(
            RecordingTransition(armed, MonitorState.On, listOf(RecordingEffect.KeepAlive(false))),
            step(finalising, RecordingEvent.TakeFinalised, monitor = MonitorState.On),
        )
        assertEquals(
            RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.KeepAlive(false), RecordingEffect.CloseInput)),
            step(finalising, RecordingEvent.TakeFinalised, monitor = MonitorState.On, disarmPending = true),
        )
        // Ignored until the take is finalised.
        for (event in listOf(RecordingEvent.Record("x", 0, true), RecordingEvent.Stop, RecordingEvent.Cancel, RecordingEvent.Arm(u, PermissionStatus.Granted), RecordingEvent.Disarm)) {
            assertEquals(RecordingTransition(finalising, MonitorState.Off), step(finalising, event), "$event")
        }
        // A leave while finalising marks the disarm for the finalisation.
        assertEquals(RecordingTransition(finalising, MonitorState.Off, emptyList(), disarmAfterFinalise = true), step(finalising, RecordingEvent.HostHidden))
    }

    @Test
    fun armedPlusALeavingSignalDisarms() {
        for (event in listOf(RecordingEvent.TrackDeleted(t), RecordingEvent.ProjectClosing, RecordingEvent.HostHidden, RecordingEvent.TransportClosed)) {
            assertEquals(
                RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CloseInput)),
                step(armed, event, monitor = MonitorState.On),
                "$event",
            )
        }
        assertEquals(
            RecordingTransition(RecordingState.Disarmed, MonitorState.Off, listOf(RecordingEffect.CloseInput, RecordingEffect.ShowDenied)),
            step(armed, RecordingEvent.PermissionRevoked),
        )
        assertEquals(RecordingTransition(RecordingState.Disarmed, MonitorState.Off), step(RecordingState.Disarmed, RecordingEvent.HostHidden))
    }

    @Test
    fun theSpeakerSuspendsMonitoringAndHeadphonesRestoreTheRememberedValue() {
        for (state in listOf(armed, recording)) {
            assertEquals(
                RecordingTransition(state, MonitorState.SuspendedSpeaker, listOf(RecordingEffect.SetMonitor(false))),
                step(state, RecordingEvent.OutputChanged(isSpeaker = true), monitor = MonitorState.On, remembered = true),
                "$state",
            )
            assertEquals(
                RecordingTransition(state, MonitorState.On, listOf(RecordingEffect.SetMonitor(true))),
                step(state, RecordingEvent.OutputChanged(isSpeaker = false), monitor = MonitorState.SuspendedSpeaker, remembered = true),
                "$state",
            )
            assertEquals(RecordingTransition(state, MonitorState.Off), step(state, RecordingEvent.OutputChanged(isSpeaker = false), monitor = MonitorState.Off, remembered = false))
            assertEquals(RecordingTransition(state, MonitorState.Off), step(state, RecordingEvent.OutputChanged(isSpeaker = true), monitor = MonitorState.Off, remembered = false))
        }
        assertEquals(RecordingTransition(RecordingState.Disarmed, MonitorState.SuspendedSpeaker), step(RecordingState.Disarmed, RecordingEvent.OutputChanged(true), remembered = true))
    }

    @Test
    fun theMonitorToggleIsRefusedOnTheSpeakerAndRememberedOtherwise() {
        assertEquals(
            RecordingTransition(armed, MonitorState.SuspendedSpeaker, listOf(RecordingEffect.ShowMonitorNeedsHeadphones)),
            step(armed, RecordingEvent.MonitorToggle(isSpeaker = true), monitor = MonitorState.SuspendedSpeaker, remembered = true, speaker = true),
        )
        assertEquals(
            RecordingTransition(armed, MonitorState.Off, listOf(RecordingEffect.ShowMonitorNeedsHeadphones)),
            step(armed, RecordingEvent.MonitorToggle(isSpeaker = true), remembered = false, speaker = true),
        )
        assertEquals(
            RecordingTransition(armed, MonitorState.On, listOf(RecordingEffect.RememberMonitor(true), RecordingEffect.SetMonitor(true))),
            step(armed, RecordingEvent.MonitorToggle(isSpeaker = false), remembered = false),
        )
        assertEquals(
            RecordingTransition(recording, MonitorState.Off, listOf(RecordingEffect.RememberMonitor(false), RecordingEffect.SetMonitor(false))),
            step(recording, RecordingEvent.MonitorToggle(isSpeaker = false), monitor = MonitorState.On, remembered = true),
        )
        // Disarmed: only the remembered value changes; nothing to monitor yet.
        assertEquals(
            RecordingTransition(RecordingState.Disarmed, MonitorState.On, listOf(RecordingEffect.RememberMonitor(true))),
            step(RecordingState.Disarmed, RecordingEvent.MonitorToggle(isSpeaker = false), remembered = false),
        )
    }

    @Test
    fun theStateKnowsItsArmedTrackAndWhetherATakeRuns() {
        assertEquals(null, RecordingState.Disarmed.armedTrack)
        assertEquals(t, RecordingState.Arming(t).armedTrack)
        assertEquals(t, preRoll.armedTrack)
        assertFalse(armed.takeRunning)
        assertFalse(RecordingState.Arming(t).takeRunning)
        assertTrue(preRoll.takeRunning)
        assertTrue(recording.takeRunning)
        assertTrue(finalising.takeRunning)
    }
}
