package audio.tembra.ui.timeline.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import audio.tembra.core.project.GridSize
import audio.tembra.ui.metronome.components.MenuEntry
import audio.tembra.ui.metronome.components.MenuPill
import audio.tembra.ui.timeline.TimelineCopy
import audio.tembra.ui.timeline.TimelineTestTags

/** The snapping grid (spec FR-018) on the metronome's [MenuPill]: Bar, 1/4, 1/8, 1/16, Off; tagged `timeline.toolbar.grid`. */
@Composable
internal fun GridPicker(grid: GridSize, onGrid: (GridSize) -> Unit, modifier: Modifier = Modifier) {
    MenuPill(
        label = grid.label,
        description = "${TimelineCopy.grid} ${grid.label}",
        entries = GridSize.entries.map { MenuEntry(it.name.lowercase(), it.label) },
        selectedId = grid.name.lowercase(),
        onSelect = { id -> GridSize.entries.firstOrNull { it.name.lowercase() == id }?.let(onGrid) },
        testTag = TimelineTestTags.GRID,
        modifier = modifier,
    )
}
