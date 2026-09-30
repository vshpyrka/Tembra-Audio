package audio.tembra.android

import android.content.Context
import audio.tembra.data.AndroidAppDirectories
import audio.tembra.data.AppDirectories
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.NativeAudioEngine
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.SoundFilePicker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Android application graph: created once in [TembraApplication] with the application context. */
@DependencyGraph(AppScope::class)
interface AndroidAppGraph : AppGraph {

    /** The activity attaches its permission launcher here (research R6). */
    val microphonePermissionHost: MicrophonePermissionHost

    /** The activity attaches its document-picker launcher here (contracts/click-sounds.md). */
    val soundFilePickerHost: SoundFilePickerHost

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDirectories(context: Context): AppDirectories = AndroidAppDirectories(context)

    /** One engine facade per process (Constitution II); the native library is loaded on first use. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioEngine(context: Context): AudioEngine = NativeAudioEngine(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMicrophonePermissionHost(): MicrophonePermissionHost = MicrophonePermissionHost()

    @Provides
    @SingleIn(AppScope::class)
    fun provideMicrophonePermission(context: Context, host: MicrophonePermissionHost): MicrophonePermission =
        AndroidMicrophonePermission(context, host)

    /** The media-playback foreground service keeps the metronome running with the screen off (research R6). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideBackgroundPlaybackHost(context: Context): BackgroundPlaybackHost = AndroidBackgroundPlaybackHost(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSoundFilePickerHost(): SoundFilePickerHost = SoundFilePickerHost()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSoundFilePicker(host: SoundFilePickerHost, context: Context): SoundFilePicker = AndroidSoundFilePicker(host, context)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AndroidAppGraph
    }
}
