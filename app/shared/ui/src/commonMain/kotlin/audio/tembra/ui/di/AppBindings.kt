package audio.tembra.ui.di

import androidx.lifecycle.ViewModel
import audio.tembra.data.AppDirectories
import audio.tembra.data.metronome.CustomClickSoundStore
import audio.tembra.data.metronome.DataStoreMetronomePreferences
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.metronome.OkioCustomClickSoundStore
import audio.tembra.data.tuner.DataStoreTunerPreferences
import audio.tembra.data.tuner.TembraDataStore
import audio.tembra.data.tuner.TunerPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.MetronomeAudio
import audio.tembra.engine.TunerAudio
import audio.tembra.ui.tuner.ComposeResourceReader
import audio.tembra.ui.tuner.ReferenceSoundRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass

/** Shared bindings contributed to every platform graph; the engine itself is provided by each platform graph (research R15). */
@BindingContainer
@ContributesTo(AppScope::class)
object AppBindings {

    /** The tuner programs against the [TunerAudio] seam (research R2); the engine is that seam. */
    @Provides
    fun provideTunerAudio(engine: AudioEngine): TunerAudio = engine

    /**
     * The process-long scope for infrastructure that must own one (DataStore); the graph is its owner, so a reader
     * can find who starts it and a test harness could cancel it.
     */
    @Provides
    @SingleIn(AppScope::class)
    @Named("app")
    fun provideAppCoroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** The app's one preferences file; DataStore allows a single instance per file, so every feature builds on this one (research R10). */
    @Provides
    @SingleIn(AppScope::class)
    fun providePreferencesDataStore(directories: AppDirectories, @Named("app") scope: CoroutineScope): DataStore<Preferences> =
        TembraDataStore.preferences(directories.preferences, scope)

    /** The tuner's remembered choices in the app's one DataStore file (research R10). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideTunerPreferences(dataStore: DataStore<Preferences>): TunerPreferences = DataStoreTunerPreferences(dataStore)

    /** The metronome's remembered state in the same file. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideMetronomePreferences(dataStore: DataStore<Preferences>): MetronomePreferences = DataStoreMetronomePreferences(dataStore)

    /** The app's copy of the imported click sound. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideCustomClickSoundStore(directories: AppDirectories): CustomClickSoundStore = OkioCustomClickSoundStore(directories)

    /** The metronome programs against the [MetronomeAudio] seam; the engine is that seam. */
    @Provides
    fun provideMetronomeAudio(engine: AudioEngine): MetronomeAudio = engine

    /** Drop-in recordings or the synthesized tone (research R5). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideReferenceSoundRepository(): ReferenceSoundRepository = ReferenceSoundRepository(ComposeResourceReader)

    /** Bridges Metro's ViewModel multibindings to the factory that `metroViewModel()` uses in Compose. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideMetroViewModelFactory(
        viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
        assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
        manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
    ): MetroViewModelFactory = object : MetroViewModelFactory() {
        override val viewModelProviders get() = viewModelProviders
        override val assistedFactoryProviders get() = assistedFactoryProviders
        override val manualAssistedFactoryProviders get() = manualAssistedFactoryProviders
    }
}
