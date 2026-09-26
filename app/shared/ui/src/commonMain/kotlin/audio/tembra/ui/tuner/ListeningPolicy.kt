package audio.tembra.ui.tuner

import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.platform.MicrophonePermissionStatus

/**
 * The microphone lease as a pure function (research R7): the engine listens only while the Tuner entry is visible,
 * the app is started, Auto Listen is selected, access is granted and no interruption is active; By Ear sounds a tone
 * only while an audition is set under the same visibility rules. A test signal needs no permission.
 */
object ListeningPolicy {
    fun desired(inputs: PolicyInputs): DesiredAudio {
        if (!inputs.screenVisible || !inputs.foreground || inputs.interrupted) return DesiredAudio.Idle
        return when (inputs.mode) {
            TunerMode.ByEar -> inputs.audition?.let { DesiredAudio.Tone(it.sound, it.loop) } ?: DesiredAudio.Idle
            TunerMode.AutoListen -> when {
                inputs.testSignal != null -> DesiredAudio.TestListening(inputs.testSignal)
                inputs.permission == MicrophonePermissionStatus.Granted -> DesiredAudio.Listening
                else -> DesiredAudio.Idle
            }
        }
    }
}
