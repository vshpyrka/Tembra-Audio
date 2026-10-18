package audio.tembra.core.timeline

import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Resolving a tap to a clip, empty lane space or the playhead marker (spec FR-009, FR-011, FR-031). */
class ClipHitTest {

    private val viewport = TimelineViewport(pixelsPerBeat = 10.0, scrollBeats = Beats(0.0), widthPx = 800.0, densityScale = 1.0)
    private val lanes = listOf(
        LaneGeometry(TrackId("a"), top = 44.0, bottom = 132.0, clips = listOf(ClipGeometry(ClipId("a1"), Beats(4.0), Beats(12.0)))),
        LaneGeometry(TrackId("b"), top = 132.0, bottom = 220.0, clips = listOf(ClipGeometry(ClipId("b1"), Beats(0.0), Beats(2.0)), ClipGeometry(ClipId("b2"), Beats(20.0), Beats(30.0)))),
    )
    private val marker = MarkerGeometry(x = 160.0, halfWidth = 14.0, top = 0.0, bottom = 22.0)

    @Test
    fun aTapInsideAClipHitsIt() {
        assertEquals(ClipHit.OnClip(TrackId("a"), ClipId("a1")), ClipHit.at(60.0, 80.0, viewport, lanes, marker))
        assertEquals(ClipHit.OnClip(TrackId("b"), ClipId("b2")), ClipHit.at(250.0, 200.0, viewport, lanes, marker))
        assertEquals(ClipHit.OnClip(TrackId("a"), ClipId("a1")), ClipHit.at(40.0, 44.0, viewport, lanes, marker))
    }

    @Test
    fun aTapOnEmptyLaneSpaceReportsTheTrackAndTheBeat() {
        assertEquals(ClipHit.OnLane(TrackId("a"), Beats(15.0)), ClipHit.at(150.0, 100.0, viewport, lanes, marker))
        assertEquals(ClipHit.OnLane(TrackId("b"), Beats(12.0)), ClipHit.at(120.0, 132.0, viewport, lanes, marker))
        assertEquals(ClipHit.OnLane(TrackId("a"), Beats(12.0)), ClipHit.at(120.0, 100.0, viewport, lanes, marker))
    }

    @Test
    fun theMarkerWinsOverEverythingUnderIt() {
        assertEquals(ClipHit.OnMarker, ClipHit.at(170.0, 10.0, viewport, lanes, marker))
        assertEquals(ClipHit.OnMarker, ClipHit.at(146.0, 22.0, viewport, lanes, marker))
        assertEquals(ClipHit.Outside, ClipHit.at(175.0, 10.0, viewport, lanes, marker))
        assertEquals(ClipHit.Outside, ClipHit.at(160.0, 30.0, viewport, lanes, null))
    }

    @Test
    fun outsideEveryLaneIsOutside() {
        assertEquals(ClipHit.Outside, ClipHit.at(10.0, 300.0, viewport, lanes, marker))
        assertEquals(ClipHit.Outside, ClipHit.at(10.0, 30.0, viewport, lanes, marker))
    }

    @Test
    fun scrollingMovesTheHitTest() {
        val scrolled = viewport.scrollTo(Beats(10.0))
        assertEquals(ClipHit.OnClip(TrackId("b"), ClipId("b2")), ClipHit.at(150.0, 150.0, scrolled, lanes, marker))
        assertEquals(ClipHit.OnLane(TrackId("a"), Beats(25.0)), ClipHit.at(150.0, 100.0, scrolled, lanes, marker))
    }
}
