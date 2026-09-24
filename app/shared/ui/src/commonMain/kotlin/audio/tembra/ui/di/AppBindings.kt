package audio.tembra.ui.di

import androidx.lifecycle.ViewModel
import audio.tembra.engine.AudioEngine
import audio.tembra.engine.NativeAudioEngine
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlin.reflect.KClass

/** Shared bindings contributed to every platform graph. */
@BindingContainer
@ContributesTo(AppScope::class)
object AppBindings {

    /** One engine facade per process (Constitution II); the native library is loaded on first use. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioEngine(): AudioEngine = NativeAudioEngine()

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
