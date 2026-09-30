package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.version.SemanticVersion
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Links the real native engine: the only Kotlin tests that do (Constitution III). No microphone is needed. */
class NativeAudioEngineTest {
    private val engine = NativeAudioEngine(InstrumentationRegistry.getInstrumentation().targetContext)

    @AfterTest
    fun tearDown() {
        engine.stopMetronome()
        engine.stopTone()
        engine.stopListening()
    }

    @Test
    fun reportsNameAndVersion() {
        val descriptor = engine.describe()
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 3, 0), descriptor.version)
        assertEquals(3, engine.abiVersion())
    }

    @Test
    fun listsTheCompiledLibraries() {
        val libraries = engine.describe().compiledLibraries
        for (prefix in listOf("miniaudio ", "dr_wav ", "signalsmith-stretch ")) {
            assertTrue(libraries.any { it.startsWith(prefix) }, "missing $prefix in $libraries")
        }
        assertEquals(EXPECT_OBOE, libraries.any { it.startsWith("oboe ") }, "oboe presence in $libraries")
    }

    @Test
    fun isStableAcrossCalls() {
        assertEquals(engine.describe(), engine.describe())
    }

    @Test
    fun startsIdleAndReadsTheRestingState() {
        assertEquals(AudioState.Idle, engine.audioState)
        val snapshot = PitchSnapshot().apply { hasPitch = true }
        engine.readPitch(snapshot)
        assertFalse(snapshot.hasPitch)
        assertFalse(snapshot.synthetic)
        assertEquals(AudioState.Idle, snapshot.state)
        assertEquals(0L, snapshot.sequence)
    }

    @Test
    fun rejectsAnInvalidWav() {
        assertNull(engine.loadSample(byteArrayOf(0, 1, 2)))
    }

    @Test
    fun aToneStartsAndStopsWithoutAMicrophone() {
        assertEquals(AudioResult.Ok, engine.startTone(440.0, 200))
        assertEquals(AudioState.Tone, engine.audioState)
        engine.stopTone()
        assertEquals(AudioState.Idle, engine.audioState)
    }

    @Test
    fun setGridAcceptsAValidGridAndRefusesThirtyThreeTicks() {
        val levels = listOf(ClickLevel.Accent, ClickLevel.Click, ClickLevel.Click, ClickLevel.Click)
        assertEquals(AudioResult.Ok, engine.setGrid(MetronomeGrid(Tempo(120), 1, levels)))
        assertEquals(AudioResult.Error(-3), engine.setGrid(MetronomeGrid(Tempo(120), 1, List(33) { ClickLevel.Click })))
    }

    @Test
    fun theMetronomeStartsTicksAndStops() {
        assertEquals(AudioResult.Ok, engine.setGrid(MetronomeGrid(Tempo(120), 1, List(4) { ClickLevel.Click })))
        assertEquals(AudioResult.Ok, engine.startMetronome())
        assertEquals(AudioState.Metronome, engine.audioState)
        val tick = TickSnapshot()
        val deadline = System.nanoTime() + 200_000_000L
        do {
            engine.readTick(tick)
            if (tick.running) break
            Thread.sleep(5)
        } while (System.nanoTime() < deadline)
        assertTrue(tick.running, "readTick did not report running within 200 ms")
        assertTrue(tick.sampleRate > 0, "sample rate ${tick.sampleRate}")
        engine.stopMetronome()
        assertEquals(AudioState.Idle, engine.audioState)
        engine.stopMetronome()
        assertEquals(AudioState.Idle, engine.audioState)
    }

    @Test
    fun anIdleEngineReadsATickThatIsNotRunning() {
        val tick = TickSnapshot().apply { running = true }
        engine.readTick(tick)
        assertFalse(tick.running)
    }

    @Test
    fun aMissingCustomSoundIsReportedAsMissing() {
        assertEquals(CustomSoundResult.Missing, engine.loadCustomSound("/nonexistent.wav"))
    }

    @Test
    fun aPreviewClickPlaysWhileIdle() {
        assertEquals(AudioResult.Ok, engine.previewClick())
    }
}

private const val EXPECT_OBOE = true
