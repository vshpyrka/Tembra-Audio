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
import audio.tembra.ui.theme.TembraTheme

/**
 * Two regions on either side of a horizontal [fold] (spec FR-002, research R2): [upper] fills the height from the
 * layout's top to [gutter] above the fold, [lower] from [gutter] below it to the bottom, each at the full width and
 * exactly its region's height (a region scrolls its own content when that is taller). Without a fold the height is
 * shared in half around two gutters, which is what UI tests use.
 *
 * The fold is in window pixels and the layout starts somewhere below the window's top (status bar, the shell's top
 * bar), so the layout's own top in the window is read in `onPlaced` into a float state that only the measure block
 * reads: the one deliberate layout-phase back-write of the feature (`compose-performance`). It costs one extra layout
 * pass, without recomposition, the first time the layout is placed or when it moves, and is stable after that. The
 * upper region is composed first, so keyboard and screen-reader traversal read it before the lower one (FR-010).
 */
@Composable
fun FoldSplit(
    fold: TabletopFold?,
    modifier: Modifier = Modifier,
    gutter: Dp = TembraTheme.spacing.md,
    upper: @Composable () -> Unit,
    lower: @Composable () -> Unit,
) {
    var originY by remember { mutableFloatStateOf(Float.NaN) }
    Layout(
        contents = listOf(upper, lower),
        modifier = modifier.onPlaced { originY = it.positionInWindow().y },
    ) { (upperMeasurables, lowerMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val regions = FoldSplitGeometry.regions(height = height, originY = originY, fold = fold, gutter = gutter.roundToPx())
        val lowerHeight = regions.lowerHeight(height)
        val upperPlaceables = upperMeasurables.map { it.measure(Constraints.fixed(width, regions.upperHeight)) }
        val lowerPlaceables = lowerMeasurables.map { it.measure(Constraints.fixed(width, lowerHeight)) }
        layout(width, height) {
            upperPlaceables.forEach { it.place(0, 0) }
            lowerPlaceables.forEach { it.place(0, regions.lowerTop) }
        }
    }
}
