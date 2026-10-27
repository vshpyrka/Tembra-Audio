package audio.tembra.ui.tracksettings

import audio.tembra.core.project.GainDb
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.PlaybackState
import audio.tembra.core.project.TrackId
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.projects.DemoProject
import audio.tembra.data.projects.DemoProjectIds
import audio.tembra.data.projects.FakeProjectRepository
import audio.tembra.data.projects.WaveformOverview
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.session.AppStartup
import audio.tembra.ui.session.ProjectSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The track-settings form over the session and the fakes (spec FR-020, FR-023 to FR-025, FR-041, FR-042; T091). */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val demo = DemoProject.build(DemoProjectIds.fixed())
    private val drumsTrack = demo.tracks[0].id
    private val overview = WaveformOverview(channels = 1, frames = 512, completedBaseBuckets = 2, levels = emptyList())
    private val engine = FakeAudioEngine()
    private val repository = FakeProjectRepository(initial = listOf(demo))
    private val session = ProjectSession(
        repository = repository,
        audio = engine,
        metronome = engine,
        preferences = FakeMetronomePreferences(),
        host = FakeBackgroundPlaybackHost(),
        scope = scope,
        overviewReader = { overview },
        ioDispatcher = dispatcher,
    )
    private lateinit var viewModel: TrackSettingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = TrackSettingsViewModel(session)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            viewModel.screenVisible(false)
        }
    }

    private fun TestScope.ui(): TrackSettingsUiState {
        runCurrent()
        return viewModel.uiState.value
    }

    private fun TestScope.openDrums(trackId: TrackId = drumsTrack) {
        scope.launch { repository.setOpen(demo.id) }
        runCurrent()
        AppStartup(session, repository, scope).start()
        advanceUntilIdle()
        viewModel.open(trackId)
        viewModel.screenVisible(true)
        runCurrent()
    }

    private fun trackName(): String? = session.state.value.project?.tracks?.firstOrNull { it.id == drumsTrack }?.name

    @Test
    fun theFieldsComeFromTheSessionsTrack() = test {
        openDrums()
        val state = ui()
        assertEquals(drumsTrack, state.trackId)
        assertEquals("Drums", state.name)
        assertEquals("Drums", state.nameField)
        assertEquals(InstrumentLabel.Drums, state.label)
        assertEquals(0, state.colorIndex)
        assertEquals(GainDb.Unity, state.gainDb)
        assertFalse(state.muted)
        assertFalse(state.soloed)
        assertFalse(state.closed)
        assertEquals("00:00.00 / 00:30.96", state.transport.readoutText)
        assertTrue(state.transport.hasProject)
    }

    @Test
    fun theNameCommitsAsOneEditAndBlankFallsBackToTrackN() = test {
        openDrums()
        viewModel.editName("Kick")
        assertEquals("Kick", ui().nameField, "the draft shows while typing")
        assertEquals("Drums", trackName(), "nothing is edited until the field commits")
        viewModel.commitName()
        assertEquals("Kick", trackName())
        assertNull(ui().nameDraft)
        assertTrue(session.state.value.canUndo)
        viewModel.editName("   ")
        viewModel.commitName()
        assertEquals("Track 1", trackName())
        viewModel.editName("x".repeat(60))
        assertEquals(40, ui().nameField.length, "the draft is cut at 40 characters")
    }

    @Test
    fun theDraftTravelsThroughTheSessionToTheOtherForm() = test {
        openDrums()
        viewModel.editName("Ki")
        assertEquals("Ki", session.settingsDraft.value?.name)
        val other = TrackSettingsViewModel(session)
        other.open(drumsTrack)
        runCurrent()
        assertEquals("Ki", other.uiState.value.nameField, "the inline form takes over the typed name")
        other.commitName()
        assertEquals("Ki", trackName())
        assertNull(session.settingsDraft.value)
    }

    @Test
    fun labelGainMuteAndSoloAreEachOneEdit() = test {
        openDrums()
        viewModel.setLabel(InstrumentLabel.Guitar)
        assertEquals(InstrumentLabel.Guitar, ui().label)
        viewModel.setLabel(null)
        assertNull(ui().label)
        assertEquals("None", ui().labelText)
        viewModel.setGain(GainDb(-6f))
        assertEquals(GainDb(-6f), ui().gainDb)
        assertEquals("setTrack(0,0.501,true)", engine.calls.last())
        viewModel.setMuted(true)
        assertTrue(ui().muted)
        viewModel.setSoloed(true)
        assertTrue(ui().soloed)
        session.undo()
        assertFalse(ui().soloed, "the solo was the last edit")
        session.undo()
        assertFalse(ui().muted)
    }

    @Test
    fun deleteAsksThenClosesTheForm() = test {
        openDrums()
        viewModel.requestDelete()
        assertTrue(ui().confirmDelete)
        viewModel.dismissDelete()
        assertFalse(ui().confirmDelete)
        assertEquals(4, session.state.value.project?.tracks?.size)
        viewModel.requestDelete()
        viewModel.confirmDelete()
        val state = ui()
        assertTrue(state.closed)
        assertFalse(state.confirmDelete)
        assertEquals(3, session.state.value.project?.tracks?.size)
    }

    @Test
    fun anUnknownTrackCloses() = test {
        openDrums(TrackId("gone"))
        assertTrue(ui().closed)
    }

    @Test
    fun theTransportBarWorksFromTheNestedScreen() = test {
        openDrums()
        viewModel.playPause()
        assertEquals(PlaybackState.Playing, ui().transport.playback)
        viewModel.playPause()
        assertEquals(PlaybackState.Stopped(audio.tembra.core.project.Beats.Zero), ui().transport.playback)
        viewModel.setMasterVolume(40)
        assertEquals(40, ui().transport.masterVolume)
        viewModel.toggleMetronome()
        assertTrue(ui().transport.metronomeOn)
    }
}
