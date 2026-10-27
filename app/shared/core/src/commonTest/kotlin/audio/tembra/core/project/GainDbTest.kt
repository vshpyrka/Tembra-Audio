package audio.tembra.core.project

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Track gain: silence to +6 dB with the design's readouts (spec FR-023; data-model.md section 1). */
class GainDbTest {

    @Test
    fun rangeIsSilenceToPlusSix() {
        GainDb(6.0f)
        GainDb(Float.NEGATIVE_INFINITY)
        assertFailsWith<IllegalArgumentException> { GainDb(6.01f) }
        assertFailsWith<IllegalArgumentException> { GainDb(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { GainDb(Float.POSITIVE_INFINITY) }
        assertEquals(GainDb(6.0f), GainDb.clamped(20f))
        assertEquals(GainDb.Silence, GainDb.clamped(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun readoutsUseOneDecimalASignAndTheUnicodeMinus() {
        assertEquals("+0.8dB", GainDb(0.8f).readout)
        assertEquals("−2.4dB", GainDb(-2.4f).readout)
        assertEquals("+0.0dB", GainDb.Unity.readout)
        assertEquals("+6.0dB", GainDb(6f).readout)
        assertEquals("−∞", GainDb.Silence.readout)
        assertEquals("+0.0dB", GainDb(-0.04f).readout)
        assertEquals("−12.0dB", GainDb(-12f).readout)
        assertEquals("−0.5dB", GainDb(-0.5f).readout)
    }

    @Test
    fun toLinearIsTenToTheDbOverTwenty() {
        assertEquals(1f, GainDb.Unity.toLinear())
        assertEquals(0f, GainDb.Silence.toLinear())
        assertTrue(abs(GainDb(-6.0206f).toLinear() - 0.5f) < 1e-4f)
        assertTrue(abs(GainDb(6f).toLinear() - 1.9953f) < 1e-3f)
    }
}
