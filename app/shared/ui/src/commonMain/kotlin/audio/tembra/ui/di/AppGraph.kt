package audio.tembra.ui.di

import audio.tembra.data.AppDirectories
import audio.tembra.data.metronome.CustomClickSoundStore
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.tuner.TunerPreferences
import audio.tembra.engine.AudioEngine
import audio.tembra.ui.platform.MicrophonePermission
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The application dependency graph shared by both platforms (Metro, `AppScope`).
 * Platform graphs (`IosAppGraph`, `AndroidAppGraph`) extend it and are created once per process.
 */
interface AppGraph : ViewModelGraph {
    val appDirectories: AppDirectories
    val audioEngine: AudioEngine
    val tunerPreferences: TunerPreferences
    val metronomePreferences: MetronomePreferences
    val customClickSoundStore: CustomClickSoundStore
    val microphonePermission: MicrophonePermission
}
