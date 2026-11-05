import SwiftUI
import TembraShared

/// The iPad's menu bar (feature 011, spec FR-022; research R6): one menu per scope with a Command chord, built from the
/// shared catalogue, and the Timeline's Undo and Redo in place of the Edit menu's. Each command calls back into Kotlin by
/// its action's name; Compose leaves these chords alone on iOS, so a chord runs once. UIKit keeps a hardware Escape from
/// the app, so ⌘. (Apple's cancel chord) is the menu's way to close the newest overlay.
struct ShortcutCommands: Commands {
    private let bridge = IosShortcutBridge.shared

    var body: some Commands {
        CommandGroup(replacing: .undoRedo) {
            ForEach(bridge.undoRedo, id: \.action) { command in
                button(command)
            }
        }
        // SwiftUI builds menus from a fixed list: one slot per scope (Everywhere and the four destinations).
        menu(0)
        menu(1)
        menu(2)
        menu(3)
        menu(4)
    }

    @CommandsBuilder
    private func menu(_ index: Int) -> some Commands {
        if index < bridge.sections.count {
            let section = bridge.sections[index]
            CommandMenu(section.title) {
                ForEach(section.commands, id: \.action) { command in
                    button(command)
                }
            }
        }
    }

    private func button(_ command: MenuCommand) -> some View {
        Button(command.label) {
            _ = bridge.dispatch(action: command.action)
        }
        .keyboardShortcut(KeyEquivalent(Character(command.input)), modifiers: modifiers(command))
    }

    private func modifiers(_ command: MenuCommand) -> EventModifiers {
        var modifiers: EventModifiers = []
        if command.command { modifiers.insert(.command) }
        if command.shift { modifiers.insert(.shift) }
        return modifiers
    }
}
