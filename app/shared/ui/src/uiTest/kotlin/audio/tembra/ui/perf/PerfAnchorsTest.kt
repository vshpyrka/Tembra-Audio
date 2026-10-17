package audio.tembra.ui.perf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.tuner.TunerMode
import audio.tembra.ui.designsystem.NavigationItemSpec
import audio.tembra.ui.designsystem.PlaceholderDestination
import audio.tembra.ui.designsystem.TembraNavigationBar
import audio.tembra.ui.metronome.MetronomeCallbacks
import audio.tembra.ui.metronome.MetronomeLayout
import audio.tembra.ui.metronome.MetronomeScreenContent
import audio.tembra.ui.metronome.MetronomeUiState
import audio.tembra.ui.metronome.ScriptedBeatTelemetry
import audio.tembra.ui.navigation.Destination
import audio.tembra.ui.platform.MicrophonePermissionStatus
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.tuner.ScriptedTelemetry
import audio.tembra.ui.tuner.TunerCallbacks
import audio.tembra.ui.tuner.TunerLayout
import audio.tembra.ui.tuner.TunerScreenContent
import audio.tembra.ui.tuner.TunerUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every journey anchor in [PerfAnchors] is a `testTag` on the plain content composables (spec FR-017), reachable by
 * UiAutomator through `testTagsAsResourceId` and by XCUITest as an accessibility identifier, and the tagged controls
 * drive the callbacks the benchmark journeys rely on.
 */
@OptIn(ExperimentalTestApi::class)
class PerfAnchorsTest {

    private val navItems = Destination.entries.map { NavigationItemSpec(it.id, it.label, it.icon) }

    private fun ComposeUiTest.settle() = repeat(3) { mainClock.advanceTimeByFrame() }

    @Test
    fun startRootAndNavigationItemsAreTagged() = runComposeUiTest {
        var selected: String? = null
        setContent {
            TembraTheme {
                PlaceholderDestination(Destination.Timeline)
                TembraNavigationBar(items = navItems, selectedId = Destination.Timeline.id, onSelect = { selected = it })
            }
        }
        onNodeWithTag(PerfAnchors.START_ROOT).assertExists()
        onNodeWithTag(PerfAnchors.NAV_TUNER).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        onNodeWithTag(PerfAnchors.NAV_METRONOME).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        onNodeWithTag(PerfAnchors.NAV_TUNER).performClick()
        assertEquals(Destination.Tuner.id, selected)
        onNodeWithTag(PerfAnchors.NAV_METRONOME).performClick()
        assertEquals(Destination.Metronome.id, selected)
    }

    @Test
    fun tunerAnchorsAreTaggedAndTheModeSwitchReachesItsCallback() = runComposeUiTest {
        var mode: TunerMode? = null
        val callbacks = TunerCallbacks.None.copy(onMode = { mode = it })
        val state = TunerUiState(loaded = true, microphone = MicrophonePermissionStatus.Granted, mode = TunerMode.AutoListen)
        setContent {
            TembraTheme {
                TunerScreenContent(state = state, callbacks = callbacks, telemetry = ScriptedTelemetry(), layout = TunerLayout.Stacked)
            }
        }
        onNodeWithTag(PerfAnchors.TUNER_ROOT).assertExists()
        onNodeWithTag(PerfAnchors.TUNER_NOTE_READOUT).assertExists()
        onNodeWithTag(PerfAnchors.TUNER_MODE_AUTO_LISTEN).assertIsSelected()
        onNodeWithTag(PerfAnchors.TUNER_MODE_BY_EAR).performClick()
        assertEquals(TunerMode.ByEar, mode)
    }

    @Test
    fun metronomeAnchorsAreTaggedAndReachTheirCallbacks() = runComposeUiTest {
        val steps = mutableListOf<Int>()
        var toggles = 0
        val callbacks = MetronomeCallbacks.None.copy(onStep = { steps += it }, onTogglePlayback = { toggles++ })
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                Box(Modifier.width(360.dp).fillMaxHeight()) {
                    MetronomeScreenContent(
                        state = MetronomeUiState(loaded = true),
                        callbacks = callbacks,
                        telemetry = ScriptedBeatTelemetry(),
                        layout = MetronomeLayout.Stacked,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
        settle()
        onNodeWithTag(PerfAnchors.METRONOME_ROOT).assertExists()
        onNodeWithTag(PerfAnchors.METRONOME_TEMPO_READOUT).assertExists()
        onNodeWithTag(PerfAnchors.METRONOME_START_STOP).assertHasClickAction()
        onNodeWithTag(PerfAnchors.METRONOME_TEMPO_UP).performClick()
        settle()
        onNodeWithTag(PerfAnchors.METRONOME_TEMPO_DOWN).performClick()
        settle()
        onNodeWithTag(PerfAnchors.METRONOME_START_STOP).performClick()
        settle()
        assertEquals(listOf(1, -1), steps)
        assertEquals(1, toggles)
    }
}
