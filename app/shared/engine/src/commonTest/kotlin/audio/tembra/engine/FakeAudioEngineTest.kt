package audio.tembra.engine

import audio.tembra.core.version.SemanticVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The test double every Kotlin tuner test programs against (contracts/tuner-audio-kotlin.md). */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeAudioEngineTest {

    @Test
    fun defaultDescriptorIsAnEmptyZeroVersionEngine() {
        val descriptor = FakeAudioEngine().describe()
        assertEquals("Tembra Engine", descriptor.name)
        assertEquals(SemanticVersion(0, 0, 0), descriptor.version)
        assertEquals(emptyList(), descriptor.compiledLibraries)
        assertEquals("Tembra Engine 0.0.0 ()", descriptor.rawDescription)
    }

    @Test
    fun returnsTheConfiguredDescriptorAndCountsCalls() {
        val configured = EngineDescriptor(
            name = "Tembra Engine",
            version = SemanticVersion(9, 8, 7),
            compiledLibraries = listOf("miniaudio 0.11.25"),
            rawDescription = "Tembra Engine 9.8.7 (miniaudio 0.11.25)",
        )
        val engine = FakeAudioEngine(configured)
        assertEquals(0, engine.describeCalls)
        assertEquals(configured, engine.describe())
        assertEquals(configured, engine.describe())
        assertEquals(2, engine.describeCalls)
    }

    @Test
    fun listeningFollowsTheCalls() {
        val engine = FakeAudioEngine()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(AudioResult.Ok, engine.startListening())
        assertEquals(listOf("startListening"), engine.calls)
        assertEquals(AudioState.Listening, engine.audioState)
        engine.stopListening()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(listOf("startListening", "stopListening"), engine.calls)
    }

    @Test
    fun testSignalsAreRecordedAndMarkTheSnapshotSynthetic() {
        val engine = FakeAudioEngine()
        val signal = TestSignals.byId("e2-flat-20")!!.signal
        assertEquals(AudioResult.Ok, engine.startTestSignal(signal))
        assertEquals(listOf("startTestSignal"), engine.calls)
        assertEquals(signal, engine.lastTestSignal)
        assertEquals(AudioState.Listening, engine.audioState)
        assertTrue(engine.nextSnapshot.synthetic)
    }

    @Test
    fun tonesFollowTheCalls() {
        val engine = FakeAudioEngine()
        assertEquals(AudioResult.Ok, engine.startTone(82.41, 0))
        assertEquals(AudioState.Tone, engine.audioState)
        assertEquals(listOf("startTone(82.41, 0)"), engine.calls)
        engine.stopTone()
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(listOf("startTone(82.41, 0)", "stopTone"), engine.calls)
    }

    @Test
    fun readPitchCopiesTheScriptedSnapshotAndCountsHeartbeats() {
        val engine = FakeAudioEngine()
        engine.nextSnapshot.apply {
            sequence = 42L
            state = AudioState.Listening
            sampleRate = 48000
            frequencyHz = 83.37f
            clarity = 0.9f
            inputPeak = 0.4f
            hasPitch = true
        }
        val snapshot = PitchSnapshot()
        engine.readPitch(snapshot)
        assertEquals(42L, snapshot.sequence)
        assertEquals(AudioState.Listening, snapshot.state)
        assertEquals(48000, snapshot.sampleRate)
        assertEquals(83.37f, snapshot.frequencyHz)
        assertEquals(0.9f, snapshot.clarity)
        assertEquals(0.4f, snapshot.inputPeak)
        assertTrue(snapshot.hasPitch)
        assertEquals(1, engine.readCount)
        engine.readPitch(snapshot)
        assertEquals(2, engine.readCount)
    }

    @Test
    fun samplesGetIncreasingHandlesAndPlayAsTones() {
        val engine = FakeAudioEngine()
        val first = engine.loadSample(byteArrayOf(1))
        val second = engine.loadSample(byteArrayOf(1, 2))
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(second.id > first.id)
        assertNull(engine.loadSample(ByteArray(0)))
        assertEquals(AudioResult.Ok, engine.playSample(first, loop = true))
        assertEquals(AudioState.Tone, engine.audioState)
        assertEquals("playSample(${first.id}, true)", engine.calls.last())
        engine.stopTone()
        engine.unloadSample(first)
        assertEquals("unloadSample", engine.calls.last())
    }

    @Test
    fun aScriptedFailureLeavesTheStateUnchanged() {
        val engine = FakeAudioEngine()
        engine.failNextStart = AudioResult.Error(-2)
        assertEquals(AudioResult.Error(-2), engine.startListening())
        assertEquals(AudioState.Idle, engine.audioState)
        assertEquals(AudioResult.Ok, engine.startListening())
        assertEquals(AudioState.Listening, engine.audioState)
    }

    @Test
    fun theWatchdogDropsToIdle() {
        val engine = FakeAudioEngine()
        engine.startListening()
        engine.simulateWatchdog()
        assertEquals(AudioState.Idle, engine.audioState)
    }

    @Test
    fun emittedEventsReachASubscriber() = runTest {
        val engine = FakeAudioEngine()
        val received = async { engine.events.first() }
        runCurrent()
        engine.emit(AudioEvent.Interrupted)
        assertEquals(AudioEvent.Interrupted, received.await())
    }
}
