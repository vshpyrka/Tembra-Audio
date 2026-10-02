package audio.tembra.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import audio.tembra.ui.posture.TabletopFold
import audio.tembra.ui.posture.VerticalFold
import audio.tembra.ui.theme.TembraTheme

/**
 * [FoldSplit] turned on its side (feature 005, design iteration D1, research R8) for a book-posture [fold]. [start] fills the width from the
 * layout's start to [gutter] before the hinge, [end] from [gutter] after it to the layout's end, both at the full
 * height; the hinge's window position is mapped into the layout by its own left edge in the window, read in
 * `onPlaced` exactly as [FoldSplit] reads its top. Without a fold the width is shared in half.
 */
@Composable
fun FoldSplitRow(
    fold: VerticalFold?,
    modifier: Modifier = Modifier,
    gutter: Dp = TembraTheme.spacing.md,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    var originX by remember { mutableFloatStateOf(Float.NaN) }
    Layout(
        contents = listOf(start, end),
        modifier = modifier.onPlaced { originX = it.positionInWindow().x },
    ) { (startMeasurables, endMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // The same arithmetic as the tabletop split, on the horizontal axis.
        val span = fold?.let { TabletopFold(top = it.left, bottom = it.right) }
        val regions = FoldSplitGeometry.regions(height = width, originY = originX, fold = span, gutter = gutter.roundToPx())
        val startPlaceables = startMeasurables.map { it.measure(Constraints.fixed(regions.upperHeight, height)) }
        val endPlaceables = endMeasurables.map { it.measure(Constraints.fixed(regions.lowerHeight(width), height)) }
        layout(width, height) {
            startPlaceables.forEach { it.place(0, 0) }
            endPlaceables.forEach { it.place(regions.lowerTop, 0) }
        }
    }
}
