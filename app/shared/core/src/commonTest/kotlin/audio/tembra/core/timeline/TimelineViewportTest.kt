package audio.tembra.core.timeline

import audio.tembra.core.project.Beats
import audio.tembra.core.project.ZoomLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Beats to pixels and back, zoom around a point, and the zoom bounds of spec FR-015 (data-model.md section 2). */
class TimelineViewportTest {

    /** 10 px per beat at 1x, 2x density (20 device px per beat), scrolled to beat 4, 800 px wide, a 100-beat project in 4/4. */
    private val viewport = TimelineViewport(
        pixelsPerBeat = 10.0, scrollBeats = Beats(4.0), widthPx = 800.0, densityScale = 2.0,
        projectLengthBeats = Beats(100.0), beatsPerBar = 4,
    )

    private fun assertClose(expected: Double, actual: Double) = assertTrue(abs(expected - actual) < 1e-9, "expected $expected, was $actual")

    @Test
    fun beatsMapToDevicePixelsFromTheScrollOrigin() {
        assertClose(0.0, viewport.xOf(Beats(4.0)))
        assertClose(100.0, viewport.xOf(Beats(9.0)))
        assertClose(-40.0, viewport.xOf(Beats(2.0)))
        assertClose(9.0, viewport.beatsAt(100.0).value)
        assertClose(44.0, viewport.endBeats.value)
        assertClose(40.0, viewport.visibleBeats)
        assertEquals(ZoomLevel(10.0), viewport.zoom)
    }

    @Test
    fun zoomAroundKeepsTheBeatUnderTheCentreFixed() {
        val before = viewport.beatsAt(200.0)
        val zoomed = viewport.zoomAround(centreX = 200.0, factor = 2.0)
        assertClose(20.0, zoomed.pixelsPerBeat)
        assertClose(before.value, zoomed.beatsAt(200.0).value)
        val out = zoomed.zoomAround(centreX = 100.0, factor = 0.5)
        assertClose(zoomed.beatsAt(100.0).value, out.beatsAt(100.0).value)
        assertClose(6.5, out.scrollBeats.value)
    }

    @Test
    fun fitShowsTheWholeProjectAndAtLeastThirtyTwoBars() {
        assertClose(800.0 / (128.0 * 2.0), viewport.fitPixelsPerBeat)
        val fitted = viewport.fit()
        assertEquals(Beats.Zero, fitted.scrollBeats)
        assertClose(128.0, fitted.visibleBeats)
        val long = viewport.copy(projectLengthBeats = Beats(400.0))
        assertClose(400.0, long.fit().visibleBeats)
        val sixEight = viewport.copy(beatsPerBar = 6)
        assertClose(192.0, sixEight.fit().visibleBeats)
    }

    @Test
    fun closestShowsOneBeatAcrossTheWidth() {
        assertClose(400.0, viewport.closestPixelsPerBeat)
        val closest = viewport.withZoom(ZoomLevel(400.0))
        assertClose(1.0, closest.visibleBeats)
    }

    @Test
    fun zoomIsClampedToTheBounds() {
        assertClose(viewport.closestPixelsPerBeat, viewport.zoomAround(100.0, 1e6).pixelsPerBeat)
        assertClose(viewport.fitPixelsPerBeat, viewport.zoomAround(100.0, 1e-6).pixelsPerBeat)
        assertClose(viewport.closestPixelsPerBeat, viewport.withZoom(ZoomLevel(9_999.0)).pixelsPerBeat)
        assertClose(viewport.fitPixelsPerBeat, viewport.withZoom(ZoomLevel(0.001)).pixelsPerBeat)
        assertClose(5.0, viewport.withZoom(ZoomLevel(5.0)).pixelsPerBeat)
        assertEquals(viewport.scrollBeats, viewport.withZoom(ZoomLevel(5.0)).scrollBeats)
    }

    @Test
    fun scrollNeverGoesBeforeTheStart() {
        assertEquals(Beats.Zero, viewport.scrollBy(-1_000.0).scrollBeats)
        assertClose(6.0, viewport.scrollBy(40.0).scrollBeats.value)
        assertEquals(Beats(4.0), viewport.zoomAround(centreX = 0.0, factor = 0.1).scrollBeats)
        assertTrue(viewport.zoomAround(centreX = 799.0, factor = 0.01).scrollBeats.value >= 0.0)
        assertClose(12.0, viewport.scrollTo(Beats(12.0)).scrollBeats.value)
        assertEquals(Beats.Zero, viewport.scrollTo(Beats(-1.0)).scrollBeats)
    }

    @Test
    fun aViewportWithoutAWidthHasNoBoundsYet() {
        val unmeasured = viewport.copy(widthPx = 0.0)
        assertClose(20.0, unmeasured.zoomAround(0.0, 2.0).pixelsPerBeat)
        assertClose(3.0, unmeasured.withZoom(ZoomLevel(3.0)).pixelsPerBeat)
    }

    @Test
    fun fittedBuildsTheWidestViewportForAProject() {
        val fitted = TimelineViewport.fitted(widthPx = 1_000.0, densityScale = 2.5, projectLengthBeats = Beats(64.0), beatsPerBar = 4)
        assertEquals(Beats.Zero, fitted.scrollBeats)
        assertClose(128.0, fitted.visibleBeats)
        assertClose(fitted.fitPixelsPerBeat, fitted.pixelsPerBeat)
    }
}
