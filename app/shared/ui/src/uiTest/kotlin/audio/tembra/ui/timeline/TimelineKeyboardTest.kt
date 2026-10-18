package audio.tembra.ui.timeline

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.metronome.TimeSignature
import audio.tembra.core.project.AudioAssetId
import audio.tembra.core.project.Beats
import audio.tembra.core.project.ClipId
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.GridSize
import audio.tembra.core.project.TrackId
import audio.tembra.data.projects.OverviewStatus
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.theme.TembraTheme
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The keyboard map of spec FR-057 (T122): every shortcut through real key input on a focused root. Keyboard tests run
 * on both lanes; a lane that cannot enter keyboard mode fails loudly rather than returning silently.
 */
@OptIn(ExperimentalTestApi::class)
class TimelineKeyboardTest {

    private val calls = mutableListOf<String>()
    private var manager: InputModeManager? = null

    private val clip = ClipUi(ClipId("c1"), AudioAssetId("a".repeat(64)), "drums", Beats(4.0), Beats(8.0), 0, 48000, 48000, null, OverviewStatus.Ready, true)
    private val track = TrackUi(TrackId("t1"), "Track 1", null, 0, GainDb.Unity, false, false, true, persistentListOf(clip))
    private var state = TimelineUiState(
        loaded = true, tracks = persistentListOf(track), timeSignature = TimeSignature.FourFour, grid = GridSize.Eighth,
        positionBeats = Beats(8.0), selection = Selection.ClipSelected(track.id, clip.id), canSplit = true,
    )
    private val callbacks = TimelineCallbacks.None.copy(
        onPlayPause = { calls += "playPause" }, onSkipToStart = { calls += "skipToStart" }, onSeek = { calls += "seek(${it.value})" },
        onSplit = { calls += "split" }, onDeleteClip = { calls += "delete(${it.value})" }, onUndo = { calls += "undo" }, onRedo = { calls += "redo" },
        onZoomStep = { calls += "zoom($it)" }, onToggleMute = { calls += "mute(${it.value})" }, onToggleSolo = { calls += "solo(${it.value})" },
    )

    private fun ComposeUiTest.setRoot() {
        setContent {
            TembraTheme {
                manager = LocalInputModeManager.current
                Box(Modifier.size(200.dp).testTag("root").focusable().timelineKeyHandling({ state }, callbacks))
            }
        }
        assertTrue(enterKeyboardMode(manager), "this lane cannot enter keyboard mode; keyboard tests must run here")
        onNodeWithTag("root").requestFocus()
    }

    private fun ComposeUiTest.press(key: Key, shift: Boolean = false, ctrl: Boolean = false) {
        onNodeWithTag("root").performKeyInput {
            if (shift) keyDown(Key.ShiftLeft)
            if (ctrl) keyDown(Key.CtrlLeft)
            pressKey(key)
            if (ctrl) keyUp(Key.CtrlLeft)
            if (shift) keyUp(Key.ShiftLeft)
        }
    }

    @Test
    fun transportAndSeekKeys() = runComposeUiTest {
        setRoot()
        press(Key.Spacebar)
        press(Key.MoveHome)
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        press(Key.DirectionRight, shift = true)
        assertEquals(listOf("playPause", "skipToStart", "seek(8.5)", "seek(7.5)", "seek(12.0)"), calls)
    }

    @Test
    fun editingKeys() = runComposeUiTest {
        setRoot()
        press(Key.S)
        press(Key.Delete)
        press(Key.Backspace)
        press(Key.Z, ctrl = true)
        press(Key.Z, ctrl = true, shift = true)
        press(Key.Y, ctrl = true)
        assertEquals(listOf("split", "delete(c1)", "delete(c1)", "undo", "redo", "redo"), calls)
    }

    @Test
    fun zoomAndTrackKeys() = runComposeUiTest {
        setRoot()
        press(Key.Plus)
        press(Key.Minus)
        press(Key.M)
        press(Key.O)
        assertEquals(listOf("zoom(1)", "zoom(-1)", "mute(t1)", "solo(t1)"), calls)
    }

    @Test
    fun splitIsIgnoredWhenNotPossibleAndArrowsUseABarWhenTheGridIsOff() = runComposeUiTest {
        state = state.copy(canSplit = false, grid = GridSize.Off)
        setRoot()
        press(Key.S)
        press(Key.DirectionRight)
        assertEquals(listOf("seek(12.0)"), calls)
    }
}
