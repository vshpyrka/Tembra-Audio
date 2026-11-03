# Contract: window behaviour

Spec FR-003 to FR-010, User Story 2, SC-002 to SC-004; research R3–R5, R13. Skill contracts:
`compose-state-and-effects` (state that must outlive a layout switch is owned by the session, the destination's
ViewModel or `rememberSaveable`, keyed by semantic identity), `compose-focus-navigation` (focus restored by identity).

## Sizes

Minimum 360 × 480 dp on every platform (platform-shells.md). Layout classes are unchanged: width < 600, 600–839,
≥ 840 dp, short windows < 480 dp tall (`theme/WindowWidth.kt`, `navigation/RememberLayoutMode.kt`,
`timeline/TimelineLayout.kt`, `tuner/TunerLayout.kt`, `metronome/MetronomeLayout.kt`, `projects/ProjectsLayout.kt`).
Above the widest layout, reading content keeps a maximum width (Projects cards at most four columns, the tuner and
metronome cards centred at their expanded width, the shortcut sheet and dialogs at their design widths); the timeline
uses the full width for time.

## The user's place: where each piece lives

| Piece | Today | After |
|---|---|---|
| Destination, open project, playhead, selection, undo history | session / ViewModels | unchanged |
| Timeline horizontal scroll and zoom | viewport state in the timeline screen | the session's per-project view state (already persisted as `view.json` for zoom and grid): the beat at the left edge plus zoom, so any layout re-derives pixels from beats |
| Vertical track scroll | `LazyListState` in a layout branch | hoisted above the layout switch, keyed by project id |
| Open menu (clip, track, card, overflow) | plain `remember` in the component | the owning screen's `rememberSaveable`, keyed by the item id |
| Clip drag in progress | gesture-local | cancelled cleanly on a layout switch (the clip returns), never half-moved |
| Shortcut sheet, sheets, dialogs | shell / screens | `rememberSaveable` (shell or screen) |
| Text being typed | `TextFieldState` | hoisted with its screen's other state |
| Keyboard focus | focus tree | restored to the same semantic id after a layout switch (falls back to the destination root) |

## Full screen (research R21)

`WindowControls` (`UI/platform/WindowControls.kt`, interface): `fullScreenAvailable: StateFlow<Boolean>`,
`isFullScreen: StateFlow<Boolean>`, `enterFullScreen()`, `exitFullScreen()`. Android binding in `MainActivity`
(`requestFullscreenMode`, API 34+, only in multi-window or free-form mode); iOS binding always unavailable. The Timeline's
overflow menu shows "Full screen" or "Exit full screen" while available.

## Audio across window changes

The engine and `ProjectSession` never observe the window, so resize, maximise, snap, tiling, display moves and a
convertible's mode switch cannot touch audio. Minimising follows the existing background rules: metronome and project
playback continue; the tuner's `ListeningPolicy` stops listening and looping tones when `foreground` turns false, and
`foreground` is driven by `ON_START`/`ON_STOP` (not pause), so losing focus to another window keeps listening.

## Tests first

- `PlaceAcrossLayoutsTest` (uiTest): with the demo project scrolled to bar 9, zoomed, a clip selected, a clip menu
  open and the search text "ba" typed on Projects, the host size steps 1200 → 700 → 400 → 1200 dp; after each step the
  same beat is at the left edge, zoom and selection are unchanged, the menu is open on the same clip and the text is
  intact.
- `FocusAcrossLayoutsTest` (uiTest): a focused track header keeps focus across a width-class change.
- `WindowFocusReturnTest` (androidApp androidTest, desktop emulator): with a track header focused, bring another task to
  the front and back; focus is on the same header and audio never stopped (FR-009).
- `FullScreenTest` (androidApp androidTest, desktop emulator): "Full screen" in the Timeline's menu fills the display,
  "Exit full screen" returns the previous bounds, the metronome keeps clicking; the item is absent when the activity is
  not in a multi-window or free-form mode (uiTest with a fake `WindowControls`).
- `ListeningForegroundTest` (commonTest): `ON_PAUSE` alone keeps listening; `ON_STOP` stops it; `ON_START` resumes.
- `WindowResizeAudioTest` (androidApp androidTest, desktop emulator): launch in freeform (`am start --windowingMode 5`),
  start the metronome at 120 BPM, make fifty window changes (resizes across 600 and 840 dp with `am task resize`,
  maximise and restore, full screen and back) as spec SC-002 requires;
  the engine's xrun and starvation counters stay 0 and the click count matches elapsed time ± 1; repeat with the demo
  project playing (playhead keeps advancing, no restart).
- `MinimumWindowTest` (androidApp androidTest, desktop emulator): a mouse drag of the window's corner towards
  300 × 400 dp stops at no less than 360 × 480 dp. The drag goes through `input mouse swipe`: `am task resize` is not
  clamped to any minimum (research hands-on check "Desktop (Preview)"); with nothing declared the platform's own
  minimum on Android 17 is 390 × 357 dp.
- `KeyboardAttachTest`: see platform-shells.md.
