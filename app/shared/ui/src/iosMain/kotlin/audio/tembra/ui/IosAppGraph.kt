package audio.tembra.ui

import audio.tembra.data.AppDirectories
import audio.tembra.data.IosAppDirectories
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.NativeAudioEngine
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.IosBackgroundPlaybackHost
import audio.tembra.ui.platform.IosMicrophonePermission
import audio.tembra.ui.platform.IosSoundFilePicker
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.SoundFilePicker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** iOS application graph: created once in [MainViewController]. */
@DependencyGraph(AppScope::class)
interface IosAppGraph : AppGraph {
    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDirectories(): AppDirectories = IosAppDirectories()

    /** One engine facade per process (Constitution II); the static archive is linked into the framework. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioEngine(): AudioEngine = NativeAudioEngine()

    @Provides
    @SingleIn(AppScope::class)
    fun provideMicrophonePermission(): MicrophonePermission = IosMicrophonePermission()

    /** The `.playback` session and `UIBackgroundModes = [audio]` keep the metronome running (research R6). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideBackgroundPlaybackHost(): BackgroundPlaybackHost = IosBackgroundPlaybackHost()

    /** The Files picker for the custom click sound (contracts/click-sounds.md). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideSoundFilePicker(): SoundFilePicker = IosSoundFilePicker()
}
