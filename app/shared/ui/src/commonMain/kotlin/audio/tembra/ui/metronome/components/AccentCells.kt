package audio.tembra.ui.metronome.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import audio.tembra.core.metronome.AccentPattern
import audio.tembra.core.metronome.BeatLevel
import audio.tembra.ui.designsystem.rememberFrameTick
import audio.tembra.ui.metronome.BeatTelemetry
import audio.tembra.ui.metronome.MetronomeCopy
import audio.tembra.ui.theme.TembraTheme

/**
 * The bar's beats as cells (research R7, R10; revised in design iteration D1): one [AccentCell] per beat of [pattern],
 * [gap] apart, all in one row whenever each cell keeps the 44 dp target, sharing the row's width. When that share is
 * narrower than the widest level label ("ACCENT" in bold with the cell padding: 6/8 and 7/8 on a phone) no cell draws
 * its label and the bar alone shows the level. Only when 44 dp cannot be kept (the landscape settings column) do the
 * cells wrap, as before, into as few rows as hold each cell at its widest label. While [running] a frame tick drives
 * the light: each cell's draw phase reads the tick and asks [telemetry] which beat is audible, so only drawing is
 * invalidated and nothing is written back to composition (`compose-performance`). Stopped, the telemetry is never
 * read. [currentBeat] is the ViewModel's 10 Hz sample (-1 when stopped): the cell it names is announced as playing.
 * Clicking a cell reports its 0-based index to [onCycle]. The row carries [testTag] and each cell the singular of it
 * with its number from 1: `metronome.cells` → `metronome.cell.1`.
 */
@Composable
internal fun AccentCells(
    pattern: AccentPattern,
    running: Boolean,
    currentBeat: Int,
    telemetry: BeatTelemetry,
    onCycle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    gap: Dp = MetronomeDefaults.CellGap,
    testTag: String = "metronome.cells",
) {
    // A labelled cell is at least as wide as its widest label ("ACCENT" in bold) with its padding.
    val labelStyle = TembraTheme.typographyExtras.microLabel.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth = remember(measurer, labelStyle, density) {
        val widest = BeatLevel.entries.maxOf { measurer.measure(MetronomeCopy.levelLabel(it), labelStyle).size.width }
        with(density) { widest.toDp() }
    }
    val target = TembraTheme.spacing.touchTargetMin
    val labelledCell = max(target, labelWidth + MetronomeDefaults.CellPadding * 2)
    val tick = rememberFrameTick(running)
    BoxWithConstraints(modifier = modifier.testTag(testTag)) {
        val beats = pattern.levels.size
        // Whole pixels: a width in dp rounds per cell, and four rounded-up cells can overflow the row and wrap.
        val gapPx = with(density) { gap.roundToPx() }
        val oneRowPx = (constraints.maxWidth - gapPx * (beats - 1)) / beats
        val oneRow = with(density) { oneRowPx.toDp() } >= target
        val perRow = if (oneRow) beats else cellsPerRow(beats, maxWidth, labelledCell, gap)
        val cellPx = (constraints.maxWidth - gapPx * (perRow - 1)) / perRow
        val cellWidth = with(density) { cellPx.toDp() }
        val labelled = cellWidth >= labelledCell
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = perRow,
        ) {
            pattern.levels.forEachIndexed { index, level ->
                AccentCell(
                    index = index,
                    level = level,
                    lit = { running && telemetry.litBeat(tick.longValue) == index },
                    playing = index == currentBeat,
                    onClick = { onCycle(index) },
                    modifier = Modifier.width(cellWidth),
                    testTag = "${cellTagPrefix(testTag)}.${index + 1}",
                    label = if (labelled) {
                        { AccentCellLabel(level) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** The cells' tag prefix: the row tag without its plural `s`. */
private fun cellTagPrefix(rowTag: String): String = rowTag.removeSuffix("s")

/**
 * When one row cannot keep 44 dp: as few rows as fit with each cell at [minCell] (its widest label), holding as equal a
 * number of cells as they can (7/8 is 3 + 3 + 1 in the narrow landscape column).
 */
private fun cellsPerRow(beats: Int, width: Dp, minCell: Dp, gap: Dp): Int {
    val fit = ((width + gap) / (minCell + gap)).toInt().coerceIn(1, beats)
    val rows = (beats + fit - 1) / fit
    return (beats + rows - 1) / rows
}
