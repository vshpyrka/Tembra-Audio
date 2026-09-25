package audio.tembra.ui.navigation

/** Navigation state (data-model.md §2): which destination is selected; the back stack is derived, never stored. */
data class ShellUiState(val selected: Destination = Destination.Home) {
    /** `[Home]` on Home, else `[Home, selected]`; what the NavDisplay renders (spec FR-010). */
    val backStack: List<Destination>
        get() = if (selected == Destination.Home) listOf(Destination.Home) else listOf(Destination.Home, selected)
}
