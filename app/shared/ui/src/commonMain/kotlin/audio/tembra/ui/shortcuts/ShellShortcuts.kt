package audio.tembra.ui.shortcuts

import audio.tembra.ui.navigation.Destination

/** What the shell's chords do (feature 011): each returns true when it acted. */
interface ShellShortcuts {
    fun open(destination: Destination): Boolean

    fun showShortcuts(): Boolean

    /** Closes the newest open overlay (rule 4); false when none is open, so Escape goes on. */
    fun closeOverlay(): Boolean
}
