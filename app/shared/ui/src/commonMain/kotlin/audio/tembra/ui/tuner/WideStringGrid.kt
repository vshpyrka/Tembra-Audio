package audio.tembra.ui.tuner

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min

/**
 * How the wide By Ear grid lays out [count] square cards (feature 005): [columns] per row, each card [card] wide and
 * tall. [arrange] works it out from the budgets the card leaves the grid.
 */
internal data class WideStringGrid(val columns: Int, val card: Dp) {

    companion object {
        /**
         * The usual [baseColumns] (three, or two for the cello) whenever their square cards, shrunk to fit
         * [widthBudget] and [heightBudget] and never over [full], keep [min]; otherwise the first larger column count
         * whose cards keep [min] (seven strings in a short window as 4 + 3); if none does, the count with the largest
         * cards, raised to [min], and the grid is taller than the budget so its parent scrolls.
         */
        fun arrange(count: Int, baseColumns: Int, full: Dp, gap: Dp, widthBudget: Dp, heightBudget: Dp, min: Dp): WideStringGrid {
            var largest = WideStringGrid(baseColumns, Dp.Hairline)
            for (columns in baseColumns..maxOf(baseColumns, count)) {
                val rows = (count + columns - 1) / columns
                val byWidth = (widthBudget - gap * (columns - 1)) / columns
                val byHeight = (heightBudget - gap * (rows - 1)) / rows
                val card = min(full, min(byWidth, byHeight))
                if (card >= min) return WideStringGrid(columns, card)
                if (card > largest.card) largest = WideStringGrid(columns, card)
            }
            return largest.copy(card = min)
        }
    }
}
