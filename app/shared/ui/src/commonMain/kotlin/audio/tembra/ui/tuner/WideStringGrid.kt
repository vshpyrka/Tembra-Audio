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
         * [widthBudget] and [heightBudget] and never over [full], keep [min]; otherwise the column count, fewer or more,
         * whose cards are largest (seven strings as 4 + 3 in a short window, as 2 per row in a narrow pane beside a
         * hinge), raised to [min] if even those are smaller, and then the grid is taller than the budget and its parent
         * scrolls.
         */
        fun arrange(count: Int, baseColumns: Int, full: Dp, gap: Dp, widthBudget: Dp, heightBudget: Dp, min: Dp): WideStringGrid {
            fun cardFor(columns: Int): Dp {
                val rows = (count + columns - 1) / columns
                val byWidth = (widthBudget - gap * (columns - 1)) / columns
                val byHeight = (heightBudget - gap * (rows - 1)) / rows
                return min(full, min(byWidth, byHeight))
            }
            val base = cardFor(baseColumns)
            if (base >= min) return WideStringGrid(baseColumns, base)
            var largest = WideStringGrid(baseColumns, base)
            for (columns in 1..maxOf(1, count)) {
                val card = cardFor(columns)
                if (card > largest.card) largest = WideStringGrid(columns, card)
            }
            return largest.copy(card = largest.card.coerceAtLeast(min))
        }
    }
}
