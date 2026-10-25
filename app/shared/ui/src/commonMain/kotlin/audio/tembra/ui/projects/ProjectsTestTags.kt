package audio.tembra.ui.projects

import audio.tembra.core.project.ProjectId
import audio.tembra.ui.perf.PerfAnchors

/** Every `testTag` of the library (contracts/projects-library-screen.md); the journey anchors come from [PerfAnchors]. */
object ProjectsTestTags {
    const val ROOT = PerfAnchors.PROJECTS_ROOT
    const val COUNT = "projects.count"
    const val NEW_PROJECT = "projects.new"
    const val SEARCH = PerfAnchors.PROJECTS_SEARCH
    const val SEARCH_CLEAR = "projects.search.clear"
    const val FILTER = "projects.filter"
    const val SORT = "projects.sort"
    const val GRID = "projects.grid"
    const val EMPTY = "projects.empty"
    const val EMPTY_RESTORE = "projects.empty.restore"
    const val NO_MATCH = "projects.noMatch"
    const val CARD_DEMO = PerfAnchors.PROJECTS_CARD_DEMO
    const val RENAME_DIALOG = "dialog.renameProject"
    const val DESCRIBE_DIALOG = "dialog.describeProject"
    const val KEY_DIALOG = "dialog.projectKey"
    const val DELETE_DIALOG = "dialog.deleteProject"
    const val DIALOG_FIELD = "dialog.field"
    const val DIALOG_CONFIRM = "dialog.confirm"
    const val DIALOG_CANCEL = "dialog.cancel"

    /** A card's tag; the demo card is [CARD_DEMO] and its children (`.open`, `.menu`, `.overview`) hang off that. */
    fun card(id: ProjectId): String = "projects.card.${id.value}"
    fun cardOpen(id: ProjectId): String = "projects.card.${id.value}.open"
    fun cardMenu(id: ProjectId): String = "projects.card.${id.value}.menu"
    fun cardOverview(id: ProjectId): String = "projects.card.${id.value}.overview"
    fun keyOption(name: String): String = "dialog.projectKey.$name"
}
