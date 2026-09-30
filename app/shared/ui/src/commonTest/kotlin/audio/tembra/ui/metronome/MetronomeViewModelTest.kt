package audio.tembra.ui.metronome

import androidx.lifecycle.ViewModelStore
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.Tempo
import audio.tembra.data.metronome.FakeCustomClickSoundStore
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.metronome.MetronomePreferencesSnapshot
import audio.tembra.engine.AudioResult
import audio.tembra.engine.AudioState
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.platform.FakeSoundFilePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ViewModel against the fakes (contracts/metronome-screen.md), User Story 1: loading, tempo intents and
 * persistence, the start and stop sequences, refused starts, the 10 Hz poll and the draw-phase telemetry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var audio: FakeAudioEngine
    private lateinit var fakeHost: FakeBackgroundPlaybackHost
    private lateinit var host: RecordingHost
    private lateinit var preferences: CountingPreferences
    private lateinit var store: FakeCustomClickSoundStore
    private lateinit var picker: FakeSoundFilePicker
    private var created: MetronomeViewModel? = null

    /** Counts writes so a test can prove an intent persisted nothing. */
    private class CountingPreferences(private val inner: FakeMetronomePreferences) : MetronomePreferences {
        var updates = 0
            private set
        override val snapshot: Flow<MetronomePreferencesSnapshot> = inner.snapshot
        override suspend fun update(transform: (MetronomePreferencesSnapshot) -> MetronomePreferencesSnapshot) {
            updates++
            inner.update(transform)
        }
    }

    /** Records each host call with the number of engine calls made before it, so the two logs can be interleaved. */
    private class RecordingHost(private val inner: FakeBackgroundPlaybackHost, private val audio: FakeAudioEngine) : BackgroundPlaybackHost by inner {
        val log = mutableListOf<Pair<String, Int>>()
        override fun keepAlive(): Boolean {
            log += "keepAlive" to audio.calls.size
            return inner.keepAlive()
        }
        override fun release() {
            log += "release" to audio.calls.size
            inner.release()
        }
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        audio = FakeAudioEngine()
        fakeHost = FakeBackgroundPlaybackHost()
        host = RecordingHost(fakeHost, audio)
        preferences = CountingPreferences(FakeMetronomePreferences())
        store = FakeCustomClickSoundStore()
        picker = FakeSoundFilePicker()
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): MetronomeViewModel {
        val viewModel = MetronomeViewModel(audio, host, preferences, store, picker, dispatcher)
        created = viewModel
        runCurrent()
        return viewModel
    }

    private fun TestScope.show(viewModel: MetronomeViewModel) {
        viewModel.screenVisible(true)
        runCurrent()
    }

    /** The poll is periodic, so virtual time is stepped with `runCurrent` and `advanceTimeBy`, never `advanceUntilIdle`. */
    private fun viewModelTest(block: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            block()
        } finally {
            created?.clear()
            created = null
            runCurrent()
        }
    }

    /** Clears the ViewModel the way its store would (`onCleared` is protected). */
    private fun MetronomeViewModel.clear() = ViewModelStore().apply { put("metronome", this@clear) }.clear()

    private fun TestScope.collectMessages(viewModel: MetronomeViewModel): List<MetronomeMessage> {
        val messages = mutableListOf<MetronomeMessage>()
        backgroundScope.launch { viewModel.messages.collect { messages += it } }
        runCurrent()
        return messages
    }

    private fun startMetronomeCount() = audio.calls.count { it == "startMetronome" }

    @Test
    fun nothingHappensBeforeTheScreenIsVisible() = viewModelTest {
        val viewModel = viewModel()
        viewModel.togglePlayback()
        viewModel.setTempo(Tempo(90))
        runCurrent()
        assertEquals(emptyList(), audio.calls)
        assertFalse(viewModel.uiState.value.loaded)
        assertEquals(0, fakeHost.keepAliveCount)
        assertEquals(0, preferences.updates)
    }

    @Test
    fun theFirstPreferencesEmissionLoadsAndProgramsTheEngine() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        val state = viewModel.uiState.value
        assertTrue(state.loaded)
        assertEquals(MetronomeSettings(), state.settings)
        assertEquals(listOf("setGrid(bpm=120, ticks=1, levels=ACCC)", "selectSound(ClassicWood)"), audio.calls)
    }

    @Test
    fun storedSettingsAreApplied() = viewModelTest {
        preferences = CountingPreferences(FakeMetronomePreferences(MetronomePreferencesSnapshot(MetronomeSettings(tempo = Tempo(90)))))
        val viewModel = viewModel()
        show(viewModel)
        assertEquals(Tempo(90), viewModel.uiState.value.settings.tempo)
        assertEquals("setGrid(bpm=90, ticks=1, levels=ACCC)", audio.calls.first())
    }

    @Test
    fun setTempoPersistsAndReachesARunningEngine() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        viewModel.setTempo(Tempo(90))
        runCurrent()
        assertEquals(Tempo(90), viewModel.uiState.value.settings.tempo)
        assertEquals(Tempo(90), preferences.snapshot.first().settings.tempo)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        viewModel.setTempo(Tempo(200))
        runCurrent()
        assertEquals("setGrid(bpm=200, ticks=1, levels=ACCC)", audio.calls.last())
        assertEquals(Tempo(200), preferences.snapshot.first().settings.tempo)
    }

    @Test
    fun stepTempoStopsAtTheEndOfTheRange() = viewModelTest {
        preferences = CountingPreferences(FakeMetronomePreferences(MetronomePreferencesSnapshot(MetronomeSettings(tempo = Tempo(240)))))
        val viewModel = viewModel()
        show(viewModel)
        val callsBefore = audio.calls.size
        viewModel.stepTempo(1)
        runCurrent()
        assertEquals(Tempo(240), viewModel.uiState.value.settings.tempo)
        assertEquals(0, preferences.updates)
        assertEquals(callsBefore, audio.calls.size)
        viewModel.stepTempo(-1)
        runCurrent()
        assertEquals(Tempo(239), viewModel.uiState.value.settings.tempo)
        assertEquals(1, preferences.updates)
    }

    @Test
    fun playKeepsTheProcessAliveThenStartsAndPauseStopsThenReleases() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        assertEquals(PlaybackState.Running, viewModel.uiState.value.playback)
        val (keepAlive, callsBeforeKeepAlive) = host.log.single()
        assertEquals("keepAlive", keepAlive)
        assertEquals("startMetronome", audio.calls[callsBeforeKeepAlive], "the engine started before the host kept the process alive")

        viewModel.togglePlayback()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        val (release, callsBeforeRelease) = host.log.last()
        assertEquals("release", release)
        assertEquals("stopMetronome", audio.calls[callsBeforeRelease - 1], "the host was released before the engine stopped")
        assertEquals(callsBeforeRelease, audio.calls.size)
    }

    @Test
    fun aRefusingHostStopsWithFocusDenied() = viewModelTest {
        fakeHost.allow = false
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        viewModel.togglePlayback()
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals(0, startMetronomeCount())
        assertEquals(listOf(MetronomeMessage.FocusDenied), messages)
    }

    @Test
    fun anEngineErrorReleasesTheHostAndStopsWithDeviceUnavailable() = viewModelTest {
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        audio.failNextStart = AudioResult.Error(-2)
        viewModel.togglePlayback()
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals(1, fakeHost.releaseCount)
        assertFalse(fakeHost.alive)
        assertEquals(listOf(MetronomeMessage.DeviceUnavailable), messages)
    }

    @Test
    fun aSoundingPreviewIsStoppedBeforeTheStart() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.previewClick()
        audio.nextTick.running = true
        viewModel.togglePlayback()
        assertEquals(PlaybackState.Running, viewModel.uiState.value.playback)
        assertEquals(listOf("previewClick(Click)", "stopTone", "startMetronome"), audio.calls.takeLast(3))
    }

    @Test
    fun thePollMirrorsTheBeatWhileRunning() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        audio.nextTick.beatIndex = 2
        viewModel.togglePlayback()
        advanceTimeBy(150)
        runCurrent()
        assertEquals(2, viewModel.uiState.value.currentBeat)
        viewModel.togglePlayback()
        advanceTimeBy(150)
        runCurrent()
        assertEquals(-1, viewModel.uiState.value.currentBeat)
    }

    @Test
    fun thePollAsksForTheOutputLatencyAgainAfterAStart() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        advanceTimeBy(1_200)
        runCurrent()
        assertEquals(3, audio.calls.count { it == "reportOutputLatencyNanos(0)" })
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, audio.calls.count { it == "reportOutputLatencyNanos(0)" })
    }

    @Test
    fun telemetryLightsTheAudibleBeatAndReadsOncePerFrame() = viewModelTest {
        val viewModel = viewModel()
        val tick = audio.nextTick
        tick.running = true
        tick.beatPeriodNanos = 500_000_000L
        tick.beatIndex = 3
        tick.audibleInNanos = -50_000_000L
        assertEquals(3, viewModel.telemetry.litBeat(1L))
        tick.beatIndex = 1
        assertEquals(3, viewModel.telemetry.litBeat(1L), "the engine was read twice in one frame")
        assertEquals(1, viewModel.telemetry.litBeat(2L))
        tick.audibleInNanos = 10_000_000L
        assertEquals(-1, viewModel.telemetry.litBeat(3L), "a tick that is not audible yet lit a cell")
        tick.audibleInNanos = -160_000_000L
        assertEquals(-1, viewModel.telemetry.litBeat(4L), "the light outlived 150 ms")
        tick.beatPeriodNanos = 200_000_000L
        tick.audibleInNanos = -130_000_000L
        assertEquals(-1, viewModel.telemetry.litBeat(5L), "the light outlived 60 % of a short beat")
        tick.audibleInNanos = -100_000_000L
        assertEquals(1, viewModel.telemetry.litBeat(6L))
        tick.audibleInNanos = 0L
        assertEquals(1, viewModel.telemetry.litBeat(7L))
    }

    @Test
    fun leavingTheScreenStopsTheClick() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        viewModel.screenVisible(false)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals("stopMetronome", audio.calls.last())
        assertEquals(1, fakeHost.releaseCount)
        assertEquals(AudioState.Idle, audio.audioState)
    }

    @Test
    fun clearingARunningViewModelStopsAndReleases() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        viewModel.clear()
        created = null
        runCurrent()
        assertEquals("stopMetronome", audio.calls.last())
        assertEquals(1, fakeHost.releaseCount)
        assertFalse(fakeHost.alive)
    }
}
