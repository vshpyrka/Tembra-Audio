package audio.tembra.ui.shortcuts

/** Command, served by the iPad's menu bar (the shell's SwiftUI commands); Compose takes the plain keys. */
internal actual val DefaultShortcutPlatform: ShortcutPlatform = ShortcutPlatform(PrimaryModifier.Meta, menuServesPrimaryChords = true)
