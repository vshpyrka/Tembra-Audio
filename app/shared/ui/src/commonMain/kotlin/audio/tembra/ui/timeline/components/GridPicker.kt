package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.project.GridSize
import audio.tembra.ui.metronome.components.MenuEntry
import audio.tembra.ui.metronome.components.MenuPill
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags

/**
 * The full toolbar's snapping grid (spec FR-018, D1) on the metronome's [MenuPill]: "Snap 1/16", its menu Bar, 1/4,
 * 1/8, 1/16, Off; tagged `timeline.toolbar.grid`. Compact and landscape windows reach the same choice through the
 * overflow menu's "Snap" entry and the [SnapSheet].
 */
@Composable
internal fun GridPicker(grid: GridSize, onGrid: (GridSize) -> Unit, modifier: Modifier = Modifier) {
    val label = TimelineCopy.snapPill(grid.label)
    MenuPill(
        label = label,
        description = label,
        entries = GridSize.entries.map { MenuEntry(it.name.lowercase(), it.label) },
        selectedId = grid.name.lowercase(),
        onSelect = { id -> GridSize.entries.firstOrNull { it.name.lowercase() == id }?.let(onGrid) },
        testTag = TimelineTestTags.GRID,
        modifier = modifier,
    )
}
