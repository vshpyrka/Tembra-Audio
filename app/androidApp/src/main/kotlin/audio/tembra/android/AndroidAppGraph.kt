package audio.tembra.android

import android.content.Context
import audio.tembra.data.AndroidAppDirectories
import audio.tembra.data.AppDirectories
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.NativeAudioEngine
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.platform.MicrophonePermission
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Android application graph: created once in [TembraApplication] with the application context. */
@DependencyGraph(AppScope::class)
interface AndroidAppGraph : AppGraph {

    /** The activity attaches its permission launcher here (research R6). */
    val microphonePermissionHost: MicrophonePermissionHost

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

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AndroidAppGraph
    }
}
