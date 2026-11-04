package audio.tembra.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Timeline's "Full screen" menu item (feature 011, spec FR-010a, research R21): "Full screen" while the window can
 * go full screen and is not, "Exit full screen" while it is, neither where the platform offers no app-requested full
 * screen (iPad, phones, a full-screen activity outside desktop windowing); choosing them asks for the change.
 */
@OptIn(ExperimentalTestApi::class)
class FullScreenMenuItemTest {

    private val requests = mutableListOf<Boolean>()

    private fun ComposeUiTest.openOverflow(item: FullScreenItem) {
        setContent {
            TembraTheme {
                Box(Modifier.requiredSize(1200.dp, 800.dp)) {
                    TimelineScreenContent(
                        state = TimelineUiState(loaded = true, fullScreenItem = item),
                        callbacks = TimelineCallbacks.None.copy(onFullScreen = { requests += it }),
                        telemetry = ScriptedTransportTelemetry.stopped(0.0),
                        layout = TimelineLayout.Expanded,
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
        onNodeWithTag(TimelineTestTags.OVERFLOW_BUTTON).performClick()
        waitForIdle()
    }

    @Test
    fun aWindowOffersFullScreen() = runComposeUiTest {
        openOverflow(FullScreenItem.Enter)
        onNodeWithText("Exit full screen").assertDoesNotExist()
        onNodeWithTag(TimelineTestTags.OVERFLOW_FULL_SCREEN).performClick()
        assertEquals(listOf(true), requests)
    }

    @Test
    fun fullScreenOffersTheWayBack() = runComposeUiTest {
        openOverflow(FullScreenItem.Exit)
        onNodeWithText("Exit full screen").performClick()
        assertEquals(listOf(false), requests)
    }

    @Test
    fun noItemWhereThePlatformOffersNone() = runComposeUiTest {
        openOverflow(FullScreenItem.Hidden)
        onNodeWithText("Full screen").assertDoesNotExist()
        onNodeWithText("Exit full screen").assertDoesNotExist()
    }
}
