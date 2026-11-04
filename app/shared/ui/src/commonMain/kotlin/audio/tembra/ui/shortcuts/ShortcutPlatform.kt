package audio.tembra.ui.shortcuts

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How the platform takes shortcuts (feature 011, contracts/keyboard-shortcuts.md rules 5 and 6): its [primary] modifier,
 * and whether a system menu serves the chords that use it ([menuServesPrimaryChords]: the iPad's menu bar, whose
 * matched commands also reach Compose, research R6), in which case the Compose handlers leave those chords alone so
 * none fires twice.
 */
data class ShortcutPlatform(val primary: PrimaryModifier, val menuServesPrimaryChords: Boolean)

/** This platform's way, as the shell and the destinations' key handlers read it; tests provide another. */
val LocalShortcutPlatform = staticCompositionLocalOf { DefaultShortcutPlatform }

internal expect val DefaultShortcutPlatform: ShortcutPlatform
