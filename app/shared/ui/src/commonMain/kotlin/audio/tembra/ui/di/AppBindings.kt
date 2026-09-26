package audio.tembra.ui.di

import androidx.lifecycle.ViewModel
import audio.tembra.data.AppDirectories
import audio.tembra.data.tuner.TembraDataStore
import audio.tembra.data.tuner.TunerPreferences
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.TunerAudio
import audio.tembra.ui.tuner.ComposeResourceReader
import audio.tembra.ui.tuner.ReferenceSoundRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/** Shared bindings contributed to every platform graph; the engine itself is provided by each platform graph (research R15). */
@BindingContainer
@ContributesTo(AppScope::class)
object AppBindings {

    /** The tuner programs against the [TunerAudio] seam (research R2); the engine is that seam. */
    @Provides
    fun provideTunerAudio(engine: AudioEngine): TunerAudio = engine

    /** The tuner's remembered choices in the app's one DataStore file (research R10). */
    @Provides
    @SingleIn(AppScope::class)
    fun provideTunerPreferences(directories: AppDirectories): TunerPreferences =
        TembraDataStore.tunerPreferences(directories.preferences)

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
