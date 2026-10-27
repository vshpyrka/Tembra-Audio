package audio.tembra.engine

import audio.tembra.core.metronome.BeatLevel
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TickGrid
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import audio.tembra.core.version.SemanticVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The test double every Kotlin tuner and metronome test programs against (contracts/tuner-audio-kotlin.md,
 * contracts/metronome-audio-kotlin.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeAudioEngineTest {

    @Test
    fun defaultDescriptorIsAnEmptyZeroVersionEngine() {
        val descriptor = FakeAudioEngine().describe()
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 0, 0), descriptor.version)
        assertEquals(emptyList(), descriptor.compiledLibraries)
        assertEquals("Tembra Engine 0.0.0 ()", descriptor.rawDescription)
    }

    @Test
    fun returnsTheConfiguredDescriptorAndCountsCalls() {
        val configured = EngineDescriptor(
            name = "Tembra Engine",
            version = SemanticVersion(9, 8, 7),
            compiledLibraries = listOf("miniaudio 0.11.25"),
            rawDescription = "Tembra Engine 9.8.7 (miniaudio 0.11.25)",
        )
        val engine = FakeAudioEngine(configured)
        assertEquals(0, engine.describeCalls)
        assertEquals(configured, engine.describe())
        assertEquals(configured, engine.describe())
        assertEquals(2, engine.describeCalls)
    }

    @Test
    fun listeningFollowsTheCalls() {
        val engine = FakeAudioEngine()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(AudioResult.Ok, engine.startListening())
        assertEquals(listOf("startListening"), engine.calls)
        assertEquals(AudioState.Listening, engine.audioState)
        engine.stopListening()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(listOf("startListening", "stopListening"), engine.calls)
    }

    @Test
    fun testSignalsAreRecordedAndMarkTheSnapshotSynthetic() {
        val engine = FakeAudioEngine()
        val signal = TestSignals.byId("e2-flat-20")!!.signal
        assertEquals(AudioResult.Ok, engine.startTestSignal(signal))
        assertEquals(listOf("startTestSignal"), engine.calls)
        assertEquals(signal, engine.lastTestSignal)
        assertEquals(AudioState.Listening, engine.audioState)
        assertTrue(engine.nextSnapshot.synthetic)
    }

    @Test
    fun tonesFollowTheCalls() {
        val engine = FakeAudioEngine()
        assertEquals(AudioResult.Ok, engine.startTone(82.41, 0))
        assertEquals(AudioState.Tone, engine.audioState)
        assertEquals(listOf("startTone(82.41, 0)"), engine.calls)
        engine.stopTone()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(listOf("startTone(82.41, 0)", "stopTone"), engine.calls)
    }

    @Test
    fun readPitchCopiesTheScriptedSnapshotAndCountsHeartbeats() {
        val engine = FakeAudioEngine()
        engine.nextSnapshot.apply {
            sequence = 42L
            state = AudioState.Listening
            sampleRate = 48000
            frequencyHz = 83.37f
            clarity = 0.9f
            inputPeak = 0.4f
            inputLevelDb = -42.5f
            gateOpen = false
            hasPitch = true
        }
        val snapshot = PitchSnapshot()
        engine.readPitch(snapshot)
        assertEquals(42L, snapshot.sequence)
        assertEquals(AudioState.Listening, snapshot.state)
        assertEquals(48000, snapshot.sampleRate)
        assertEquals(83.37f, snapshot.frequencyHz)
        assertEquals(0.9f, snapshot.clarity)
        assertEquals(0.4f, snapshot.inputPeak)
        assertEquals(-42.5f, snapshot.inputLevelDb)
        assertEquals(false, snapshot.gateOpen)
        assertTrue(snapshot.hasPitch)
        assertEquals(1, engine.readCount)
        engine.readPitch(snapshot)
        assertEquals(2, engine.readCount)
    }

    @Test
    fun aFreshSnapshotRestsAtTheFloorLevelWithTheGateOpen() {
        val snapshot = PitchSnapshot()
        assertEquals(-120f, snapshot.inputLevelDb)
        assertTrue(snapshot.gateOpen)
    }

    @Test
    fun theNoiseGateIsRecordedWithItsValueAndChangesNoState() {
        val engine = FakeAudioEngine()
        assertNull(engine.noiseGateDb)
        assertEquals(AudioResult.Ok, engine.setNoiseGate(-50f))
        assertEquals(-50f, engine.noiseGateDb)
        assertEquals(AudioResult.Ok, engine.setNoiseGate(null))
        assertNull(engine.noiseGateDb)
        assertEquals(listOf("setNoiseGate(-50.0)", "setNoiseGate(off)"), engine.calls)
        assertEquals(AudioState.Idle, engine.audioState)
    }

    @Test
    fun samplesGetIncreasingHandlesAndPlayAsTones() {
        val engine = FakeAudioEngine()
        val first = engine.loadSample(byteArrayOf(1))
        val second = engine.loadSample(byteArrayOf(1, 2))
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(second.id > first.id)
        assertNull(engine.loadSample(ByteArray(0)))
        assertEquals(AudioResult.Ok, engine.playSample(first, loop = true))
        assertEquals(AudioState.Tone, engine.audioState)
        assertEquals("playSample(${first.id}, true)", engine.calls.last())
        engine.stopTone()
        engine.unloadSample(first)
        assertEquals("unloadSample", engine.calls.last())
    }

    @Test
    fun aScriptedFailureLeavesTheStateUnchanged() {
        val engine = FakeAudioEngine()
        engine.failNextStart = AudioResult.Error(-2)
        assertEquals(AudioResult.Error(-2), engine.startListening())
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(AudioResult.Ok, engine.startListening())
        assertEquals(AudioState.Listening, engine.audioState)
    }

    @Test
    fun theWatchdogDropsToIdle() {
        val engine = FakeAudioEngine()
        engine.startListening()
        engine.simulateWatchdog()
        assertEquals(AudioState.Idle, engine.audioState)
    }

    @Test
    fun emittedEventsReachASubscriber() = runTest {
        val engine = FakeAudioEngine()
        val received = async { engine.events.first() }
        runCurrent()
        engine.emit(AudioEvent.Interrupted)
        assertEquals(AudioEvent.Interrupted, received.await())
    }

    @Test
    fun setGridAndSelectSoundAreRecorded() {
        val engine = FakeAudioEngine()
        val levels = listOf(ClickLevel.Accent, ClickLevel.Click, ClickLevel.Click, ClickLevel.Click, ClickLevel.Sub)
        assertEquals(AudioResult.Ok, engine.setGrid(MetronomeGrid(Tempo(120), 4, levels)))
        assertEquals(AudioResult.Ok, engine.selectSound(ClickSoundId.Rim808))
        assertEquals(listOf("setGrid(bpm=120, ticks=4, levels=ACCCS)", "selectSound(Rim808)"), engine.calls)
    }

    @Test
    fun loadCustomSoundReturnsTheScriptedResultAndRecordsThePath() {
        val engine = FakeAudioEngine()
        assertTrue(engine.loadCustomSound("/x.wav") is CustomSoundResult.Loaded)
        engine.customSoundResult = CustomSoundResult.Silent
        assertEquals(CustomSoundResult.Silent, engine.loadCustomSound("/y.wav"))
        engine.unloadCustomSound()
        assertEquals(listOf("loadCustomSound(/x.wav)", "loadCustomSound(/y.wav)", "unloadCustomSound"), engine.calls)
    }

    @Test
    fun aPerPathResultOverridesTheScriptedOne() {
        val engine = FakeAudioEngine()
        engine.customSoundResults["/staged.wav"] = CustomSoundResult.NotWav
        assertEquals(CustomSoundResult.NotWav, engine.loadCustomSound("/staged.wav"))
        assertTrue(engine.loadCustomSound("/kept.wav") is CustomSoundResult.Loaded)
    }

    @Test
    fun theMetronomeFollowsTheCalls() {
        val engine = FakeAudioEngine()
        assertEquals(AudioResult.Ok, engine.startMetronome())
        assertEquals(AudioState.Metronome, engine.audioState)
        engine.stopMetronome()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(listOf("startMetronome", "stopMetronome"), engine.calls)
    }

    @Test
    fun aScriptedFailureAppliesToTheMetronomeStart() {
        val engine = FakeAudioEngine()
        engine.failNextStart = AudioResult.Error(-2)
        assertEquals(AudioResult.Error(-2), engine.startMetronome())
        assertEquals(AudioState.Idle, engine.audioState)
        assertNull(engine.failNextStart)
        assertEquals(AudioResult.Ok, engine.startMetronome())
        assertEquals(AudioState.Metronome, engine.audioState)
    }

    @Test
    fun previewClickIsRecordedAndSoundsAsATone() {
        val engine = FakeAudioEngine()
        assertEquals(AudioResult.Ok, engine.previewClick())
        assertEquals(listOf("previewClick(Click)"), engine.calls)
        assertEquals(AudioState.Tone, engine.audioState)
    }

    @Test
    fun aPreviewEndsOnItsOwnOrWithTheMetronomeStop() {
        val engine = FakeAudioEngine()
        engine.previewClick()
        engine.endPreview()
        assertEquals(AudioState.Idle, engine.audioState)
        engine.previewClick(ClickLevel.Accent)
        engine.stopMetronome()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(listOf("previewClick(Click)", "previewClick(Accent)", "stopMetronome"), engine.calls)
    }

    @Test
    fun endPreviewLeavesAToneSounding() {
        val engine = FakeAudioEngine()
        engine.startTone(82.41, 0)
        engine.endPreview()
        engine.stopMetronome()
        assertEquals(AudioState.Tone, engine.audioState)
    }

    @Test
    fun readTickCopiesTheScriptedTick() {
        val engine = FakeAudioEngine()
        engine.nextTick.apply {
            tickIndex = 7L
            beatIndex = 2
            level = ClickLevel.Accent
            audibleInNanos = -3_000_000L
            beatPeriodNanos = 500_000_000L
            running = true
            sampleRate = 48000
        }
        val snapshot = TickSnapshot()
        engine.readTick(snapshot)
        assertEquals(7L, snapshot.tickIndex)
        assertEquals(2, snapshot.beatIndex)
        assertEquals(ClickLevel.Accent, snapshot.level)
        assertEquals(-3_000_000L, snapshot.audibleInNanos)
        assertEquals(500_000_000L, snapshot.beatPeriodNanos)
        assertTrue(snapshot.running)
        assertEquals(48000, snapshot.sampleRate)
    }

    @Test
    fun reportedOutputLatencyIsRecorded() {
        val engine = FakeAudioEngine()
        engine.reportOutputLatencyNanos(5_000_000)
        assertEquals(listOf("reportOutputLatencyNanos(5000000)"), engine.calls)
    }

    @Test
    fun anOutputRouteLossReachesASubscriber() = runTest {
        val engine = FakeAudioEngine()
        val received = async { engine.events.first() }
        runCurrent()
        engine.emit(AudioEvent.OutputRouteLost)
        assertEquals(AudioEvent.OutputRouteLost, received.await())
    }

    @Test
    fun aGridMapsTheDomainLevels() {
        val grid = MetronomeGrid.from(
            TickGrid(
                2,
                listOf(
                    BeatLevel.Accent,
                    BeatLevel.Sub,
                    BeatLevel.Click,
                    BeatLevel.Sub,
                ),
            ),
            Tempo(96),
        )
        assertEquals(MetronomeGrid(Tempo(96), 2, listOf(ClickLevel.Accent, ClickLevel.Sub, ClickLevel.Click, ClickLevel.Sub)), grid)
    }

    // --- Transport (feature 009, contracts/timeline-audio-kotlin.md) -------------------------------------------------

    @Test
    fun transportOpensFromIdleAndClosesBackToIdle() = runTest {
        val engine = FakeAudioEngine()
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        assertEquals(AudioState.Transport, engine.audioState)
        engine.closeTransport()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(listOf("openTransport", "closeTransport"), engine.calls)
    }

    @Test
    fun transportIsRefusedWhileAnotherStreamRunsAndWhenTheDeviceFails() = runTest {
        val engine = FakeAudioEngine()
        engine.startMetronome()
        assertEquals(TransportOpenResult.Busy(AudioState.Metronome), engine.openTransport())
        engine.stopMetronome()
        engine.failNextStart = AudioResult.Error(-2)
        assertEquals(TransportOpenResult.DeviceFailed, engine.openTransport())
        assertEquals(AudioState.Idle, engine.audioState)
    }

    @Test
    fun everyTransportCallIsRecordedWithItsArguments() = runTest {
        val engine = FakeAudioEngine()
        engine.openTransport()
        val opened = engine.openClipSource("/p/audio/abc.wav") as ClipSourceResult.Opened
        engine.setTempo(Tempo(124), TimeSignature.FourFour)
        engine.setMaster(0.8f)
        engine.setArrangement(
            ArrangementSnapshot(
                tracks = listOf(TrackRender(1f, true), TrackRender(0.5f, false)),
                clips = listOf(ClipRender(0, opened.handle, 0.0, 0L, 48000L, 48000)),
            ),
        )
        engine.setTrack(1, 0.5f, false)
        engine.setMetronome(true)
        assertEquals(PlayResult.Started, engine.play())
        engine.seek(16.0.let(::Beats))
        engine.pause()
        engine.closeClipSource(opened.handle)
        engine.closeTransport()
        assertEquals(
            listOf(
                "openTransport", "openClipSource(/p/audio/abc.wav)", "setTempo(124, 4/4)", "setMaster(0.8)",
                "setArrangement(tracks=2,clips=1)", "setTrack(1,0.5,false)", "setMetronome(true)", "play", "seek(16.0)", "pause",
                "closeClipSource(1)", "closeTransport",
            ),
            engine.calls,
        )
        assertEquals(2, engine.lastArrangement?.tracks?.size)
    }

    @Test
    fun inspectAndClipSourcesFollowTheScript() = runTest {
        val engine = FakeAudioEngine()
        val flac = AudioInfo(AudioFormat.Flac, channels = 1, sampleRate = 44100, frames = 44100)
        engine.inspectResults["/a.flac"] = flac
        engine.inspectResults["/bad.txt"] = null
        engine.clipSourceFailures += "/gone.wav"
        assertEquals(flac, engine.inspect("/a.flac"))
        assertNull(engine.inspect("/bad.txt"))
        assertEquals(AudioFormat.Wav, engine.inspect("/unknown.wav")?.format)
        assertEquals(ClipSourceResult.Undecodable, engine.openClipSource("/gone.wav"))
        assertEquals(ClipSourceResult.Undecodable, engine.openClipSource("/bad.txt"))
        val opened = engine.openClipSource("/a.flac") as ClipSourceResult.Opened
        assertEquals(flac, opened.info)
        assertEquals("/a.flac" to flac, engine.openClipSources[opened.handle.id])
    }

    @Test
    fun aScriptedPlayFailureIsReturnedOnceAndLeavesTheTransportStopped() = runTest {
        val engine = FakeAudioEngine()
        engine.openTransport()
        engine.failNextPlay = PlayResult.Failed(-2)
        assertEquals(PlayResult.Failed(-2), engine.play())
        assertTrue(!engine.transportPlaying)
        assertEquals(PlayResult.Started, engine.play())
        assertTrue(engine.transportPlaying)
    }

    @Test
    fun playIsRefusedWithoutAnOpenTransport() = runTest {
        val engine = FakeAudioEngine()
        assertEquals(PlayResult.Failed(-1), engine.play())
    }

    @Test
    fun readTransportCopiesTheScriptedSnapshotAndCounts() = runTest {
        val engine = FakeAudioEngine()
        engine.nextTransport.positionBeats = 3.5
        engine.nextTransport.audiblePositionSeconds = 1.75
        engine.nextTransport.sampleRate = 48000
        val into = TransportSnapshot()
        engine.readTransport(into)
        engine.readTransport(into)
        assertEquals(3.5, into.positionBeats)
        assertEquals(1.75, into.audiblePositionSeconds)
        assertEquals(48000, into.sampleRate)
        assertEquals(2, engine.readTransportCount)
    }

    @Test
    fun buildOverviewRunsTheWriterAndReportsItsAnswer() = runTest {
        val engine = FakeAudioEngine()
        val written = mutableListOf<String>()
        engine.overviewWriter = { path -> written += path; path.endsWith(".peaks") }
        assertTrue(engine.buildOverview("/a.wav", "/cache/a.peaks"))
        assertTrue(!engine.buildOverview("/a.wav", "/cache/a.bad"))
        assertEquals(listOf("/cache/a.peaks", "/cache/a.bad"), written)
        assertEquals(listOf("buildOverview(/a.wav)", "buildOverview(/a.wav)"), engine.calls)
    }
}
