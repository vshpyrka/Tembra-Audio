package audio.tembra.ui.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import audio.tembra.ui.designsystem.FaderDefaults
import audio.tembra.ui.designsystem.ScrollbarShown
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.shortcuts.LocalOverlayStack
import audio.tembra.ui.shortcuts.OverlayStack
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.transport.TransportCopy
import kotlinx.collections.immutable.toPersistentList
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The mixer sheet over a lanes box (feature 012, contracts/mixer-ui.md "MixerSheet"; research R1; T045): it opens at
 * the half height with the lanes visible above, three strips and the pinned master fit 360 dp, a drag on the handle
 * moves it between the two heights, a swipe down from the half height closes it, the shell's close-overlay action (the
 * Escape key's route after 011) closes it, landscape has one anchor, and a drag on a fader moves the fader, not the
 * sheet. The state is held as the ViewModel would hold it: the callbacks that change the height or close the sheet
 * write it back. The per-frame meter read lands in User Story 2 (T059 extends this file).
 */
@OptIn(ExperimentalTestApi::class)
class MixerSheetTest {

    private val calls = mutableListOf<String>()
    private val stack = OverlayStack()
    private val ui = mutableStateOf(MixerUi())
    private var densityScale = 1f
    private var pageMargin = 0.dp
    private val shownLayout = mutableStateOf(TimelineLayout.Compact)
    private val shownWidth = mutableStateOf(360.dp)
    private val shownLanes = mutableStateOf(500.dp)

    private val callbacks = TimelineCallbacks.None.copy(
        onMixerToggle = {
            calls += "toggle"
            ui.value = ui.value.copy(open = !ui.value.open)
        },
        onMixerHeight = { height ->
            calls += "height($height)"
            ui.value = ui.value.copy(height = height, form = StripForm.of(height, ui.value.anchors.heightOf(height).value))
        },
        onGain = { id, gain -> calls += "gain(${id.value},${gain.readout})" },
        onGainSettled = { calls += "settled" },
        onResetTrackClip = { calls += "resetClip(${it.value})" },
        onResetMasterClip = { calls += "resetMaster" },
        onOpenMixerWindow = { calls += "openWindow" },
    )

    private fun strips(count: Int, names: (Int) -> String = { "Track ${it + 1}" }) = List(count) { index ->
        StripUi(
            id = TrackId("t${index + 1}"), index = index, number = index + 1, name = names(index), colorIndex = index,
            gainDb = GainDb.Unity, pan = Pan.Centre, muted = false, soloed = false, audible = true,
        )
    }.toPersistentList()

    /**
     * The sheet inside a lanes box of [width] × [lanes] under [layout], opened at [height]; a 40 dp marker at the top
     * stands for the lanes. The box follows [shownWidth] and [shownLanes], so [relayout] can change the window while
     * the sheet stays composed.
     */
    private fun ComposeUiTest.setSheet(
        layout: TimelineLayout = TimelineLayout.Compact,
        width: Dp = 360.dp,
        lanes: Dp = 500.dp,
        height: MixerHeight = MixerHeight.Half,
        strips: Int = 3,
        telemetry: ScriptedMixTelemetry = ScriptedMixTelemetry.silent(strips),
        frozen: Boolean = false,
        fontScale: Float = 1f,
        names: (Int) -> String = { "Track ${it + 1}" },
        windowAvailable: Boolean = false,
    ) {
        val anchors = MixerAnchors.of(layout, lanes)
        shownLayout.value = layout
        shownWidth.value = width
        shownLanes.value = lanes
        ui.value = MixerUi(open = true, height = height, form = StripForm.of(height, anchors.heightOf(height).value), anchors = anchors, strips = strips(strips, names), windowAvailable = windowAvailable)
        // A frozen clock must be frozen before the first frame: the test harness parks an infinite animation (the meters'
        // frame tick) for good once it has run under an auto-advancing clock.
        if (frozen) mainClock.autoAdvance = false
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                TembraTheme {
                    CompositionLocalProvider(LocalOverlayStack provides stack) {
                        densityScale = base.density
                        pageMargin = TembraTheme.spacing.pageMargin
                        Box(Modifier.requiredWidth(shownWidth.value).requiredHeight(shownLanes.value)) {
                            Box(Modifier.fillMaxWidth().height(LANES_MARKER_HEIGHT).align(Alignment.TopCenter).testTag(LANES_MARKER))
                            MixerSheet(ui = ui.value, callbacks = callbacks, telemetry = telemetry, inputTelemetry = InputTelemetry { _, _ -> }, lanesHeight = shownLanes.value)
                        }
                    }
                }
            }
        }
        // The open animation (300 ms) under either clock.
        if (frozen) mainClock.advanceTimeBy(600) else waitForIdle()
    }

    /** A window change while the sheet stays composed: the layout class, width and lanes height, the anchors and form recomputed as the ViewModel would. */
    private fun ComposeUiTest.relayout(layout: TimelineLayout = shownLayout.value, width: Dp = shownWidth.value, lanes: Dp = shownLanes.value) {
        val anchors = MixerAnchors.of(layout, lanes)
        shownLayout.value = layout
        shownWidth.value = width
        shownLanes.value = lanes
        ui.value = ui.value.copy(anchors = anchors, form = StripForm.of(ui.value.height, anchors.heightOf(ui.value.height).value))
        waitForIdle()
    }

    /** The index of the first strip inside the row's bounds, in whole or in part (by bounds: a root wider than the screen overflows it). */
    private fun ComposeUiTest.firstVisibleStrip(count: Int): Int {
        val row = onNodeWithTag(MixerTestTags.STRIPS).getUnclippedBoundsInRoot()
        return (0 until count).first { index ->
            val tag = MixerTestTags.strip(index)
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() && onNodeWithTag(tag).getUnclippedBoundsInRoot().right > row.left
        }
    }

    /** Research R6 (T059): the root reads the telemetry once per frame whatever the strip count; the strips draw from that one snapshot. */
    @Test
    fun theRootReadsTheTelemetryOncePerFrameForAnyNumberOfStrips() = runComposeUiTest {
        val telemetry = ScriptedMixTelemetry.level(index = 0, peakDb = -20f, rmsDb = -23f, trackCount = 8)
        setSheet(strips = 8, telemetry = telemetry, frozen = true)
        val before = telemetry.reads
        repeat(5) { mainClock.advanceTimeByFrame() }
        val reads = telemetry.reads - before
        assertTrue(reads in 4..6, "five frames with eight strips read the telemetry $reads times, not once per frame")
        for (index in 0 until 3) onNodeWithTag(MixerTestTags.meter(index)).assertIsDisplayed()
    }

    /** The 10 Hz mirror (research R6): the meters' announcements follow the snapshot a tenth of a second later, strip by strip. */
    @Test
    fun theMirrorRefreshesTheMeterSemanticsAtTenHertz() = runComposeUiTest {
        setSheet(telemetry = ScriptedMixTelemetry.level(index = 1, peakDb = -20f, rmsDb = -23f, trackCount = 3), frozen = true)
        mainClock.advanceTimeBy(150)
        onNodeWithTag(MixerTestTags.meter(1)).assertContentDescriptionEquals(MixerCopy.level).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−20 dB"))
        onNodeWithTag(MixerTestTags.meter(0)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−60 dB"))
        onNodeWithTag(MixerTestTags.MASTER_METER).assertContentDescriptionEquals(MixerCopy.level).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "−60 dB"))
        onNodeWithTag(MixerTestTags.clip(1)).assertContentDescriptionEquals(MixerCopy.clipIndicator)
    }

    /** A lit latch reaches the strip's and the master's clip squares, and a tap on each asks for its reset (spec FR-012). */
    @Test
    fun aLitLatchAnnouncesOnceAndATapResetsIt() = runComposeUiTest {
        val telemetry = ScriptedMixTelemetry { _, into ->
            into.trackCount = 3
            into.peak[0] = 1f
            into.peak[1] = 1f
            into.clipMask = 1L
            into.masterPeak[0] = 1f
            into.masterPeak[1] = 1f
            into.masterClipped = true
        }
        setSheet(telemetry = telemetry, frozen = true)
        mainClock.advanceTimeBy(150)
        onNodeWithTag(MixerTestTags.clip(0)).assertContentDescriptionEquals(MixerCopy.clippedTapToReset).performClick()
        onNodeWithTag(MixerTestTags.MASTER_CLIP).assertContentDescriptionEquals(MixerCopy.clippedTapToReset).performClick()
        assertEquals(listOf("resetClip(t1)", "resetMaster"), calls)
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue(abs((expected - actual).value) <= 1f, "$what is $actual, expected $expected")

    @Test
    fun opensAtHalfWithTheLanesVisibleAbove() = runComposeUiTest {
        setSheet()
        // Compact at 500 dp of lanes: 45 % is 225 dp, under the 256 dp floor, so the half sheet is 256 dp tall.
        val sheet = onNodeWithTag(MixerTestTags.SHEET).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertNear(HALF_TOP, sheet.top, "the sheet's top")
        assertNear(500.dp, sheet.bottom, "the sheet's bottom")
        onNodeWithTag(LANES_MARKER).assertIsDisplayed()
        onNodeWithTag(MixerTestTags.HANDLE).assertIsDisplayed()
        onNodeWithText(MixerCopy.mixer).assertIsDisplayed()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun threeStripsAndTheMasterFitThreeHundredSixtyWithTheMasterPinnedRight() = runComposeUiTest {
        setSheet(strips = 8)
        for (index in 0 until 3) onNodeWithTag(MixerTestTags.strip(index)).assertIsDisplayed()
        val master = onNodeWithTag(MixerTestTags.MASTER).assertIsDisplayed().getUnclippedBoundsInRoot()
        val row = onNodeWithTag(MixerTestTags.STRIPS).getUnclippedBoundsInRoot()
        assertTrue(row.right <= master.left + 1.dp, "the strip row $row runs under the master $master")
        assertTrue(master.right >= 360.dp - pageMargin - 1.dp, "the master $master is not pinned at the right edge")
        onNodeWithTag(MixerTestTags.MASTER_FADER).assertIsDisplayed()
    }

    @Test
    fun aDragOnTheHandleMovesBetweenTheHeights() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.HANDLE).performTouchInput { swipeUp(startY = centerY, endY = centerY - 300f * densityScale) }
        waitForIdle()
        assertEquals(listOf("height(Full)"), calls)
        assertNear(0.dp, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the full sheet's top")
        onNodeWithTag(MixerTestTags.HANDLE).performTouchInput { swipeDown(startY = centerY, endY = centerY + 150f * densityScale) }
        waitForIdle()
        assertEquals(listOf("height(Full)", "height(Half)"), calls)
        assertNear(HALF_TOP, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the half sheet's top")
    }

    @Test
    fun aSwipeDownFromHalfCloses() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.HANDLE).performTouchInput { swipeDown(startY = centerY, endY = centerY + 260f * densityScale) }
        waitForIdle()
        assertEquals(listOf("toggle"), calls)
        assertFalse(ui.value.open)
        onNodeWithTag(MixerTestTags.SHEET).assertDoesNotExist()
    }

    @Test
    fun theShellsCloseOverlayActionClosesTheSheet() = runComposeUiTest {
        setSheet()
        assertTrue(stack.closeTop(), "the open sheet registered as an overlay")
        waitForIdle()
        assertEquals(listOf("toggle"), calls)
        onNodeWithTag(MixerTestTags.SHEET).assertDoesNotExist()
        assertFalse(stack.closeTop(), "the closed sheet left the overlay stack")
    }

    @Test
    fun landscapeHasTheOneFullAnchor() = runComposeUiTest {
        setSheet(layout = TimelineLayout.Landscape, width = 700.dp, lanes = 300.dp, height = MixerHeight.Full)
        assertNear(0.dp, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the sheet's top")
        onNodeWithTag(LANES_MARKER).assertIsDisplayed()
        // A short, slow drag down has no half anchor to land on: the sheet returns to the full height, nothing is reported.
        onNodeWithTag(MixerTestTags.HANDLE).performTouchInput { swipeDown(startY = centerY, endY = centerY + 60f * densityScale, durationMillis = 1_000) }
        waitForIdle()
        assertEquals(emptyList(), calls)
        assertNear(0.dp, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the sheet's top after the short drag")
    }

    @Test
    fun aDragOnAFaderMovesTheFaderNotTheSheet() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.fader(1)).performTouchInput { swipeUp() }
        waitForIdle()
        assertTrue(calls.any { it.startsWith("gain(t2,") }, "the fader did not move: $calls")
        assertTrue(calls.none { it.startsWith("height") || it == "toggle" }, "the sheet moved under the fader drag: $calls")
        assertEquals("settled", calls.last())
    }

    /** Data model §5 (T082): the anchors at the four reference widths; the half sheet is never under 240 dp; the expanded one leaves 192 dp of lanes. */
    @Test
    fun theAnchorsFollowTheLayoutClassAtEveryWidth() = runComposeUiTest {
        setSheet(lanes = 600.dp)
        for ((width, layout) in listOf(360.dp to TimelineLayout.Compact, 600.dp to TimelineLayout.Medium, 840.dp to TimelineLayout.Expanded, 1024.dp to TimelineLayout.Expanded)) {
            relayout(layout = layout, width = width)
            val anchors = MixerAnchors.of(layout, 600.dp)
            assertTrue(anchors.half >= 240.dp, "$layout at $width: the half sheet is ${anchors.half}")
            if (layout == TimelineLayout.Expanded) assertEquals(600.dp - MixerAnchors.ExpandedLanesKept, anchors.half, "$layout at $width: two expanded lanes stay visible")
            // The root may be wider than the screen (1024 dp on a phone) and is then centred over it: every edge is read against
            // the lanes box and the sheet, never the screen.
            val box = onNodeWithTag(LANES_MARKER).assertExists().getUnclippedBoundsInRoot()
            val sheet = onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot()
            assertNear(box.top + 600.dp - anchors.half, sheet.top, "$layout at $width: the half sheet's top")
            assertNear(width, sheet.width, "$layout at $width: the sheet's width")
            assertTrue(onNodeWithTag(MixerTestTags.MASTER).getUnclippedBoundsInRoot().right >= sheet.right - pageMargin - 1.dp, "$layout at $width: the master is not pinned at the sheet's right edge")
        }
        assertEquals(emptyList(), calls)
    }

    /** Spec FR-019 (T082): phone landscape at 600 × 400 has the one full anchor and the full form without the dial. */
    @Test
    fun landscapeSixHundredByFourHundredKeepsTheKnobAndDropsTheDial() = runComposeUiTest {
        setSheet(layout = TimelineLayout.Landscape, width = 600.dp, lanes = 400.dp, height = MixerHeight.Full)
        assertEquals(StripForm.FullNoVu, ui.value.form)
        // A 600 dp root overflows a phone's portrait screen: the knob is checked by its bounds inside the sheet, not its visibility.
        val sheet = onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot()
        assertNear(onNodeWithTag(LANES_MARKER).getUnclippedBoundsInRoot().top, sheet.top, "the sheet's top")
        val knob = onNodeWithTag(MixerTestTags.pan(0)).assertExists().getUnclippedBoundsInRoot()
        assertTrue(knob.top >= sheet.top && knob.bottom <= sheet.bottom && knob.left >= sheet.left, "the knob $knob is outside the sheet $sheet")
        onNodeWithTag(MixerTestTags.vu(0)).assertDoesNotExist()
        assertTrue(onNodeWithTag(MixerTestTags.fader(0)).getUnclippedBoundsInRoot().height >= FaderDefaults.MinThrow - 0.5.dp, "the fader's throw in landscape")
    }

    /** Spec FR-025 (T082): a 440 dp full sheet drops the dial and keeps the knob; a 280 dp one drops the knob too, before the fader falls under 120 dp. */
    @Test
    fun aShortFullSheetDropsTheDialFirstAndTheKnobSecond() = runComposeUiTest {
        setSheet(lanes = 440.dp, height = MixerHeight.Full)
        assertEquals(StripForm.FullNoVu, ui.value.form)
        onNodeWithTag(MixerTestTags.pan(0)).assertIsDisplayed()
        onNodeWithTag(MixerTestTags.vu(0)).assertDoesNotExist()
        assertTrue(onNodeWithTag(MixerTestTags.fader(0)).getUnclippedBoundsInRoot().height >= FaderDefaults.MinThrow - 0.5.dp, "the fader's throw at 440 dp")
        relayout(lanes = 280.dp)
        assertEquals(StripForm.Compact, ui.value.form)
        onNodeWithTag(MixerTestTags.pan(0)).assertDoesNotExist()
        assertTrue(onNodeWithTag(MixerTestTags.fader(0)).getUnclippedBoundsInRoot().height >= FaderDefaults.MinThrow - 0.5.dp, "the fader's throw at 280 dp")
    }

    /** Spec FR-026 (T082): at the largest font nothing is clipped: the labels grow in height, the strip keeps its width and the name ellipsises inside it. */
    @Test
    fun theLargestFontClipsNothingAndEllipsisesTheName() = runComposeUiTest {
        setSheet(fontScale = 2f, names = { "Acoustic guitar overdub ${it + 1}" })
        val sheet = onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot()
        val strip = onNodeWithTag(MixerTestTags.strip(0)).getUnclippedBoundsInRoot()
        assertNear(MixerStripDefaults.Width, strip.width, "the strip's width at the largest font")
        assertTrue(strip.top >= sheet.top - 0.5.dp && strip.bottom <= sheet.bottom + 0.5.dp, "the strip $strip is clipped by the sheet $sheet")
        for (tag in listOf(MixerTestTags.number(0), MixerTestTags.solo(0), MixerTestTags.mute(0), MixerTestTags.fader(0), MixerTestTags.meter(0), MixerTestTags.name(0))) {
            val bounds = onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(bounds.top >= strip.top - 0.5.dp && bounds.bottom <= strip.bottom + 0.5.dp, "$tag $bounds is clipped by the strip $strip")
            assertTrue(bounds.left >= strip.left - 0.5.dp && bounds.right <= strip.right + 0.5.dp, "$tag $bounds runs past the strip $strip")
        }
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithTag(MixerTestTags.name(0)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        // Truncated to the label's width (Skia's layout reports the truncation as overflow, not as an ellipsised line).
        val layout = layouts.single()
        val stripPx = with(density) { MixerStripDefaults.Width.roundToPx() }
        assertTrue(layout.hasVisualOverflow && layout.size.width <= stripPx, "the long name is not ellipsised inside the strip: overflow=${layout.hasVisualOverflow}, width=${layout.size.width} px of $stripPx")
        onNodeWithTag(MixerTestTags.MASTER_FADER).assertIsDisplayed()
        onNodeWithText(TransportCopy.masterVolumePercent(MasterUi.DEFAULT_PERCENT)).assertIsDisplayed()
    }

    /** Spec FR-022 (T082): a window change while the sheet is open keeps it open at its height and keeps the strip row where it was scrolled. */
    @Test
    fun aLayoutClassChangeKeepsTheSheetOpenAtItsHeightAndTheRowsScroll() = runComposeUiTest {
        setSheet(lanes = 600.dp, strips = 24)
        onNodeWithTag(MixerTestTags.STRIPS).performTouchInput { swipeLeft(startX = right - 10f, endX = left + 10f, durationMillis = 1_000) }
        waitForIdle()
        val scrolledTo = firstVisibleStrip(24)
        assertTrue(scrolledTo > 0, "the row did not scroll")
        relayout(layout = TimelineLayout.Expanded, width = 840.dp)
        assertTrue(ui.value.open, "the sheet closed on the layout change")
        assertEquals(MixerHeight.Half, ui.value.height)
        val boxTop = onNodeWithTag(LANES_MARKER).getUnclippedBoundsInRoot().top
        assertNear(boxTop + 600.dp - MixerAnchors.of(TimelineLayout.Expanded, 600.dp).half, onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top, "the expanded half sheet's top")
        assertEquals(scrolledTo, firstVisibleStrip(24), "the row's scroll offset after the layout change")
        assertEquals(emptyList(), calls)
    }

    /**
     * Spec SC-005, FR-024 (T096): ten open-and-close cycles under the frozen clock, each settled within the 300 ms tween
     * plus the frames that take the request, start the effect and report the animation over ([SETTLE_FRAMES]), and
     * reporting nothing back; the root's read stays one per frame with 24 strips afterwards.
     */
    @Test
    fun tenOpenCloseCyclesSettleWithinThreeHundredMillisecondsEach() = runComposeUiTest {
        val telemetry = ScriptedMixTelemetry.level(index = 0, peakDb = -20f, rmsDb = -23f, trackCount = 24)
        setSheet(strips = 24, telemetry = telemetry, frozen = true)
        repeat(10) { cycle ->
            ui.value = ui.value.copy(open = false)
            framesUntil("cycle $cycle: the close") { onAllNodesWithTag(MixerTestTags.SHEET).fetchSemanticsNodes().isEmpty() }
            ui.value = ui.value.copy(open = true)
            framesUntil("cycle $cycle: the open") {
                onAllNodesWithTag(MixerTestTags.SHEET).fetchSemanticsNodes().isNotEmpty() &&
                    abs((onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot().top - HALF_TOP).value) <= 1f
            }
        }
        val before = telemetry.reads
        repeat(5) { mainClock.advanceTimeByFrame() }
        val reads = telemetry.reads - before
        assertTrue(reads in 4..6, "five frames with 24 strips read the telemetry $reads times, not once per frame")
        onNodeWithTag(MixerTestTags.meter(0)).assertIsDisplayed()
        assertEquals(emptyList(), calls)
    }

    /**
     * The owner's finding of 2026-11-09: the strips' tint bands stayed on the lanes box's bottom edge while the sheet
     * closed, because the sheet collapsed in height and every band sits at the base of its strip. Below its lowest anchor
     * the sheet slides out instead: half-way through the close it keeps the half anchor's height and the strips theirs,
     * their bases (and bands) have left the lanes box with it, and at the end nothing of it remains.
     */
    @Test
    fun aClosingSheetSlidesOutWithItsBandsInsteadOfCollapsing() = runComposeUiTest {
        setSheet(frozen = true)
        val sheetBefore = onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot()
        val stripBefore = onNodeWithTag(MixerTestTags.strip(0)).getUnclippedBoundsInRoot()
        assertNear(shownLanes.value, stripBefore.bottom, "the strip's base at the half height")
        ui.value = ui.value.copy(open = false)
        mainClock.advanceTimeBy(150)
        val sheet = onNodeWithTag(MixerTestTags.SHEET).getUnclippedBoundsInRoot()
        val strip = onNodeWithTag(MixerTestTags.strip(0)).getUnclippedBoundsInRoot()
        assertTrue(sheet.top > sheetBefore.top + 20.dp, "half-way through the close the sheet's top is ${sheet.top}, not well below ${sheetBefore.top}")
        assertNear(sheetBefore.height, sheet.height, "the closing sheet's height")
        assertNear(stripBefore.height, strip.height, "the closing sheet's first strip's height")
        assertTrue(strip.bottom > shownLanes.value + 20.dp, "the strip's base is at ${strip.bottom}, not below the lanes box's bottom (${shownLanes.value})")
        mainClock.advanceTimeBy(600)
        assertTrue(onAllNodesWithTag(MixerTestTags.SHEET).fetchSemanticsNodes().isEmpty(), "the sheet is still composed after the close")
        assertEquals(emptyList(), calls)
    }

    /** Advances the frozen clock frame by frame until [settled] holds; fails when [SETTLE_FRAMES] frames were not enough. */
    private fun ComposeUiTest.framesUntil(what: String, settled: () -> Boolean) {
        var frames = 0
        while (!settled()) {
            assertTrue(frames < SETTLE_FRAMES, "$what did not settle within $SETTLE_FRAMES frames")
            mainClock.advanceTimeByFrame()
            frames++
        }
    }

    /** Spec FR-021a (T102): the handle offers "Open in a window" only where the platform has a second window, and the tap asks for it. */
    @Test
    fun theHandleOffersTheWindowOnlyWhereThePlatformHasOne() = runComposeUiTest {
        setSheet(windowAvailable = true)
        onNodeWithTag(MixerTestTags.WINDOW_OPEN).assertIsDisplayed().assertContentDescriptionEquals(MixerCopy.openInWindow).performClick()
        assertEquals(listOf("openWindow"), calls)
    }

    @Test
    fun theHandleHasNoWindowButtonWithoutTheCapability() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.WINDOW_OPEN).assertDoesNotExist()
        onNodeWithTag(MixerTestTags.HANDLE).assertIsDisplayed()
    }

    /** Feature 011's scrollbar on the strip row (T084): the row carries one for the strips that overflow the width. */
    @Test
    fun theStripRowCarriesAScrollbar() = runComposeUiTest {
        setSheet(strips = 12)
        onNodeWithTag(MixerTestTags.STRIPS).assert(SemanticsMatcher.keyIsDefined(ScrollbarShown))
    }

    private companion object {
        const val LANES_MARKER = "lanes.top"
        val LANES_MARKER_HEIGHT = 40.dp

        /** 500 dp of lanes less the 256 dp half sheet. */
        val HALF_TOP = 244.dp

        /** The 300 ms tween at 16 ms frames (19), plus the frame that takes the request, the one that starts the effect and two that report the animation over. */
        const val SETTLE_FRAMES = 23
    }
}
