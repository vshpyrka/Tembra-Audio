package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.TrackId
import audio.tembra.ui.metronome.MetronomeCallbacks
import audio.tembra.ui.metronome.MetronomeLayout
import audio.tembra.ui.metronome.MetronomeScreenContent
import audio.tembra.ui.metronome.MetronomeUiState
import audio.tembra.ui.metronome.ScriptedBeatTelemetry
import audio.tembra.ui.projects.ProjectsCallbacks
import audio.tembra.ui.projects.ProjectsLayout
import audio.tembra.ui.projects.ProjectsScreenContent
import audio.tembra.ui.projects.ProjectsUiState
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.timeline.TimelineScreenContent
import audio.tembra.ui.timeline.TimelineUiState
import audio.tembra.ui.timeline.TrackUi
import audio.tembra.ui.tuner.ScriptedTelemetry
import audio.tembra.ui.tuner.TunerCallbacks
import audio.tembra.ui.tuner.TunerLayout
import audio.tembra.ui.tuner.TunerScreenContent
import audio.tembra.ui.tuner.TunerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tooltips on every icon-only control (feature 011, spec FR-011; contracts/pointer-input.md "Coverage"): a semantics
 * walk finds each control with a click action, a description and no visible text inside it on the Timeline, Tuner,
 * Metronome and Projects destinations (disabled ones included), hovers it with the mouse, and expects its name to
 * appear as a tooltip. The failure lists every control that has none; a destination may have none at all (the tuner's
 * controls all show text). (The navigation items' tooltips are `TembraNavigationRailTest`'s.)
 *
 * Android only: on the iOS simulator lane the test's synthetic mouse hover reaches a disabled button's `hoverable` but
 * not an enabled `clickable` (2026-11-03), so the iPad's hover tooltips are checked by hand (quickstart §5); the tooltip
 * itself is `TembraTooltipTest`'s on both lanes, driven by interactions.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipCoverageTest {

    /** A control with a name and no visible text anywhere inside it (the unmerged tree keeps the text nodes apart). */
    private val iconOnly = SemanticsMatcher("an icon-only control") { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null &&
            !node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
            !node.showsText()
    }

    /**
     * Controls that show their name as text on screen but replace their semantics with a fuller description, so the
     * walk sees no text node in them: the snap and tempo pills, the sort menu and the tap-tempo button.
     */
    private val textShownOnScreen = listOf("Snap ", "Tempo ", "Sort by ", "Tap tempo")

    private fun SemanticsNode.showsText(): Boolean =
        !config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() || children.any { it.showsText() }

    private fun ComposeUiTest.assertEveryIconOnlyControlHasATooltip(screen: String, content: @Composable () -> Unit) {
        // Inside a default desktop window (1382 × 864 dp less its caption on Tembra_Desktop_API37), so the transport bar at
        // the foot can be hovered; clear of the window's top so a tooltip has room above a toolbar.
        setContent { TembraTheme { Box(Modifier.requiredSize(1200.dp, 780.dp).padding(top = 60.dp)) { content() } } }
        waitForIdle()
        val controls = onAllNodes(iconOnly, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].first() }
        val missing = mutableListOf<String>()
        controls.forEachIndexed { index, description ->
            if (textShownOnScreen.any { description.startsWith(it) }) return@forEachIndexed
            val node = onAllNodes(iconOnly, useUnmergedTree = true)[index]
            node.performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(1_500)
            if (onAllNodes(hasText(description)).fetchSemanticsNodes().isEmpty()) missing += description
            node.performMouseInput { moveTo(Offset(-50f, -50f)) }
            mainClock.advanceTimeBy(1_500)
        }
        assertTrue(missing.isEmpty(), "$screen: icon-only controls without a tooltip: $missing")
    }

    @Test
    fun timeline() = runComposeUiTest {
        assertEveryIconOnlyControlHasATooltip("Timeline") {
            TimelineScreenContent(
                state = TimelineUiState(
                    loaded = true, timeSignature = TimeSignature.FourFour,
                    tracks = persistentListOf(TrackUi(TrackId("t1"), "Drums", null, 0, GainDb.Unity, false, false, true, persistentListOf())),
                ),
                callbacks = TimelineCallbacks.None,
                telemetry = ScriptedTransportTelemetry.stopped(0.0),
                layout = TimelineLayout.Expanded,
                snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }

    @Test
    fun tuner() = runComposeUiTest {
        assertEveryIconOnlyControlHasATooltip("Tuner") {
            TunerScreenContent(state = TunerUiState(loaded = true), callbacks = TunerCallbacks.None, telemetry = ScriptedTelemetry(), layout = TunerLayout.Wide)
        }
    }

    @Test
    fun metronome() = runComposeUiTest {
        assertEveryIconOnlyControlHasATooltip("Metronome") {
            MetronomeScreenContent(
                state = MetronomeUiState(loaded = true), callbacks = MetronomeCallbacks.None, telemetry = ScriptedBeatTelemetry(),
                layout = MetronomeLayout.Wide, snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }

    @Test
    fun projects() = runComposeUiTest {
        assertEveryIconOnlyControlHasATooltip("Projects") {
            ProjectsScreenContent(
                state = ProjectsUiState(loaded = true), callbacks = ProjectsCallbacks.None,
                layout = ProjectsLayout.ThreeColumns, snackbarHostState = remember { SnackbarHostState() },
            )
        }
    }
}
