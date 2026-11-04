package audio.tembra.ui.tuner

import audio.tembra.core.tuner.TunerMode
import audio.tembra.engine.TestSignal
import audio.tembra.ui.platform.MicrophonePermissionStatus

/**
 * Everything the listening policy needs to decide what the audio should be doing (data-model.md §3). [foreground]
 * follows `ON_START`/`ON_STOP` of the screen's lifecycle (`TunerScreen`'s `LifecycleStartEffect`), never pause: a
 * desktop window that loses focus to another window only pauses on Android (multi-resume) and on iOS (resign-active
 * is `ON_PAUSE` in Compose Multiplatform), so listening goes on; minimising stops the activity or backgrounds the
 * scene (`ON_STOP`), so listening and a looping tone stop (feature 011, spec FR-006/FR-009, `ListeningForegroundTest`).
 */
data class PolicyInputs(
    val mode: TunerMode,
    val screenVisible: Boolean,
    val foreground: Boolean,
    val permission: MicrophonePermissionStatus,
    val audition: AuditionState?,
    val interrupted: Boolean,
    val testSignal: TestSignal? = null,
)
