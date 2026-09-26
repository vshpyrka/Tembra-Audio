package audio.tembra.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.tuner.TargetString
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.theme.WindowWidth

/** At most seven cards in two columns (three on expanded windows), lowest string first (spec FR-017, FR-023). */
@Composable
internal fun StringCardGrid(
    strings: List<TargetString>,
    playingIndex: Int?,
    windowWidth: WindowWidth,
    onAudition: (Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TembraTheme.spacing
    val expanded = windowWidth == WindowWidth.Expanded
    val columns = if (expanded) 3 else 2
    val gap = if (expanded) spacing.lg else spacing.sm
    Column(
        modifier = modifier.widthIn(max = if (expanded) spacing.byEarGridMaxWidthWide else spacing.byEarGridMaxWidth),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (row in strings.indices.chunked(columns)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (index in row) {
                    StringCard(
                        string = strings[index],
                        playing = playingIndex == index,
                        windowWidth = windowWidth,
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
