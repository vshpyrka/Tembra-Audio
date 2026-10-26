package audio.tembra.ui.navigation

import androidx.lifecycle.ViewModel
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.timeline.FakeTimelinePreferences
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.FakeAudioFilePicker
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.projects.LibraryViewStateHolder
import audio.tembra.ui.projects.ProjectsViewModel
import audio.tembra.ui.session.ProjectSession
import audio.tembra.ui.timeline.TimelineViewModel
import audio.tembra.ui.tracksettings.TrackSettingsViewModel
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.reflect.KClass

/**
 * The ViewModel factory a shell test installs in place of the Metro graph: the Timeline's ViewModel over the fakes,
 * with a session that is not started, so the destination composes with no project and no engine; a test that needs
 * content calls [openDemo] first.
 */
class TestViewModelFactory : MetroViewModelFactory() {
    private val engine = FakeAudioEngine()
    private val preferences = FakeMetronomePreferences()
    private val timelinePreferences = FakeTimelinePreferences()
    private val repository = FakeProjectRepository()
    private val holder = LibraryViewStateHolder()
    private val session = ProjectSession(
        repository = repository,
        audio = engine,
        metronome = engine,
        preferences = preferences,
        host = FakeBackgroundPlaybackHost(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    /** Starts the session and opens the fake repository's demo project, so the Timeline composes with its clips. */
    suspend fun openDemo() {
        session.start()
        session.open(repository.installDemo().id)
    }

    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>
        get() = mapOf(
            TimelineViewModel::class to { TimelineViewModel(session, FakeAudioFilePicker(), preferences, timelinePreferences) },
            TrackSettingsViewModel::class to { TrackSettingsViewModel(session) },
            ProjectsViewModel::class to { ProjectsViewModel(repository, session, holder) },
        )

    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>
        get() = emptyMap()

    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>
        get() = emptyMap()
}
