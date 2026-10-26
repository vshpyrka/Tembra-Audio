package audio.tembra.core.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The zoom control's stored ids are a file format: they must read back, and nothing else may (spec FR-015, D1). */
class ZoomControlTest {

    @Test
    fun theButtonsAreTheDefaultAndEveryIdReadsBack() {
        assertEquals(ZoomControl.Buttons, ZoomControl.Default)
        assertEquals(listOf("buttons", "overviewStrip"), ZoomControl.entries.map { it.id })
        for (control in ZoomControl.entries) assertEquals(control, ZoomControl.byId(control.id))
        assertNull(ZoomControl.byId("strip"))
    }
}
