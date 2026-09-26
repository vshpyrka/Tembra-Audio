package audio.tembra.ui.tuner

import audio.tembra.core.tuner.TunerMode
import audio.tembra.engine.TestSignal
import audio.tembra.ui.platform.MicrophonePermissionStatus

/** Everything the listening policy needs to decide what the audio should be doing (data-model.md §3). */
data class PolicyInputs(
    val mode: TunerMode,
    val screenVisible: Boolean,
    val foreground: Boolean,
    val permission: MicrophonePermissionStatus,
    val audition: AuditionState?,
    val interrupted: Boolean,
    val testSignal: TestSignal? = null,
)
