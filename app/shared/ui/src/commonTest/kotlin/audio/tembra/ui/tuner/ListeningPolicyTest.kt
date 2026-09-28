package audio.tembra.ui.tuner

import audio.tembra.core.tuner.TunerMode
import audio.tembra.engine.TestSignals
import audio.tembra.ui.platform.MicrophonePermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every row of the policy table in data-model.md §3 (research R7). */
class ListeningPolicyTest {

    private val signal = TestSignals.byId("e2-in-tune")!!.signal
    private val audition = AuditionState(0, true, ReferenceSound.Synth(82.41))

    private fun inputs(
        mode: TunerMode = TunerMode.AutoListen,
        screenVisible: Boolean = true,
        foreground: Boolean = true,
        permission: MicrophonePermissionStatus = MicrophonePermissionStatus.Granted,
        audition: AuditionState? = null,
        interrupted: Boolean = false,
        testSignal: audio.tembra.engine.TestSignal? = null,
    ) = PolicyInputs(mode, screenVisible, foreground, permission, audition, interrupted, testSignal)

    @Test
    fun listensOnlyWhenEverythingAllowsIt() {
        assertEquals(DesiredAudio.Listening, ListeningPolicy.desired(inputs()))
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(screenVisible = false)))
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(foreground = false)))
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(interrupted = true)))
        for (denied in listOf(MicrophonePermissionStatus.NotDetermined, MicrophonePermissionStatus.DeniedCanAsk, MicrophonePermissionStatus.DeniedOpenSettings)) {
            assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(permission = denied)), denied.name)
        }
    }

    @Test
    fun byEarSoundsATonePreciselyWhenAnAuditionIsSet() {
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(mode = TunerMode.ByEar)))
        for (permission in MicrophonePermissionStatus.entries) {
            assertEquals(
                DesiredAudio.Tone(audition.sound, audition.loop),
                ListeningPolicy.desired(inputs(mode = TunerMode.ByEar, permission = permission, audition = audition)),
                permission.name,
            )
        }
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(mode = TunerMode.ByEar, audition = audition, screenVisible = false)))
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(mode = TunerMode.ByEar, audition = audition, foreground = false)))
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(mode = TunerMode.ByEar, audition = audition, interrupted = true)))
    }

    @Test
    fun aTestSignalListensWithoutThePermission() {
        for (permission in listOf(MicrophonePermissionStatus.NotDetermined, MicrophonePermissionStatus.DeniedOpenSettings, MicrophonePermissionStatus.Granted)) {
            assertEquals(DesiredAudio.TestListening(signal), ListeningPolicy.desired(inputs(permission = permission, testSignal = signal)), permission.name)
        }
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(testSignal = signal, screenVisible = false)))
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(testSignal = signal, foreground = false)))
        assertEquals(DesiredAudio.Idle, ListeningPolicy.desired(inputs(mode = TunerMode.ByEar, testSignal = signal)))
    }
}
