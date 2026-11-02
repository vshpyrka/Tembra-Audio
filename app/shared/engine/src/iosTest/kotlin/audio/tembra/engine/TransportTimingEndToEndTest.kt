package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.dataWithBytes
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Over ten seconds of real playback the audible position advances within 2 % of wall time and the health counters
 * report no starvation (spec SC-001, SC-003; T043). The Mac's output is muted by the run. The bare simulator test
 * process has no output unit (feature 003): there the open reports the device error and the measurement is skipped.
 */
@OptIn(ExperimentalForeignApi::class)
class TransportTimingEndToEndTest {
    private val engine = NativeAudioEngine()
    private val root = NSTemporaryDirectory().trimEnd('/') + "/transport-timing-${Random.nextLong()}"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun tearDown() {
        runBlocking { engine.closeTransport() }
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    @Test
    fun audibleSecondsFollowWallTimeWithoutStarvation() = runBlocking {
        when (val opened = engine.openTransport()) {
            TransportOpenResult.Opened -> Unit
            TransportOpenResult.DeviceFailed -> {
                println("TransportTimingEndToEndTest: no output unit in this simulator process; timing skipped")
                return@runBlocking
            }
            is TransportOpenResult.Busy -> throw AssertionError("the engine was busy before the test started: $opened")
        }
        val bytes = TestWav.tone(12.0, channels = 2)
        val path = "$root/tone.wav"
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        NSFileManager.defaultManager.createFileAtPath(path, contents = data, attributes = null)
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
        NSThread.sleepForTimeInterval(0.5)
        engine.readTransport(snapshot)
        val firstAudible = snapshot.audiblePositionSeconds
        val mark = TimeSource.Monotonic.markNow()
        NSThread.sleepForTimeInterval(10.0)
        engine.readTransport(snapshot)
        val audible = snapshot.audiblePositionSeconds - firstAudible
        val wall = mark.elapsedNow().inWholeMicroseconds / 1e6
        println("TransportTimingEndToEndTest: audible $audible s over $wall s wall")
        assertTrue(abs(audible - wall) / wall <= 0.02, "audible seconds $audible drifted from $wall s of wall time")
        val health = engine.audioHealth()
        assertTrue(health != null, "no health while the transport plays")
        assertEquals(0, health.starvation, "fifo starvation during playback")
        assertTrue(health.clipsActive >= 1, "no clip active in the last block")
    }
}
