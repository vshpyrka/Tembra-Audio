package audio.tembra.ui.tuner

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The meter's row layout in both sizes and its inverse, the width that fits a height (data-model.md §8). */
class MeterLayoutTest {

    @Test
    fun compactRowsAreShorterAndTheArcIsNot() {
        val regular = MeterLayout(300f)
        val compact = MeterLayout(300f, size = MeterSize.Compact)
        assertEquals(188f, regular.textRowsHeight)
        assertEquals(132f, compact.textRowsHeight)
        assertEquals(regular.arcHeight, compact.arcHeight)
        assertEquals(compact.arcHeight + 132f, compact.height)
        assertTrue(compact.noteBaseline < regular.noteBaseline)
    }

    @Test
    fun rowsScaleWithDensityAndFontScale() {
        assertEquals(132f * 2f * 1.5f, MeterLayout(300f, density = 2f, fontScale = 1.5f, size = MeterSize.Compact).textRowsHeight)
        assertEquals(188f * 2.75f, MeterLayout.textRowsHeight(density = 2.75f))
    }

    @Test
    fun widthForHeightRoundTripsThroughTheLayout() {
        for (size in MeterSize.entries) {
            for ((density, fontScale) in listOf(1f to 1f, 2.75f to 2f, 3f to 1.3f)) {
                // 1400 px is taller than the tallest rows here (188 × 2.75 × 2), so a positive width exists.
                val width = MeterLayout.widthForHeight(1400f, density, fontScale, size)
                val height = MeterLayout(width, density, fontScale, size).height
                assertTrue(width > 0f && abs(height - 1400f) < 0.5f, "$size at $density/$fontScale: width $width gives height $height")
            }
        }
    }

    @Test
    fun widthForHeightIsNeverNegative() {
        assertEquals(0f, MeterLayout.widthForHeight(10f))
        assertEquals(0f, MeterLayout.widthForHeight(100f, density = 3f, size = MeterSize.Compact))
    }
}
