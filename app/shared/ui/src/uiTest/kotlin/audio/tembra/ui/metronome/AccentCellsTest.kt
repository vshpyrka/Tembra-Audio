package audio.tembra.ui.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.AccentPattern
import audio.tembra.core.metronome.BeatLevel
import audio.tembra.ui.designsystem.approximately
import audio.tembra.ui.designsystem.averageColorAt
import audio.tembra.ui.designsystem.isCloserTo
import audio.tembra.ui.metronome.components.AccentCells
import audio.tembra.ui.metronome.components.MetronomeDefaults
import audio.tembra.ui.theme.TembraTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The beat cells (contracts/design-system-additions.md, research R7): labels, semantics, the draw-phase light, the glow and the bar heights. */
@OptIn(ExperimentalTestApi::class)
class AccentCellsTest {

    private val zeroScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = 0f
    }

    private val acccPattern = AccentPattern(listOf(BeatLevel.Accent, BeatLevel.Click, BeatLevel.Click, BeatLevel.Click))
    private val cycled = mutableListOf<Int>()
    private var secondary = Color.Unspecified
    private var surfaceContainer = Color.Unspecified
    private var pixelDensity = 1f

    private fun ComposeUiTest.setCells(telemetry: BeatTelemetry, running: Boolean = true, pattern: AccentPattern = acccPattern) {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                secondary = TembraTheme.colors.secondary
                surfaceContainer = TembraTheme.colors.surfaceContainer
                pixelDensity = LocalDensity.current.density
                Box(Modifier.width(320.dp)) {
                    AccentCells(pattern = pattern, running = running, telemetry = telemetry, onCycle = { cycled += it }, testTag = "cells")
                }
            }
        }
        repeat(2) { mainClock.advanceTimeByFrame() }
    }

    private fun ComposeUiTest.barBox(beat: Int): ImageBitmap = onNodeWithTag("cells.$beat.bar", useUnmergedTree = true).captureToImage()

    /** The 3 × 3 block at the middle of the bar box's top edge, where the lit outline is drawn. */
    private fun ComposeUiTest.topEdge(beat: Int): Color {
        val image = barBox(beat)
        return image.averageColorAt(image.width / 2, 1)
    }

    /** Beside the bar, in the box's padding at mid-height: only a glow tints it. */
    private fun ComposeUiTest.besideTheBar(beat: Int): Color {
        val image = barBox(beat)
        val x = (MetronomeDefaults.BarBoxPadding.value * pixelDensity / 2).roundToInt()
        return image.averageColorAt(x, image.height / 2)
    }

    private fun Color.pinkness(): Float = red - green

    @Test
    fun rendersOneCellPerBeatWithLabelsAndSemantics() = runComposeUiTest {
        setCells(ScriptedBeatTelemetry())
        val labels = listOf("ACCENT", "CLICK", "CLICK", "CLICK")
        val levels = listOf("Accent", "Click", "Click", "Click")
        for (beat in 1..4) {
            onNodeWithTag("cells.$beat")
                .assertContentDescriptionEquals("Beat $beat")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, levels[beat - 1]))
                .assertWidthIsAtLeast(44.dp)
                .assertHeightIsAtLeast(44.dp)
            onNode(hasText(labels[beat - 1]) and hasAnyAncestor(hasTestTag("cells.$beat")), useUnmergedTree = true).assertExists()
        }
        onNodeWithTag("cells.5").assertDoesNotExist()
    }

    @Test
    fun theScriptedBeatIsOutlined() = runComposeUiTest {
        setCells(ScriptedBeatTelemetry(litBeat = 1))
        mainClock.advanceTimeByFrame()
        val lit = topEdge(2)
        assertTrue(lit.isCloserTo(secondary, surfaceContainer), "cell 2 is not outlined: $lit")
        val other = topEdge(1)
        assertFalse(other.isCloserTo(secondary, surfaceContainer), "cell 1 is outlined: $other")
        assertFalse(topEdge(3).isCloserTo(secondary, surfaceContainer), "cell 3 is outlined")
    }

    @Test
    fun nothingIsReadOrLitWhileStopped() = runComposeUiTest {
        val telemetry = ScriptedBeatTelemetry(litBeat = 1)
        setCells(telemetry, running = false)
        mainClock.advanceTimeByFrame()
        assertEquals(0, telemetry.calls)
        for (beat in 1..4) assertFalse(topEdge(beat).isCloserTo(secondary, surfaceContainer), "cell $beat is outlined while stopped")
    }

    /** Widths whose pixel rounding once pushed the fourth cell onto a second row (found on the iPhone 17 simulator). */
    @Test
    fun fourBeatsStayInOneRowAtAnyWidth() = runComposeUiTest {
        var width by mutableStateOf(331.dp)
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                Box(Modifier.width(width)) {
                    AccentCells(pattern = acccPattern, running = false, telemetry = ScriptedBeatTelemetry(), onCycle = {}, testTag = "cells")
                }
            }
        }
        for (candidate in listOf(331.dp, 337.3.dp, 346.dp, 353.7.dp, 354.dp, 369.dp, 402.dp)) {
            width = candidate
            repeat(2) { mainClock.advanceTimeByFrame() }
            val tops = (1..4).map { onNodeWithTag("cells.$it").getUnclippedBoundsInRoot().top }
            assertTrue(tops.all { it == tops.first() }, "at $candidate the cells are not in one row: $tops")
        }
    }

    @Test
    fun clickingACellCyclesIt() = runComposeUiTest {
        setCells(ScriptedBeatTelemetry())
        onNodeWithTag("cells.3").performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(listOf(2), cycled)
    }

    @Test
    fun theAccentBarGlowsInSecondary() = runComposeUiTest {
        setCells(ScriptedBeatTelemetry())
        val accent = besideTheBar(1)
        val click = besideTheBar(2)
        assertTrue(accent.pinkness() > click.pinkness() + 0.03f, "no secondary glow beside the Accent bar: $accent against $click")
    }

    @Test
    fun theGlowIsStaticUnderReduceMotion() = runComposeUiTest(effectContext = zeroScale) {
        setCells(ScriptedBeatTelemetry())
        val before = besideTheBar(1)
        mainClock.advanceTimeBy(1000)
        val after = besideTheBar(1)
        assertTrue(before.approximately(after, 0.01f) && abs(before.alpha - after.alpha) < 0.01f, "the glow changed under reduce motion: $before -> $after")
    }

    @Test
    fun barHeightsFollowTheLevel() = runComposeUiTest {
        setCells(ScriptedBeatTelemetry(), pattern = AccentPattern(listOf(BeatLevel.Accent, BeatLevel.Click, BeatLevel.Sub, BeatLevel.Click)))
        fun ratio(beat: Int): Float {
            val box = onNodeWithTag("cells.$beat.bar", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val bar = onNodeWithTag("cells.$beat.level", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val inner = (box.bottom - box.top) - MetronomeDefaults.BarBoxPadding * 2
            return (bar.bottom - bar.top) / inner
        }
        assertTrue(ratio(1) >= 0.9f, "Accent bar ${ratio(1)}")
        assertTrue(ratio(2) >= 0.55f, "Click bar ${ratio(2)}")
        assertTrue(ratio(3) <= 0.5f, "Sub bar ${ratio(3)}")
    }
}
