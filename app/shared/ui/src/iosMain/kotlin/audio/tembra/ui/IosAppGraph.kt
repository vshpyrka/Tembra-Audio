package audio.tembra.ui

import audio.tembra.data.AppDirectories
import audio.tembra.data.IosAppDirectories
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.NativeAudioEngine
import audio.tembra.ui.di.AppGraph
import audio.tembra.ui.drop.DroppedFilesReader
import audio.tembra.ui.drop.IosDroppedFilesReader
import audio.tembra.ui.drop.IosOpenedFiles
import audio.tembra.ui.drop.OpenedFiles
import audio.tembra.ui.platform.AudioFilePicker
import audio.tembra.ui.platform.AudioRouteMonitor
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.IosAudioRouteMonitor
import audio.tembra.ui.platform.IosAudioFilePicker
import audio.tembra.ui.platform.IosBackgroundPlaybackHost
import audio.tembra.ui.platform.IosMicrophonePermission
import audio.tembra.ui.platform.IosTrackpadPinchSource
import audio.tembra.ui.platform.IosWindowControls
import audio.tembra.ui.platform.MicrophonePermission
import audio.tembra.ui.platform.TrackpadPinchSource
import audio.tembra.ui.platform.WindowControls
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

    /** The `.playback` session and `UIBackgroundModes = [audio]` keep the metronome and the transport running (research R6). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideBackgroundPlaybackHost(): BackgroundPlaybackHost = IosBackgroundPlaybackHost()

    /** The Files picker for the timeline's audio and the custom click sound (contracts/project-storage.md). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioFilePicker(): AudioFilePicker = IosAudioFilePicker()

    /** The session's current route for the recording controller (feature 010, research R12). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioRouteMonitor(): AudioRouteMonitor = IosAudioRouteMonitor()
    /** iPadOS's own window controls give full screen; the app requests none (feature 011, research R21). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideWindowControls(): WindowControls = IosWindowControls()

    /** The shell's trackpad pinch bridge reports here (feature 011, research R9); deleted with Compose 1.13. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideIosTrackpadPinchSource(): IosTrackpadPinchSource = IosTrackpadPinchSource()

    @Provides
    fun provideTrackpadPinchSource(source: IosTrackpadPinchSource): TrackpadPinchSource = source

    /** The shell's bridge reaches the source through this. */
    val iosTrackpadPinchSource: IosTrackpadPinchSource

    /** Audio files dropped onto the timeline or the library, copied into the cache (feature 011, spec FR-026). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideDroppedFilesReader(directories: AppDirectories): DroppedFilesReader = IosDroppedFilesReader(directories.cache)

    @Provides
    @SingleIn(AppScope::class)
    fun provideIosOpenedFiles(): IosOpenedFiles = IosOpenedFiles()

    /** Audio opened with the app from the Files app (feature 011, spec FR-026a). */
    @Provides
    fun provideOpenedFiles(opened: IosOpenedFiles): OpenedFiles = opened

    /** The shell's `.onOpenURL` reaches the opened files through this. */
    val iosOpenedFiles: IosOpenedFiles
}
