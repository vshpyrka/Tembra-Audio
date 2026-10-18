package audio.tembra.ui.tuner

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import audio.tembra.core.tuner.NoiseGate
import audio.tembra.ui.designsystem.distanceTo
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.theme.LocalTembraMotion
import audio.tembra.ui.theme.TembraMotion
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The noise gate slider on its own (feature 006, contracts/tuner-screen.md §2; spec FR-001, FR-002, FR-011, FR-012, FR-018, FR-019). */
@OptIn(ExperimentalTestApi::class)
class NoiseGateSliderTest {

    private val reported = mutableListOf<NoiseGate>()
    private var settled = 0
    private val telemetry = ScriptedTelemetry()
    private var primary = Color.Unspecified
    private var outline = Color.Unspecified
    private var emptyTrack = Color.Unspecified
    private var pixelDensity = 1f

    /**
     * How far the track's ends lie inside the slider node, in pixels. Measured from where the handle's centre really
     * stands, never assumed: Material lays the track out between the first and the last position of the handle's
     * centre and keeps some room of its own on each side of the node.
     */
    private var insetPx = 0f
    private var inputModeManager: InputModeManager? = null
    private val aboveGate = mutableStateOf<Boolean?>(null)

    /** Renders the slider 320 dp wide over an opaque surface and returns a reader of the hoisted value. */
    private fun ComposeUiTest.setSlider(
        initial: NoiseGate = NoiseGate.Off,
        width: Dp = 320.dp,
        interactionSource: MutableInteractionSource? = null,
    ): () -> NoiseGate {
        var gate by mutableStateOf(initial)
        setContent {
            inputModeManager = LocalInputModeManager.current
            TembraTheme {
                pixelDensity = LocalDensity.current.density
                primary = TembraTheme.colors.primary
                val above by aboveGate
                // An opaque surface: Android captures a transparent node over the activity's white window.
                Box(Modifier.width(width).background(TembraTheme.colors.surface)) {
                    NoiseGateSlider(
                        gate = gate,
                        onGate = {
                            reported += it
                            gate = it
                        },
                        onGateSettled = { settled++ },
                        telemetry = telemetry,
                        listening = false,
                        soundAboveGate = above,
                        interactionSource = interactionSource,
                    )
                }
            }
        }
        waitForIdle()
        measureInset(initial)
        return { gate }
    }

    /** Sets [insetPx] from the handle's place for [gate]; a handle at the very middle says nothing about the ends. */
    private fun ComposeUiTest.measureInset(gate: NoiseGate) {
        if (gate.step * 2 == NoiseGate.STEPS) return
        val slider = onNodeWithTag("tuner.noiseGate.slider").getUnclippedBoundsInRoot()
        val handle = onNodeWithTag("tuner.noiseGate.handle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val widthPx = slider.width.value * pixelDensity
        val centrePx = ((handle.left + handle.right).value / 2f - slider.left.value) * pixelDensity
        insetPx = (centrePx - widthPx * gate.fraction) / (1f - 2f * gate.fraction)
    }

    /**
     * Renders the slider with the frame clock held, so the test steps the frames the track draws on; the level and the
     * gate's state come from [telemetry]. Returns a setter of the hoisted gate.
     */
    private fun ComposeUiTest.setLiveSlider(initial: NoiseGate, listening: Boolean = true, reduceMotion: Boolean = false): (NoiseGate) -> Unit {
        var gate by mutableStateOf(initial)
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                outline = TembraTheme.colors.outline
                emptyTrack = TembraTheme.colors.surfaceContainerHighest
                pixelDensity = LocalDensity.current.density
                val motion = if (reduceMotion) TembraMotion(reduceMotion = true) else LocalTembraMotion.current
                CompositionLocalProvider(LocalTembraMotion provides motion) {
                    // An opaque surface: Android captures a transparent node over the activity's white window.
                    Box(Modifier.width(320.dp).background(TembraTheme.colors.surface)) {
                        NoiseGateSlider(
                            gate = gate,
                            onGate = {
                                reported += it
                                gate = it
                            },
                            onGateSettled = { settled++ },
                            telemetry = telemetry,
                            listening = listening,
                            soundAboveGate = null,
                        )
                    }
                }
            }
        }
        frames(4)
        measureInset(initial)
        return { next ->
            gate = next
            frames(3)
        }
    }

    private fun ComposeUiTest.frames(count: Int) = repeat(count) { mainClock.advanceTimeByFrame() }

    private fun script(levelDb: Float, gateOpen: Boolean) {
        telemetry.snapshot.inputLevelDb = levelDb
        telemetry.snapshot.gateOpen = gateOpen
    }

    /** The colour on the track's centre line at [fraction] of the track, from a capture of the slider node. */
    private fun ComposeUiTest.colorOnTrack(fraction: Float): Color {
        val image = onNodeWithTag("tuner.noiseGate.slider").captureToImage()
        val x = (insetPx + (image.width - 2 * insetPx) * fraction).roundToInt()
        return image.toPixelMap()[x, image.height / 2]
    }

    /**
     * Where the fill ends, in pixels from the left of the slider node: the end of the run of [fill]-coloured pixels that
     * starts just past the place the handle's disc covers at "Off". -1 when there is no such run.
     */
    private fun ComposeUiTest.fillEndPx(fill: Color): Int {
        val pixels = onNodeWithTag("tuner.noiseGate.slider").captureToImage().toPixelMap()
        val y = pixels.height / 2
        var end = -1
        for (x in (insetPx + 10f * pixelDensity).roundToInt() until (pixels.width - insetPx).roundToInt()) {
            val pixel = pixels[x, y]
            if (pixel.distanceTo(fill) < pixel.distanceTo(emptyTrack)) end = x else break
        }
        return end
    }

    /** The x of [fraction] of the track, in pixels from the left of the slider node whose width is [nodeWidthPx]. */
    private fun trackX(fraction: Float, nodeWidthPx: Int): Float = insetPx + (nodeWidthPx - 2 * insetPx) * fraction

    /** The x of [step] on the slider node whose width is [nodeWidth], in pixels. */
    private fun stepX(step: Int, nodeWidth: Float): Float = insetPx + (nodeWidth - 2 * insetPx) * step / NoiseGate.STEPS

    @Test
    fun showsTheLabelTheTwoEndLabelsAndNoDecibelFigure() = runComposeUiTest {
        setSlider()
        onNodeWithTag("tuner.noiseGate").assertExists()
        onNodeWithTag("tuner.noiseGate.slider").assertExists()
        onNodeWithTag("tuner.noiseGate.handle", useUnmergedTree = true).assertExists()
        val inside = hasAnyAncestor(hasTestTag("tuner.noiseGate"))
        onNode(hasText("NOISE GATE") and inside, useUnmergedTree = true).assertExists()
        onNode(hasText("Off") and inside, useUnmergedTree = true).assertExists()
        onNode(hasText("High") and inside, useUnmergedTree = true).assertExists()
        onAllNodes(hasText("dB", substring = true, ignoreCase = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aClickOnTheTrackMovesTheHandleThere() = runComposeUiTest {
        val gate = setSlider()
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { click(Offset(stepX(10, width.toFloat()), centerY)) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(10), gate())
        assertEquals(-60, gate().levelDb)
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { click(Offset(stepX(20, width.toFloat()), centerY)) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(20), gate())
        assertEquals(listOf(NoiseGate.ofStep(10), NoiseGate.ofStep(20)), reported)
    }

    @Test
    fun aSidewaysDragReportsRisingSteps() = runComposeUiTest {
        val gate = setSlider()
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput {
            down(Offset(stepX(0, width.toFloat()), centerY))
            for (step in 1..12) moveTo(Offset(stepX(step, width.toFloat()), centerY))
            up()
        }
        waitForIdle()
        // Material starts a drag once the touch slop is passed, so the handle trails the finger by the slop: it ends a
        // few steps short of the twelve the finger travelled, as the tempo slider's does.
        assertTrue(gate().step in 8..12, "the handle ended at step ${gate().step}")
        assertEquals(reported.last(), gate())
        assertTrue(reported.size >= 3, "only ${reported.size} steps were reported")
        assertEquals(reported.sortedBy { it.step }, reported, "the steps did not rise: ${reported.map { it.step }}")
        assertEquals(reported.distinct(), reported, "a step was reported twice: ${reported.map { it.step }}")
    }

    @Test
    fun theHandleStopsAtBothEnds() = runComposeUiTest {
        val gate = setSlider(NoiseGate.ofStep(15))
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput {
            down(center)
            moveTo(Offset(-200f, centerY))
            up()
        }
        waitForIdle()
        assertEquals(NoiseGate.Off, gate())
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput {
            down(center)
            moveTo(Offset(width + 200f, centerY))
            up()
        }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(NoiseGate.STEPS), gate())
        assertEquals(-20, gate().levelDb)
    }

    @Test
    fun anUnchangedStepIsNotReported() = runComposeUiTest {
        setSlider(NoiseGate.ofStep(15))
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { click(center) }
        waitForIdle()
        assertEquals(emptyList(), reported)
    }

    // --- The move has ended (User Story 4: the level is written once per move) ---

    @Test
    fun aDragSettlesOnceWhenTheFingerLifts() = runComposeUiTest {
        setSlider()
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput {
            down(Offset(stepX(0, width.toFloat()), centerY))
            for (step in 1..12) moveTo(Offset(stepX(step, width.toFloat()), centerY))
        }
        waitForIdle()
        assertTrue(reported.isNotEmpty(), "the drag reported no step")
        assertEquals(0, settled, "the move settled while the finger was still down")
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { up() }
        waitForIdle()
        assertEquals(1, settled)
    }

    @Test
    fun aTouchOnTheTrackSettlesOnce() = runComposeUiTest {
        val gate = setSlider()
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { click(Offset(stepX(10, width.toFloat()), centerY)) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(10), gate())
        assertEquals(1, settled)
    }

    // --- The live level on the track (User Story 3) ---

    @Test
    fun theFillEndsWhereTheHandleStandsForTheSameLevel() = runComposeUiTest {
        script(levelDb = -50f, gateOpen = true)
        val setGate = setLiveSlider(NoiseGate.ofStep(NoiseGate.STEPS))
        val slider = onNodeWithTag("tuner.noiseGate.slider").getUnclippedBoundsInRoot()
        val nodeWidthPx = (slider.width.value * pixelDensity).roundToInt()
        val fillEnd = fillEndPx(primary)
        assertTrue(abs(fillEnd - trackX(0.5f, nodeWidthPx)) <= 2f * pixelDensity, "a -50 dB level ends at $fillEnd px, the middle of the track is at ${trackX(0.5f, nodeWidthPx)} px")

        // The handle at -50 dB stands on the same place (spec SC-006: one scale for the level and the gate).
        setGate(NoiseGate.ofLevelDb(-50)!!)
        val handle = onNodeWithTag("tuner.noiseGate.handle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val handleCentrePx = ((handle.left + handle.right).value / 2f - slider.left.value) * pixelDensity
        assertTrue(abs(fillEnd - handleCentrePx) <= 2f * pixelDensity, "the fill ends at $fillEnd px and the handle's centre is at $handleCentrePx px")
    }

    @Test
    fun aHeldBackSoundIsDrawnMutedAndShortOfTheHandle() = runComposeUiTest {
        script(levelDb = -60f, gateOpen = false)
        setLiveSlider(NoiseGate.ofLevelDb(-40)!!)
        val inTheFill = colorOnTrack(0.2f)
        assertTrue(inTheFill.distanceTo(outline) < inTheFill.distanceTo(primary), "a held-back sound is drawn in $inTheFill, not the muted colour")
        assertTrue(inTheFill.distanceTo(outline) < inTheFill.distanceTo(emptyTrack), "no fill is drawn for a held-back sound")
        val pastTheFill = colorOnTrack(0.5f)
        assertTrue(pastTheFill.distanceTo(emptyTrack) < pastTheFill.distanceTo(outline), "the fill reaches past the level: $pastTheFill at the middle")
        val slider = onNodeWithTag("tuner.noiseGate.slider").getUnclippedBoundsInRoot()
        val nodeWidthPx = (slider.width.value * pixelDensity).roundToInt()
        assertTrue(fillEndPx(outline) < trackX(NoiseGate.ofLevelDb(-40)!!.fraction, nodeWidthPx) - 8f * pixelDensity, "the fill does not end short of the handle")
    }

    @Test
    fun aSoundThatIsReadIsDrawnInTheActiveColourWithTheGateOffOrOpen() = runComposeUiTest {
        script(levelDb = -45f, gateOpen = true)
        val setGate = setLiveSlider(NoiseGate.Off)
        var inTheFill = colorOnTrack(0.3f)
        assertTrue(inTheFill.distanceTo(primary) < inTheFill.distanceTo(outline), "with the gate off the fill is drawn in $inTheFill")
        assertTrue(inTheFill.distanceTo(primary) < inTheFill.distanceTo(emptyTrack), "with the gate off no fill is drawn")
        setGate(NoiseGate.ofLevelDb(-60)!!)
        inTheFill = colorOnTrack(0.45f)
        assertTrue(inTheFill.distanceTo(primary) < inTheFill.distanceTo(outline), "with the gate open the fill is drawn in $inTheFill")
    }

    @Test
    fun notListeningDrawsNoFillAndTheHandleStillMoves() = runComposeUiTest {
        script(levelDb = -30f, gateOpen = true)
        setLiveSlider(NoiseGate.ofLevelDb(-30)!!, listening = false)
        val onTheTrack = colorOnTrack(0.2f)
        assertTrue(onTheTrack.distanceTo(emptyTrack) < onTheTrack.distanceTo(primary), "a fill is drawn while not listening: $onTheTrack")
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { click(Offset(stepX(20, width.toFloat()), centerY)) }
        frames(3)
        assertEquals(listOf(NoiseGate.ofStep(20)), reported)
    }

    @Test
    fun theFillFollowsTheLevelWithinAFifthOfASecond() = runComposeUiTest {
        script(levelDb = -60f, gateOpen = true)
        setLiveSlider(NoiseGate.Off)
        val slider = onNodeWithTag("tuner.noiseGate.slider").getUnclippedBoundsInRoot()
        val nodeWidthPx = (slider.width.value * pixelDensity).roundToInt()
        assertTrue(abs(fillEndPx(primary) - trackX(NoiseGate.fractionOfLevel(-60f), nodeWidthPx)) <= 2f * pixelDensity)
        script(levelDb = -30f, gateOpen = true)
        frames(12)
        val target = trackX(NoiseGate.fractionOfLevel(-30f), nodeWidthPx)
        assertTrue(abs(fillEndPx(primary) - target) <= 2f * pixelDensity, "200 ms after a rise the fill ends at ${fillEndPx(primary)} px, the level is at $target px")
        script(levelDb = -60f, gateOpen = true)
        frames(12)
        val lower = trackX(NoiseGate.fractionOfLevel(-60f), nodeWidthPx)
        assertTrue(abs(fillEndPx(primary) - lower) <= 2f * pixelDensity, "200 ms after a fall the fill ends at ${fillEndPx(primary)} px, the level is at $lower px")
    }

    @Test
    fun withReduceMotionTheFillStillFollowsTheLevelAtOnce() = runComposeUiTest {
        script(levelDb = -60f, gateOpen = true)
        setLiveSlider(NoiseGate.Off, reduceMotion = true)
        val slider = onNodeWithTag("tuner.noiseGate.slider").getUnclippedBoundsInRoot()
        val nodeWidthPx = (slider.width.value * pixelDensity).roundToInt()
        script(levelDb = -30f, gateOpen = true)
        frames(1)
        val target = trackX(NoiseGate.fractionOfLevel(-30f), nodeWidthPx)
        assertTrue(abs(fillEndPx(primary) - target) <= 2f * pixelDensity, "one frame after a rise the fill ends at ${fillEndPx(primary)} px, the level is at $target px")
    }

    // --- Pointer, keyboard and screen reader (User Story 5) ---

    @Test
    fun theSliderIsATouchTargetTallAndItsHandleIsOneInBothDirections() = runComposeUiTest {
        setSlider()
        onNodeWithTag("tuner.noiseGate.slider").assertHeightIsAtLeast(44.dp)
        val handle = onNodeWithTag("tuner.noiseGate.handle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        // A size in dp lands on whole pixels (44 dp is 107.25 px on the fold emulator), so the half dp that Compose's
        // own size assertions allow is allowed here.
        assertTrue(handle.width >= 44.dp - 0.5.dp && handle.height >= 44.dp - 0.5.dp, "the handle's touch box is $handle")
        // The disc drawn in the box keeps its 16 dp and sits at the box's centre.
        val slider = onNodeWithTag("tuner.noiseGate.slider").getUnclippedBoundsInRoot()
        val pixels = onNodeWithTag("tuner.noiseGate.slider").captureToImage().toPixelMap()
        val centreX = (((handle.left + handle.right).value / 2f - slider.left.value) * pixelDensity).roundToInt()
        val centreY = pixels.height / 2
        assertTrue(pixels[centreX, centreY].distanceTo(primary) < 0.1f, "no disc at the centre of the handle's box")
        val outside = (14f * pixelDensity).roundToInt()
        assertTrue(pixels[centreX - outside, centreY].distanceTo(primary) > 0.1f, "the disc is wider than 16 dp to the left")
        assertTrue(pixels[centreX, centreY - outside].distanceTo(primary) > 0.1f, "the disc is taller than 16 dp")
    }

    @Test
    fun theArrowKeysMoveTheHandleOneStepAndSettleEachTime() = runComposeUiTest {
        val gate = setSlider(NoiseGate.ofStep(1))
        // Declared per lane: the Android device lane has no keyboard input mode and skips by that declaration.
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        val slider = onNodeWithTag("tuner.noiseGate.slider")
        slider.requestFocus()
        slider.assertIsFocused()
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(2), gate())
        assertEquals(1, settled, "a key press must end its move")
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(NoiseGate.Off, gate())
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals(NoiseGate.Off, gate(), "the handle must stop at the start")
        assertEquals(listOf(2, 1, 0), reported.map { it.step })
    }

    /** Up and Down step too (spec FR-018: every arrow key), though Material 1.5 moves a horizontal slider with Left and Right only. */
    @Test
    fun upAndDownMoveTheHandleOneStepAndSettleEachTime() = runComposeUiTest {
        val gate = setSlider(NoiseGate.ofStep(1))
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        val slider = onNodeWithTag("tuner.noiseGate.slider")
        slider.requestFocus()
        slider.performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(2), gate())
        assertEquals(1, settled, "a key press must end its move")
        slider.performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(1), gate())
        assertEquals(listOf(2, 1), reported.map { it.step })
    }

    @Test
    fun theArrowKeysStopAtTheLoudEnd() = runComposeUiTest {
        val gate = setSlider(NoiseGate.ofStep(NoiseGate.STEPS - 1))
        if (!enterKeyboardMode(inputModeManager)) return@runComposeUiTest
        val slider = onNodeWithTag("tuner.noiseGate.slider")
        slider.requestFocus()
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        slider.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(NoiseGate.STEPS), gate())
        assertEquals(listOf(NoiseGate.STEPS), reported.map { it.step })
    }

    private fun ComposeUiTest.emit(source: MutableInteractionSource, interaction: Interaction) {
        runOnIdle { source.tryEmit(interaction) }
        repeat(2) { mainClock.advanceTimeByFrame() }
    }

    /** Pixels that changed between two captures of the slider and moved towards `primary`. */
    private fun changedTowardsPrimary(before: ImageBitmap, after: ImageBitmap): Int {
        val a = before.toPixelMap()
        val b = after.toPixelMap()
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (a[x, y].distanceTo(b[x, y]) > 0.05f && b[x, y].distanceTo(primary) < a[x, y].distanceTo(primary)) count++
        }
        return count
    }

    @Test
    fun theHandleShowsHoverPressAndFocus() = runComposeUiTest {
        val source = MutableInteractionSource()
        mainClock.autoAdvance = false
        setSlider(NoiseGate.ofStep(10), interactionSource = source)
        repeat(2) { mainClock.advanceTimeByFrame() }
        fun capture() = onNodeWithTag("tuner.noiseGate.slider").captureToImage()
        // A state layer is a ring of the disc's own diameter or more: at least a few hundred pixels.
        val minimum = (60 * pixelDensity * pixelDensity).roundToInt()
        val rest = capture()
        val enter = HoverInteraction.Enter()
        emit(source, enter)
        val hovered = changedTowardsPrimary(rest, capture())
        assertTrue(hovered > minimum, "hover drew no halo: $hovered pixels moved towards primary (want > $minimum)")
        emit(source, HoverInteraction.Exit(enter))
        val press = PressInteraction.Press(androidx.compose.ui.geometry.Offset.Zero)
        emit(source, press)
        val pressed = changedTowardsPrimary(rest, capture())
        assertTrue(pressed > minimum, "press drew no halo: $pressed pixels moved towards primary (want > $minimum)")
        emit(source, PressInteraction.Release(press))
        emit(source, FocusInteraction.Focus())
        val focused = changedTowardsPrimary(rest, capture())
        assertTrue(focused > minimum, "focus drew no ring: $focused pixels moved towards primary (want > $minimum)")
    }

    @Test
    fun theScreenReaderHearsTheLabelThePlaceAndWhetherTheSoundIsAboveTheGate() = runComposeUiTest {
        val gate = setSlider(NoiseGate.Off)
        val slider = onNodeWithTag("tuner.noiseGate.slider")
        slider.assertContentDescriptionEquals("Noise gate")
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
        slider.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        // One element: the drawn label and end labels repeat what the slider says, so they are hidden from the reader.
        for (text in listOf("NOISE GATE", "Off", "High")) {
            onNode(hasText(text) and hasAnyAncestor(hasTestTag("tuner.noiseGate")), useUnmergedTree = true)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        }
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..NoiseGate.STEPS.toFloat(), NoiseGate.STEPS - 1)))

        // The platform's adjust gestures arrive as the set-progress action.
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
        waitForIdle()
        assertEquals(NoiseGate.ofStep(15), gate())
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "50 percent"))

        aboveGate.value = true
        waitForIdle()
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "50 percent, sound above the gate"))
        aboveGate.value = false
        waitForIdle()
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "50 percent, sound below the gate"))
        aboveGate.value = null
        waitForIdle()
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "50 percent"))
    }

    @Test
    fun anUpAndDownDragThatStartsOnTheSliderScrollsAndLeavesTheHandle() = runComposeUiTest {
        val scroll = ScrollState(0)
        var gate by mutableStateOf(NoiseGate.ofStep(10))
        setContent {
            TembraTheme {
                Column(Modifier.width(320.dp).height(240.dp).verticalScroll(scroll).background(TembraTheme.colors.surface)) {
                    Spacer(Modifier.height(120.dp))
                    NoiseGateSlider(
                        gate = gate,
                        onGate = {
                            reported += it
                            gate = it
                        },
                        onGateSettled = { settled++ },
                        telemetry = telemetry,
                        listening = false,
                        soundAboveGate = null,
                    )
                    Spacer(Modifier.height(400.dp))
                }
            }
        }
        waitForIdle()
        onNodeWithTag("tuner.noiseGate.slider").performTouchInput { swipeUp(startY = centerY, endY = centerY - 100.dp.toPx()) }
        waitForIdle()
        assertTrue(scroll.value > 0, "the up-and-down drag did not scroll")
        assertEquals(NoiseGate.ofStep(10), gate)
        assertEquals(emptyList(), reported)
    }
}
