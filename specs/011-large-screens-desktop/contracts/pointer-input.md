# Contract: pointer input (mouse and trackpad)

Spec FR-011 to FR-016; research R7–R9. Skill contracts: `compose-component-design` (`modifier` first, applied to the
root; no Boolean shape flags), `compose-ui-testing-patterns` (hover and press states through an injected
`MutableInteractionSource`; mouse gestures through `performMouseInput`).

## Building blocks (`ui.designsystem`)

- `Modifier.onSecondaryClick(key, onSecondaryClick: (Offset) -> Unit)`: `pointerInput(key)` watching
  `PointerEventPass.Initial` for `PointerEventType.Press` with `buttons.isSecondaryPressed`; consumes the change (so
  Android's `clickable` does not also fire) and reports the local position. Replaces the inline code in
  `timeline/components/TrackLane.kt:224-228`.
- `PointerShapes` (`expect object`): `grab`, `grabbing`. Common code uses `PointerIcon.Hand` and `PointerIcon.Text`.
- `TembraMenu` gains `offset: DpOffset = DpOffset.Zero` so a menu opened by a secondary click drops at the pointer.
- `TembraSlider` gains wheel stepping: one notch = one step, up/away increases (as `TempoDial` already does).
- `scrollNotches(delta)` (`expect fun`): Android passes deltas through; iOS divides by the 0.01 pan multiplier.
- `TembraScrollbar` (`UI/designsystem/TembraScrollbar.kt`, research R18): `modifier` first; takes a scroll position and
  extent (adapters for `LazyListState`, `ScrollState` and the timeline viewport); visible while the content scrolls from
  a pointer (wheel, trackpad), fading after one second at rest; draggable with the mouse; tokens only.
- `PointerShapes.busy` (research R19): Android `TYPE_WAIT`, iOS `PointerIcon.Default`; the shell root shows it while the
  session reports work in progress.
- Pointer areas (research R20): design-system components draw their hover indication over their whole interactive
  bounds; no invisible growth beyond the drawn shape.

## Coverage (what gets which behaviour)

| Element | Hover state | Tooltip | Pointer shape | Secondary click | Drag | Wheel |
|---|---|---|---|---|---|---|
| Navigation items | exists | exists (`NavigationItemIcon`) | Hand | — | — | — |
| Icon-only toolbar, transport, header buttons | exists | audit: every one | Hand | — | — | — |
| Clip | exists | — | grab / grabbing while dragging | menu at pointer (exists) | press-drag (exists after slop) | — |
| Track header (both layouts) | exists | — | Hand on controls | track menu at pointer (new) | — | — |
| Project card | exists | — | Hand | card menu at pointer (new) | — | — |
| Sliders and dials | exists | value readout | Hand | — | drag (exists) | step (new for sliders) |
| Text fields | — | — | Text | — | — | — |
| Timeline viewport | — | — | — | — | — | scroll, Shift = horizontal, Ctrl/⌘ = zoom at pointer; Android trackpad `Scale`/`Pan` events (new); iPad pinch via `TrackpadPinchSource` (new) |

Escape during a clip drag cancels it (`onClipDragCancel`), restoring the clip. Scrollbars (`TembraScrollbar`) appear on
the timeline's tracks and time axis, the Projects grid, the tuner's and metronome's scrolling panes and the shortcut
sheet.

## Gesture equivalents (FR-016)

Every touch gesture has a pointer or keyboard equivalent that needs no precise or multi-finger input. Audited on
2026-11-03 (T024) over every `pointerInput`, `combinedClickable`, `detect*Gestures`, long-press and `ModalBottomSheet` in
`:shared:ui` `commonMain`; no gap needs a task (FR-016 asks for a pointer *or* a keyboard path, and each row has one):

| Touch gesture (where) | Pointer | Keyboard |
|---|---|---|
| Long-press a clip for its menu (`TrackLane`) | secondary click (exists, lifted into `onSecondaryClick` by T065) | the menu's two actions have keys: S splits, Delete/Backspace deletes the selected clip; Tab reaches the clip, Enter selects it |
| A track header's or a project card's menu (`TrackHeader`, `TrackHeaderCompact`, `ProjectCard`) | the ⋮ overflow button; secondary click (T067, T068) | Tab to the ⋮ button, Enter |
| Long-press a clip, then drag it (`TrackLane`) | press-drag past the slop (exists since 009); Escape cancels (T080) | none: moving a clip by keys is not offered by feature 009 (follow-up note, not an FR-016 gap: the mouse path needs no touchscreen) |
| Pinch the timeline (`ViewportGestures`) | Ctrl/⌘+wheel (exists), Android trackpad `Scale` and the iPad bridge (T074, T081) | + / − (exists) |
| Two-finger pan of the timeline (`ViewportGestures`) | wheel, Shift+wheel (T074), trackpad pan | ←/→ move the playhead a bar; the viewport follows the playhead |
| Drag or pinch the overview strip (`OverviewStrip`) | press-drag the window; Ctrl/⌘+wheel over the timeline | + / − |
| Tap or drag on the ruler to seek (`TimelineRuler`) | click, press-drag | ←/→, Shift+←/→, Home |
| Drag a gain readout up or down; double-tap for 0 dB (`GainReadout`) | press-drag, double-click | ↑/↓/←/→ (0.5 dB), Page Up/Down |
| Rotate the tempo dial (`TempoDial`) | drag, wheel (exists) | ↑/↓, Shift+↑/↓ on the Metronome (T095) |
| Press and hold a tempo stepper to repeat (`TempoStepper`) | press and hold with the mouse (same handler) | ↑/↓ held repeats (T095) |
| Long-press the transport's metronome toggle for its settings (`MetronomeToggle`) | press and hold, or the toolbar's metronome-settings button (in the overflow menu when the toolbar is narrow) | Tab to that button, Enter |
| Swipe a sheet down to close (`SnapSheet`, `MetronomeSettingsSheet`, the metronome's sound sheet) | click outside the sheet | Escape (Android's `EscapeAsBack` today; the shell's Escape order, T094) |

## Tests first

- `PointerShapesDesktopTest` (androidApp androidTest, desktop emulator; written before `PointerShapes`): with a real
  mouse hover injected, the resolved pointer icon over a design-system button is `TYPE_HAND`, over the Projects search
  field `TYPE_TEXT`, over a clip `TYPE_GRAB` (and `TYPE_GRABBING` while dragging), and over the window during a long
  import `TYPE_WAIT` (resolved through the root view's `onResolvePointerIcon`).
- `TembraScrollbarTest` (uiTest): a mouse wheel scroll shows the scrollbar, it fades after a second at rest, a touch
  scroll does not show it, dragging the thumb scrolls the content.
- `PointerAreaTest` (uiTest): for every design-system interactive component, the clickable node's bounds equal the drawn
  container's bounds.

- `SecondaryClickTest` (uiTest, `performMouseInput { rightClick() }`): a clip, a track header and a project card open
  their menus at the click position; the primary `onClick` is not called on a right-click (Android regression).
- `TooltipCoverageTest` (uiTest): every icon-only control in Timeline, Tuner, Metronome, Projects and the shell exposes a
  tooltip when an injected `HoverInteraction.Enter` is emitted (a semantics walk finds icon-only nodes by role and
  absent text), and every interactive control's hover state renders (screenshot of the hovered state per component).
- `ViewportGesturesTest` (uiTest): wheel scrolls vertically; Shift+wheel scrolls time; Ctrl+wheel zooms keeping the
  beat under the pointer; a `Scale` pointer event zooms (Android lane).
- `ClipDragEscapeTest` (uiTest): Escape during a mouse drag returns the clip to its start.
- `TembraSliderWheelTest` (uiTest): one notch up raises the value by one step on every slider.
- `TrackpadPinchTest` (iosTest): a `TrackpadPinch(scale = 2f, focus)` from the fake source zooms the viewport around
  `focus`.
