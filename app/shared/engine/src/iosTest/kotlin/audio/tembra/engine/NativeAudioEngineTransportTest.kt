package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The real engine's transport through the cinterop actual on the simulator (contracts/timeline-audio-kotlin.md,
 * T042): open, two generated WAV sources, a two-clip arrangement, play, positions advancing, seek, pause, close; the
 * state exclusivity with the tuner; the offline inspector and the overview builder. The Mac's output is muted by the run.
 * The bare simulator test process has no output unit (RemoteIO fails to initialise with -10851, feature 003): there
 * the open reports the device error and the playback checks are skipped, as the tone and metronome round trips are;
 * the simulator app and the devices run them (quickstart §3).
 */
@OptIn(ExperimentalForeignApi::class)
class NativeAudioEngineTransportTest {
    private val engine = NativeAudioEngine()
    private val root = NSTemporaryDirectory().trimEnd('/') + "/transport-test-${Random.nextLong()}"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun tearDown() {
        engine.closeTransport()
        engine.stopListening()
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    private fun write(name: String, bytes: ByteArray): String {
        val path = "$root/$name"
        val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        NSFileManager.defaultManager.createFileAtPath(path, contents = data, attributes = null)
        return path
    }

    private fun wav(name: String, seconds: Double, channels: Int = 1): String = write(name, TestWav.tone(seconds, channels = channels))

    private fun sleep(millis: Long) = NSThread.sleepForTimeInterval(millis / 1000.0)

    /** True when the transport opened; false after the simulator process's device error, which leaves the engine idle. */
    private suspend fun openOrSkip(test: String): Boolean = when (val result = engine.openTransport()) {
        TransportOpenResult.Opened -> true
        TransportOpenResult.DeviceFailed -> {
            assertEquals(AudioState.Idle, engine.audioState, "a failed open must leave the engine idle")
            println("NativeAudioEngineTransportTest: no output unit in this simulator process; $test skipped")
            false
        }
        is TransportOpenResult.Busy -> throw AssertionError("the engine was busy before the test started: $result")
    }

    @Test
    fun opensPlaysSeeksPausesAndCloses() = runBlocking {
        if (!openOrSkip("playback")) return@runBlocking
        assertEquals(AudioState.Transport, engine.audioState)
        val mono = assertIs<ClipSourceResult.Opened>(engine.openClipSource(wav("mono.wav", 4.0)))
        val stereo = assertIs<ClipSourceResult.Opened>(engine.openClipSource(wav("stereo.wav", 4.0, channels = 2)))
        assertEquals(AudioFormat.Wav, mono.info.format)
        assertEquals(1, mono.info.channels)
        assertEquals(192000L, mono.info.frames)
        assertEquals(2, stereo.info.channels)
        engine.setTempo(Tempo(120), TimeSignature.FourFour)
        engine.setMaster(0.5f)
        engine.setArrangement(
            ArrangementSnapshot(
                tracks = listOf(TrackRender(1f, true), TrackRender(0.8f, true)),
                clips = listOf(
                    ClipRender(0, mono.handle, startBeats = 0.0, offsetFrames = 0, lengthFrames = 192000, sourceRate = 48000),
                    ClipRender(1, stereo.handle, startBeats = 2.0, offsetFrames = 0, lengthFrames = 192000, sourceRate = 48000),
                ),
            ),
        )
        assertEquals(PlayResult.Started, engine.play())
        val snapshot = TransportSnapshot()
        // The callback publishes the playing record on its next block (research R7); wait for it, at most half a second.
        val armed = TimeSource.Monotonic.markNow()
        while (armed.elapsedNow().inWholeMilliseconds < 500) {
            engine.readTransport(snapshot)
            if (snapshot.playing) break
            sleep(5)
        }
        assertTrue(snapshot.playing, "the transport never reported playing within half a second of play()")
        var previous = -1L
        var advanced = 0
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow().inWholeMilliseconds < 1_000) {
            engine.readTransport(snapshot)
            assertTrue(snapshot.playing, "the transport reports stopped while playing")
            assertTrue(snapshot.positionFrames >= previous, "the position went backwards: $previous → ${snapshot.positionFrames}")
            if (snapshot.positionFrames > previous) advanced++
            previous = snapshot.positionFrames
            sleep(20)
        }
        assertTrue(advanced >= 10, "the position advanced only $advanced times in a second")
        assertTrue(snapshot.sampleRate > 0)

        engine.seek(Beats(8.0))
        sleep(150)
        engine.readTransport(snapshot)
        assertTrue(snapshot.positionBeats in 8.0..8.6, "seek landed at ${snapshot.positionBeats} beats")
        engine.pause()
        sleep(50)
        engine.readTransport(snapshot)
        assertTrue(!snapshot.playing)
        val paused = snapshot.positionFrames
        sleep(100)
        engine.readTransport(snapshot)
        assertEquals(paused, snapshot.positionFrames, "the position moved while paused")

        engine.setArrangement(ArrangementSnapshot.Empty)
        engine.closeClipSource(mono.handle)
        engine.closeClipSource(stereo.handle)
        engine.closeTransport()
        assertEquals(AudioState.Idle, engine.audioState)
        engine.readTransport(snapshot)
        assertEquals(0L, snapshot.positionFrames)
    }

    @Test
    fun theTunerIsRefusedWhileTheTransportIsOpen() = runBlocking {
        if (!openOrSkip("state exclusivity")) return@runBlocking
        assertEquals(AudioResult.Error(-1), engine.startListening())
        assertEquals(AudioResult.Error(-1), engine.startMetronome())
        engine.closeTransport()
        engine.closeTransport()
        assertEquals(AudioState.Idle, engine.audioState)
    }

    @Test
    fun inspectReportsAWavAndRefusesText() = runBlocking {
        val info = engine.inspect(wav("tone.wav", 0.5, channels = 2))
        assertEquals(AudioInfo(AudioFormat.Wav, channels = 2, sampleRate = 48000, frames = 24000), info)
        assertEquals(null, engine.inspect(write("not-audio.txt", "not audio".encodeToByteArray())))
        assertEquals(null, engine.inspect("$root/missing.wav"))
    }

    @Test
    fun buildOverviewWritesAValidHeader() = runBlocking {
        val audio = wav("overview.wav", 1.0)
        val peaks = "$root/overview.peaks"
        assertTrue(engine.buildOverview(audio, peaks))
        val data = NSData.dataWithContentsOfFile(peaks)
        assertTrue(data != null && data.length >= 32uL, "the overview is missing or short")
        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        assertEquals("TPK1", bytes.decodeToString(0, 4))
        val channels = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
        assertEquals(1, channels)
        var frames = 0L
        for (i in 7 downTo 0) frames = (frames shl 8) or (bytes[16 + i].toLong() and 0xFF)
        assertEquals(48000L, frames)
    }
}
