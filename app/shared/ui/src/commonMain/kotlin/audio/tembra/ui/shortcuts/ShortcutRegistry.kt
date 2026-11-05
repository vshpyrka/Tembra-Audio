package audio.tembra.ui.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * The targets that can act now, for the iPad's menu commands (feature 011, contracts/keyboard-shortcuts.md rule 5): the
 * shell and the showing destination register theirs while composed ([RegisterShortcuts]); a menu command dispatches to
 * [current]. The shell also registers its [TextInputFocus], so a menu command keeps rule 1 (the shell's chords wait
 * while a text field has focus). Main thread only.
 */
object ShortcutRegistry {
    var current: ShortcutTargets = ShortcutTargets()
        private set

    internal var textInput: TextInputFocus? = null

    /** Whether a text field of the shell has keyboard focus now. */
    val typing: Boolean get() = textInput?.focused == true

    internal fun update(transform: (ShortcutTargets) -> ShortcutTargets) {
        current = transform(current)
    }
}

/** Registers the shell's [textInput] while this composable is in composition. */
@Composable
internal fun RegisterTextInput(textInput: TextInputFocus) {
    DisposableEffect(textInput) {
        ShortcutRegistry.textInput = textInput
        onDispose { if (ShortcutRegistry.textInput === textInput) ShortcutRegistry.textInput = null }
    }
}

/** Registers [targets]' non-null scopes while this composable is in composition. */
@Composable
internal fun RegisterShortcuts(targets: ShortcutTargets) {
    DisposableEffect(targets) {
        ShortcutRegistry.update { it.merge(targets) }
        onDispose { ShortcutRegistry.update { it.without(targets) } }
    }
}

private fun ShortcutTargets.merge(other: ShortcutTargets) = ShortcutTargets(
    shell = other.shell ?: shell,
    timeline = other.timeline ?: timeline,
    tuner = other.tuner ?: tuner,
    metronome = other.metronome ?: metronome,
    projects = other.projects ?: projects,
)

private fun ShortcutTargets.without(other: ShortcutTargets) = ShortcutTargets(
    shell = shell.takeUnless { it != null && it === other.shell },
    timeline = timeline.takeUnless { it != null && it === other.timeline },
    tuner = tuner.takeUnless { it != null && it === other.tuner },
    metronome = metronome.takeUnless { it != null && it === other.metronome },
    projects = projects.takeUnless { it != null && it === other.projects },
)
