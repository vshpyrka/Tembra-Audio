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
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The real engine's mixer through the cinterop actual on the simulator (feature 012, contracts/engine-c-abi-v8.md
 * "Kotlin seam"; T020): a clip panned hard left meters on the left and reads silence on the right, metering off zeros
 * the snapshot, a latch lights at full scale and clears on a reset. The bare simulator test process has no output unit
 * (feature 003): there the open reports the device error and the checks are skipped, as the transport test's are; the
 * simulator app and the devices run them (quickstart §4). The Mac's output is muted by the run.
 */
@OptIn(ExperimentalForeignApi::class)
class NativeAudioEngineMixerTest {
    private val engine = NativeAudioEngine()
    private val root = NSTemporaryDirectory().trimEnd('/') + "/mixer-test-${Random.nextLong()}"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun tearDown() {
        runBlocking { engine.closeTransport() }
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    private fun wav(name: String, seconds: Double): String {
        val bytes = TestWav.tone(seconds)
        val path = "$root/$name"
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        NSFileManager.defaultManager.createFileAtPath(path, contents = data, attributes = null)
        return path
    }

    /**
     * The transport open with a 0.5-peak tone on track 0 and an empty track 1; false in the bare simulator process.
     * Track 0's [pan] is stored before the arrangement lands, so the strip snaps there instead of ramping from the centre
     * once the clip sounds (a ramp's first samples sit in the 64 ms peak window and the 300 ms rms window for seconds).
     */
    private suspend fun openWithOneClip(pan: Float = 0f): Boolean {
        val opened = engine.openTransport()
        if (opened != TransportOpenResult.Opened) {
            println("NativeAudioEngineMixerTest: no output unit in this simulator process ($opened); the playback checks are skipped")
            return false
        }
        if (pan != 0f) engine.setPan(0, pan)
        val mono = assertIs<ClipSourceResult.Opened>(engine.openClipSource(wav("mono.wav", 6.0)))
        engine.setTempo(Tempo(120), TimeSignature.FourFour)
        engine.setArrangement(
            ArrangementSnapshot(
                tracks = listOf(TrackRender(1f, true), TrackRender(1f, true)),
                clips = listOf(ClipRender(0, mono.handle, startBeats = 0.0, offsetFrames = 0, lengthFrames = 288000, sourceRate = 48000)),
            ),
        )
        return true
    }

    /** Polls the meter snapshot for up to two seconds until [accept]; returns the last read. */
    private fun awaitMix(accept: (MixSnapshot) -> Boolean): MixSnapshot {
        val mix = MixSnapshot()
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow().inWholeMilliseconds < 2_000) {
            engine.readMix(mix)
            if (accept(mix)) return mix
            NSThread.sleepForTimeInterval(0.01)
        }
        engine.readMix(mix)
        return mix
    }

    @Test
    fun aClipPannedHardLeftMetersOnTheLeftAndReadsSilenceOnTheRight() = runBlocking {
        if (!openWithOneClip(pan = -1f)) return@runBlocking
        engine.setMetering(true)
        assertEquals(PlayResult.Started, engine.play())
        val mix = awaitMix { it.metering && it.peakDb(0, 0) > -20f && it.rmsDb(0, 0) > -20f }
        assertTrue(mix.metering)
        assertEquals(2, mix.trackCount)
        assertTrue(mix.peakDb(0, 0) > -20f, "left peak ${mix.peakDb(0, 0)} dB")
        assertTrue(mix.peakDb(0, 1) < -60f, "right peak ${mix.peakDb(0, 1)} dB")
        assertTrue(mix.masterPeakDb(0) > -20f, "master left ${mix.masterPeakDb(0)} dB")
        assertTrue(mix.masterPeakDb(1) < -60f, "master right ${mix.masterPeakDb(1)} dB")
        assertEquals(MixSnapshot.SILENCE_DB, mix.peakDb(1, 0))
        assertFalse(mix.clipped(0))
        engine.pause()
    }

    @Test
    fun meteringOffZerosTheSnapshot() = runBlocking {
        if (!openWithOneClip()) return@runBlocking
        engine.setMetering(true)
        assertEquals(PlayResult.Started, engine.play())
        assertTrue(awaitMix { it.peakDb(0, 0) > -20f }.metering)
        engine.setMetering(false)
        val mix = awaitMix { !it.metering }
        assertFalse(mix.metering)
        assertEquals(0, mix.trackCount)
        assertEquals(0f, mix.peak[0])
        assertEquals(0L, mix.clipMask)
        engine.pause()
    }

    @Test
    fun resetMixClipClearsALatchAndTheHealthCountsTheClamp() = runBlocking {
        if (!openWithOneClip(pan = -1f)) return@runBlocking
        engine.setTrack(0, 2f, true)  // +6 dB and hard left: the strip reaches √2, the master clamps
        engine.setMetering(true)
        assertEquals(PlayResult.Started, engine.play())
        val lit = awaitMix { it.clipped(0) && it.masterClipped }
        assertTrue(lit.clipped(0), "strip latch")
        assertTrue(lit.masterClipped, "master latch")
        engine.pause()
        NSThread.sleepForTimeInterval(0.1)
        engine.resetMixClip(0)
        engine.resetMixClip(MixSnapshot.MASTER_INDEX)
        val cleared = awaitMix { !it.clipped(0) && !it.masterClipped }
        assertFalse(cleared.clipped(0))
        assertFalse(cleared.masterClipped)
        val health = assertNotNull(engine.audioHealth())
        assertTrue(health.masterClips > 0, "master_clips ${health.masterClips}")
    }

    /** Without a transport the calls are refused by state and the read is zeros: the seam holds in every process. */
    @Test
    fun withoutATransportTheReadIsZerosAndTheSettersAreHarmless() {
        engine.setPan(0, 0.5f)
        engine.setMetering(true)
        engine.resetMixClip(MixSnapshot.MASTER_INDEX)
        val mix = MixSnapshot()
        mix.trackCount = 7
        engine.readMix(mix)
        assertEquals(0, mix.trackCount)
        assertFalse(mix.metering)
        assertEquals(0L, mix.block)
    }
}
