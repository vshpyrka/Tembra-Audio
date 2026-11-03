# Contract: keyboard shortcuts

Spec FR-017 to FR-024, FR-018a; research R6, R10, R11. Skill contracts: `compose-focus-navigation` (consume exactly
the handled event, focus requested from keyed effects, tests drive real key input), `kotlin-api-design` (catalogue as
data owned by `ShortcutCatalogue`; platform lists as bindings), `kotlin-control-flow` (exhaustive `when` over
`ShortcutAction`, no `else`).

## Files (`app/shared/ui/src/commonMain/kotlin/audio/tembra/ui/shortcuts/`)

`ShortcutScope.kt`, `ChordKey.kt`, `KeyChord.kt`, `ShortcutAction.kt`, `Shortcut.kt`, `ShortcutCatalogue.kt`,
`ShortcutCopy.kt`, `ShortcutDispatcher.kt` (routes an action to the shell or the current destination),
`ShellKeyHandling.kt` (`Modifier.shellKeyHandling`), `ShortcutSheet.kt` (+ `ShortcutSheetGroup.kt`).
Per destination: `metronome/MetronomeKeyHandling.kt`, `tuner/TunerKeyHandling.kt`, `projects/ProjectsKeyHandling.kt`;
`timeline/TimelineKeyHandling.kt` keeps its keys and reads its chords from the catalogue.

## Dispatch rules

1. The navigation shell's root carries `shellKeyHandling` (preview pass): Ctrl/⌘+1–5, Ctrl/⌘+/ and Escape. It handles
   a chord only when focus is not in a text field, except Escape.
2. The shown destination's content root carries its own handler (regular pass) for its scope.
3. A handler returns `true` only for a chord it acted on; everything else falls through to normal focus traversal and
   to the focused control (a focused slider keeps its arrows: the Metronome's ↑/↓ act only when focus is not on a
   control that consumes arrows).
4. Escape closes the topmost overlay in this order: menu, shortcut sheet, sheet, dialog; focus returns to the control
   that opened it, else the destination root. With nothing open, Escape keeps today's Android behaviour (Back).
5. On iPad, Command chords arrive through the menu commands and call `ShortcutDispatcher`; the Compose handler ignores
   Command chords on iOS to avoid double dispatch (pinned by a test, research R6).
6. Matching: `KeyChord.matches(event)` compares `utf16CodePoint` for `Character` keys (lower-cased; layout-independent)
   and `Key` for `Named` keys; `primary` means Ctrl on Android and ChromeOS, Command on iPad; KeyDown only; key repeat
   repeats tempo changes within 40–240 BPM.

## The shortcut sheet

A modal sheet listing `ShortcutCatalogue.all` grouped by scope (Everywhere first, then the destinations in navigation
order), each row the chord as key caps and the label; opened by Ctrl/⌘+/ from anywhere and by the "Keyboard shortcuts"
row on Profile; closed by Escape, its close button or a click outside; its open state survives window changes.

## Tests first

- `ShortcutCatalogueTest` (commonTest): no duplicate chord per scope pair, no Alt/Meta chord, every action has a
  handler (an exhaustive `when` in `ShortcutDispatcher`), every label non-empty.
- `KeyChordTest` (commonTest): `Character('s')` matches an AZERTY event whose `Key` is not `S` but whose code point is
  `s`; `primary` matches Ctrl on Android-like events and Meta on iOS-like events.
- `ShellKeyHandlingTest` (uiTest, `performKeyInput`): Ctrl+2 opens Tuner; Ctrl+/ opens the sheet; Escape closes it and
  focus returns; with focus in the Projects search field, typing "2" and "/" types, Ctrl+2 still does nothing.
- `MetronomeKeyHandlingTest`, `TunerKeyHandlingTest`, `ProjectsKeyHandlingTest` (uiTest): every chord in the scope,
  including focus-on-slider exclusions and "number above the string count does nothing".
- `ShortcutSheetTest` (uiTest): every catalogue entry rendered, grouped; Profile's row opens it.
- `InitialFocusTest` (uiTest): each destination's root is focused after it appears, so Tab moves to its first control.
