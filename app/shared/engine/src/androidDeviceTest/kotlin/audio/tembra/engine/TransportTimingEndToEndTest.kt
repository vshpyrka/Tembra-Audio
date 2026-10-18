package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Over ten seconds of real playback the audible position advances within 2 % of wall time and the health counters
 * report no starvation (spec SC-001, SC-003; T043). The device is muted by the run.
 */
class TransportTimingEndToEndTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = NativeAudioEngine(context)
    private val root = File(context.cacheDir, "transport-timing-${System.nanoTime()}").apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        engine.closeTransport()
        root.deleteRecursively()
    }

    @Test
    fun audibleSecondsFollowWallTimeWithoutStarvation() = runBlocking {
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        val path = File(root, "tone.wav").apply { writeBytes(TestWav.tone(12.0, channels = 2)) }.path
        val source = assertIs<ClipSourceResult.Opened>(engine.openClipSource(path))
        engine.setTempo(Tempo(124), TimeSignature.FourFour)
        engine.setMaster(0.5f)
        engine.setArrangement(
            ArrangementSnapshot(
                tracks = listOf(TrackRender(1f, true)),
                clips = listOf(ClipRender(0, source.handle, 0.0, 0, source.info.frames, source.info.sampleRate)),
            ),
        )
        assertEquals(PlayResult.Started, engine.play())
        val snapshot = TransportSnapshot()
        // Let the stream settle, then measure from a first reading.
        Thread.sleep(500)
        engine.readTransport(snapshot)
        val firstAudible = snapshot.audiblePositionSeconds
        val firstWall = System.nanoTime()
        Thread.sleep(10_000)
        engine.readTransport(snapshot)
        val audible = snapshot.audiblePositionSeconds - firstAudible
        val wall = (System.nanoTime() - firstWall) / 1e9
        println("TransportTimingEndToEndTest: audible $audible s over $wall s wall")
        assertTrue(abs(audible - wall) / wall <= 0.02, "audible seconds $audible drifted from $wall s of wall time")
        val health = engine.audioHealth()
        assertTrue(health != null, "no health while the transport plays")
        assertEquals(0, health.starvation, "fifo starvation during playback")
        assertEquals(0, health.xruns, "xruns during playback")
        assertTrue(health.clipsActive >= 1, "no clip active in the last block")
    }
}
