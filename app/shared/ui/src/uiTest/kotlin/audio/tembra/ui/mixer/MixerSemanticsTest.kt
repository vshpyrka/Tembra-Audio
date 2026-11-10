package audio.tembra.ui.mixer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GainDb
import audio.tembra.core.project.Pan
import audio.tembra.core.project.TrackId
import audio.tembra.ui.designsystem.enterKeyboardMode
import audio.tembra.ui.navigation.Destination
import audio.tembra.ui.session.InputTelemetry
import audio.tembra.ui.shortcuts.LocalOverlayStack
import audio.tembra.ui.shortcuts.LocalShortcutPlatform
import audio.tembra.ui.shortcuts.OverlayStack
import audio.tembra.ui.shortcuts.SHORTCUT_LIST_TAG
import audio.tembra.ui.shortcuts.ShellShortcuts
import audio.tembra.ui.shortcuts.ShortcutCopy
import audio.tembra.ui.shortcuts.ShortcutScope
import audio.tembra.ui.shortcuts.ShortcutSheet
import audio.tembra.ui.shortcuts.TextInputFocus
import audio.tembra.ui.shortcuts.shellKeyHandling
import audio.tembra.ui.shortcuts.shortcutGroupTag
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCallbacks
import audio.tembra.ui.timeline.TimelineLayout
import audio.tembra.ui.transport.TransportCopy
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mixer for a screen reader and a keyboard (feature 012, spec FR-028 to FR-030; contracts/mixer-ui.md "Keyboard" and
 * "Semantics and anchors"; T083): every fader and knob names its track and reads its value, the master reads its percent,
 * the meters read "Level" and the clip square its state; Tab walks the handle, each strip's controls top to bottom and
 * the master's fader, never the number or name labels; Escape on a focused strip closes the sheet through 011's overlay
 * stack and the shell's key map; the shortcut sheet lists the Mixer group after the Timeline's.
 */
@OptIn(ExperimentalTestApi::class)
class MixerSemanticsTest {

    private val calls = mutableListOf<String>()
    private val stack = OverlayStack()
    private val ui = mutableStateOf(MixerUi())
    private var manager: InputModeManager? = null

    private val callbacks = TimelineCallbacks.None.copy(
        onMixerToggle = {
            calls += "toggle"
            ui.value = ui.value.copy(open = !ui.value.open)
        },
    )

    /** The shell as the Timeline's root hosts it: Escape's close-overlay action closes the newest overlay. */
    private val shell = object : ShellShortcuts {
        override fun open(destination: Destination): Boolean = false
        override fun showShortcuts(): Boolean = false
        override fun closeOverlay(): Boolean = stack.closeTop()
    }

    private val strips = persistentListOf(
        StripUi(id = TrackId("t1"), index = 0, number = 1, name = "Drums", colorIndex = 0, gainDb = GainDb(-3f), pan = Pan(-35), muted = false, soloed = false, audible = true),
        StripUi(id = TrackId("t2"), index = 1, number = 2, name = "Bass", colorIndex = 1, gainDb = GainDb.Unity, pan = Pan.Centre, muted = false, soloed = false, audible = true),
    )

    /** The full sheet (the knob and the dial shown) over 600 dp of lanes in a 360 dp window, under the shell's key map. */
    private fun ComposeUiTest.setSheet() {
        val anchors = MixerAnchors.of(TimelineLayout.Compact, LANES)
        ui.value = MixerUi(open = true, height = MixerHeight.Full, form = StripForm.of(MixerHeight.Full, LANES.value), anchors = anchors, strips = strips)
        setContent {
            TembraTheme {
                CompositionLocalProvider(LocalOverlayStack provides stack) {
                    manager = LocalInputModeManager.current
                    Box(Modifier.requiredWidth(360.dp).requiredHeight(LANES).shellKeyHandling(shell, TextInputFocus(), LocalShortcutPlatform.current)) {
                        MixerSheet(ui = ui.value, callbacks = callbacks, telemetry = ScriptedMixTelemetry.silent(strips.size), inputTelemetry = InputTelemetry { _, _ -> }, lanesHeight = LANES)
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun state(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun everyControlNamesItsTrackAndReadsItsState() = runComposeUiTest {
        setSheet()
        onNodeWithTag(MixerTestTags.fader(0)).assertContentDescriptionEquals(MixerCopy.gainDescription("Drums")).assert(state(GainDb(-3f).readout))
        onNodeWithTag(MixerTestTags.fader(1)).assertContentDescriptionEquals(MixerCopy.gainDescription("Bass")).assert(state(GainDb.Unity.readout))
        onNodeWithTag(MixerTestTags.pan(0)).assertContentDescriptionEquals(MixerCopy.panDescription("Drums")).assert(state("L 35"))
        onNodeWithTag(MixerTestTags.pan(1)).assertContentDescriptionEquals(MixerCopy.panDescription("Bass")).assert(state(Pan.Centre.readout))
        onNodeWithTag(MixerTestTags.MASTER_FADER).assertContentDescriptionEquals(MixerCopy.masterVolume).assert(state(TransportCopy.masterVolumeState(MasterUi.DEFAULT_PERCENT)))
        onNodeWithTag(MixerTestTags.meter(0)).assertContentDescriptionEquals(MixerCopy.level)
        onNodeWithTag(MixerTestTags.clip(0)).assertContentDescriptionEquals(MixerCopy.clipIndicator)
        onNodeWithTag(MixerTestTags.vu(0)).assertContentDescriptionEquals(MixerCopy.vu)
        onNodeWithTag(MixerTestTags.MASTER_METER).assertContentDescriptionEquals(MixerCopy.level)
        onNodeWithTag(MixerTestTags.solo(0)).assertContentDescriptionEquals(MixerCopy.solo)
        onNodeWithTag(MixerTestTags.mute(0)).assertContentDescriptionEquals(MixerCopy.mute)
    }

    /** T092's desktop finding: a sheet opened under a keyboard puts the focus on the handle, so Tab walks the strips next. */
    @Test
    fun openingTheSheetUnderAKeyboardFocusesTheHandle() = runComposeUiTest {
        setSheet()
        if (!enterKeyboardMode(manager)) return@runComposeUiTest
        ui.value = ui.value.copy(open = false)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(MixerTestTags.HANDLE).fetchSemanticsNodes().isEmpty() }
        ui.value = ui.value.copy(open = true)
        waitForIdle()
        onNodeWithTag(MixerTestTags.HANDLE).assertIsFocused()
    }

    /** Contracts/mixer-ui.md "Keyboard": the handle, then each strip's controls top to bottom (the knob above the toggles in the full form), then the master's fader; the labels are not stops. */
    @Test
    fun tabWalksTheHandleEachStripsControlsThenTheMaster() = runComposeUiTest {
        setSheet()
        if (!enterKeyboardMode(manager)) return@runComposeUiTest
        onNodeWithTag(MixerTestTags.HANDLE).requestFocus()
        onNodeWithTag(MixerTestTags.HANDLE).assertIsFocused()
        val order = listOf(
            MixerTestTags.pan(0), MixerTestTags.solo(0), MixerTestTags.mute(0), MixerTestTags.fader(0),
            MixerTestTags.pan(1), MixerTestTags.solo(1), MixerTestTags.mute(1), MixerTestTags.fader(1),
            MixerTestTags.MASTER_FADER,
        )
        var focused = MixerTestTags.HANDLE
        for (next in order) {
            onNodeWithTag(focused).performKeyInput { pressKey(Key.Tab) }
            onNodeWithTag(next).assertIsFocused()
            focused = next
        }
    }

    @Test
    fun escapeOnAFocusedStripClosesTheSheetThroughTheOverlayStack() = runComposeUiTest {
        setSheet()
        if (!enterKeyboardMode(manager)) return@runComposeUiTest
        onNodeWithTag(MixerTestTags.fader(0)).requestFocus()
        onNodeWithTag(MixerTestTags.fader(0)).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(listOf("toggle"), calls)
        onNodeWithTag(MixerTestTags.SHEET).assertDoesNotExist()
    }

    /** Spec FR-029: the shortcut sheet lists the Mixer group, after the Timeline's, with the focused strip's keys. */
    @Test
    fun theShortcutSheetListsTheMixerGroupAfterTheTimelines() = runComposeUiTest {
        setContent { TembraTheme { ShortcutSheet(onDismiss = {}) } }
        assertEquals(ShortcutScope.Timeline.ordinal + 1, ShortcutScope.Mixer.ordinal)
        onNodeWithTag(SHORTCUT_LIST_TAG).performScrollToIndex(ShortcutScope.Mixer.ordinal)
        onNodeWithTag(shortcutGroupTag(ShortcutScope.Mixer)).assertExists()
        onNodeWithText(ShortcutCopy.scopeTitle(ShortcutScope.Mixer)).assertExists()
        onNodeWithText(ShortcutCopy.mixerFaderUp).assertExists()
        onNodeWithText(ShortcutCopy.mixerMute).assertExists()
        onNodeWithText(ShortcutCopy.mixerHome).assertExists()
    }

    private companion object {
        val LANES = 600.dp
    }
}
