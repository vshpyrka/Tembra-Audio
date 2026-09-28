package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import audio.tembra.core.tuner.TargetString
import audio.tembra.ui.theme.TembraTheme

/**
 * At most seven cards, lowest string first (spec FR-017, FR-023): two columns when stacked, three when wide, and in
 * phone landscape as many as fill two rows. A specified [rowMinHeight] (landscape) makes every row at least that tall
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
) {
    val spacing = TembraTheme.spacing
    val columns = when (layout) {
        TunerLayout.Stacked -> 2
        TunerLayout.Wide -> 3
        TunerLayout.Landscape -> ((strings.size + 1) / 2).coerceAtLeast(1)
    }
    val gap = when (layout) {
        TunerLayout.Stacked, TunerLayout.Landscape -> spacing.sm
        TunerLayout.Wide -> spacing.lg
    }
    val maxWidth = when (layout) {
        TunerLayout.Stacked -> spacing.byEarGridMaxWidth
        TunerLayout.Wide -> spacing.byEarGridMaxWidthWide
        TunerLayout.Landscape -> Dp.Unspecified
    }
    val rowModifier = if (rowMinHeight.isSpecified) Modifier.heightIn(min = rowMinHeight).height(IntrinsicSize.Max) else Modifier
    Column(
        modifier = modifier.widthIn(max = maxWidth),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (row in strings.indices.chunked(columns)) {
            Row(modifier = Modifier.fillMaxWidth().then(rowModifier), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (index in row) {
                    StringCard(
                        string = strings[index],
                        playing = playingIndex == index,
                        layout = layout,
                        onAudition = { onAudition(index) },
                        onStop = onStop,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
