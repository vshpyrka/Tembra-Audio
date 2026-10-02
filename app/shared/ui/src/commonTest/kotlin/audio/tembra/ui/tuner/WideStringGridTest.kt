package audio.tembra.ui.tuner

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** The wide By Ear grid's columns and card size from the space it is given (feature 005). */
class WideStringGridTest {

    private fun arrange(count: Int, base: Int, width: Int, height: Int) =
        WideStringGrid.arrange(count, base, full = 180.dp, gap = 20.dp, widthBudget = width.dp, heightBudget = height.dp, min = 128.dp)

    @Test
    fun aTallWindowKeepsTheUsualColumnsAtFullSize() {
        assertEquals(WideStringGrid(3, 180.dp), arrange(7, 3, width = 800, height = 600))
        assertEquals(WideStringGrid(3, 180.dp), arrange(6, 3, width = 800, height = 600))
        assertEquals(WideStringGrid(2, 180.dp), arrange(4, 2, width = 800, height = 600))
    }

    @Test
    fun aSlightlyShortWindowShrinksTheCardsInTheUsualColumns() {
        assertEquals(WideStringGrid(3, (500f / 3f).dp), arrange(7, 3, width = 800, height = 540))
    }

    @Test
    fun aShortWindowSpreadsSevenStringsOverFourColumns() {
        // Three rows would leave (380 - 40) / 3 = 113 dp cards; four columns in two rows give 180 by height, 170 by width.
        assertEquals(WideStringGrid(4, 170.dp), arrange(7, 3, width = 740, height = 380))
    }

    @Test
    fun theCelloKeepsTwoByTwoWhenItStillFits() {
        assertEquals(WideStringGrid(2, 170.dp), arrange(4, 2, width = 740, height = 360))
    }

    @Test
    fun cardsNeverShrinkUnderTheMinimum() {
        assertEquals(WideStringGrid(7, 128.dp), arrange(7, 3, width = 700, height = 120))
    }
}
