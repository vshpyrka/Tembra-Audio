package audio.tembra.engine

import android.app.Activity
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Over ten seconds of real playback the audible position advances within 2 % of wall time and the health counters
 * report no starvation (spec SC-001, SC-003; T043). The device is muted by the run. The test plays from a foreground
 * process, as the app always does (an activity on screen or the playback service): without one the instrumentation
 * process sits in Android's background cpuset, where the dex optimisation that follows an install stalls the streamer
 * thread for longer than a fifo holds.
 */
class TransportTimingEndToEndTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val engine = NativeAudioEngine(context)
    private val root = File(context.cacheDir, "transport-timing-${System.nanoTime()}").apply { mkdirs() }
    private var foreground: Activity? = null

    @BeforeTest
    fun bringTheProcessToTheForeground() {
        val intent = Intent(instrumentation.context, ForegroundActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        foreground = instrumentation.startActivitySync(intent)
    }

    @AfterTest
    fun tearDown() {
        runBlocking { engine.closeTransport() }
        root.deleteRecursively()
        foreground?.finish()
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
        val snapshot = TransportSnapshot()
        val beforePlay = engine.audioHealth()?.starvation ?: 0
        val playedAt = System.nanoTime()
        assertEquals(PlayResult.Started, engine.play())
        // Let the stream settle, then measure from a first reading; the first half second is sampled every 10 ms so a
        // miss at the start says when it happened and what the callback saw then.
        val startTrace = mutableListOf<String>()
        var seen = beforePlay
        repeat(50) {
            Thread.sleep(10)
            val health = engine.audioHealth() ?: return@repeat
            if (health.starvation != seen) {
                engine.readTransport(snapshot)
                startTrace += "t=${(System.nanoTime() - playedAt) / 1_000_000} ms starvation=${health.starvation} clipsActive=${health.clipsActive} audible=${snapshot.audiblePositionSeconds} playing=${snapshot.playing}"
                seen = health.starvation
            }
        }
        println("TransportTimingEndToEndTest: starvation $beforePlay before play; start trace $startTrace")
        engine.readTransport(snapshot)
        val firstAudible = snapshot.audiblePositionSeconds
        val firstWall = System.nanoTime()
        val settledStarvation = engine.audioHealth()?.starvation ?: 0
        // Sample the counter through the window so a miss says when it happened (the streamer thread's stalls).
        val starvedAt = mutableListOf<Double>()
        var lastStarvation = settledStarvation
        repeat(100) {
            Thread.sleep(100)
            val now = engine.audioHealth()?.starvation ?: 0
            if (now > lastStarvation) {
                starvedAt += (System.nanoTime() - firstWall) / 1e9
                lastStarvation = now
            }
        }
        engine.readTransport(snapshot)
        val audible = snapshot.audiblePositionSeconds - firstAudible
        val wall = (System.nanoTime() - firstWall) / 1e9
        println("TransportTimingEndToEndTest: audible $audible s over $wall s wall; starvation $settledStarvation after settling, rose at $starvedAt s")
        assertTrue(abs(audible - wall) / wall <= 0.02, "audible seconds $audible drifted from $wall s of wall time")
        val health = engine.audioHealth()
        assertTrue(health != null, "no health while the transport plays")
        assertEquals(0, health.starvation, "fifo starvation during playback ($settledStarvation after settling, rose at $starvedAt s)")
        assertEquals(0, health.xruns, "xruns during playback")
        assertTrue(health.clipsActive >= 1, "no clip active in the last block")
    }
}
