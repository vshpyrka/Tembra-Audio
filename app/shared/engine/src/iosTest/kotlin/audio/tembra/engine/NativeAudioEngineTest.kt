package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.version.SemanticVersion
import platform.Foundation.NSThread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Links the real native engine: the only Kotlin tests that do (Constitution III). No microphone is needed. */
class NativeAudioEngineTest {
    private val engine = NativeAudioEngine()

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

    /**
     * The simulator's bare test process has no output audio unit (RemoteIO fails to initialise with -10851), so the
     * tone path reports the device error there and is verified in the simulator app (quickstart §5); a device plays it.
     */
    @Test
    fun aToneStartsAndStopsWithoutAMicrophone() {
        when (val result = engine.startTone(440.0, 200)) {
            AudioResult.Ok -> {
                assertEquals(AudioState.Tone, engine.audioState)
                engine.stopTone()
                assertEquals(AudioState.Idle, engine.audioState)
            }
            is AudioResult.Error -> {
                assertEquals(AudioResult.Error(-2), result, "only the device error is acceptable")
                assertEquals(AudioState.Idle, engine.audioState)
                println("NativeAudioEngineTest: no output unit in this simulator process; tone round trip skipped")
            }
        }
    }

    @Test
    fun setGridAcceptsAValidGridAndRefusesThirtyThreeTicks() {
        val levels = listOf(ClickLevel.Accent, ClickLevel.Click, ClickLevel.Click, ClickLevel.Click)
        assertEquals(AudioResult.Ok, engine.setGrid(MetronomeGrid(Tempo(120), 1, levels)))
        assertEquals(AudioResult.Error(-3), engine.setGrid(MetronomeGrid(Tempo(120), 1, List(33) { ClickLevel.Click })))
    }

    /** As for tones, the bare simulator test process has no output unit: only the device error is acceptable there. */
    @Test
    fun theMetronomeStartsTicksAndStops() {
        assertEquals(AudioResult.Ok, engine.setGrid(MetronomeGrid(Tempo(120), 1, List(4) { ClickLevel.Click })))
        when (val result = engine.startMetronome()) {
            AudioResult.Ok -> {
                assertEquals(AudioState.Metronome, engine.audioState)
                val tick = TickSnapshot()
                val started = TimeSource.Monotonic.markNow()
                do {
                    engine.readTick(tick)
                    if (tick.running) break
                    NSThread.sleepForTimeInterval(0.005)
                } while (started.elapsedNow() < 200.milliseconds)
                assertTrue(tick.running, "readTick did not report running within 200 ms")
                assertTrue(tick.sampleRate > 0, "sample rate ${tick.sampleRate}")
                engine.stopMetronome()
                assertEquals(AudioState.Idle, engine.audioState)
            }
            is AudioResult.Error -> {
                assertEquals(AudioResult.Error(-2), result, "only the device error is acceptable")
                assertEquals(AudioState.Idle, engine.audioState)
                println("NativeAudioEngineTest: no output unit in this simulator process; metronome round trip skipped")
            }
        }
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
        when (val result = engine.previewClick()) {
            AudioResult.Ok -> Unit
            is AudioResult.Error -> {
                assertEquals(AudioResult.Error(-2), result, "only the device error is acceptable")
                println("NativeAudioEngineTest: no output unit in this simulator process; preview skipped")
            }
        }
    }
}

private const val EXPECT_OBOE = false
