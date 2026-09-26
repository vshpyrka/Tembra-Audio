package audio.tembra.engine

import audio.tembra.core.tuner.PitchMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.TimeSource

/** Every catalogue test signal through the real engine's synthetic device, ring buffer, analyzer and cinterop (spec FR-028, research R16). */
class TunerSignalEndToEndTest {
    private val engine: AudioEngine = NativeAudioEngine()

    @AfterTest
    fun tearDown() = engine.stopListening()

    @Test
    fun everyCatalogueSignalReadsAsExpected() = runBlocking {
        val started = TimeSource.Monotonic.markNow()
        for (entry in TestSignals.catalogue) {
            assertEquals(AudioResult.Ok, engine.startTestSignal(entry.signal), entry.id)
            assertEquals(AudioState.Listening, engine.audioState, entry.id)
            val snapshot = PitchSnapshot()
            val deadline = TimeSource.Monotonic.markNow()
            while (true) {
                engine.readPitch(snapshot)
                if (snapshot.sequence >= 10L) break
                if (deadline.elapsedNow().inWholeMilliseconds > 3_000) fail("${entry.id}: only ${snapshot.sequence} hops after 3 s")
                delay(50)
            }
            assertTrue(snapshot.synthetic, entry.id)
            assertEquals(AudioState.Listening, snapshot.state, entry.id)
            when (val expected = entry.expected) {
                is ExpectedReading.Note -> {
                    assertTrue(snapshot.hasPitch, "${entry.id}: no pitch")
                    val reading = PitchMath.nearest(snapshot.frequencyHz.toDouble())
                    assertEquals(expected.note, reading.note, "${entry.id}: read ${snapshot.frequencyHz} Hz")
                    assertTrue(abs(reading.cents - expected.cents) <= 1.0, "${entry.id}: ${reading.cents} cents, expected ${expected.cents}")
                }
                ExpectedReading.Resting -> assertFalse(snapshot.hasPitch, "${entry.id}: read ${snapshot.frequencyHz} Hz")
            }
            engine.stopListening()
            assertEquals(AudioState.Idle, engine.audioState, entry.id)
        }
        assertTrue(started.elapsedNow().inWholeSeconds < 60, "the catalogue took longer than a minute")
    }
}
