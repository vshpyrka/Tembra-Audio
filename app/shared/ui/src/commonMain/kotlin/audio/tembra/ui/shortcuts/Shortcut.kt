package audio.tembra.ui.shortcuts

/** One entry of the [ShortcutCatalogue]: its [chords] (the first is the one the sheet shows first), where it works and what it does. */
data class Shortcut(
    val chords: List<KeyChord>,
    val scope: ShortcutScope,
    val action: ShortcutAction,
    val label: String,
) {
    /**
     * The chord the iPad's menu bar serves (feature 011, contracts/keyboard-shortcuts.md rule 5): the first with the
     * primary modifier, as a menu item takes one key equivalent. Compose keeps the entry's other chords there.
     */
    val menuChord: KeyChord? get() = chords.firstOrNull { it.primary }
}
