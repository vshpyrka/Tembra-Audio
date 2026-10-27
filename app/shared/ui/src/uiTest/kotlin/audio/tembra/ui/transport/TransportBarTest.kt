package audio.tembra.ui.transport

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipe
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.Beats
import audio.tembra.core.project.PlaybackState
import audio.tembra.ui.designsystem.containsColor
import audio.tembra.ui.perf.PerfAnchors
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The transport bar driven with controlled state and scripted telemetry (contracts/timeline-screen.md; T079):
 * semantics of every control, the readout from the telemetry under a frozen clock, the master slider's keys, hover
 * and focus through injected sources, and the play button's toggle. Each test fixes the bar's width so the master
 * volume's form does not follow the device: below 600 dp a speaker button with a pop-up slider, from 600 dp the inline
 * slider with its percentage (spec FR-007, D1 2026-10-26; T138).
 */
@OptIn(ExperimentalTestApi::class)
class TransportBarTest {

    private val calls = mutableListOf<String>()
    private val callbacks = TransportCallbacks(
        onPlayPause = { calls += "playPause" },
        onSkipToStart = { calls += "skipToStart" },
        onStepBar = { calls += "stepBar($it)" },
        onMasterVolume = { calls += "master($it)" },
        onMetronomeToggle = { calls += "metronome" },
        onMetronomeSettings = { calls += "metronomeSettings" },
    )
    private var primary: Color = Color.Unspecified

    private val demo = TransportUiState(
        playback = PlaybackState.Stopped(Beats.Zero),
        positionSeconds = 0.0,
        lengthSeconds = 30.9677,
        masterVolume = 80,
        metronomeOn = false,
        hasProject = true,
    )

    /** The bar at [width], a phone's 360 dp unless a test needs the wide form: `requiredWidth`, so no device window changes it. */
    private fun ComposeUiTest.setBar(
        state: TransportUiState = demo,
        telemetry: ScriptedTransportTelemetry = ScriptedTransportTelemetry.stopped(0.0),
        width: Dp = PHONE_WIDTH,
        stateHolder: ((TransportUiState) -> Unit) -> Unit = {},
    ) {
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                var current by androidx.compose.runtime.remember { mutableStateOf(state) }
                stateHolder { current = it }
                Box(Modifier.requiredWidth(width)) { TransportBar(state = current, callbacks = callbacks, telemetry = telemetry) }
            }
        }
        mainClock.autoAdvance = false
    }

    /**
     * At a phone's width (360 dp) every control keeps its target, the master volume is the 44 dp speaker button instead of
     * the slider, and the stacked readout sits between its neighbours (spec FR-004, FR-007).
     */
    @Test
    fun atPhoneWidthNothingOverlapsAndTheReadoutStacks() = runComposeUiTest {
        setContent {
            TembraTheme {
                Box(Modifier.width(360.dp)) { TransportBar(state = demo, callbacks = callbacks, telemetry = ScriptedTransportTelemetry.stopped(0.0)) }
            }
        }
        val readout = onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).getUnclippedBoundsInRoot()
        val forward = onNodeWithTag(STEP_FORWARD_TAG).getUnclippedBoundsInRoot()
        val metronome = onNodeWithTag(PerfAnchors.TRANSPORT_METRONOME).getUnclippedBoundsInRoot()
        val speaker = onNodeWithTag(MASTER_BUTTON_TAG).getUnclippedBoundsInRoot()
        assertTrue(readout.left >= forward.right, "the readout (${readout.left}) overlaps the step-forward button (${forward.right})")
        assertTrue(readout.right <= metronome.left, "the readout (${readout.right}) overlaps the metronome toggle (${metronome.left})")
        assertTrue(metronome.right <= 360.dp, "the metronome toggle leaves the 360 dp bar: ${metronome.right}")
        assertTrue(speaker.left >= 0.dp, "the speaker button leaves the bar: ${speaker.left}")
        val order = listOf(
            MASTER_BUTTON_TAG, PerfAnchors.TRANSPORT_SKIP_START, STEP_BACK_TAG, PerfAnchors.TRANSPORT_PLAY, STEP_FORWARD_TAG,
            PerfAnchors.TRANSPORT_READOUT, PerfAnchors.TRANSPORT_METRONOME,
        )
        for ((a, b) in order.zipWithNext()) {
            val left = onNodeWithTag(a).getUnclippedBoundsInRoot()
            val right = onNodeWithTag(b).getUnclippedBoundsInRoot()
            assertTrue(left.right <= right.left, "$a ($left) overlaps $b ($right)")
        }
        onNodeWithTag(TAG).assertDoesNotExist()
        onNodeWithTag(MASTER_BUTTON_TAG).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertWidthIsEqualTo(48.dp)
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).assertWidthIsEqualTo(44.dp)
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertContentDescriptionEquals("00:00.00 of 00:30.00")
    }

    /** At 360 dp with the system font doubled (spec FR-059) the readout shrinks to its room instead of running under its neighbours. */
    @Test
    fun atPhoneWidthAndTwiceTheFontTheReadoutStillFits() = runComposeUiTest {
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                TembraTheme {
                    Box(Modifier.width(360.dp)) { TransportBar(state = demo, callbacks = callbacks, telemetry = ScriptedTransportTelemetry.stopped(0.0)) }
                }
            }
        }
        val readout = onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).getUnclippedBoundsInRoot()
        val forward = onNodeWithTag(STEP_FORWARD_TAG).getUnclippedBoundsInRoot()
        val metronome = onNodeWithTag(PerfAnchors.TRANSPORT_METRONOME).getUnclippedBoundsInRoot()
        assertTrue(readout.left >= forward.right && readout.right <= metronome.left, "the doubled readout ($readout) runs under its neighbours ($forward, $metronome)")
        assertTrue(readout.right - readout.left >= 40.dp, "the readout has no room: $readout")
        assertTrue(metronome.right <= 360.dp, "the metronome toggle leaves the bar: ${metronome.right}")
    }

    /**
     * The reference phone (411 dp) at twice the font: the bar's budget is what the readout really gets (no weighted slot).
     * `requiredWidth`: a narrower device window (the 360 dp OPPO, the 393 dp Redmi) must not squeeze the bar under test.
     */
    @Test
    fun atTheReferencePhoneWidthAndTwiceTheFontTheReadoutKeepsItsBudget() = runComposeUiTest {
        setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                TembraTheme {
                    Box(Modifier.requiredWidth(411.dp)) { TransportBar(state = demo, callbacks = callbacks, telemetry = ScriptedTransportTelemetry.stopped(0.0)) }
                }
            }
        }
        val readout = onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).getUnclippedBoundsInRoot()
        val forward = onNodeWithTag(STEP_FORWARD_TAG).getUnclippedBoundsInRoot()
        val metronome = onNodeWithTag(PerfAnchors.TRANSPORT_METRONOME).getUnclippedBoundsInRoot()
        val speaker = onNodeWithTag(MASTER_BUTTON_TAG).getUnclippedBoundsInRoot()
        // Positions are relative to the bar: on a window narrower than 411 dp (the OPPO, the Redmi) the required width centres the bar past the window's left edge.
        val bar = onNodeWithTag(ROOT_TAG).getUnclippedBoundsInRoot()
        val width = readout.right - readout.left
        assertTrue(readout.left >= forward.right && readout.right <= metronome.left, "the readout ($readout) runs under its neighbours ($forward, $metronome)")
        // 411 − 2 × 4 padding − 6 × 2 gaps − 44 speaker − 3 × 44 − 48 − 44 = 123 dp is the budget; the doubled digits use most of it.
        assertTrue(width >= 80.dp && width <= 123.dp, "the readout's width is not its budget: $width")
        assertTrue(metronome.right <= bar.right, "the metronome toggle leaves the bar: ${metronome.right} of $bar")
        // One pixel of rounding room: the fold emulator's 2.44 density lays 44 dp out as 43.9 dp.
        val speakerWidth = speaker.right - speaker.left
        assertTrue(speaker.left >= bar.left && speakerWidth >= 44.dp - 1.dp && speakerWidth <= 44.dp + 1.dp, "the speaker button is not a 44 dp target inside the bar: $speaker in $bar")
    }

    /** A long press on the toggle opens the metronome settings sheet (spec FR-040; T108, US5). */
    @Test
    fun aLongPressOnTheMetronomeToggleOpensItsSettings() = runComposeUiTest {
        setBar()
        onNodeWithTag(PerfAnchors.TRANSPORT_METRONOME).performTouchInput { longClick() }
        assertEquals(listOf("metronomeSettings"), calls)
    }

    @Test
    fun everyControlHasItsSemanticsAndTheBarIs64dp() = runComposeUiTest {
        setBar()
        onNodeWithTag(ROOT_TAG).assertHeightIsEqualTo(64.dp)
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertContentDescriptionEquals(TransportCopy.play).assertHeightIsEqualTo(48.dp)
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).assertContentDescriptionEquals(TransportCopy.skipToStart).assertHeightIsEqualTo(44.dp)
        onNodeWithTag(STEP_BACK_TAG).assertContentDescriptionEquals(TransportCopy.stepBack)
        onNodeWithTag(STEP_FORWARD_TAG).assertContentDescriptionEquals(TransportCopy.stepForward)
        onNodeWithTag(PerfAnchors.TRANSPORT_METRONOME)
            .assertContentDescriptionEquals(TransportCopy.metronomeOn)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, TransportCopy.stateOff))
        onNodeWithTag(MASTER_BUTTON_TAG)
            .assertContentDescriptionEquals(TransportCopy.masterVolume)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80 percent"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsEqualTo(44.dp)
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertContentDescriptionEquals("00:00.00 of 00:30.00")
    }

    /**
     * The phone bar's speaker button opens the pop-up above the bar: its 200 dp slider reads and changes the volume
     * through the bar's callback, and Escape or a tap outside closes it (spec FR-007, D1 2026-10-26).
     */
    @Test
    fun thePhoneBarOpensThePopupWhoseSliderChangesTheVolumeAndCloses() = runComposeUiTest {
        setContent {
            TembraTheme {
                var state by androidx.compose.runtime.remember { mutableStateOf(demo) }
                val wired = callbacks.copy(onMasterVolume = {
                    calls += "master($it)"
                    state = state.copy(masterVolume = it)
                })
                // At the window's bottom, as on Timeline: the pop-up has the room above the bar.
                Box(Modifier.fillMaxSize().testTag(WINDOW), contentAlignment = Alignment.BottomStart) {
                    Box(Modifier.requiredWidth(PHONE_WIDTH)) { TransportBar(state = state, callbacks = wired, telemetry = ScriptedTransportTelemetry.stopped(0.0)) }
                }
            }
        }
        onNodeWithTag(TAG).assertDoesNotExist()
        onNodeWithTag(MASTER_BUTTON_TAG).performClick()
        onNodeWithTag(MASTER_POPUP_TAG).assertExists()
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("80%")
        val slot = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        assertTrue(slot.isSliderSlotOf(200.dp), "the pop-up's slider $slot is not 200 dp wide")
        val card = screenBounds(MASTER_POPUP_TAG)
        val bar = screenBounds(ROOT_TAG)
        val window = screenBounds(WINDOW)
        assertTrue(card.bottom <= bar.top, "the pop-up $card covers the bar $bar")
        assertTrue(card.left >= window.left && card.right <= window.right && card.top >= window.top, "the pop-up $card leaves the window $window")

        // Dragging the slider to its end sets full volume through the bar's callback; the arrows step from there. The drag
        // starts at the centre: the node's own edges are Material's focus-ring room, outside the slider's touch area. It
        // ends a node's width past the right edge: the iOS lane's injected swipe reports its last move short of the end
        // point (95 % at the edge itself), and past the track the slider holds 100 %.
        onNodeWithTag(TAG).performTouchInput { swipe(start = center, end = centerRight + Offset(width.toFloat(), 0f)) }
        assertEquals("master(100)", calls.last())
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("100%")
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals("master(99)", calls.last())
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("99%")
        onNodeWithTag(MASTER_BUTTON_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "99 percent"))

        onNodeWithTag(TAG).performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag(MASTER_POPUP_TAG).assertDoesNotExist()

        onNodeWithTag(MASTER_BUTTON_TAG).performClick()
        onNodeWithTag(MASTER_POPUP_TAG).assertExists()
        tapThroughTheWindow(onNodeWithTag(PerfAnchors.TRANSPORT_READOUT))
        onNodeWithTag(MASTER_POPUP_TAG).assertDoesNotExist()
        onNodeWithTag(MASTER_BUTTON_TAG).assertExists()
    }

    /** From 600 dp the slider stays inline with its percentage after it, and no speaker button is drawn (spec FR-004, FR-007). */
    @Test
    fun theWideBarKeepsTheInlineSliderWithItsPercent() = runComposeUiTest {
        setBar(width = WIDE_WIDTH)
        onNodeWithTag(MASTER_BUTTON_TAG).assertDoesNotExist()
        onNodeWithTag(TAG)
            .assertContentDescriptionEquals(TransportCopy.masterVolume)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80 percent"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(80f, 0f..100f, 99)))
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("80%")
        val slider = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        val percent = onNodeWithTag(MASTER_PERCENT_TAG).getUnclippedBoundsInRoot()
        val skip = onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).getUnclippedBoundsInRoot()
        assertTrue(slider.right - slider.left >= 72.dp, "the inline slider is too short to use: $slider")
        // Clear of the slider's whole node, Material's focus-ring room included: neither the ring nor its bounds cover the label.
        assertTrue(percent.left >= slider.right && percent.right <= skip.left, "the percentage $percent is not between the slider $slider and skip-to-start $skip")
    }

    /** The form changes at exactly 600 dp of bar width: 599 dp is a phone's, 600 dp keeps the slider inline. */
    @Test
    fun theSliderIsInlineFrom600dp() = runComposeUiTest {
        var width by mutableStateOf(599.dp)
        setContent {
            TembraTheme {
                Box(Modifier.requiredWidth(width)) { TransportBar(state = demo, callbacks = callbacks, telemetry = ScriptedTransportTelemetry.stopped(0.0)) }
            }
        }
        onNodeWithTag(MASTER_BUTTON_TAG).assertExists()
        onNodeWithTag(TAG).assertDoesNotExist()
        width = 600.dp
        onNodeWithTag(MASTER_BUTTON_TAG).assertDoesNotExist()
        onNodeWithTag(TAG).assertExists()
        onNodeWithTag(MASTER_PERCENT_TAG).assertTextEquals("80%")
    }

    @Test
    fun buttonsForwardTheirIntents() = runComposeUiTest {
        setBar()
        mainClock.autoAdvance = true
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).performClick()
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).performClick()
        onNodeWithTag(STEP_BACK_TAG).performClick()
        onNodeWithTag(STEP_FORWARD_TAG).performClick()
        onNodeWithTag(PerfAnchors.TRANSPORT_METRONOME).performClick()
        assertEquals(listOf("playPause", "skipToStart", "stepBar(-1)", "stepBar(1)", "metronome"), calls)
    }

    @Test
    fun playTogglesToPauseAndTheReadoutFollowsTheTelemetry() = runComposeUiTest {
        var update: (TransportUiState) -> Unit = {}
        val telemetry = ScriptedTransportTelemetry.playing(startSeconds = 15.0)
        setBar(telemetry = telemetry) { update = it }
        update(demo.copy(playback = PlaybackState.Playing, positionSeconds = 15.4))
        mainClock.advanceTimeByFrame()
        onNodeWithTag(PerfAnchors.TRANSPORT_PLAY).assertContentDescriptionEquals(TransportCopy.pause)
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertContentDescriptionEquals("00:15.00 of 00:30.00")
        // While playing the readout reads the telemetry each frame; the script advances with the clock.
        val readsBefore = telemetry.reads
        mainClock.advanceTimeBy(FRAMES_MS)
        waitForIdle() // the draw pass that reads the telemetry runs on the platform's own frame, after the clock's ticks
        assertTrue(telemetry.reads > readsBefore, "the readout did not read the telemetry while playing")
    }

    @Test
    fun aStoppedReadoutDoesNotReadTheTelemetry() = runComposeUiTest {
        val telemetry = ScriptedTransportTelemetry.stopped(0.0)
        setBar(telemetry = telemetry)
        mainClock.advanceTimeBy(FRAMES_MS)
        assertEquals(0, telemetry.reads)
    }

    /** Without a project only the master volume works: the phone bar's speaker button and the slider it opens. */
    @Test
    fun controlsAreDisabledWithoutAProjectExceptTheMaster() = runComposeUiTest {
        setBar(state = demo.copy(hasProject = false))
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).assertIsNotEnabled()
        onNodeWithTag(STEP_BACK_TAG).assertIsNotEnabled()
        onNodeWithTag(PerfAnchors.TRANSPORT_METRONOME).assertIsNotEnabled()
        onNodeWithTag(MASTER_BUTTON_TAG).assertIsEnabled()
        mainClock.autoAdvance = true
        onNodeWithTag(MASTER_BUTTON_TAG).performClick()
        onNodeWithTag(TAG).assertIsEnabled()
    }

    @Test
    fun masterSliderMovesOnePercentWithArrowsAndTenWithPageKeys() = runComposeUiTest {
        setBar(width = WIDE_WIDTH)
        mainClock.autoAdvance = true
        onNodeWithTag(TAG).requestFocus()
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageUp) }
        onNodeWithTag(TAG).performKeyInput { pressKey(Key.PageDown) }
        assertEquals(listOf("master(81)", "master(90)", "master(70)"), calls)
    }

    @Test
    fun hoverAndFocusAreVisibleOnAButton() = runComposeUiTest {
        val source = MutableInteractionSource()
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                TransportButton(icon = audio.tembra.ui.theme.TembraIcon.SkipPrevious, description = "Skip", onClick = {}, interactionSource = source, testTag = "button")
            }
        }
        mainClock.autoAdvance = false
        onNodeWithText("Skip").assertDoesNotExist()
        assertTrue(source.tryEmit(HoverInteraction.Enter()))
        mainClock.advanceTimeBy(FRAMES_MS)
        onNodeWithText("Skip").assertExists()
        assertTrue(source.tryEmit(FocusInteraction.Focus()))
        mainClock.advanceTimeBy(FRAMES_MS)
        assertTrue(onNodeWithTag("button").captureToImage().containsColor(primary), "the focused button draws no primary ring")
    }

    private fun ComposeUiTest.screenBounds(tag: String): Rect {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        return Rect(node.positionOnScreen, node.size.toSize())
    }

    private companion object {
        const val FRAMES_MS = 100L
        const val WINDOW = "test.window"
        /** The narrowest phone among the targets (the OPPO), so a phone-width bar fits every device's window. */
        val PHONE_WIDTH = 360.dp
        /** Past the 600 dp threshold, as a foldable's inner screen or a tablet's bar. */
        val WIDE_WIDTH = 640.dp
    }
}
