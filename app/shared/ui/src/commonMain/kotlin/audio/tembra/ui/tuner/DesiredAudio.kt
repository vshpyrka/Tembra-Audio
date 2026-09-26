package audio.tembra.ui.tuner

import audio.tembra.engine.TestSignal

/** What the policy wants the engine to do; the ViewModel applies transitions between these (data-model.md §3). */
sealed interface DesiredAudio {
    data object Idle : DesiredAudio

    data object Listening : DesiredAudio

    /** Listening through the synthetic device (debug builds, research R16). */
    data class TestListening(val signal: TestSignal) : DesiredAudio

    data class Tone(val sound: ReferenceSound, val loop: Boolean) : DesiredAudio
}
