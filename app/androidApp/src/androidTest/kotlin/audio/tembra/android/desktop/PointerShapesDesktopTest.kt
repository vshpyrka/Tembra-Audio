package audio.tembra.android.desktop

import android.view.PointerIcon
import audio.tembra.android.MainActivity
import audio.tembra.data.projects.PickedFile
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.session.ImportTarget
import audio.tembra.core.project.Beats
import okio.Buffer
import okio.Source
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch

/**
 * Pointer shapes in a desktop window (feature 011, spec FR-015, FR-015a; contracts/pointer-input.md): a hand over a
 * design-system button, a text cursor over the Projects search field, an open hand over a clip and a closed one while
 * dragging it, and the busy shape over the window while an import copies files. Read through the root view's
 * `onResolvePointerIcon` at real mouse positions.
 */
class PointerShapesDesktopTest {

    private lateinit var activity: MainActivity

    @Before
    fun openTheApp() {
        DesktopWindow.assumeFreeform()
        activity = DesktopWindow.launchFreeform()
        DesktopWindow.resizeTaskDp(1200, 800)
    }

    private fun iconAt(anchor: String): PointerIcon? {
        val bounds = DesktopApp.find(anchor).visibleBounds
        DesktopInput.hover(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        return DesktopInput.resolvedPointerIcon(activity, bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    @Test
    fun aHandOverButtonsAndATextCursorOverTheSearchField() {
        DesktopApp.tap(PerfAnchors.NAV_TIMELINE)
        assertEquals(DesktopInput.systemIcon(activity, PointerIcon.TYPE_HAND), iconAt(PerfAnchors.TIMELINE_ADD_TRACK))
        DesktopApp.tap(PerfAnchors.NAV_PROJECTS)
        assertEquals(DesktopInput.systemIcon(activity, PointerIcon.TYPE_TEXT), iconAt(PerfAnchors.PROJECTS_SEARCH))
    }

    @Test
    fun anOpenHandOverAClipAndAClosedOneWhileDraggingIt() {
        DesktopApp.tap(PerfAnchors.NAV_TIMELINE)
        assertEquals(DesktopInput.systemIcon(activity, PointerIcon.TYPE_GRAB), iconAt(PerfAnchors.TIMELINE_CLIP_FIRST))
        val clip = DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST).visibleBounds
        val x = clip.centerX().toFloat()
        val y = clip.centerY().toFloat()
        DesktopInput.primaryDrag(x, y, x + 40f, y, release = false)
        try {
            assertEquals(DesktopInput.systemIcon(activity, PointerIcon.TYPE_GRABBING), DesktopInput.resolvedPointerIcon(activity, x + 40f, y))
        } finally {
            DesktopInput.key("KEYCODE_ESCAPE")
            DesktopInput.releasePrimary(x + 40f, y)
        }
    }

    @Test
    fun theBusyShapeWhileAnImportCopiesFiles() {
        DesktopApp.tap(PerfAnchors.NAV_TIMELINE)
        DesktopApp.find(PerfAnchors.TIMELINE_CLIP_FIRST)
        val release = CountDownLatch(1)
        // A file whose bytes arrive only when the test lets them: the import stays in flight meanwhile.
        val slow = PickedFile("slow.wav") {
            object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    release.await()
                    return -1
                }
                override fun timeout() = Timeout.NONE
                override fun close() = Unit
            }
        }
        DesktopApp.graph.projectSession.importAudio(listOf(slow), ImportTarget.NewTracks(Beats.Zero))
        try {
            DesktopApp.waitUntil("the session to report work in progress") { DesktopApp.graph.projectSession.busy.value }
            assertEquals(DesktopInput.systemIcon(activity, PointerIcon.TYPE_WAIT), iconAt(PerfAnchors.TIMELINE_RULER))
        } finally {
            release.countDown()
        }
        DesktopApp.waitUntil("the import to end") { !DesktopApp.graph.projectSession.busy.value }
    }
}
