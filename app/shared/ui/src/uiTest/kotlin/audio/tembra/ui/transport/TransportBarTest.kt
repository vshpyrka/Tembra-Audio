package audio.tembra.ui.transport

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
 * and focus through injected sources, and the play button's toggle.
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

    private fun ComposeUiTest.setBar(
        state: TransportUiState = demo,
        telemetry: ScriptedTransportTelemetry = ScriptedTransportTelemetry.stopped(0.0),
        stateHolder: ((TransportUiState) -> Unit) -> Unit = {},
    ) {
        setContent {
            TembraTheme {
                primary = TembraTheme.colors.primary
                var current by androidx.compose.runtime.remember { mutableStateOf(state) }
                stateHolder { current = it }
                TransportBar(state = current, callbacks = callbacks, telemetry = telemetry)
            }
        }
        mainClock.autoAdvance = false
    }

    /** At a phone's width (360 dp) every control keeps its target and the stacked readout sits between its neighbours (spec FR-004). */
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
        val slider = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        assertTrue(readout.left >= forward.right, "the readout (${readout.left}) overlaps the step-forward button (${forward.right})")
        assertTrue(readout.right <= metronome.left, "the readout (${readout.right}) overlaps the metronome toggle (${metronome.left})")
        assertTrue(metronome.right <= 360.dp, "the metronome toggle leaves the 360 dp bar: ${metronome.right}")
        assertTrue(slider.right - slider.left >= 56.dp, "the master slider is too short to use: ${slider.right - slider.left}")
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
        val width = readout.right - readout.left
        assertTrue(readout.left >= forward.right && readout.right <= metronome.left, "the readout ($readout) runs under its neighbours ($forward, $metronome)")
        // 411 − 2 × 4 padding − 6 × 2 gaps − 56 slider − 3 × 44 − 48 − 44 = 111 dp is the budget; the doubled digits use most of it.
        assertTrue(width >= 80.dp && width <= 111.dp, "the readout's width is not its budget: $width")
        assertTrue(metronome.right <= 411.dp, "the metronome toggle leaves the bar: ${metronome.right}")
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
        onNodeWithTag(TAG)
            .assertContentDescriptionEquals(TransportCopy.masterVolume)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80 percent"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(80f, 0f..100f, 99)))
        onNodeWithTag(PerfAnchors.TRANSPORT_READOUT).assertContentDescriptionEquals("00:00.00 of 00:30.00")
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

    @Test
    fun controlsAreDisabledWithoutAProjectExceptTheMaster() = runComposeUiTest {
        setBar(state = demo.copy(hasProject = false))
        onNodeWithTag(PerfAnchors.TRANSPORT_SKIP_START).assertIsNotEnabled()
        onNodeWithTag(STEP_BACK_TAG).assertIsNotEnabled()
        onNodeWithTag(PerfAnchors.TRANSPORT_METRONOME).assertIsNotEnabled()
        onNodeWithTag(TAG).assertIsEnabled()
    }

    @Test
    fun masterSliderMovesOnePercentWithArrowsAndTenWithPageKeys() = runComposeUiTest {
        setBar()
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

    private companion object {
        const val FRAMES_MS = 100L
    }
}
