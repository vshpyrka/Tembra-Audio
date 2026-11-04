package audio.tembra.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.InstrumentLabel
import audio.tembra.core.project.TrackId
import audio.tembra.ui.projects.components.KeyPickerDialog
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ScriptedTransportTelemetry
import audio.tembra.ui.timeline.components.SnapSheet
import audio.tembra.ui.tracksettings.TrackSettingsCallbacks
import audio.tembra.ui.tracksettings.TrackSettingsScreenContent
import audio.tembra.ui.tracksettings.TrackSettingsUiState
import audio.tembra.ui.transport.TransportCallbacks
import audio.tembra.ui.transport.TransportUiState
import kotlin.test.Test

/**
 * Every scrolling surface shows the scrollbar while a wheel scrolls it (feature 011, spec FR-014a; desktop quality
 * guideline T-Scrollbar_Display, found missing on these four in the T125 self-check): the project key picker, the snap
 * sheet, the compact track settings and the navigation rail in a window too short for its items.
 */
@OptIn(ExperimentalTestApi::class)
class ScrollbarCoverageTest {

    private val scrollbar = SemanticsMatcher.keyIsDefined(ScrollbarShown)
    private val shown = SemanticsMatcher.expectValue(ScrollbarShown, true)

    private fun ComposeUiTest.wheelShowsTheScrollbar(content: @Composable () -> Unit) {
        setContent { TembraTheme { content() } }
        waitForIdle()
        mainClock.autoAdvance = false
        onNode(scrollbar).performMouseInput { moveTo(center); scroll(2f) }
        mainClock.advanceTimeBy(100)
        onNode(scrollbar).assert(shown)
    }

    @Test
    fun theKeyPicker() = runComposeUiTest {
        wheelShowsTheScrollbar { KeyPickerDialog(selected = null, onConfirm = {}, onDismiss = {}) }
    }

    @Test
    fun theSnapSheet() = runComposeUiTest {
        wheelShowsTheScrollbar { SnapSheet(grid = GridSize.Bar, onGrid = {}, onDismiss = {}) }
    }

    @Test
    fun theTrackSettings() = runComposeUiTest {
        val state = TrackSettingsUiState(
            trackId = TrackId("t1"), name = "Drums", label = InstrumentLabel.Drums, colorIndex = 0, gainDb = GainDb.Unity,
            muted = false, soloed = false, transport = TransportUiState(lengthSeconds = 30.0, hasProject = true),
        )
        wheelShowsTheScrollbar {
            TrackSettingsScreenContent(
                state = state,
                callbacks = TrackSettingsCallbacks(
                    onName = {}, onNameCommit = {}, onLabel = {}, onGain = {}, onMute = {}, onSolo = {},
                    onDelete = {}, onConfirmDelete = {}, onDismissDelete = {},
                ),
                transportCallbacks = TransportCallbacks.None,
                telemetry = ScriptedTransportTelemetry.stopped(0.0),
                onBack = {},
            )
        }
    }

    @Test
    fun theNavigationRailInAShortWindow() = runComposeUiTest {
        val items = listOf(
            NavigationItemSpec("timeline", "Timeline", TembraIcon.Timeline),
            NavigationItemSpec("tuner", "Tuner", TembraIcon.Tuner),
            NavigationItemSpec("metronome", "Metronome", TembraIcon.Metronome),
            NavigationItemSpec("projects", "Projects", TembraIcon.Projects),
            NavigationItemSpec("profile", "Profile", TembraIcon.Profile),
        )
        wheelShowsTheScrollbar {
            Box(Modifier.height(200.dp)) { TembraNavigationRail(items = items, selectedId = "timeline", onSelect = {}) }
        }
    }
}
