package audio.tembra.ui.navigation

import androidx.lifecycle.ViewModel
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.FakeAudioFilePicker
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.session.ProjectSession
import audio.tembra.ui.timeline.TimelineViewModel
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass

/**
 * The ViewModel factory a shell test installs in place of the Metro graph: the Timeline's ViewModel over the fakes,
 * with a session that is never started, so the destination composes with no project and no engine.
 */
class TestViewModelFactory : MetroViewModelFactory() {
    private val engine = FakeAudioEngine()
    private val session = ProjectSession(
        repository = FakeProjectRepository(),
        audio = engine,
        metronome = engine,
        preferences = FakeMetronomePreferences(),
        host = FakeBackgroundPlaybackHost(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>
        get() = mapOf(TimelineViewModel::class to { TimelineViewModel(session, FakeAudioFilePicker()) })

    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>
        get() = emptyMap()

    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>
        get() = emptyMap()
}
