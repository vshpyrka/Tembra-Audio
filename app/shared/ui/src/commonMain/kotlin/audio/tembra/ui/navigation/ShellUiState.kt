package audio.tembra.ui.navigation

/** The start destination: the first in display order. */
val StartDestination: Destination = Destination.entries.first()

/** Navigation state (data-model.md §2): which destination is selected; the back stack is derived, never stored. */
data class ShellUiState(val selected: Destination = StartDestination) {
    /** `[start]` on the start destination, else `[start, selected]`; what the NavDisplay renders (spec FR-010). */
    val backStack: List<Destination>
        get() = if (selected == StartDestination) listOf(StartDestination) else listOf(StartDestination, selected)
}
