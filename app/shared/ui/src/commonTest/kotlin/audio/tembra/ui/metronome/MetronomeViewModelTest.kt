package audio.tembra.ui.metronome

import androidx.lifecycle.ViewModelStore
import audio.tembra.core.metronome.AccentPattern
import audio.tembra.core.metronome.BeatLevel
import audio.tembra.core.metronome.BuiltInClickSound
import audio.tembra.core.metronome.ClickSound
import audio.tembra.core.metronome.CustomSoundRef
import audio.tembra.core.metronome.MetronomeSettings
import audio.tembra.core.metronome.MetronomeStyle
import audio.tembra.core.metronome.Subdivision
import audio.tembra.core.metronome.SyncopationReading
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.data.metronome.FakeCustomClickSoundStore
import audio.tembra.data.metronome.FakeMetronomePreferences
import audio.tembra.data.metronome.MetronomePreferences
import audio.tembra.data.metronome.MetronomePreferencesSnapshot
import audio.tembra.data.metronome.MetronomePreferenceKeys
import audio.tembra.engine.AudioEvent
import audio.tembra.engine.AudioResult
import audio.tembra.engine.AudioState
import audio.tembra.engine.CustomSoundResult
import audio.tembra.engine.FakeAudioEngine
import audio.tembra.ui.platform.BackgroundPlaybackHost
import audio.tembra.ui.platform.FakeBackgroundPlaybackHost
import audio.tembra.ui.platform.FakeSoundFilePicker
import audio.tembra.ui.platform.SoundFilePicker
import audio.tembra.ui.platform.SoundPickFailure
import audio.tembra.ui.platform.SoundPickResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ViewModel against the fakes (contracts/metronome-screen.md). User Story 1: loading, tempo intents and
 * persistence, the start and stop sequences, refused starts, the 10 Hz poll and the draw-phase telemetry. User Story 2:
 * the signature, subdivision, cell and style intents with their re-application rules (spec FR-006 to FR-010) and the
 * debug Syncopated reading (spec FR-037). User Story 3: tap tempo and its caption timer (spec FR-015 to FR-017). User
 * Story 4: the sound selection with its preview, swap and import rules (spec FR-020, FR-022). User Story 5: the custom
 * WAV import, its refusals, the replace action and the startup fallback (spec FR-022 to FR-025). User Story 6: the
 * interruptions, route and device losses, stop requests and the screen leaving, none of which ever restarts (spec FR-027).
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
        // The picker writes into the store's map, so a commit moves what was picked.
        picker = FakeSoundFilePicker(files = store.files)
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
    fun aRefusingHostStopsWithDeviceUnavailable() = viewModelTest {
        fakeHost.allow = false
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        viewModel.togglePlayback()
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals(0, startMetronomeCount())
        assertEquals(listOf(MetronomeMessage.DeviceUnavailable), messages)
    }

    /** Android requests focus inside `startMetronome()`: a denial there is another app holding the audio (review I3). */
    @Test
    fun aFocusDenialReleasesTheHostAndStopsWithFocusDenied() = viewModelTest {
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        audio.failNextStart = AudioResult.Error(AudioResult.FOCUS_DENIED)
        viewModel.togglePlayback()
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals(1, fakeHost.releaseCount)
        assertFalse(fakeHost.alive)
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

    @Test
    fun aStreamTheEngineLostStopsThePlayback() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        // The engine can stay in the Metronome state after a failed reopen: the tick's own flag reports the loss.
        audio.nextTick.running = false
        advanceTimeBy(150)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals("stopMetronome", audio.calls.last())
        assertEquals(1, fakeHost.releaseCount)
        assertEquals(1, startMetronomeCount())
    }

    @Test
    fun anInterruptionStopsAndItsEndDoesNotRestart() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        audio.emit(AudioEvent.Interrupted)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals("stopMetronome", audio.calls.last())
        assertEquals(1, fakeHost.releaseCount)
        audio.emit(AudioEvent.InterruptionEnded)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals(1, startMetronomeCount())
    }

    @Test
    fun aStopRequestFromTheHostStops() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        fakeHost.requestStop()
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals("stopMetronome", audio.calls.last())
        assertEquals(1, fakeHost.releaseCount)
    }

    // --- User Story 2: bar shape and styles ------------------------------------------------------------------------------

    private fun TestScope.running(settings: MetronomeSettings = MetronomeSettings()): MetronomeViewModel {
        preferences = CountingPreferences(FakeMetronomePreferences(MetronomePreferencesSnapshot(settings)))
        val viewModel = viewModel()
        show(viewModel)
        audio.nextTick.running = true
        viewModel.togglePlayback()
        return viewModel
    }

    private fun pattern(text: String) = AccentPattern(text.map { BeatLevel.byId(it)!! })

    @Test
    fun selectSignatureReappliesTheSelectedStyleAndReachesARunningEngine() = viewModelTest {
        val viewModel = running()
        viewModel.selectSignature(TimeSignature.SevenEight)
        runCurrent()
        val settings = viewModel.uiState.value.settings
        assertEquals(TimeSignature.SevenEight, settings.signature)
        assertEquals(pattern("ACCCCCC"), settings.pattern)
        assertEquals(MetronomeStyle.Standard, settings.style)
        assertEquals("setGrid(bpm=120, ticks=1, levels=ACCCCCC)", audio.calls.last())
        val stored = preferences.snapshot.first().settings
        assertEquals(TimeSignature.SevenEight, stored.signature)
        assertEquals(pattern("ACCCCCC"), stored.pattern)
    }

    @Test
    fun selectSignatureAfterAHandEditResizesAndKeepsNoStyle() = viewModelTest {
        val viewModel = running()
        viewModel.cycleBeat(1)
        viewModel.selectSignature(TimeSignature.SixEight)
        runCurrent()
        val settings = viewModel.uiState.value.settings
        assertEquals(pattern("ASCC").resizedTo(6), settings.pattern)
        assertNull(settings.style)
        assertEquals("setGrid(bpm=120, ticks=1, levels=ASCCCC)", audio.calls.last())
        assertNull(preferences.snapshot.first().settings.style)
    }

    @Test
    fun selectSubdivisionSendsTheSubdividedGrid() = viewModelTest {
        val viewModel = running()
        viewModel.selectSubdivision(Subdivision.Two)
        runCurrent()
        assertEquals(Subdivision.Two, viewModel.uiState.value.settings.subdivision)
        assertEquals("setGrid(bpm=120, ticks=2, levels=ASCSCSCS)", audio.calls.last())
        assertEquals(Subdivision.Two, preferences.snapshot.first().settings.subdivision)
    }

    @Test
    fun cycleBeatClearsTheStyleWhenThePatternMatchesNone() = viewModelTest {
        val viewModel = running()
        viewModel.cycleBeat(1)
        runCurrent()
        val settings = viewModel.uiState.value.settings
        assertEquals(BeatLevel.Sub, settings.pattern.levels[1])
        assertNull(settings.style)
        assertEquals("setGrid(bpm=120, ticks=1, levels=ASCC)", audio.calls.last())
        assertEquals(pattern("ASCC"), preferences.snapshot.first().settings.pattern)
    }

    @Test
    fun cycleBeatBackToAStylesPatternSelectsThatStyle() = viewModelTest {
        val viewModel = running()
        viewModel.cycleBeat(1)
        viewModel.cycleBeat(1)
        runCurrent()
        assertEquals(pattern("AACC"), viewModel.uiState.value.settings.pattern)
        assertNull(viewModel.uiState.value.settings.style)
        viewModel.cycleBeat(1)
        runCurrent()
        assertEquals(pattern("ACCC"), viewModel.uiState.value.settings.pattern)
        assertEquals(MetronomeStyle.Standard, viewModel.uiState.value.settings.style)
        assertEquals(MetronomeStyle.Standard, preferences.snapshot.first().settings.style)
    }

    @Test
    fun selectStyleRewritesTheCells() = viewModelTest {
        val viewModel = running()
        viewModel.selectStyle(MetronomeStyle.Downbeat)
        runCurrent()
        val settings = viewModel.uiState.value.settings
        assertEquals(pattern("ASSS"), settings.pattern)
        assertEquals(MetronomeStyle.Downbeat, settings.style)
        assertEquals("setGrid(bpm=120, ticks=1, levels=ASSS)", audio.calls.last())
        assertEquals(MetronomeStyle.Downbeat, preferences.snapshot.first().settings.style)
    }

    @Test
    fun reselectingTheSelectedStyleChangesNothing() = viewModelTest {
        val viewModel = running()
        val callsBefore = audio.calls.size
        val updatesBefore = preferences.updates
        viewModel.selectStyle(MetronomeStyle.Standard)
        viewModel.selectSignature(TimeSignature.FourFour)
        viewModel.selectSubdivision(Subdivision.One)
        runCurrent()
        assertEquals(callsBefore, audio.calls.size)
        assertEquals(updatesBefore, preferences.updates)
    }

    @Test
    fun theDebugReadingChangesTheSyncopatedGrid() = viewModelTest {
        val syncopated = MetronomeSettings(
            subdivision = Subdivision.Two,
            pattern = MetronomeStyle.Syncopated.pattern(TimeSignature.FourFour),
            style = MetronomeStyle.Syncopated,
        )
        val viewModel = running(syncopated)
        assertEquals("setGrid(bpm=120, ticks=2, levels=CSASCSAS)", audio.calls.first())
        viewModel.useDebugSettings(MetronomeDebugRequest(SyncopationReading.OffBeatTicks))
        runCurrent()
        assertEquals(SyncopationReading.OffBeatTicks, viewModel.uiState.value.syncopationReading)
        assertEquals("setGrid(bpm=120, ticks=2, levels=SASASASA)", audio.calls.last())
        viewModel.useDebugSettings(null)
        runCurrent()
        assertEquals(SyncopationReading.EvenBeats, viewModel.uiState.value.syncopationReading)
        assertEquals("setGrid(bpm=120, ticks=2, levels=CSASCSAS)", audio.calls.last())
    }

    @Test
    fun aDebugReadingSetBeforeLoadingReachesTheFirstGrid() = viewModelTest {
        preferences = CountingPreferences(
            FakeMetronomePreferences(
                MetronomePreferencesSnapshot(
                    MetronomeSettings(pattern = MetronomeStyle.Syncopated.pattern(TimeSignature.FourFour), style = MetronomeStyle.Syncopated),
                ),
            ),
        )
        val viewModel = viewModel()
        viewModel.useDebugSettings(MetronomeDebugRequest(SyncopationReading.AllButOne))
        runCurrent()
        assertEquals(emptyList(), audio.calls)
        show(viewModel)
        assertEquals("setGrid(bpm=120, ticks=1, levels=SAAA)", audio.calls.first())
    }

    // --- User Story 3: tap tempo ---------------------------------------------------------------------------------------

    private val millis = 1_000_000L

    @Test
    fun aSingleTapKeepsTheTempoAndAsksForAnother() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        val callsBefore = audio.calls.size
        viewModel.tap(0)
        runCurrent()
        assertEquals(Tempo(120), viewModel.uiState.value.settings.tempo)
        assertEquals(TapCaption.Again, viewModel.uiState.value.tapCaption)
        assertEquals(callsBefore, audio.calls.size)
        assertEquals(0, preferences.updates)
    }

    @Test
    fun aSecondTapSetsTheTempoThroughTheSettingsPath() = viewModelTest {
        val viewModel = running(MetronomeSettings(tempo = Tempo(90)))
        val startsBefore = startMetronomeCount()
        viewModel.tap(0)
        viewModel.tap(500 * millis)
        runCurrent()
        assertEquals(Tempo(120), viewModel.uiState.value.settings.tempo)
        assertEquals(TapCaption.Counting(2), viewModel.uiState.value.tapCaption)
        assertEquals(Tempo(120), preferences.snapshot.first().settings.tempo)
        assertEquals("setGrid(bpm=120, ticks=1, levels=ACCC)", audio.calls.last())
        // A tapped tempo never restarts the click (spec FR-017).
        assertEquals(startsBefore, startMetronomeCount())
        assertEquals(PlaybackState.Running, viewModel.uiState.value.playback)
    }

    @Test
    fun tapsFasterThanTheRangeClampTo240() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        // 300 BPM: a tap every 200 ms.
        for (n in 0 until 4) viewModel.tap(n * 200 * millis)
        runCurrent()
        assertEquals(Tempo(240), viewModel.uiState.value.settings.tempo)
        assertEquals(TapCaption.Counting(4), viewModel.uiState.value.tapCaption)
    }

    /** Found on the Pixel: ten taps read "9 taps" because the sequence keeps only the last nine for the average. */
    @Test
    fun theCaptionCountsEveryTapOfALongRun() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        for (n in 0 until 12) viewModel.tap(n * 600 * millis)
        runCurrent()
        assertEquals(TapCaption.Counting(12), viewModel.uiState.value.tapCaption)
        assertEquals(Tempo(100), viewModel.uiState.value.settings.tempo)
    }

    @Test
    fun theCaptionRestsTwoSecondsAfterTheLastTapAndTheNextTapStartsAgain() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        viewModel.tap(0)
        viewModel.tap(500 * millis)
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(TapCaption.Rest, viewModel.uiState.value.tapCaption)
        viewModel.tap(3_000 * millis)
        runCurrent()
        assertEquals(TapCaption.Again, viewModel.uiState.value.tapCaption)
        assertEquals(Tempo(120), viewModel.uiState.value.settings.tempo)
    }

    @Test
    fun theCaptionTimerIsReplacedByEachTap() = viewModelTest {
        preferences = CountingPreferences(FakeMetronomePreferences(MetronomePreferencesSnapshot(MetronomeSettings(tempo = Tempo(90)))))
        val viewModel = viewModel()
        show(viewModel)
        val captions = mutableListOf<TapCaption>()
        backgroundScope.launch { viewModel.uiState.collect { captions += it.tapCaption } }
        runCurrent()
        viewModel.tap(0)
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()
        viewModel.tap(1_500 * millis)
        runCurrent()
        // A stacked timer from the first tap would end the sequence here, at 2 s.
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(TapCaption.Counting(2), viewModel.uiState.value.tapCaption)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(TapCaption.Rest, viewModel.uiState.value.tapCaption)
        val afterTheFirstTap = captions.dropWhile { it == TapCaption.Rest }
        assertEquals(1, afterTheFirstTap.count { it == TapCaption.Rest }, "the caption rested more than once: $captions")
        assertEquals(TapCaption.Rest, captions.last())
    }

    // --- User Story 4: click sounds --------------------------------------------------------------------------------------

    private val rim = ClickSound.BuiltIn(BuiltInClickSound.Rim808)

    @Test
    fun aSoundSelectedWhileStoppedIsPreviewedAndPersisted() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        viewModel.selectSound(rim)
        runCurrent()
        assertEquals(listOf("selectSound(Rim808)", "previewClick(Click)"), audio.calls.takeLast(2))
        assertEquals(rim, viewModel.uiState.value.settings.sound)
        val stored = preferences.snapshot.first()
        assertEquals(rim, stored.settings.sound)
        assertEquals("808-rim", stored.encode()[MetronomePreferenceKeys.SOUND])
    }

    @Test
    fun aSoundSelectedWhileRunningSwapsWithoutAPreview() = viewModelTest {
        val viewModel = running()
        viewModel.selectSound(rim)
        runCurrent()
        assertEquals("selectSound(Rim808)", audio.calls.last())
        assertFalse(audio.calls.any { it.startsWith("previewClick") })
        assertEquals(rim, preferences.snapshot.first().settings.sound)
    }

    @Test
    fun selectingTheSelectedSoundDoesNothing() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        val callsBefore = audio.calls.size
        viewModel.selectSound(ClickSound.BuiltIn(BuiltInClickSound.ClassicWood))
        runCurrent()
        assertEquals(callsBefore, audio.calls.size)
        assertEquals(0, preferences.updates)
    }

    @Test
    fun anAvailableCustomSoundIsSelected() = viewModelTest {
        preferences = CountingPreferences(
            FakeMetronomePreferences(MetronomePreferencesSnapshot(MetronomeSettings(customSound = CustomSoundRef("Kick", store.path)))),
        )
        val viewModel = viewModel()
        show(viewModel)
        assertEquals(CustomSoundState.Available("Kick"), viewModel.uiState.value.customSound)
        viewModel.selectSound(ClickSound.Custom("Kick"))
        runCurrent()
        assertEquals(listOf("selectSound(Custom)", "previewClick(Click)"), audio.calls.takeLast(2))
        assertEquals(ClickSound.Custom("Kick"), preferences.snapshot.first().settings.sound)
        assertEquals(emptyList(), picker.destinations)
    }

    @Test
    fun anEmptyCustomEntryOpensThePickerInstead() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        val callsBefore = audio.calls.size
        viewModel.selectSound(ClickSound.Custom("Custom"))
        runCurrent()
        assertEquals(listOf(store.stagingPath), picker.destinations)
        assertEquals(callsBefore, audio.calls.size)
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.ClassicWood), viewModel.uiState.value.settings.sound)
        assertEquals(0, preferences.updates)
    }

    @Test
    fun aRefusedPreviewIsIgnoredWithoutAMessage() = viewModelTest {
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        viewModel.selectSound(rim)
        // The first preview still sounds: the engine refuses the second.
        audio.failNextStart = AudioResult.Error(-1)
        viewModel.selectSound(ClickSound.BuiltIn(BuiltInClickSound.DigitalBeep))
        runCurrent()
        assertEquals(listOf("selectSound(DigitalBeep)", "previewClick(Click)"), audio.calls.takeLast(2))
        assertEquals(ClickSound.BuiltIn(BuiltInClickSound.DigitalBeep), viewModel.uiState.value.settings.sound)
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals(emptyList(), messages)
    }

    // --- Fix round 1 -------------------------------------------------------------------------------------------------

    @Test
    fun leavingTheScreenEndsTheTapSequence() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        val captions = mutableListOf<TapCaption>()
        backgroundScope.launch { viewModel.uiState.map { it.tapCaption }.distinctUntilChanged().collect { captions += it } }
        runCurrent()
        viewModel.tap(0)
        viewModel.tap(500 * millis)
        runCurrent()
        viewModel.screenVisible(false)
        runCurrent()
        assertEquals(TapCaption.Rest, viewModel.uiState.value.tapCaption)
        assertEquals(0, viewModel.uiState.value.tap.count)
        advanceTimeBy(2_001)
        runCurrent()
        show(viewModel)
        assertEquals(TapCaption.Rest, viewModel.uiState.value.tapCaption)
        assertEquals(listOf(TapCaption.Rest, TapCaption.Counting(2), TapCaption.Rest), captions)
    }

    /** Holds dispatched work until [release], so a test can leave the screen while a custom sound is being prepared. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = mutableListOf<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            held += block
        }
        fun release() {
            val blocks = held.toList()
            held.clear()
            blocks.forEach { it.run() }
        }
    }

    @Test
    fun aCustomSoundPreparationCutShortIsRedoneOnTheNextShow() = viewModelTest {
        preferences = CountingPreferences(
            FakeMetronomePreferences(
                MetronomePreferencesSnapshot(
                    MetronomeSettings(sound = ClickSound.Custom("Kick"), customSound = CustomSoundRef("Kick", store.path)),
                ),
            ),
        )
        val prepare = HeldDispatcher()
        val viewModel = MetronomeViewModel(audio, host, preferences, store, picker, prepare)
        created = viewModel
        show(viewModel)
        // Loaded, with Classic Wood standing in while the custom sound is prepared.
        assertTrue(viewModel.uiState.value.loaded)
        assertEquals(listOf("setGrid(bpm=120, ticks=1, levels=ACCC)", "selectSound(ClassicWood)"), audio.calls)
        viewModel.screenVisible(false)
        runCurrent()
        prepare.release()
        runCurrent()
        assertEquals(CustomSoundState.None, viewModel.uiState.value.customSound)
        show(viewModel)
        prepare.release()
        runCurrent()
        assertEquals(CustomSoundState.Available("Kick"), viewModel.uiState.value.customSound)
        assertEquals("selectSound(Custom)", audio.calls.last())
    }

    // --- User Story 5: the custom sound ------------------------------------------------------------------------------

    private val kick = ClickSound.Custom("Kick")
    private val classicWood = ClickSound.BuiltIn(BuiltInClickSound.ClassicWood)

    private fun storedCustom(sound: ClickSound) = CountingPreferences(
        FakeMetronomePreferences(MetronomePreferencesSnapshot(MetronomeSettings(sound = sound, customSound = CustomSoundRef("Kick", store.path)))),
    )

    private fun TestScope.viewModelPreparingOn(prepare: CoroutineDispatcher, filePicker: SoundFilePicker = picker): MetronomeViewModel {
        val viewModel = MetronomeViewModel(audio, host, preferences, store, filePicker, prepare)
        created = viewModel
        runCurrent()
        return viewModel
    }

    /** A picker that suspends until the test answers, as the platform sheet does while the user browses. */
    private class GatedPicker : SoundFilePicker {
        val answer = CompletableDeferred<SoundPickResult>()
        var opened = 0
        override suspend fun pickWav(destinationPath: String): SoundPickResult {
            opened++
            return answer.await()
        }
    }

    @Test
    fun aPickedWavIsPreparedFromStagingThenCommittedSelectedAndPersisted() = viewModelTest {
        val prepare = HeldDispatcher()
        val viewModel = viewModelPreparingOn(prepare)
        show(viewModel)
        picker.next = SoundPickResult.Picked("Kick")
        picker.bytesToWrite = byteArrayOf(1, 2, 3)
        viewModel.importCustomSound()
        assertEquals(CustomSoundState.Importing, viewModel.uiState.value.customSound)
        runCurrent()
        assertEquals(listOf(store.stagingPath), picker.destinations)
        // Nothing is committed before the engine has accepted the staged file.
        assertFalse("commit" in store.calls)
        assertEquals(listOf<Byte>(1, 2, 3), store.files[store.stagingPath]?.toList())
        // The preparation waits on the prepare dispatcher, never the main one.
        assertFalse(audio.calls.any { it.startsWith("loadCustomSound") })
        assertEquals(CustomSoundState.Importing, viewModel.uiState.value.customSound)
        prepare.release()
        runCurrent()
        assertEquals(listOf("loadCustomSound(${store.stagingPath})", "selectSound(Custom)", "previewClick(Click)"), audio.calls.takeLast(3))
        assertEquals(listOf("commit"), store.calls)
        assertEquals(listOf<Byte>(1, 2, 3), store.files[store.path]?.toList())
        assertFalse(store.stagingPath in store.files)
        val state = viewModel.uiState.value
        assertEquals(CustomSoundState.Available("Kick"), state.customSound)
        assertEquals(kick, state.settings.sound)
        assertEquals(CustomSoundRef("Kick", store.path), state.settings.customSound)
        val stored = preferences.snapshot.first().settings
        assertEquals(kick, stored.sound)
        assertEquals(CustomSoundRef("Kick", store.path), stored.customSound)
    }

    @Test
    fun aWavImportedWhileRunningSwapsWithoutAPreview() = viewModelTest {
        val viewModel = running()
        picker.next = SoundPickResult.Picked("Kick")
        viewModel.importCustomSound()
        runCurrent()
        assertEquals("selectSound(Custom)", audio.calls.last())
        assertFalse(audio.calls.any { it.startsWith("previewClick") })
        assertEquals(kick, viewModel.uiState.value.settings.sound)
    }

    @Test
    fun aCancelledPickChangesNothing() = viewModelTest {
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        val callsBefore = audio.calls.size
        viewModel.importCustomSound()
        runCurrent()
        assertEquals(CustomSoundState.None, viewModel.uiState.value.customSound)
        assertEquals(classicWood, viewModel.uiState.value.settings.sound)
        assertEquals(callsBefore, audio.calls.size)
        assertEquals(emptyList(), store.calls)
        assertEquals(0, preferences.updates)
        assertEquals(emptyList(), messages)
    }

    @Test
    fun aFileTooLargeIsRefusedWithItsMessage() = viewModelTest {
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        viewModel.selectSound(rim)
        runCurrent()
        val updatesBefore = preferences.updates
        picker.next = SoundPickResult.Failed(SoundPickFailure.TooLarge)
        // A platform picker can leave a partial copy behind; the ViewModel removes it.
        picker.partialOnFailure = byteArrayOf(9, 9)
        viewModel.importCustomSound()
        runCurrent()
        assertEquals(listOf<MetronomeMessage>(MetronomeMessage.TooLarge), messages)
        assertEquals(rim, viewModel.uiState.value.settings.sound)
        assertEquals(CustomSoundState.None, viewModel.uiState.value.customSound)
        assertEquals(listOf("deleteStaging"), store.calls)
        assertFalse(store.stagingPath in store.files, "the staging file was left behind")
        assertEquals(updatesBefore, preferences.updates)
    }

    @Test
    fun anUnreadableFileIsRefusedWithItsMessage() = viewModelTest {
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        picker.next = SoundPickResult.Failed(SoundPickFailure.Unreadable)
        viewModel.importCustomSound()
        runCurrent()
        assertEquals(listOf<MetronomeMessage>(MetronomeMessage.ImportFailed), messages)
        assertEquals(CustomSoundState.None, viewModel.uiState.value.customSound)
    }

    private val previousBytes = byteArrayOf(4, 2)

    /**
     * An import or a replace the engine refuses. [previous] is the sound selected before it; [previousCopy] says whether
     * the app already held a custom sound ("Kick", its bytes at `store.path`). The refused file is staged only: the
     * previous copy, the entry, the selection and the preferences stay, and a selected custom sound is reloaded.
     */
    private fun refusedByTheEngine(
        result: CustomSoundResult,
        message: MetronomeMessage,
        previous: ClickSound = rim,
        previousCopy: Boolean = false,
    ) = viewModelTest {
        if (previousCopy) {
            preferences = storedCustom(previous)
            store.files[store.path] = previousBytes.copyOf()
            store.present = true
        }
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        if (!previousCopy) {
            viewModel.selectSound(previous)
            runCurrent()
        }
        val entryBefore = viewModel.uiState.value.customSound
        val refBefore = viewModel.uiState.value.settings.customSound
        val updatesBefore = preferences.updates
        val callsBefore = audio.calls.size
        audio.customSoundResults[store.stagingPath] = result
        picker.next = SoundPickResult.Picked("Notes")
        picker.bytesToWrite = byteArrayOf(7, 7, 7)
        if (previousCopy) viewModel.replaceCustomSound() else viewModel.importCustomSound()
        runCurrent()
        assertEquals(listOf(message), messages)
        val state = viewModel.uiState.value
        assertEquals(previous, state.settings.sound)
        assertEquals(entryBefore, state.customSound)
        assertEquals(refBefore, state.settings.customSound)
        assertEquals(updatesBefore, preferences.updates)
        // Only the staged file is removed; the previous copy is never committed over nor deleted.
        assertFalse(store.stagingPath in store.files, "the refused staging file was left behind")
        assertFalse("commit" in store.calls)
        assertFalse("delete" in store.calls)
        if (previousCopy) {
            assertEquals(previousBytes.toList(), store.files[store.path]?.toList(), "the previous copy was touched")
        } else {
            assertFalse(store.path in store.files)
        }
        val importCalls = audio.calls.drop(callsBefore)
        if (previous is ClickSound.Custom) {
            // The engine gets the previous sound back: reloaded from its copy, then selected.
            assertEquals(
                listOf("loadCustomSound(${store.stagingPath})", "loadCustomSound(${store.path})", "selectSound(Custom)"),
                importCalls,
            )
        } else {
            assertEquals(listOf("loadCustomSound(${store.stagingPath})"), importCalls)
        }
    }

    @Test
    fun aFileThatIsNotAWavIsRefusedAndThePreviousSoundKept() = refusedByTheEngine(CustomSoundResult.NotWav, MetronomeMessage.NotWav)

    @Test
    fun aSilentFileIsRefusedAndThePreviousSoundKept() = refusedByTheEngine(CustomSoundResult.Silent, MetronomeMessage.Silent)

    @Test
    fun aRefusedReplaceKeepsTheSelectedCustomSoundItsCopyAndReloadsIt() =
        refusedByTheEngine(CustomSoundResult.NotWav, MetronomeMessage.NotWav, previous = kick, previousCopy = true)

    @Test
    fun aRefusedReplaceWithABuiltInSelectedLeavesThePreviousCopyUntouched() =
        refusedByTheEngine(CustomSoundResult.Silent, MetronomeMessage.Silent, previous = rim, previousCopy = true)

    @Test
    fun aStagedFileTheEngineCannotFindIsRefusedAsImportFailed() =
        refusedByTheEngine(CustomSoundResult.Missing, MetronomeMessage.ImportFailed, previous = kick, previousCopy = true)

    @Test
    fun aRefusedReplaceSurvivesARelaunch() = viewModelTest {
        preferences = storedCustom(kick)
        store.files[store.path] = previousBytes.copyOf()
        store.present = true
        val first = viewModel()
        show(first)
        audio.customSoundResults[store.stagingPath] = CustomSoundResult.NotWav
        picker.next = SoundPickResult.Picked("Notes")
        first.replaceCustomSound()
        runCurrent()
        first.clear()
        runCurrent()
        // The engine's loader finds only what is on disk.
        if (store.path !in store.files) audio.customSoundResults[store.path] = CustomSoundResult.Missing
        // The next launch finds the previous sound where the preferences point.
        val next = viewModel()
        val messages = collectMessages(next)
        show(next)
        assertEquals(kick, next.uiState.value.settings.sound)
        assertEquals(CustomSoundState.Available("Kick"), next.uiState.value.customSound)
        assertEquals(emptyList(), messages)
        assertEquals("selectSound(Custom)", audio.calls.last())
    }

    @Test
    fun aMissingStoredCustomSoundFallsBackToClassicWoodOnce() = viewModelTest {
        preferences = storedCustom(kick)
        audio.customSoundResult = CustomSoundResult.Missing
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        val state = viewModel.uiState.value
        assertEquals(classicWood, state.settings.sound)
        assertEquals(CustomSoundState.Unavailable("Kick"), state.customSound)
        assertEquals(listOf<MetronomeMessage>(MetronomeMessage.CustomSoundMissing), messages)
        assertFalse(audio.calls.any { it == "selectSound(Custom)" })
        val stored = preferences.snapshot.first().settings
        assertEquals(classicWood, stored.sound)
        // The name stays, so the Custom entry reads it greyed.
        assertEquals(CustomSoundRef("Kick", store.path), stored.customSound)
        // Leaving and returning does not repeat the message.
        viewModel.screenVisible(false)
        runCurrent()
        show(viewModel)
        assertEquals(1, messages.size)
    }

    @Test
    fun aMissingCustomSoundThatIsNotSelectedIsShownUnavailableWithoutAMessage() = viewModelTest {
        preferences = storedCustom(rim)
        audio.customSoundResult = CustomSoundResult.Missing
        val viewModel = viewModel()
        val messages = collectMessages(viewModel)
        show(viewModel)
        assertEquals(rim, viewModel.uiState.value.settings.sound)
        assertEquals(CustomSoundState.Unavailable("Kick"), viewModel.uiState.value.customSound)
        assertEquals(emptyList(), messages)
        assertEquals(0, preferences.updates)
    }

    @Test
    fun anUnavailableCustomEntryImportsAgain() = viewModelTest {
        preferences = storedCustom(rim)
        val loaded = audio.customSoundResult
        audio.customSoundResult = CustomSoundResult.Missing
        val viewModel = viewModel()
        show(viewModel)
        audio.customSoundResult = loaded
        picker.next = SoundPickResult.Picked("Kick 2")
        viewModel.selectSound(kick)
        runCurrent()
        assertEquals(listOf(store.stagingPath), picker.destinations)
        assertEquals(CustomSoundState.Available("Kick 2"), viewModel.uiState.value.customSound)
        assertEquals(ClickSound.Custom("Kick 2"), viewModel.uiState.value.settings.sound)
    }

    @Test
    fun replaceRunsTheSameFlowAndReplacesTheName() = viewModelTest {
        preferences = storedCustom(kick)
        val viewModel = viewModel()
        show(viewModel)
        assertEquals(CustomSoundState.Available("Kick"), viewModel.uiState.value.customSound)
        picker.next = SoundPickResult.Picked("Snare")
        viewModel.replaceCustomSound()
        assertEquals(CustomSoundState.Importing, viewModel.uiState.value.customSound)
        runCurrent()
        assertEquals(listOf(store.stagingPath), picker.destinations)
        assertEquals(CustomSoundState.Available("Snare"), viewModel.uiState.value.customSound)
        assertEquals(ClickSound.Custom("Snare"), viewModel.uiState.value.settings.sound)
        val stored = preferences.snapshot.first().settings
        assertEquals(ClickSound.Custom("Snare"), stored.sound)
        assertEquals(CustomSoundRef("Snare", store.path), stored.customSound)
    }

    @Test
    fun aCancelledReplaceKeepsTheCustomSound() = viewModelTest {
        preferences = storedCustom(kick)
        val viewModel = viewModel()
        show(viewModel)
        viewModel.replaceCustomSound()
        runCurrent()
        assertEquals(CustomSoundState.Available("Kick"), viewModel.uiState.value.customSound)
        assertEquals(kick, viewModel.uiState.value.settings.sound)
        assertEquals(0, preferences.updates)
    }

    @Test
    fun aSecondImportWhileImportingIsIgnored() = viewModelTest {
        val prepare = HeldDispatcher()
        val viewModel = viewModelPreparingOn(prepare)
        show(viewModel)
        picker.next = SoundPickResult.Picked("Kick")
        viewModel.importCustomSound()
        runCurrent()
        viewModel.importCustomSound()
        viewModel.replaceCustomSound()
        runCurrent()
        assertEquals(1, picker.destinations.size)
        prepare.release()
        runCurrent()
        assertEquals(CustomSoundState.Available("Kick"), viewModel.uiState.value.customSound)
        assertEquals(1, audio.calls.count { it.startsWith("loadCustomSound") })
    }

    @Test
    fun theStartupLoadNeverOverwritesAnImportInFlight() = viewModelTest {
        preferences = storedCustom(kick)
        val prepare = HeldDispatcher()
        val gated = GatedPicker()
        val viewModel = viewModelPreparingOn(prepare, gated)
        show(viewModel)
        viewModel.importCustomSound()
        runCurrent()
        assertEquals(1, gated.opened)
        // The stored sound finishes preparing while the picker is still open.
        prepare.release()
        runCurrent()
        assertEquals(CustomSoundState.Importing, viewModel.uiState.value.customSound)
        gated.answer.complete(SoundPickResult.Cancelled)
        runCurrent()
        // The cancelled pick restores what the startup load found, not the state from before it.
        assertEquals(CustomSoundState.Available("Kick"), viewModel.uiState.value.customSound)
        assertEquals("selectSound(Custom)", audio.calls.last())
    }

    // --- User Story 6: background playback and interruptions (spec FR-027) -------------------------------------------

    @Test
    fun anOutputRouteLossStops() = viewModelTest {
        val viewModel = running()
        audio.emit(AudioEvent.OutputRouteLost)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals("stopMetronome", audio.calls.last())
        assertEquals(1, fakeHost.releaseCount)
        assertFalse(fakeHost.alive)
    }

    @Test
    fun aDeviceLostEventStops() = viewModelTest {
        val viewModel = running()
        audio.emit(AudioEvent.DeviceLost)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals("stopMetronome", audio.calls.last())
        assertEquals(1, fakeHost.releaseCount)
    }

    @Test
    fun theEngineFallingToIdleWhileRunningIsADeviceLoss() = viewModelTest {
        val viewModel = running()
        // The tick still reads running: only the engine state reports the closed stream.
        audio.audioState = AudioState.Idle
        advanceTimeBy(150)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals("stopMetronome", audio.calls.last())
        assertEquals(1, fakeHost.releaseCount)
        assertEquals(1, startMetronomeCount())
    }

    @Test
    fun leavingTheScreenCancelsTheObserversSoLaterEventsCallNothing() = viewModelTest {
        val viewModel = running()
        viewModel.screenVisible(false)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        val callsAfterLeaving = audio.calls.toList()
        audio.emit(AudioEvent.Interrupted)
        audio.emit(AudioEvent.InterruptionEnded)
        audio.emit(AudioEvent.OutputRouteLost)
        audio.emit(AudioEvent.DeviceLost)
        fakeHost.requestStop()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(callsAfterLeaving, audio.calls, "an event after leaving reached the engine")
        assertEquals(1, fakeHost.keepAliveCount)
        assertEquals(1, fakeHost.releaseCount)
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
    }

    @Test
    fun returningToTheScreenDoesNotRestartTheClick() = viewModelTest {
        val viewModel = running()
        viewModel.screenVisible(false)
        runCurrent()
        show(viewModel)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals(1, startMetronomeCount())
        assertEquals(1, fakeHost.keepAliveCount)
        assertFalse(fakeHost.alive)
    }

    @Test
    fun eventsWhileStoppedAreIgnored() = viewModelTest {
        val viewModel = viewModel()
        show(viewModel)
        val messages = collectMessages(viewModel)
        val callsBefore = audio.calls.toList()
        audio.emit(AudioEvent.Interrupted)
        runCurrent()
        audio.emit(AudioEvent.InterruptionEnded)
        runCurrent()
        audio.emit(AudioEvent.OutputRouteLost)
        runCurrent()
        audio.emit(AudioEvent.DeviceLost)
        runCurrent()
        fakeHost.requestStop()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(callsBefore, audio.calls, "an event while stopped reached the engine")
        assertEquals(0, fakeHost.keepAliveCount)
        assertEquals(0, fakeHost.releaseCount)
        assertEquals(PlaybackState.Stopped, viewModel.uiState.value.playback)
        assertEquals(emptyList(), messages, "an ignored event produced a message")
    }
}
