package audio.tembra.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TembraMotionTest {

    @Test
    fun defaultsToTheMaterialStandardTransition() {
        val motion = TembraMotion()
        assertEquals(300, motion.standardDuration)
        assertEquals(0f, motion.standardEasing.transform(0f))
        assertEquals(1f, motion.standardEasing.transform(1f))
        assertFalse(motion.reduceMotion)
        assertEquals(300, motion.effectiveDuration)
    }

    @Test
    fun reduceMotionZeroesTheEffectiveDuration() {
        assertEquals(0, TembraMotion(reduceMotion = true).effectiveDuration)
    }
}
