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

Escape during a clip drag cancels it (`onClipDragCancel`), restoring the clip.

## Tests first

- `SecondaryClickTest` (uiTest, `performMouseInput { rightClick() }`): a clip, a track header and a project card open
  their menus at the click position; the primary `onClick` is not called on a right-click (Android regression).
- `TooltipCoverageTest` (uiTest): every icon-only control in Timeline, Tuner, Metronome, Projects and the shell exposes a
  tooltip when an injected `HoverInteraction.Enter` is emitted (a semantics walk finds icon-only nodes by role and
  absent text).
- `ViewportGesturesTest` (uiTest): wheel scrolls vertically; Shift+wheel scrolls time; Ctrl+wheel zooms keeping the
  beat under the pointer; a `Scale` pointer event zooms (Android lane).
- `ClipDragEscapeTest` (uiTest): Escape during a mouse drag returns the clip to its start.
- `TembraSliderWheelTest` (uiTest): one notch up raises the value by one step on every slider.
- `TrackpadPinchTest` (iosTest): a `TrackpadPinch(scale = 2f, focus)` from the fake source zooms the viewport around
  `focus`.
