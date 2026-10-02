package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import audio.tembra.core.tuner.TargetString
import audio.tembra.ui.theme.TembraTheme

/**
 * At most seven cards, lowest string first (spec FR-017, FR-023): two columns when stacked; three when wide, or two
 * when that fills every row (the cello), the grid narrowing so the cards keep their size, a short last row centred,
 * and with a [heightBudget] and a [widthBudget] the square cards shrinking, or spreading over more columns, until the
 * grid fits them ([WideStringGrid], feature 005); in phone landscape and above a tabletop fold as many as fill two rows. A specified [rowMinHeight] (landscape) makes every row at least that tall
 * and its cards equally tall, so the rows fill the card.
 */
@Composable
internal fun StringCardGrid(
    strings: List<TargetString>,
    playingIndex: Int?,
    layout: TunerLayout,
    onAudition: (Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    rowMinHeight: Dp = Dp.Unspecified,
    heightBudget: Dp = Dp.Unspecified,
    widthBudget: Dp = Dp.Unspecified,
) {
    val spacing = TembraTheme.spacing
    val gap = when (layout) {
        TunerLayout.Stacked, TunerLayout.Landscape, TunerLayout.Tabletop -> spacing.sm
        TunerLayout.Wide -> spacing.lg
    }
    // Wide: three columns, or two when that fills every row and three would not (the cello's four strings: 2 + 2),
    // each card the size three columns across `byEarGridMaxWidthWide` give it; with budgets the cards shrink, or spread
    // over more columns, to fit them (WideStringGrid).
    val wideBase = if (strings.size % WIDE_COLUMNS != 0 && strings.size % 2 == 0) 2 else WIDE_COLUMNS
    val wideFull = (spacing.byEarGridMaxWidthWide - gap * (WIDE_COLUMNS - 1)) / WIDE_COLUMNS
    val wide = if (heightBudget.isSpecified && widthBudget.isSpecified) {
        WideStringGrid.arrange(strings.size, wideBase, wideFull, gap, widthBudget, heightBudget, spacing.stringCardMinWide)
    } else {
        WideStringGrid(wideBase, wideFull)
    }
    val columns = when (layout) {
        TunerLayout.Stacked -> 2
        TunerLayout.Wide -> wide.columns
        TunerLayout.Landscape, TunerLayout.Tabletop -> ((strings.size + 1) / 2).coerceAtLeast(1)
    }
    val maxWidth = when (layout) {
        TunerLayout.Stacked -> spacing.byEarGridMaxWidth
        TunerLayout.Wide -> wide.card * columns + gap * (columns - 1)
        TunerLayout.Landscape, TunerLayout.Tabletop -> Dp.Unspecified
    }
    val rowModifier = if (rowMinHeight.isSpecified) Modifier.heightIn(min = rowMinHeight).height(IntrinsicSize.Max) else Modifier
    Column(
        // The cap comes before the caller's modifier, so a caller's fillMaxWidth fills up to the cap, not past it.
        modifier = Modifier.widthIn(max = maxWidth).then(modifier),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (row in strings.indices.chunked(columns)) {
            // Wide: every card exactly the arranged size and a short last row centred; otherwise the cards share the
            // row and empty slots stay empty.
            val wideRow = layout == TunerLayout.Wide
            val arrangement = if (wideRow) Arrangement.spacedBy(gap, Alignment.CenterHorizontally) else Arrangement.spacedBy(gap)
            Row(modifier = Modifier.fillMaxWidth().then(rowModifier), horizontalArrangement = arrangement) {
                for (index in row) {
                    StringCard(
                        string = strings[index],
                        playing = playingIndex == index,
                        layout = layout,
                        onAudition = { onAudition(index) },
                        onStop = onStop,
                        modifier = if (wideRow) Modifier.width(wide.card) else Modifier.weight(1f),
                    )
                }
                if (!wideRow) repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

private const val WIDE_COLUMNS = 3
