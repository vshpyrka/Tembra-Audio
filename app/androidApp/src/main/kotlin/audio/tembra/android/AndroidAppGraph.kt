package audio.tembra.android

import android.content.Context
import audio.tembra.data.AndroidAppDirectories
import audio.tembra.data.AppDirectories
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.NativeAudioEngine
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.platform.AudioRouteMonitor
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.AudioFilePicker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Android application graph: created once in [TembraApplication] with the application context. */
@DependencyGraph(AppScope::class)
interface AndroidAppGraph : AppGraph {

    /** The activity attaches its permission launcher here (research R6). */
    val microphonePermissionHost: MicrophonePermissionHost

    /** The activity attaches its document-picker launchers here (contracts/project-storage.md). */
    val audioFilePickerHost: AudioFilePickerHost

    /** The activity attaches its notifications-permission launcher here (contracts/background-playback.md). */
    val notificationPermissionHost: NotificationPermissionHost

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

    /** The media-playback foreground service keeps the metronome and the transport running with the screen off (research R6). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideBackgroundPlaybackHost(context: Context, notifications: NotificationPermissionHost): BackgroundPlaybackHost =
        AndroidBackgroundPlaybackHost(context, notifications)

    /** A grant re-posts the running service's notification, which stayed hidden without the permission. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideNotificationPermissionHost(context: Context): NotificationPermissionHost =
        NotificationPermissionHost { AndroidBackgroundPlaybackHost.repostRunningNotification(context) }

    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioFilePickerHost(): AudioFilePickerHost = AudioFilePickerHost()

    /** The document picker for the timeline's audio and the metronome's custom click sound. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioFilePicker(host: AudioFilePickerHost, context: Context): AudioFilePicker = AndroidAudioFilePicker(host, context)

    /** The device routes for the recording controller (feature 010, research R12). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioRouteMonitor(context: Context): AudioRouteMonitor = AndroidAudioRouteMonitor(context)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AndroidAppGraph
    }
}
