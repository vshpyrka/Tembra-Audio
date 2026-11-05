package audio.tembra.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.TrackId
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.ClipUi
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

/**
 * Keyboard focus keeps its place across window changes (feature 011, spec FR-007; contracts/window-behaviour.md): a
 * focused track header and a focused tuner control are focused again, by their semantic identity, after the layout class
 * changes and their composables are rebuilt; when the control does not exist in the new layout, focus falls back to the
 * destination's root. Runs where the lane declares keyboard input mode.
 */
@OptIn(ExperimentalTestApi::class)
class FocusAcrossLayoutsTest {

    private var manager: InputModeManager? = null
    private val clip = ClipUi(ClipId("c1"), AudioAssetId("a".repeat(64)), "drums", Beats(0.0), Beats(8.0), 0, 48000, 48000, null, OverviewStatus.Ready, true)
    private val tracks = persistentListOf(
        TrackUi(TrackId("t1"), "Drums", null, 0, GainDb.Unity, false, false, true, persistentListOf(clip)),
        TrackUi(TrackId("t2"), "Bass", null, 1, GainDb.Unity, false, false, true, persistentListOf()),
    )

    @Test
    fun aFocusedTrackHeaderKeepsFocusAcrossTheExpandedLineAndFallsBackToTheRootWhenItGoes() = runComposeUiTest {
        var width by mutableStateOf(1200.dp)
        var shown by mutableStateOf(tracks)
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                Box(Modifier.requiredSize(width, 800.dp)) {
                    TimelineScreenContent(
                        state = TimelineUiState(loaded = true, tracks = shown, timeSignature = TimeSignature.FourFour),
                        callbacks = TimelineCallbacks.None,
                        telemetry = ScriptedTransportTelemetry.stopped(0.0),
                        layout = TimelineLayout.from(width, 800.dp, LayoutMode.Rail),
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
        if (!enterKeyboardMode(manager)) return@runComposeUiTest
        onNodeWithTag("timeline.header.1").requestFocus()
        onNodeWithTag("timeline.header.1").assertIsFocused()
        for (step in listOf(700.dp, 1200.dp)) {
            width = step
            waitForIdle()
            onNodeWithTag("timeline.header.1").assertIsFocused()
        }
        // The focused track is deleted: its header no longer exists, so the destination's root takes focus.
        shown = persistentListOf(tracks.first())
        waitForIdle()
        onNodeWithTag("screen.timeline").assertIsFocused()
    }

    @Test
    fun aFocusedTunerControlKeepsFocusAcrossEveryForm() = runComposeUiTest {
        var layout by mutableStateOf(TunerLayout.Wide)
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                Box(Modifier.requiredSize(1000.dp, 800.dp)) {
                    TunerScreenContent(state = TunerUiState(loaded = true), callbacks = TunerCallbacks.None, telemetry = ScriptedTelemetry(), layout = layout)
                }
            }
        }
        if (!enterKeyboardMode(manager)) return@runComposeUiTest
        onNodeWithTag("tuner.mode.byEar").requestFocus()
        layout = TunerLayout.Landscape
        waitForIdle()
        onNodeWithTag("tuner.mode.byEar").assertIsFocused()
        layout = TunerLayout.Stacked
        waitForIdle()
        onNodeWithTag("tuner.mode.byEar").assertIsFocused()
        onNodeWithTag("tuner.instrument.guitar").requestFocus()
        layout = TunerLayout.Wide
        waitForIdle()
        onNodeWithTag("tuner.instrument.guitar").assertIsFocused()
    }
}
