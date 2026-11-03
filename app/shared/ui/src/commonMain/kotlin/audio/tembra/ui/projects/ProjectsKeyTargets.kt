package audio.tembra.ui.projects

import androidx.compose.ui.focus.FocusRequester
import audio.tembra.ui.shortcuts.ProjectsShortcuts

/** The library's shortcut actions: [search] is the search field's requester. */
internal class ProjectsKeyTargets(private val search: FocusRequester, private val onNewProject: () -> Unit) : ProjectsShortcuts {
    override fun focusSearch(): Boolean = search.requestFocus()

    override fun newProject(): Boolean = true.also { onNewProject() }
}
