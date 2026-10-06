package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.engine.AudioState
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.posture.TabletopFold
import audio.tembra.ui.posture.VerticalFold
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The noise gate control on screen in every form the tuner takes (feature 006, spec FR-016, FR-017;
 * contracts/tuner-screen.md §7): displayed, not cut, inside the window and clear of everything beside it, at each
 * window size and font scale, on either side of a fold, and through a change of orientation or posture.
 *
 * A forced window is laid out whole whatever window the lane has: the host takes the window's size in dp and the
 * density is lowered until that many dp fit the lane's pixels. "Displayed" and "not cut" are then the platform's own
 * answers about a window that is really on screen, and every bound read back is in the forced window's dp.
 */
@OptIn(ExperimentalTestApi::class)
class TunerNoiseGateLayoutTest {

    private enum class Fold { None, Tabletop, Book }

    private val telemetry = ScriptedTelemetry()

    /** Listening behind a gate at the middle of the scale, the gate open. */
    private val gated = TunerUiState(
        loaded = true,
        microphone = MicrophonePermissionStatus.Granted,
        audio = AudioState.Listening,
        noiseGate = NoiseGate.ofStep(NoiseGate.STEPS / 2),
    )

    private val sizeHolder = mutableStateOf<DpSize?>(null)
    private val scaleHolder = mutableStateOf<Float?>(null)
    private val layoutHolder = mutableStateOf<TunerLayout?>(null)
    private val foldHolder = mutableStateOf(Fold.None)
    private val hostOrigin = mutableStateOf(Offset.Zero)
    private var composed = false

    /** What the last composition used: the form, the window, and one of its pixels in its own dp. */
    private var layoutInUse = TunerLayout.Stacked
    private var windowInUse = DpSize.Zero
    private var pixel = 1.dp
    private var fontScaleInUse = 1f

    /**
     * Composes once per test (Android allows one `setContent`) and re-configures through state. A null [size] is the
     * lane's own window at its own density; a null [scale] its own font scale; a null [layout] the form the window's
     * size gives. A [fold] is scripted through the middle of the window, in window pixels as the platform reports it.
     */
    private fun ComposeUiTest.setWindow(size: DpSize?, scale: Float? = 1f, layout: TunerLayout? = null, fold: Fold = Fold.None) {
        sizeHolder.value = size
        scaleHolder.value = scale
        layoutHolder.value = layout
        foldHolder.value = fold
        if (!composed) {
            composed = true
            mainClock.autoAdvance = false
            setContent {
                TembraTheme {
                    val platform = LocalDensity.current
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val forced by sizeHolder
                        val fonts by scaleHolder
                        val forcedLayout by layoutHolder
                        val scripted by foldHolder
                        val origin by hostOrigin
                        val widthPx = constraints.maxWidth
                        val heightPx = constraints.maxHeight
                        val fit = forced?.let { minOf(platform.density, widthPx / it.width.value, heightPx / it.height.value) } ?: platform.density
                        val window = forced ?: DpSize((widthPx / fit).dp, (heightPx / fit).dp)
                        val current = forcedLayout ?: TunerLayout.from(WindowWidth.fromWidth(window.width), isShort = window.height < SHORT_WINDOW)
                        layoutInUse = current
                        windowInUse = window
                        pixel = (1f / fit).dp
                        fontScaleInUse = fonts ?: platform.fontScale
                        CompositionLocalProvider(LocalDensity provides Density(density = fit, fontScale = fonts ?: platform.fontScale)) {
                            Box(modifier = Modifier.size(window).onPlaced { hostOrigin.value = it.positionInWindow() }.testTag(WINDOW)) {
                                TunerScreenContent(
                                    state = gated,
                                    callbacks = TunerCallbacks.None,
                                    telemetry = telemetry,
                                    layout = current,
                                    fold = (origin.y + window.height.value * fit / 2f).takeIf { scripted == Fold.Tabletop }?.let { TabletopFold(top = it, bottom = it) },
                                    verticalFold = (origin.x + window.width.value * fit / 2f).takeIf { scripted == Fold.Book }?.let { VerticalFold(left = it, right = it) },
                                )
                            }
                        }
                    }
                }
            }
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
    }

    private fun ComposeUiTest.rect(tag: String): DpRect = onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun DpRect.intersects(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun DpRect.contains(other: DpRect, slack: Dp): Boolean =
        other.left >= left - slack && other.right <= right + slack && other.top >= top - slack && other.bottom <= bottom + slack

    /** The closest container above [tag] that scrolls up and down, or null. */
    private fun ComposeUiTest.scrollContainerOf(tag: String): SemanticsNode? {
        var node = onNodeWithTag(tag).fetchSemanticsNode().parent
        while (node != null && node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) == null) node = node.parent
        return node
    }

    /** How far the closest scrolling container above [tag] can scroll, in pixels; zero when nothing there has to scroll. */
    private fun ComposeUiTest.scrollRangeAround(tag: String): Float =
        scrollContainerOf(tag)?.config?.get(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f

    /**
     * Scrolls [tag]'s container so that [tag] is inside it, its top first when it is taller than the container. The
     * container's scroll action animates and this test holds the clock, so the helper asks for the scroll once and
     * runs the clock until it has settled; `performScrollTo` would wait for an animation that never advances.
     */
    private fun ComposeUiTest.bringIntoView(tag: String) {
        val container = scrollContainerOf(tag) ?: return
        if (container.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() <= 0f) return
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        val viewport = container.boundsInRoot
        val top = node.positionInRoot.y
        val bottom = top + node.size.height
        val delta = when {
            top < viewport.top -> top - viewport.top
            bottom > viewport.bottom -> minOf(bottom - viewport.bottom, top - viewport.top)
            else -> 0f
        }
        if (delta == 0f) return
        runOnUiThread { container.config[SemanticsActions.ScrollBy].action?.invoke(0f, delta) }
        mainClock.advanceTimeBy(SCROLL_SETTLE_MILLIS)
    }

    /** [tag] is displayed, nothing above it cuts it, and it lies inside the window and inside the lane's real root. */
    private fun ComposeUiTest.assertWhole(tag: String, what: String) {
        val node = onNodeWithTag(tag)
        node.assertIsDisplayed()
        val unclipped = node.getUnclippedBoundsInRoot()
        val clipped = node.getBoundsInRoot()
        val cut = maxOf(
            abs((clipped.left - unclipped.left).value),
            abs((clipped.top - unclipped.top).value),
            abs((clipped.right - unclipped.right).value),
            abs((clipped.bottom - unclipped.bottom).value),
        )
        assertTrue(cut <= pixel.value, "$what: $tag is cut: $clipped is shown of $unclipped")
        assertTrue(rect(WINDOW).contains(unclipped, pixel), "$what: $tag $unclipped leaves the window ${rect(WINDOW)}")
        val semantics = node.fetchSemanticsNode()
        val root = onRoot().fetchSemanticsNode().size
        val position = semantics.positionInRoot
        assertTrue(
            position.x >= -1f && position.y >= -1f && position.x + semantics.size.width <= root.width + 1f && position.y + semantics.size.height <= root.height + 1f,
            "$what: $tag at $position, ${semantics.size} px, leaves the root of $root px",
        )
    }

    /**
     * The contract of FR-016 and FR-017 for whatever form is composed: the gauge whole (or, where the content had to
     * scroll, reached by scrolling and never narrower than its minimum), the control whole, clear of the gauge, the
     * selectors and the pill, and at its place: under the gauge in the two card forms, in the settings card under
     * the mode control in the others. With [mayScroll] false the gauge and the control must be whole together.
     */
    private fun ComposeUiTest.assertControlOnScreen(what: String, mayScroll: Boolean = true) {
        val slack = pixel + 0.5.dp
        val gaugeScrolls = scrollRangeAround(METER) > 0f
        val controlScrolls = scrollRangeAround(GATE) > 0f
        if (!mayScroll) assertTrue(!gaugeScrolls && !controlScrolls, "$what: the content scrolls (around the gauge: $gaugeScrolls, around the control: $controlScrolls)")
        if (gaugeScrolls) {
            // Large fonts in a short window: the gauge is reached by scrolling and keeps its width on screen.
            bringIntoView(METER)
            val gauge = onNodeWithTag(METER)
            gauge.assertIsDisplayed()
            val unclipped = gauge.getUnclippedBoundsInRoot()
            val clipped = gauge.getBoundsInRoot()
            assertTrue(abs((clipped.left - unclipped.left).value) <= pixel.value && abs((clipped.right - unclipped.right).value) <= pixel.value, "$what: the gauge is cut at its sides: $clipped of $unclipped")
        } else {
            assertWhole(METER, what)
        }
        val inSettings = layoutInUse == TunerLayout.Landscape || layoutInUse == TunerLayout.Tabletop || foldHolder.value == Fold.Book
        // Over the control the gauge's rows grow with the font, so the least width that keeps its labels apart grows
        // with it too, up to the width the card and the gauge's cap allow (a defect found on the Pixel 6 Pro at the
        // largest font size: a 238 dp gauge with "Flat" over "Sharp").
        val least = if (inSettings) {
            METER_MIN_WIDTH
        } else {
            val cap = if (layoutInUse == TunerLayout.Wide) METER_MAX_WIDTH_WIDE else METER_MAX_WIDTH
            minOf(METER_MIN_WIDTH * fontScaleInUse.coerceAtLeast(1f), cap, rect("tuner.autoListen").width - GUTTER * 2)
        }
        assertTrue(rect(METER).width >= least - slack, "$what: the gauge is ${rect(METER).width} wide, under the $least its text needs")

        bringIntoView(GATE)
        assertWhole(GATE, what)
        assertWhole("$GATE.slider", what)
        val gate = rect(GATE)
        for (tag in listOf(METER, "tuner.instrument", "tuner.mode", "tuner.pill")) {
            assertTrue(!gate.intersects(rect(tag)), "$what: the control $gate overlaps $tag ${rect(tag)}")
        }
        if (inSettings) {
            assertTrue(rect("tuner.settings").contains(gate, slack), "$what: the control $gate is outside the settings card ${rect("tuner.settings")}")
            assertTrue(gate.top >= rect("tuner.mode").bottom - slack, "$what: the control $gate is not under the mode control ${rect("tuner.mode")}")
        } else {
            assertTrue(rect("tuner.autoListen").contains(gate, slack), "$what: the control $gate is outside the Auto Listen card ${rect("tuner.autoListen")}")
            assertTrue(gate.top >= rect(METER).bottom - slack, "$what: the control $gate is not under the gauge ${rect(METER)}")
        }
    }

    @Test
    fun onScreenAtEveryWindowSizeAndFontScale() = runComposeUiTest {
        val windows = listOf(
            DpSize(320.dp, 568.dp) to TunerLayout.Stacked,
            DpSize(360.dp, 740.dp) to TunerLayout.Stacked,
            // The iPhone Duo's outer display.
            DpSize(466.dp, 678.dp) to TunerLayout.Stacked,
            DpSize(800.dp, 360.dp) to TunerLayout.Landscape,
            DpSize(1024.dp, 1366.dp) to TunerLayout.Wide,
            DpSize(1366.dp, 1024.dp) to TunerLayout.Wide,
        )
        for ((window, form) in windows) for (scale in listOf(1f, 2f)) {
            setWindow(window, scale)
            assertEquals(form, layoutInUse, "the form of a ${window.width} x ${window.height} window")
            // At the standard font size nothing scrolls anywhere: the gauge and the control are whole together.
            assertControlOnScreen("${window.width} x ${window.height} at font scale $scale", mayScroll = scale > 1f)
        }
    }

    /** The content area a 360 x 740 phone and the iPhone Duo's outer display leave under the shell's bars. */
    @Test
    fun aShortContentAreaKeepsTheGaugeWholeOverTheControl() = runComposeUiTest {
        for (area in listOf(DpSize(360.dp, 548.dp), DpSize(386.dp, 540.dp))) {
            setWindow(area, layout = TunerLayout.Stacked)
            assertControlOnScreen("a ${area.width} x ${area.height} content area", mayScroll = false)
        }
    }

    @Test
    fun tabletopKeepsTheControlBelowTheFold() = runComposeUiTest {
        for (scale in listOf(1f, 2f)) {
            setWindow(FOLD_INNER, scale, layout = TunerLayout.Tabletop, fold = Fold.Tabletop)
            val what = "tabletop at font scale $scale"
            assertControlOnScreen(what, mayScroll = scale > 1f)
            val slack = pixel + 0.5.dp
            val foldY = rect(WINDOW).top + FOLD_INNER.height / 2
            assertTrue(rect("tuner.tabletop.lower").contains(rect(GATE), slack), "$what: the control ${rect(GATE)} is not in the lower region ${rect("tuner.tabletop.lower")}")
            assertTrue(rect(GATE).top >= foldY + GUTTER - slack, "$what: the control ${rect(GATE)} is within a gutter of the fold at $foldY")
            assertTrue(rect("tuner.autoListen").bottom <= foldY - GUTTER + slack, "$what: the gauge card ${rect("tuner.autoListen")} is within a gutter of the fold at $foldY")
        }
    }

    @Test
    fun bookKeepsTheControlBeforeTheHinge() = runComposeUiTest {
        for (scale in listOf(1f, 2f)) {
            setWindow(FOLD_INNER, scale, layout = TunerLayout.Wide, fold = Fold.Book)
            val what = "book at font scale $scale"
            assertControlOnScreen(what, mayScroll = scale > 1f)
            val slack = pixel + 0.5.dp
            val foldX = rect(WINDOW).left + FOLD_INNER.width / 2
            assertTrue(rect(GATE).right <= foldX - GUTTER + slack, "$what: the control ${rect(GATE)} is within a gutter of the hinge at $foldX")
            assertTrue(rect("tuner.autoListen").left >= foldX + GUTTER - slack, "$what: the gauge card ${rect("tuner.autoListen")} is within a gutter of the hinge at $foldX")
        }
    }

    /** Rotation and folding re-lay the screen out; the state is untouched, so the control must come back as it was. */
    @Test
    fun theControlKeepsItsValueThroughOrientationAndPostureChanges() = runComposeUiTest {
        val upright = DpSize(360.dp, 740.dp)
        val sideways = DpSize(740.dp, 360.dp)
        val changes = listOf(
            Change("upright", upright, null, Fold.None, TunerLayout.Stacked),
            Change("sideways", sideways, null, Fold.None, TunerLayout.Landscape),
            Change("upright again", upright, null, Fold.None, TunerLayout.Stacked),
            Change("opened flat", FOLD_INNER, null, Fold.None, TunerLayout.Wide),
            Change("tabletop", FOLD_INNER, TunerLayout.Tabletop, Fold.Tabletop, TunerLayout.Tabletop),
            Change("book", FOLD_INNER, TunerLayout.Wide, Fold.Book, TunerLayout.Wide),
            Change("flat again", FOLD_INNER, null, Fold.None, TunerLayout.Wide),
        )
        for (change in changes) {
            setWindow(change.window, layout = change.layout, fold = change.fold)
            assertEquals(change.form, layoutInUse, change.name)
            onAllNodesWithTag(GATE).assertCountEquals(1)
            assertControlOnScreen(change.name, mayScroll = false)
            val slider = onNodeWithTag("$GATE.slider")
            slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(gated.noiseGate.step.toFloat(), 0f..NoiseGate.STEPS.toFloat(), NoiseGate.STEPS - 1)))
            slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "50 percent, sound above the gate"))
        }
    }

    /**
     * A screen reader meets the control where it is seen (FR-018): after the gauge in the two card forms, after the
     * mode selector in the settings forms. Read from the order of the semantics tree, which is the order of placing.
     */
    @Test
    fun aScreenReaderMeetsTheControlWhereItIsSeen() = runComposeUiTest {
        val forms = listOf(
            Change("upright", DpSize(360.dp, 740.dp), null, Fold.None, TunerLayout.Stacked),
            Change("sideways", DpSize(740.dp, 360.dp), null, Fold.None, TunerLayout.Landscape),
            Change("opened flat", FOLD_INNER, null, Fold.None, TunerLayout.Wide),
            Change("tabletop", FOLD_INNER, TunerLayout.Tabletop, Fold.Tabletop, TunerLayout.Tabletop),
            Change("book", FOLD_INNER, TunerLayout.Wide, Fold.Book, TunerLayout.Wide),
        )
        for (form in forms) {
            setWindow(form.window, layout = form.layout, fold = form.fold)
            val order = mutableListOf<String>()
            fun visit(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.TestTag)?.let { order += it }
                node.children.forEach(::visit)
            }
            visit(onRoot(useUnmergedTree = true).fetchSemanticsNode())
            val before = if (form.form == TunerLayout.Stacked || (form.form == TunerLayout.Wide && form.fold == Fold.None)) METER else "tuner.mode"
            assertTrue(order.indexOf(before) in 0 until order.indexOf("$GATE.slider"), "${form.name}: the control does not come after $before in $order")
        }
    }

    /** No forced size: the lane's own window at its own density and font scale, in the form that window gives. */
    @Test
    fun onThisDevicesOwnWindow() = runComposeUiTest {
        setWindow(size = null, scale = null)
        assertControlOnScreen("this device's window (${windowInUse.width} x ${windowInUse.height}, $layoutInUse)")
    }

    private class Change(val name: String, val window: DpSize, val layout: TunerLayout?, val fold: Fold, val form: TunerLayout)
}

private const val WINDOW = "test.window"
private const val GATE = "tuner.noiseGate"
private const val METER = "tuner.meter"

/** Longer than the scroll action's spring takes to come to rest. */
private const val SCROLL_SETTLE_MILLIS = 1_500L

/** Window-core's medium height breakpoint, under which a wide window is a phone held sideways (`TunerLayout`). */
private val SHORT_WINDOW = 480.dp

/** `TembraSpacing.meterMinWidth`, the gauge's two caps, and the gutter each side of a fold (`spacing.md`). */
private val METER_MIN_WIDTH = 200.dp
private val METER_MAX_WIDTH = 340.dp
private val METER_MAX_WIDTH_WIDE = 480.dp
private val GUTTER = 12.dp

/** The Pixel 9 Pro Fold's inner display, less nothing: the fold runs through its middle. */
private val FOLD_INNER = DpSize(852.dp, 760.dp)
