package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.version.SemanticVersion
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
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
        // The gate value lives in the engine for the life of the process: leave none behind for the next test.
        engine.setNoiseGate(null)
    }

    @Test
    fun reportsNameAndVersion() {
        val descriptor = engine.describe()
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 5, 0), descriptor.version)
        assertEquals(6, engine.abiVersion())
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
        assertEquals(-120f, snapshot.inputLevelDb)
        assertTrue(snapshot.gateOpen)
    }

    @Test
    fun theNoiseGateIsAcceptedOrRefusedByItsRange() {
        assertEquals(AudioResult.Ok, engine.setNoiseGate(null))
        assertEquals(AudioResult.Ok, engine.setNoiseGate(-50f))
        assertEquals(AudioResult.Ok, engine.setNoiseGate(0f))
        assertEquals(AudioResult.Error(-3), engine.setNoiseGate(1f))
        assertEquals(AudioResult.Error(-3), engine.setNoiseGate(-121f))
        assertEquals(AudioResult.Ok, engine.setNoiseGate(null))
        assertEquals(AudioState.Idle, engine.audioState)
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

    /**
     * A refused start, preview or tone never deactivates the session of the running stream (contract ruling). The
     * synthetic test signal runs without an output unit, so this holds in the simulator process too.
     */
    @Test
    fun aRefusedStartKeepsTheRunningStreamsSession() {
        assertTrue(IosAudioSession.activateForPlayback(), "activateForPlayback")
        assertEquals(AudioResult.Ok, engine.startTestSignal(TestSignals.byId("e2-flat-20")!!.signal))
        assertEquals(AudioState.Listening, engine.audioState)

        assertEquals(AudioResult.Error(-1), engine.startMetronome())
        assertEquals(AudioResult.Error(-1), engine.previewClick())
        assertEquals(AudioResult.Error(-1), engine.startTone(440.0, 0))
        engine.stopMetronome()
        engine.stopTone()
        assertEquals(AudioState.Listening, engine.audioState)
        assertTrue(IosAudioSession.isActive, "a refused call deactivated the running stream's session")

        engine.stopListening()
        assertFalse(IosAudioSession.isActive, "stopListening leaves the session active")
    }

    /**
     * 0 means "measure the current route again" on both actuals: iOS forwards the session's latency, never a 0 that
     * would wipe the beat light's compensation (final review I1); a measured value passes through.
     */
    @Test
    fun aZeroLatencyReportForwardsTheSessionsLatency() {
        val forwarded = mutableListOf<Long>()
        val recording = NativeAudioEngine { forwarded += it }
        assertTrue(IosAudioSession.activateForPlayback(), "activateForPlayback")
        try {
            recording.reportOutputLatencyNanos(0)
            recording.reportOutputLatencyNanos(7_000_000)
            val session = IosAudioSession.outputLatencyNanos()
            assertTrue(session > 0L, "session latency $session ns")
            assertEquals(listOf(session, 7_000_000L), forwarded)
        } finally {
            IosAudioSession.deactivate()
        }
    }

    /** The preview ends on its own; the actual deactivates the session once the engine is back to Idle. */
    @Test
    fun aPreviewDeactivatesTheSessionOnceTheClickEnds() {
        when (val result = engine.previewClick()) {
            AudioResult.Ok -> {
                assertTrue(IosAudioSession.isActive)
                val started = TimeSource.Monotonic.markNow()
                while (IosAudioSession.isActive && started.elapsedNow() < 1_500.milliseconds) {
                    // The check runs on the main queue: let this (main) thread's run loop serve it.
                    NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02))
                }
                assertFalse(IosAudioSession.isActive, "the preview's session was not deactivated within 1.5 s")
                assertEquals(AudioState.Idle, engine.audioState)
            }
            is AudioResult.Error -> {
                assertEquals(AudioResult.Error(-2), result, "only the device error is acceptable")
                assertFalse(IosAudioSession.isActive, "a failed preview leaves the session active")
                println("NativeAudioEngineTest: no output unit in this simulator process; preview release skipped")
            }
        }
    }
}

private const val EXPECT_OBOE = false
