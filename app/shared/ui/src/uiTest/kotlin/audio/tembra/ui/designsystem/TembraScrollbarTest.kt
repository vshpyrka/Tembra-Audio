package audio.tembra.ui.designsystem

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import audio.tembra.ui.theme.TembraTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Scrollbars for pointer scrolling (feature 011, spec FR-014a, research R18): a wheel scroll over a list shows the
 * scrollbar, which fades a second after scrolling stops; a touch scroll does not show it; dragging the thumb with the
 * mouse scrolls the content.
 */
@OptIn(ExperimentalTestApi::class)
class TembraScrollbarTest {

    private val listState = LazyListState()
    private var pxPerDp = 1f
    private val shown = SemanticsMatcher.expectValue(ScrollbarShown, true)
    private val hidden = SemanticsMatcher.expectValue(ScrollbarShown, false)

    private fun ComposeUiTest.setList() {
        mainClock.autoAdvance = false
        setContent {
            TembraTheme {
                pxPerDp = LocalDensity.current.density
                LazyColumn(
                    state = listState,
                    modifier = Modifier.size(300.dp, 400.dp).testTag("list")
                        .tembraScrollbar(remember { ScrollbarAdapter(listState) }, Orientation.Vertical),
                ) {
                    items(100) { index -> Text("Row $index", Modifier.fillMaxWidth().height(48.dp)) }
                }
            }
        }
        mainClock.advanceTimeByFrame()
    }

    @Test
    fun aWheelScrollShowsTheScrollbarWhichFadesAfterASecond() = runComposeUiTest {
        setList()
        onNodeWithTag("list").assert(hidden)
        onNodeWithTag("list").performMouseInput { moveTo(center); scroll(3f) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("list").assert(shown)
        mainClock.advanceTimeBy(800)
        onNodeWithTag("list").assert(shown)
        mainClock.advanceTimeBy(1_500)
        onNodeWithTag("list").assert(hidden)
    }

    @Test
    fun aTouchScrollShowsNoScrollbar() = runComposeUiTest {
        setList()
        onNodeWithTag("list").performTouchInput { swipeUp() }
        repeat(10) { mainClock.advanceTimeByFrame() }
        assertTrue(listState.firstVisibleItemIndex > 0, "the swipe did not scroll")
        onNodeWithTag("list").assert(hidden)
    }

    @Test
    fun draggingTheThumbWithTheMouseScrollsTheContent() = runComposeUiTest {
        setList()
        onNodeWithTag("list").performMouseInput { moveTo(center); scroll(1f) }
        mainClock.advanceTimeBy(100)
        val before = listState.firstVisibleItemIndex
        // The thumb runs down the trailing edge: 2 dp in from the edge, 6 dp thick, starting near the top.
        val x = (300f - 5f) * pxPerDp
        val y = 20f * pxPerDp
        onNodeWithTag("list").performMouseInput {
            moveTo(Offset(x, y))
            press()
            moveTo(Offset(x, y + 150f * pxPerDp))
            release()
        }
        mainClock.advanceTimeBy(100)
        assertTrue(listState.firstVisibleItemIndex > before + 10, "dragging the thumb did not scroll (${listState.firstVisibleItemIndex} from $before)")
    }
}
