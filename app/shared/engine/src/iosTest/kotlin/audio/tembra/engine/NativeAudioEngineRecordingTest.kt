package audio.tembra.engine

import audio.tembra.core.metronome.Tempo
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.recording.TakeStopReason
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.inputNumberOfChannels
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The real engine's capture path through the cinterop actual on the simulator (feature 010, contracts/engine-c-abi-v7.md;
 * T022): arm enables the RemoteIO unit's input bus beside its output, the capture snapshot reports the channels and the
 * rate, a take with a one-bar count-in at 120 BPM records a WAV whose header agrees with the take info, the peak ring
 * delivers buckets, and disarm closes the input. The bare simulator test process has no output unit (the transport test's
 * skip, feature 003) and may have no microphone grant: where the open or the arm reports a device error the checks are
 * skipped and the simulator app and the devices run them (quickstart §3; `xcrun simctl privacy booted grant microphone`).
 */
@OptIn(ExperimentalForeignApi::class)
class NativeAudioEngineRecordingTest {
    private val engine = NativeAudioEngine()
    private val root = NSTemporaryDirectory().trimEnd('/') + "/recording-test-${Random.nextLong()}"

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun tearDown() {
        runBlocking { engine.disarm() }
        engine.closeTransport()
        NSFileManager.defaultManager.removeItemAtPath(root, error = null)
    }

    private fun sleep(millis: Long) = NSThread.sleepForTimeInterval(millis / 1000.0)

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val mark = TimeSource.Monotonic.markNow()
        while (mark.elapsedNow().inWholeMilliseconds < timeoutMs) {
            if (condition()) return true
            sleep(10)
        }
        return condition()
    }

    private suspend fun openAndArmOrSkip(test: String): ArmResult.Armed? {
        when (val opened = engine.openTransport()) {
            TransportOpenResult.Opened -> Unit
            TransportOpenResult.DeviceFailed -> {
                println("NativeAudioEngineRecordingTest: no output unit in this simulator process; $test skipped")
                return null
            }
            is TransportOpenResult.Busy -> throw AssertionError("the engine was busy before the test started: $opened")
        }
        engine.setTempo(Tempo(120), TimeSignature.FourFour)
        engine.setArrangement(ArrangementSnapshot(tracks = listOf(TrackRender(1f, true)), clips = emptyList()))
        return when (val armed = engine.arm(2)) {
            is ArmResult.Armed -> armed
            ArmResult.InputUnavailable -> {
                println("NativeAudioEngineRecordingTest: the input could not open in this simulator process (no microphone grant or unit); $test skipped")
                assertEquals(AudioState.Transport, engine.audioState, "a failed arm leaves the transport open")
                null
            }
            ArmResult.NotTransport -> throw AssertionError("arm reported no transport right after the open")
        }
    }

    @Test
    fun armsRecordsATakeWithTheCountInAndDisarms() = runBlocking {
        val armed = openAndArmOrSkip("capture") ?: return@runBlocking
        assertTrue(armed.channels in 1..2)
        assertTrue(armed.sampleRate > 0)
        val snapshot = CaptureSnapshot()
        assertTrue(waitFor(1_000) { engine.readCapture(snapshot); snapshot.armed })
        assertEquals(armed.channels, snapshot.inputChannels)
        assertEquals(armed.sampleRate, snapshot.sampleRate)
        assertTrue(snapshot.alignmentFrames >= 0)
        val path = "$root/.take-1.wav"
        val config = RecordConfig.of(path, trackIndex = 0, countInBeats = 4, sampleRate = armed.sampleRate)
        assertEquals(RecordStartResult.Started, engine.recordStart(config))
        val transport = TransportSnapshot()
        assertTrue(waitFor(1_000) { engine.readTransport(transport); transport.playing && transport.countInBeatsLeft > 0 })
        assertTrue(waitFor(3_500) { engine.readCapture(snapshot); snapshot.recording }, "the take never started after the count-in")
        val captureBegan = TimeSource.Monotonic.markNow()
        sleep(4_000)
        val info = engine.recordStop()
        val recordedSeconds = captureBegan.elapsedNow().inWholeMilliseconds / 1000.0
        assertEquals(TakeStopReason.User, info.stopReason)
        assertEquals(armed.channels, info.channels)
        val seconds = info.frames.toDouble() / info.sampleRate
        assertTrue(seconds in (recordedSeconds - 0.6)..(recordedSeconds + 0.1), "kept $seconds s of a ${recordedSeconds}s take")
        assertTrue(info.startBeats in 0.0..0.2, "the take starts on the downbeat, at ${info.startBeats}")
        val inspected = engine.inspect(path)!!
        assertEquals(info.frames, inspected.frames, "the WAV header agrees with the take info")
        assertEquals(info.channels, inspected.channels)
        val buckets = PeakBuckets()
        engine.readPeaks(0, buckets)
        assertTrue(buckets.count > 0, "no peak buckets reached the ring")
        engine.pause()
        engine.disarm()
        assertTrue(waitFor(500) { engine.readCapture(snapshot); !snapshot.armed })
        assertEquals(AudioState.Transport, engine.audioState)
    }

    /**
     * The first arm after the unit is created must open exactly the route's input channels: a client format wider than
     * the hardware records a take whose extra channel is silence (seen as a stereo WAV with a dead right channel on
     * every first take of 2026-11-02).
     */
    @Test
    fun theFirstArmReportsTheRoutesInputChannels() = runBlocking {
        val armed = openAndArmOrSkip("channels") ?: return@runBlocking
        val route = AVAudioSession.sharedInstance().inputNumberOfChannels.toInt()
        assertEquals(route, armed.channels, "the first arm must open the route's $route input channel(s), not a default")
    }

    @Test
    fun aCancelledCountInLeavesNoFile() = runBlocking {
        assertEquals(ArmResult.NotTransport, engine.arm(1))
        openAndArmOrSkip("cancel") ?: return@runBlocking
        val path = "$root/.take-2.wav"
        assertEquals(RecordStartResult.Started, engine.recordStart(RecordConfig.of(path, 0, countInBeats = 8, sampleRate = 48000)))
        sleep(300)
        engine.recordCancel()
        sleep(100)
        val transport = TransportSnapshot()
        engine.readTransport(transport)
        assertTrue(!transport.playing)
        assertTrue(!NSFileManager.defaultManager.fileExistsAtPath(path), "the staging file survived the cancel")
    }
}
