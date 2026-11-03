package audio.tembra.ui.di

import audio.tembra.data.AppDirectories
import audio.tembra.data.metronome.CustomClickSoundStore
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.projects.ProjectRepository
import audio.tembra.data.tuner.TunerPreferences
import audio.tembra.engine.AudioEngine
import audio.tembra.ui.platform.AudioFilePicker
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.TrackpadPinchSource
import audio.tembra.ui.platform.WindowControls
import audio.tembra.ui.projects.ProjectsSeeder
import audio.tembra.ui.session.AppStartup
import audio.tembra.ui.session.ProjectSession
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
    val backgroundPlaybackHost: BackgroundPlaybackHost
    val audioFilePicker: AudioFilePicker
    val windowControls: WindowControls
    val trackpadPinchSource: TrackpadPinchSource
    val projectRepository: ProjectRepository
    val projectSession: ProjectSession
    val appStartup: AppStartup
    val projectsSeeder: ProjectsSeeder
}

/**
 * The app-wide objects the screens' ViewModels are built from: the directories, the preferences file and the stores on
 * it, the engine, the playback host and the microphone permission. Metro builds each on its first use, which was the
 * frame that opened a screen: building the DataStore alone (its file, the first use of `Dispatchers.IO`) was half of
 * the frame that opened the Metronome on a Pixel 6 Pro, and the permission's first look at the audio session 8 ms of
 * the frame that opened the Tuner on an iPhone 14. [App] asks for them once off the main thread after the first frame;
 * each is built once, on whichever thread asks first.
 */
internal fun AppGraph.prebuiltForScreens(): List<Any> = listOf(
    appDirectories, tunerPreferences, metronomePreferences, customClickSoundStore, audioEngine, backgroundPlaybackHost,
    microphonePermission,
)
