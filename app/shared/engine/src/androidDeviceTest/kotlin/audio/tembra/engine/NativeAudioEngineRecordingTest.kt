package audio.tembra.engine

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.recording.TakeStopReason
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The real engine's capture path through the JNI actual (feature 010, contracts/engine-c-abi-v7.md; T022): arm opens
 * the second Oboe stream beside the transport's output, the capture snapshot reports the input's channels and the
 * stream rate with a positive alignment, a take with a one-bar count-in at 120 BPM records a WAV whose header agrees
 * with the take info, the peak ring delivers buckets, and disarm closes the input. The device is muted by the run
 * (quickstart §2); the room is the input. Needs RECORD_AUDIO, granted by the runner or here.
 */
class NativeAudioEngineRecordingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val engine = NativeAudioEngine(context)
    private val root = File(context.cacheDir, "recording-test-${System.nanoTime()}").apply { mkdirs() }

    @BeforeTest
    fun grantTheMicrophone() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO), "RECORD_AUDIO is needed on this device")
    }

    @AfterTest
    fun tearDown() {
        engine.disarm()
        engine.closeTransport()
        root.deleteRecursively()
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val started = System.nanoTime()
        while (System.nanoTime() - started < timeoutMs * 1_000_000L) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Test
    fun armsRecordsATakeWithTheCountInAndDisarms() = runBlocking {
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        engine.setTempo(Tempo(120), TimeSignature.FourFour)
        engine.setArrangement(ArrangementSnapshot(tracks = listOf(TrackRender(1f, true)), clips = emptyList()))
        val armed = assertIs<ArmResult.Armed>(engine.arm(2))
        assertTrue(armed.channels in 1..2, "channels ${armed.channels}")
        assertTrue(armed.sampleRate > 0)
        val snapshot = CaptureSnapshot()
        // The callback publishes the armed snapshot once both streams run: on a device busy with a previous lane the
        // duplex start can take over a second (02:54 on 2026-10-28), so the wait is generous.
        assertTrue(waitFor(5_000) { engine.readCapture(snapshot); snapshot.armed }, "the capture snapshot never reported armed")
        assertEquals(armed.channels, snapshot.inputChannels)
        assertEquals(armed.sampleRate, snapshot.sampleRate)
        assertTrue(snapshot.alignmentFrames > 0, "the alignment is the input plus the output latency: ${snapshot.alignmentFrames}")
        // The measured value for specs/010-timeline-recording/evidence/alignment.md (read from logcat after the lane).
        android.util.Log.i("TembraRecordingTest", "alignmentFrames=${snapshot.alignmentFrames} sampleRate=${snapshot.sampleRate} inputLatencyNs=${snapshot.inputLatencyNanos} outputLatencyNs=${snapshot.outputLatencyNanos}")
        assertTrue(snapshot.outputLatencyNanos > 0)
        assertEquals(TakeStopReason.User, snapshot.stopReason)

        val path = File(root, ".take-1.wav").path
        val config = RecordConfig.of(path, trackIndex = 0, countInBeats = 4, sampleRate = armed.sampleRate)
        assertEquals(RecordStartResult.Started, engine.recordStart(config))
        assertEquals(RecordStartResult.TakeRunning, engine.recordStart(config))
        val transport = TransportSnapshot()
        assertTrue(waitFor(3_000) { engine.readTransport(transport); transport.playing && transport.countInBeatsLeft > 0 }, "the count-in never started")
        assertEquals(4, transport.countInBeatsLeft)
        // One bar of count-in at 120 BPM is two seconds; the take then captures from the downbeat.
        assertTrue(waitFor(3_500) { engine.readCapture(snapshot); snapshot.recording }, "the take never started after the count-in")
        val captureBegan = System.nanoTime()
        engine.readTransport(transport)
        assertTrue(transport.recording)
        assertEquals(-1, transport.countInBeatsLeft)
        Thread.sleep(4_000)
        val info = engine.recordStop()
        val recordedSeconds = (System.nanoTime() - captureBegan) / 1e9
        assertEquals(TakeStopReason.User, info.stopReason)
        assertEquals(armed.channels, info.channels)
        assertEquals(armed.sampleRate, info.sampleRate)
        assertTrue(info.alignmentFrames > 0)
        val seconds = info.frames.toDouble() / info.sampleRate
        assertTrue(seconds in (recordedSeconds - 0.6)..(recordedSeconds + 0.1), "kept $seconds s of a ${recordedSeconds}s take")
        assertTrue(info.startFrames >= 0)
        assertTrue(info.startBeats in 0.0..0.2, "the take starts on the downbeat, at ${info.startBeats}")
        val inspected = engine.inspect(path)!!
        assertEquals(info.frames, inspected.frames, "the WAV header agrees with the take info")
        assertEquals(info.channels, inspected.channels)
        assertEquals(info.sampleRate, inspected.sampleRate)
        engine.readTransport(transport)
        assertTrue(transport.playing, "playback continues after the stop")
        // The live peak ring delivered one bucket per 256 kept frames.
        val buckets = PeakBuckets()
        val next = engine.readPeaks(0, buckets)
        assertTrue(buckets.count > 0, "no peak buckets reached the ring")
        assertTrue(next > 0)
        engine.pause()
        engine.disarm()
        assertTrue(waitFor(500) { engine.readCapture(snapshot); !snapshot.armed })
        engine.disarm() // idempotent
        assertEquals(AudioState.Transport, engine.audioState)
    }

    @Test
    fun aCancelledCountInLeavesNoFileAndArmIsRefusedWithoutATransport() = runBlocking {
        assertEquals(ArmResult.NotTransport, engine.arm(1))
        assertEquals(TransportOpenResult.Opened, engine.openTransport())
        engine.setTempo(Tempo(120), TimeSignature.FourFour)
        assertIs<ArmResult.Armed>(engine.arm(1))
        assertIs<ArmResult.Armed>(engine.arm(1)) // idempotent
        val path = File(root, ".take-2.wav").path
        assertEquals(RecordStartResult.Started, engine.recordStart(RecordConfig.of(path, 0, countInBeats = 8, sampleRate = 48000)))
        Thread.sleep(300)
        engine.recordCancel()
        Thread.sleep(100)
        val transport = TransportSnapshot()
        engine.readTransport(transport)
        assertTrue(!transport.playing)
        assertEquals(-1, transport.countInBeatsLeft)
        assertTrue(!File(path).exists(), "the staging file survived the cancel")
        engine.disarm()
    }
}
