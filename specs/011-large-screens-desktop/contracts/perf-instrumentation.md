# Contract: performance instrumentation

Constitution "every feature instruments itself"; spec section "Performance instrumentation declared by this
feature"; research R15.

## Catalogue (`app/shared/engine/perf-catalogue.txt`, `PerfSection.kt`, `PerfFamily.kt`)

```
# Feature 011 (large screens and desktop, contracts/perf-instrumentation.md): window changes, shortcut navigation, menus.
SHELL_WINDOW_RELAYOUT shell shell.windowRelayout
SHELL_SHORTCUT_NAVIGATE shell shell.shortcutNavigate
TIMELINE_CONTEXT_MENU timeline timeline.contextMenu
```

`PerfFamily` gains `SHELL`; the pipeline's `perf/tracepulse/catalogue.py` and its tests learn the family.

- `SHELL_WINDOW_RELAYOUT`: begins when the shell observes a new window size that crosses a width or height class,
  ends at the first frame drawn at the new size (the existing first-frame reporting).
- `SHELL_SHORTCUT_NAVIGATE`: begins in `ShortcutDispatcher` for a destination chord, ends at the destination's first
  frame.
- `TIMELINE_CONTEXT_MENU`: begins in `onSecondaryClick` on a clip or track header, ends at the menu's first frame.

## Journey `windowing` (both platforms)

Android (`perf-benchmarks/.../journeys/WindowingJourney.kt` + `WindowingJourneyBenchmark.kt`): open Metronome and start
it; change the window across the 600 and 840 dp classes and back three times (free-form devices, including the
Desktop (Preview) emulator: `am task resize`;
phones: rotation); stop; open the demo project, play, change the window again, right-click the first clip (mouse
injection) and dismiss the menu, press Ctrl+2 then Ctrl+1, pause.
iOS (`iosApp/TembraPerfTests/Journeys/WindowingJourney.swift`): the same steps with rotation as the window change,
`rightClick()` and `typeKey(_:modifierFlags: .command)` on an iPad simulator, iPhone falling back to rotation and keys.

Gates: zero xruns and zero starvation during the journey on the reference devices; the trial round fails if any of
the three sections is absent from the trace. This is the sections' test: `PerfTrace` records only in measurement builds,
through a sink internal to `:shared:engine`, so emission is proven by the measured round rather than a unit test.
`tracepulse.yml`: `journeys: [startup, tuner, metronome, timeline, stress, windowing]`.

## Anchors (`ui/perf/PerfAnchors.kt`, checked by `PerfAnchorsCatalogueTest`)

`METRONOME_START`, `TIMELINE_FIRST_CLIP` (if not already present), `PROJECTS_SEARCH`, `SHORTCUT_SHEET`; navigation items
exist. The Android journey also finds the task id with `am stack list` for resizes.

## Baseline profile and session

Startup is unchanged unless the launch path changes (focus requests and shortcut registration add little); the
profile is regenerated if `tracepulse profile compare` shows the startup family regressing beyond its gate. After the
merge: a labelled session on every available device and the desktop emulator (report in the pull request).
