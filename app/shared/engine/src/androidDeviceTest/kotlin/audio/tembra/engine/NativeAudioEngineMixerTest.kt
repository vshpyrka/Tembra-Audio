package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real engine's mixer through the JNI actual (feature 012, contracts/engine-c-abi-v8.md "Kotlin seam"; T020): a clip
 * panned hard left meters on the left and reads silence on the right, metering off zeros the snapshot, a latch lights at
 * full scale and clears on a reset. The device is muted by the run; nothing listens.
 */
class NativeAudioEngineMixerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = NativeAudioEngine(context)
    private val root = File(context.cacheDir, "mixer-test-${System.nanoTime()}").apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        runBlocking { engine.closeTransport() }
        root.deleteRecursively()
    }

    private fun wav(name: String, seconds: Double): String = File(root, name).apply { writeBytes(TestWav.tone(seconds)) }.path

    /** The transport open with a 0.5-peak tone on track 0 and an empty track 1. */
    private suspend fun openWithOneClip() {
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        val mono = assertIs<ClipSourceResult.Opened>(engine.openClipSource(wav("mono.wav", 6.0)))
        engine.setTempo(Tempo(120), TimeSignature.FourFour)
        engine.setArrangement(
            ArrangementSnapshot(
                tracks = listOf(TrackRender(1f, true), TrackRender(1f, true)),
                clips = listOf(ClipRender(0, mono.handle, startBeats = 0.0, offsetFrames = 0, lengthFrames = 288000, sourceRate = 48000)),
            ),
        )
    }

    /** Polls the meter snapshot for up to two seconds until [accept]; returns the last read. */
    private fun awaitMix(accept: (MixSnapshot) -> Boolean): MixSnapshot {
        val mix = MixSnapshot()
        val started = System.nanoTime()
        while (System.nanoTime() - started < 2_000_000_000L) {
            engine.readMix(mix)
            if (accept(mix)) return mix
            Thread.sleep(10)
        }
        engine.readMix(mix)
        return mix
    }

    @Test
    fun aClipPannedHardLeftMetersOnTheLeftAndReadsSilenceOnTheRight() = runBlocking {
        openWithOneClip()
        engine.setPan(0, -1f)
        engine.setMetering(true)
        assertEquals(PlayResult.Started, engine.play())
        val mix = awaitMix { it.metering && it.peakDb(0, 0) > -20f && it.rmsDb(0, 0) > -20f }
        assertTrue(mix.metering)
        assertEquals(2, mix.trackCount)
        assertTrue(mix.peakDb(0, 0) > -20f, "left peak ${mix.peakDb(0, 0)} dB")  // 0.5 × √2 after the law: about −3 dB
        assertTrue(mix.peakDb(0, 1) < -60f, "right peak ${mix.peakDb(0, 1)} dB")
        assertTrue(mix.rmsDb(0, 1) < -60f, "right rms ${mix.rmsDb(0, 1)} dB")
        assertTrue(mix.masterPeakDb(0) > -20f, "master left ${mix.masterPeakDb(0)} dB")
        assertTrue(mix.masterPeakDb(1) < -60f, "master right ${mix.masterPeakDb(1)} dB")
        assertEquals(MixSnapshot.SILENCE_DB, mix.peakDb(1, 0))  // the empty track rests
        assertFalse(mix.clipped(0))
        engine.pause()
    }

    @Test
    fun meteringOffZerosTheSnapshot() = runBlocking {
        openWithOneClip()
        engine.setMetering(true)
        assertEquals(PlayResult.Started, engine.play())
        assertTrue(awaitMix { it.peakDb(0, 0) > -20f }.metering)
        engine.setMetering(false)
        val mix = awaitMix { !it.metering }
        assertFalse(mix.metering)
        assertEquals(0, mix.trackCount)
        assertEquals(0f, mix.peak[0])
        assertEquals(0f, mix.masterPeak[0])
        assertEquals(0L, mix.clipMask)
        engine.pause()
    }

    @Test
    fun resetMixClipClearsALatchAndTheHealthCountsTheClamp() = runBlocking {
        openWithOneClip()
        engine.setTrack(0, 2f, true)  // +6 dB and hard left: the strip reaches √2, the master clamps
        engine.setPan(0, -1f)
        engine.setMetering(true)
        assertEquals(PlayResult.Started, engine.play())
        val lit = awaitMix { it.clipped(0) && it.masterClipped }
        assertTrue(lit.clipped(0), "strip latch")
        assertTrue(lit.masterClipped, "master latch")
        engine.pause()
        Thread.sleep(100)  // the release tail is over: nothing lights the latches again
        engine.resetMixClip(0)
        engine.resetMixClip(MixSnapshot.MASTER_INDEX)
        val cleared = awaitMix { !it.clipped(0) && !it.masterClipped }
        assertFalse(cleared.clipped(0))
        assertFalse(cleared.masterClipped)
        val health = assertNotNull(engine.audioHealth())
        assertTrue(health.masterClips > 0, "master_clips ${health.masterClips}")
    }
}
