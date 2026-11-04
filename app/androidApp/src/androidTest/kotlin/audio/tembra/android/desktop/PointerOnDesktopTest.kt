package audio.tembra.android.desktop

import audio.tembra.ui.perf.PerfAnchors
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real mouse input in a desktop window (feature 011, spec FR-012, FR-014): a secondary press from the platform's mouse
 * on the first clip opens its menu, and the wheel (horizontal, or vertical with Shift) moves the timeline in time.
 */
class PointerOnDesktopTest {

    @Before
    fun openTheTimeline() {
        DesktopWindow.assumeFreeform()
        DesktopWindow.launchFreeform()
        DesktopWindow.resizeTaskDp(1200, 800)
        DesktopApp.tap(PerfAnchors.NAV_TIMELINE)
        DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST)
    }

    @Test
    fun aRightClickOnAClipOpensItsMenu() {
        val clip = DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST).visibleBounds
        DesktopInput.secondaryClick(clip.centerX().toFloat(), clip.centerY().toFloat())
        assertNotNull("no clip menu after a right-click", DesktopApp.device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("${PerfAnchors.TIMELINE_CLIP_FIRST}.menu")), 5_000))
        DesktopInput.key("KEYCODE_ESCAPE")
    }

    @Test
    fun aHorizontalWheelMovesTheTimelineInTime() {
        val before = DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST).visibleBounds
        val lane = DesktopApp.find(PerfAnchors.TIMELINE_LANE_FIRST).visibleBounds
        DesktopInput.scroll(lane.centerX().toFloat(), lane.centerY().toFloat(), horizontal = 3f)
        DesktopApp.waitUntil("the clip to move with the timeline") { DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST).visibleBounds.left != before.left }
        val after = DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST).visibleBounds
        assertTrue("the wheel moved the clip ${after.left - before.left} px", after.left != before.left)
    }
}
