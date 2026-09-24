package audio.tembra.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowWidthTest {

    @Test
    fun mapsWidthsToTheMaterialBuckets() {
        assertEquals(WindowWidth.Compact, WindowWidth.fromWidth(0.dp))
        assertEquals(WindowWidth.Compact, WindowWidth.fromWidth(599.dp))
        assertEquals(WindowWidth.Medium, WindowWidth.fromWidth(600.dp))
        assertEquals(WindowWidth.Medium, WindowWidth.fromWidth(839.dp))
        assertEquals(WindowWidth.Expanded, WindowWidth.fromWidth(840.dp))
    }

    @Test
    fun onlyCompactIsNarrow() {
        assertFalse(WindowWidth.Compact.isWide)
        assertTrue(WindowWidth.Medium.isWide)
        assertTrue(WindowWidth.Expanded.isWide)
    }
}
