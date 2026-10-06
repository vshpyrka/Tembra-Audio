package audio.tembra.engine

import androidx.test.platform.app.InstrumentationRegistry
import audio.tembra.core.tuner.Note
import audio.tembra.core.tuner.PitchMath
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The noise gate through the real engine: synthetic device, ring buffer, analyzer, gate and JNI (feature 006, spec
 * FR-022; contracts/tuner-audio-kotlin.md). Four in-tune E2 signals at set levels against four gate values, then a
 * live change of the gate and the rate at which readings arrive.
 */
class NoiseGateEndToEndTest {
    private val engine: AudioEngine = NativeAudioEngine(InstrumentationRegistry.getInstrumentation().targetContext)

    private val levels = listOf("e2-70db" to -70f, "e2-55db" to -55f, "e2-45db" to -45f, "e2-30db" to -30f)
    private val gates = listOf<Float?>(null, -60f, -50f, -40f)
    private val e2 = Note.parse("E2")

    @AfterTest
    fun tearDown() {
        engine.stopListening()
        // The gate value lives in the engine for the life of the process: leave none behind for the next test.
        engine.setNoiseGate(null)
    }

    /** Polls until [accept] holds or [millis] pass; fails if the sequence ever goes back (a restarted stream). */
    private fun await(millis: Long, label: String, accept: (PitchSnapshot) -> Boolean): PitchSnapshot {
        val snapshot = PitchSnapshot()
        var lastSequence = -1L
        val deadline = System.currentTimeMillis() + millis
        while (true) {
            engine.readPitch(snapshot)
            assertTrue(snapshot.sequence >= lastSequence, "$label: the sequence went back from $lastSequence to ${snapshot.sequence}")
            lastSequence = snapshot.sequence
            if (accept(snapshot)) return snapshot
            if (System.currentTimeMillis() > deadline) fail("$label: not within $millis ms (sequence ${snapshot.sequence}, pitch ${snapshot.hasPitch})")
            Thread.sleep(5)
        }
    }

    @Test
    fun everyLevelSignalIsReadOrHeldBackByEveryGate() {
        for (gate in gates) {
            for ((id, level) in levels) {
                val label = "gate ${gate ?: "off"}, $id"
                val reads = gate == null || level >= gate
                assertEquals(AudioResult.Ok, engine.setNoiseGate(gate), label)
                assertEquals(AudioResult.Ok, engine.startTestSignal(TestSignals.byId(id)!!.signal), label)
                var everRead = false
                val snapshot = await(3_000, label) {
                    if (it.hasPitch) everRead = true
                    it.sequence >= 20L
                }
                assertTrue(snapshot.synthetic, label)
                assertEquals(reads, snapshot.gateOpen, "$label: gate open")
                assertTrue(abs(snapshot.inputLevelDb - level) <= 1f, "$label: level ${snapshot.inputLevelDb} dB")
                if (reads) {
                    assertTrue(snapshot.hasPitch, "$label: no pitch")
                    val reading = PitchMath.nearest(snapshot.frequencyHz.toDouble())
                    assertEquals(e2, reading.note, "$label: read ${snapshot.frequencyHz} Hz")
                    assertTrue(abs(reading.cents) <= 1.0, "$label: ${reading.cents} cents")
                } else {
                    assertFalse(everRead, "$label: a pitch was read behind the gate")
                    assertFalse(snapshot.hasPitch, "$label: read ${snapshot.frequencyHz} Hz")
                }
                engine.stopListening()
                assertEquals(AudioState.Idle, engine.audioState, label)
            }
        }
    }

    @Test
    fun movingTheGateWhileListeningTakesEffectWithoutRestartingTheStream() {
        assertEquals(AudioResult.Ok, engine.setNoiseGate(null))
        assertEquals(AudioResult.Ok, engine.startTestSignal(TestSignals.byId("e2-55db")!!.signal))
        val before = await(3_000, "no gate") { it.sequence >= 20L && it.hasPitch }

        assertEquals(AudioResult.Ok, engine.setNoiseGate(-50f))
        val rested = await(500, "gate over the sound") { !it.hasPitch }
        assertFalse(rested.gateOpen)
        assertTrue(rested.sequence > before.sequence, "the stream restarted: ${before.sequence} then ${rested.sequence}")
        assertEquals(AudioState.Listening, engine.audioState)

        assertEquals(AudioResult.Ok, engine.setNoiseGate(null))
        val readAgain = await(250, "no gate again") { it.hasPitch }
        assertTrue(readAgain.gateOpen)
        assertTrue(readAgain.sequence > rested.sequence, "the stream restarted: ${rested.sequence} then ${readAgain.sequence}")
        assertEquals(AudioState.Listening, engine.audioState)
        assertEquals(e2, PitchMath.nearest(readAgain.frequencyHz.toDouble()).note)
    }

    @Test
    fun readingsArriveAtLeastTwentyTimesASecond() {
        assertEquals(AudioResult.Ok, engine.startTestSignal(TestSignals.byId("e2-30db")!!.signal))
        val first = await(3_000, "warm-up") { it.sequence >= 5L }
        val start = System.currentTimeMillis()
        val startSequence = first.sequence
        val later = await(3_000, "one second") { System.currentTimeMillis() - start >= 1_000 }
        assertTrue(later.sequence - startSequence >= 20L, "only ${later.sequence - startSequence} readings in a second")
    }
}
