package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioFormat
import audio.tembra.core.project.Beats
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The real engine's transport through the JNI actual (contracts/timeline-audio-kotlin.md, T042): open, two generated
 * WAV sources, a two-clip arrangement, play, positions advancing, seek, pause, close; the state exclusivity with the
 * tuner; the offline inspector and the overview builder. The device is muted by the run; nothing listens.
 */
class NativeAudioEngineTransportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = NativeAudioEngine(context)
    private val root = File(context.cacheDir, "transport-test-${System.nanoTime()}").apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        runBlocking { engine.closeTransport() }
        engine.stopListening()
        root.deleteRecursively()
    }

    private fun wav(name: String, seconds: Double, channels: Int = 1): String =
        File(root, name).apply { writeBytes(TestWav.tone(seconds, channels = channels)) }.path

    @Test
    fun opensPlaysSeeksPausesAndCloses() = runBlocking {
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        assertEquals(AudioState.Transport, engine.audioState)
        val mono = assertIs<ClipSourceResult.Opened>(engine.openClipSource(wav("mono.wav", 4.0)))
        val stereo = assertIs<ClipSourceResult.Opened>(engine.openClipSource(wav("stereo.wav", 4.0, channels = 2)))
        assertEquals(AudioInfo(AudioFormat.Wav, channels = 1, sampleRate = 48000, frames = 192000), mono.info)
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
        val armed = System.nanoTime()
        while (System.nanoTime() - armed < 500_000_000L) {
            engine.readTransport(snapshot)
            if (snapshot.playing) break
            Thread.sleep(5)
        }
        assertTrue(snapshot.playing, "the transport never reported playing within half a second of play()")
        var previous = -1L
        var advanced = 0
        val started = System.nanoTime()
        while (System.nanoTime() - started < 1_000_000_000L) {
            engine.readTransport(snapshot)
            assertTrue(snapshot.playing, "the transport reports stopped while playing")
            assertTrue(snapshot.positionFrames >= previous, "the position went backwards: $previous → ${snapshot.positionFrames}")
            if (snapshot.positionFrames > previous) advanced++
            previous = snapshot.positionFrames
            Thread.sleep(20)
        }
        assertTrue(advanced >= 10, "the position advanced only $advanced times in a second")
        assertEquals(48000, snapshot.sampleRate)

        engine.seek(Beats(8.0))
        Thread.sleep(150)
        engine.readTransport(snapshot)
        assertTrue(snapshot.positionBeats in 8.0..8.6, "seek landed at ${snapshot.positionBeats} beats")
        engine.pause()
        Thread.sleep(50)
        engine.readTransport(snapshot)
        assertTrue(!snapshot.playing)
        val paused = snapshot.positionFrames
        Thread.sleep(100)
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
    fun theTunerIsRefusedWhileTheTransportIsOpenAndTheTransportWhileListening() = runBlocking {
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        assertEquals(AudioResult.Error(-1), engine.startListening())
        assertEquals(AudioResult.Error(-1), engine.startMetronome())
        engine.closeTransport()
        engine.closeTransport() // idempotent
        assertEquals(AudioState.Idle, engine.audioState)
    }

    @Test
    fun inspectReportsTheThreeFormatsAndRefusesText() = runBlocking {
        val info = engine.inspect(wav("tone.wav", 0.5, channels = 2))
        assertEquals(AudioInfo(AudioFormat.Wav, channels = 2, sampleRate = 48000, frames = 24000), info)
        val text = File(root, "not-audio.txt").apply { writeText("not audio") }.path
        assertEquals(null, engine.inspect(text))
        assertEquals(null, engine.inspect(File(root, "missing.wav").path))
    }

    @Test
    fun buildOverviewWritesAValidHeader() = runBlocking {
        val audio = wav("overview.wav", 1.0)
        val peaks = File(root, "overview.peaks").path
        assertTrue(engine.buildOverview(audio, peaks))
        val bytes = File(peaks).readBytes()
        assertTrue(bytes.size >= 32, "the overview is only ${bytes.size} bytes")
        assertEquals("TPK1", String(bytes, 0, 4, Charsets.US_ASCII))
        val channels = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
        assertEquals(1, channels)
        var frames = 0L
        for (i in 7 downTo 0) frames = (frames shl 8) or (bytes[16 + i].toLong() and 0xFF)
        assertEquals(48000L, frames)
    }
}
