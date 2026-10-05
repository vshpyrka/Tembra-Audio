package audio.tembra.engine

import android.media.AudioAttributes
import android.media.AudioManager
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
        // The gate value lives in the engine for the life of the process: leave none behind for the next test.
        engine.setNoiseGate(null)
    }

    @Test
    fun reportsNameAndVersion() {
        val descriptor = engine.describe()
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 4, 0), descriptor.version)
        assertEquals(4, engine.abiVersion())
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

    /** A refused start, preview or tone never releases what the running metronome holds (contract ruling). */
    @Test
    fun aStartWhileTheMetronomeRunsKeepsItsFocusAndReceiver() {
        val first = engine.startMetronome()
        if (first == AudioResult.Error(AudioResult.FOCUS_DENIED)) {
            println("NativeAudioEngineTest: Gain focus denied (another app holds focus); assertion skipped")
            return
        }
        assertEquals(AudioResult.Ok, first)
        assertEquals(FocusType.Gain, engine.focus.held)
        assertTrue(engine.becomingNoisy.registered)

        assertEquals(AudioResult.Error(-1), engine.startMetronome())
        assertEquals(AudioResult.Error(-1), engine.previewClick())
        assertEquals(AudioResult.Error(-1), engine.startTone(440.0, 0))
        engine.stopTone()
        engine.stopListening()
        assertEquals(AudioState.Metronome, engine.audioState)
        assertEquals(FocusType.Gain, engine.focus.held)
        assertTrue(engine.becomingNoisy.registered)

        engine.stopMetronome()
        assertEquals(AudioState.Idle, engine.audioState)
        assertNull(engine.focus.held)
        assertFalse(engine.becomingNoisy.registered)
    }

    /** A metronome stop never releases a sounding tone's focus; the tone's own stop does. */
    @Test
    fun aMetronomeStopLeavesAToneItsFocus() {
        assertEquals(AudioResult.Ok, engine.startTone(440.0, 0))
        engine.stopMetronome()
        assertEquals(AudioState.Tone, engine.audioState)
        assertEquals(FocusType.TransientMayDuck, engine.focus.held)
        engine.stopTone()
        assertNull(engine.focus.held)
    }

    /**
     * The preview ends on its own; the actual closes the output stream it opened and abandons its transient focus once
     * the engine is back to Idle (final review I2: the stream used to keep running silence for the process lifetime).
     */
    @Test
    fun aPreviewReleasesItsFocusOnceTheClickEnds() {
        val before = openMediaStreams()
        assertEquals(AudioResult.Ok, engine.previewClick())
        assertEquals(FocusType.TransientMayDuck, engine.focus.held)
        val deadline = System.nanoTime() + 1_500_000_000L
        while (engine.focus.held != null && System.nanoTime() < deadline) Thread.sleep(20)
        assertNull(engine.focus.held, "the preview's focus was not released within 1.5 s")
        assertEquals(AudioState.Idle, engine.audioState)
        assertTrue(awaitMediaStreams { it <= before }, "the preview's output stream was still open after the click ended")
    }

    /** The stream probe the preview test relies on sees an open output: a looping tone's stream, gone after its stop. */
    @Test
    fun theStreamProbeSeesAnOpenOutput() {
        val before = openMediaStreams()
        assertEquals(AudioResult.Ok, engine.startTone(440.0, 0))
        assertTrue(awaitMediaStreams { it > before }, "the probe did not see the tone's output stream")
        engine.stopTone()
        assertTrue(awaitMediaStreams { it <= before }, "the tone's output stream was still open after its stop")
    }

    /**
     * Started players with the engine's media usage: an open output stream. The content type is not compared because
     * the system anonymises it (to unknown) in the configurations an unprivileged app receives.
     */
    private fun openMediaStreams(): Int = audioManager.activePlaybackConfigurations.count {
        it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA
    }

    /** Playback configurations reach the app asynchronously: polls [condition] for up to 1 s. */
    private fun awaitMediaStreams(condition: (Int) -> Boolean): Boolean {
        val deadline = System.nanoTime() + 1_000_000_000L
        while (System.nanoTime() < deadline) {
            if (condition(openMediaStreams())) return true
            Thread.sleep(20)
        }
        return condition(openMediaStreams())
    }

    private val audioManager: AudioManager
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(AudioManager::class.java)
}

private const val EXPECT_OBOE = true
