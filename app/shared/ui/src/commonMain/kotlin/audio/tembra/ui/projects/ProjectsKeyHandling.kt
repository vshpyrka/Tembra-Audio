package audio.tembra.ui.projects

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import audio.tembra.ui.shortcuts.KeyInput
import audio.tembra.ui.shortcuts.ProjectsShortcuts
import audio.tembra.ui.shortcuts.ShortcutCatalogue
import audio.tembra.ui.shortcuts.ShortcutDispatcher
import audio.tembra.ui.shortcuts.ShortcutPlatform
import audio.tembra.ui.shortcuts.ShortcutScope
import audio.tembra.ui.shortcuts.ShortcutTargets

/**
 * The library's keys on its content root (feature 011, spec FR-021): Ctrl/⌘+F focuses the search field, Ctrl/⌘+N starts
 * a new project. On iPad the menu bar serves both ([ShortcutPlatform.menuServesPrimaryChords]).
 */
internal fun Modifier.projectsKeyHandling(targets: ProjectsShortcuts, platform: ShortcutPlatform): Modifier =
    onKeyEvent { event ->
        val action = ShortcutCatalogue.match(KeyInput.of(event), ShortcutScope.Projects, platform) ?: return@onKeyEvent false
        ShortcutDispatcher.dispatch(action, ShortcutTargets(projects = targets))
    }
