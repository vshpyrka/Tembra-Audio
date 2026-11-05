package audio.tembra.ui.shortcuts

/** What the library's keys do (feature 011, spec FR-021); each returns true when it acted. */
interface ProjectsShortcuts {
    fun focusSearch(): Boolean

    fun newProject(): Boolean
}
