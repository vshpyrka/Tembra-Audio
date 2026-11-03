package audio.tembra.ui.timeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import audio.tembra.core.project.GridSize
import audio.tembra.ui.perf.anchorRoot
import audio.tembra.ui.theme.TembraIcon
import audio.tembra.ui.theme.TembraIcons
import audio.tembra.ui.theme.TembraTheme
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags
import kotlinx.coroutines.launch

/**
 * The snap picker as a sheet (spec FR-018, D1), shown for `TimelineSheet.GridMenu`: "Snap to grid" over a caption saying
 * what lands on the lines, then the five [GridSize] rows (Bar, 1/4, 1/8, 1/16, Off) as one radio group with a check on
 * [grid]. Any row, the current one included, reports [onGrid] and then, once the sheet has slid away, [onDismiss]; a
 * swipe, a tap outside or Back only dismiss. Opens fully expanded and scrolls when the window is shorter than the list.
 * Tagged `timeline.snapSheet`, rows `timeline.snapSheet.<grid, lowercase>` and the check `<row>.check`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SnapSheet(grid: GridSize, onGrid: (GridSize) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    // Hidden until shown, then straight to expanded: the list is short, a half-open sheet would only hide rows.
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.anchorRoot().testTag(TimelineTestTags.SNAP_SHEET),
        sheetState = sheetState,
        containerColor = colors.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = TimelineCopy.snapToGrid,
                style = TembraTheme.typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(horizontal = spacing.pageMargin),
            )
            Text(
                text = TimelineCopy.snapCaption,
                style = TembraTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.pageMargin).padding(bottom = spacing.sm),
            )
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                for (size in GridSize.entries) {
                    SnapRow(
                        size = size,
                        current = size == grid,
                        onClick = {
                            onGrid(size)
                            scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
                        },
                    )
                }
            }
        }
    }
}

/** One radio row: the size's label, `primary` and checked when [current]. */
@Composable
private fun SnapRow(size: GridSize, current: Boolean, onClick: () -> Unit) {
    val spacing = TembraTheme.spacing
    val colors = TembraTheme.colors
    val accent = if (current) colors.primary else colors.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .testTag(TimelineTestTags.snapSheetRow(size))
            .pointerHoverIcon(PointerIcon.Hand).selectable(selected = current, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = spacing.pageMargin),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = size.label, style = TembraTheme.typography.bodyLarge, color = accent, modifier = Modifier.weight(1f))
        if (current) {
            Icon(
                imageVector = TembraIcons.vector(TembraIcon.Check),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(CHECK_SIZE).testTag(TimelineTestTags.snapSheetCheck(size)),
            )
        }
    }
}

/** The one-line list item's height in Material 3: above the 44 dp touch target. */
private val ROW_MIN_HEIGHT = 56.dp
private val CHECK_SIZE = 24.dp
