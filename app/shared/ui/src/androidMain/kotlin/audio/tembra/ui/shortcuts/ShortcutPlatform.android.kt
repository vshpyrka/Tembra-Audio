package audio.tembra.ui.shortcuts

/** Ctrl, and no menu bar: Compose takes every chord (the system's list only shows them, `onProvideKeyboardShortcuts`). */
internal actual val DefaultShortcutPlatform: ShortcutPlatform = ShortcutPlatform(PrimaryModifier.Ctrl, menuServesPrimaryChords = false)
