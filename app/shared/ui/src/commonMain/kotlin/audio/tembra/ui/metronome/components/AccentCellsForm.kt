package audio.tembra.ui.metronome.components

/**
 * How [AccentCells] draws its cells: [Labelled] is the portrait and expanded card (the 64 dp bar box with the level's
 * name under it, hidden when a cell is too narrow for it); [BarOnly] is the phone-landscape card (T152): 44 dp cells
 * holding only the bar box, the level carried by the bar's height and colour and announced by the cell's state.
 */
internal enum class AccentCellsForm { Labelled, BarOnly }
