package audio.tembra.ui.mixer

import androidx.compose.ui.unit.dp
import audio.tembra.ui.timeline.TimelineLayout
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sheet's heights per layout and the strip form per height (feature 012, data-model.md §5; spec FR-018 to FR-021, FR-025). */
class MixerAnchorsTest {

    @Test
    fun compactAndMediumOpenAtTheLargerOfTheFloorAndFortyFivePercent() {
        assertEquals(MixerAnchors(half = 256.dp, full = 500.dp), MixerAnchors.of(TimelineLayout.Compact, 500.dp), "45 % of 500 is under the floor")
        assertEquals(MixerAnchors(half = 270.dp, full = 600.dp), MixerAnchors.of(TimelineLayout.Compact, 600.dp))
        assertEquals(MixerAnchors(half = 450.dp, full = 1000.dp), MixerAnchors.of(TimelineLayout.Medium, 1000.dp))
    }

    @Test
    fun aLanesBoxUnderTheFloorCapsTheHalfHeightAtTheFullOne() {
        assertEquals(MixerAnchors(half = 200.dp, full = 200.dp), MixerAnchors.of(TimelineLayout.Compact, 200.dp))
        assertEquals(MixerAnchors(half = 150.dp, full = 150.dp), MixerAnchors.of(TimelineLayout.Expanded, 150.dp))
    }

    @Test
    fun landscapeHasOneAnchorAtTheFullHeight() {
        val anchors = MixerAnchors.of(TimelineLayout.Landscape, 300.dp)
        assertEquals(MixerAnchors(half = 300.dp, full = 300.dp, singleAnchor = true), anchors)
        assertEquals(300.dp, anchors.heightOf(MixerHeight.Half), "landscape's half is its full")
    }

    @Test
    fun expandedLeavesTwoExpandedLanesVisibleAtTheHalfHeight() {
        assertEquals(MixerAnchors(half = 608.dp, full = 800.dp), MixerAnchors.of(TimelineLayout.Expanded, 800.dp))
        assertEquals(MixerAnchors(half = 256.dp, full = 400.dp), MixerAnchors.of(TimelineLayout.Expanded, 400.dp), "never under the floor")
    }

    @Test
    fun anUnmeasuredLanesBoxHasNoAnchors() {
        assertEquals(MixerAnchors.None, MixerAnchors.of(TimelineLayout.Compact, 0.dp))
        assertEquals(0.dp, MixerAnchors.None.heightOf(MixerHeight.Full))
    }

    @Test
    fun theHalfHeightIsTheCompactFormAndTheFullOneDropsTheDialUnder480() {
        assertEquals(StripForm.Compact, StripForm.of(MixerHeight.Half, 1000f))
        assertEquals(StripForm.FullNoVu, StripForm.of(MixerHeight.Full, 479f))
        assertEquals(StripForm.Full, StripForm.of(MixerHeight.Full, 480f))
        assertEquals(false, StripForm.Compact.showsPan)
        assertEquals(true, StripForm.FullNoVu.showsPan)
        assertEquals(false, StripForm.FullNoVu.showsVu)
        assertEquals(true, StripForm.Full.showsVu)
    }

    @Test
    fun theUiReportsItsSheetHeightFromTheAnchors() {
        val ui = MixerUi(open = true, height = MixerHeight.Half, anchors = MixerAnchors.of(TimelineLayout.Compact, 600.dp))
        assertEquals(270.dp, ui.sheetHeight)
        assertEquals(600.dp, ui.copy(height = MixerHeight.Full).sheetHeight)
    }
}
