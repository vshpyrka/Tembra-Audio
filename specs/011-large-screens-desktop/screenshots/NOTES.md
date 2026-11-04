# Feature 011 screenshots and evidence

## Compose compiler report (T134, 2026-11-03)

`./gradlew :shared:ui:compileAndroidMain -PcomposeReports=true --no-build-cache --rerun` on the branch after US5
(`shared/ui/build/compose_compiler/tembra_shared:ui-composables.csv`): 238 composables, 204 restartable and all of
them skippable, 34 neither (inline or value-returning: `remember…` helpers, `RegisterShortcuts`, `RegisterOverlay`,
`RegisterTextInput`, the composable `Modifier` factories `audioDropTarget` and `hoverArea`). Feature 011 adds no
restartable composable that cannot skip, so there is nothing to explain.

## Captures

- `us5-picker-roots-api37.png`: the audio picker opened from the empty timeline inside the app's free-form window on
  Tembra_Desktop_API37, offering Recent, Audio, Downloads, Android Desktop and a removable "Virtual SD card"
  (FR-025, T121).
- `us5-picker-roots-api34.png`: the same picker on Tembra_Desktop_API34 (release build, maximised): Recent, Audio,
  Downloads, the device and a removable "Virtual SD card" with its eject control (FR-025, T121, 2026-11-04).

## Desktop quality guideline self-check (T125, SC-010, 2026-11-04)

Tembra_Desktop_API37 (Desktop Preview, Android 17), the release build (`tools/release-android.sh --serial
emulator-5582`), sound muted. Pointer input came from the guest's own virtio mouse (`sendevent` on
`/dev/input/event1` after `adb root`, the cursor read back from `dumpsys input`), so hover, right-click and real
drags reached the app as from a mouse; adb's `input` cannot hover, and the host pointer was not used (a host click
makes the emulator offer to capture the Mac's mouse and keyboard). Files in `guidelines/`.

| Test | Result | Evidence |
|---|---|---|
| T-Scrollbar_Display | Pass after fixes | The key picker, snap sheet, compact track settings and a short window's rail scrolled without a scrollbar: fixed test-first (`ScrollbarCoverageTest`, T138). The timeline's horizontal thumb ran under the pinned track headers and stopped a header's width short of the end: fixed (`ScrollbarThumbTest`, T140). `T-Scrollbar_Display-key-list.png`, `T-Scrollbar_Display-timeline-thumb-fixed.png` |
| T-Hover_Parity | Pass after fix | Hover states and tooltips on the transport (`T-Hover_Parity-transport.png`); the toolbar row's tooltips were placed under the window's caption bar, invisible (`…-toolbar-FAIL.png`): they now drop below the control when above is covered (`TooltipPositionTest`, T139; `…-toolbar-fixed.png`) |
| T-Desktop_Menus | Pass | Right-click on a clip and on a project card opens the item's menu at the pointer (`T-Desktop_Menus-clip.png`, `…-project.png`) |
| T-Request_Fullscreen_Mode | Pass after fix | "Full screen" in the Timeline menu was refused ("Fullscreen request denied by system policy") in a maximised and a restored free-form window although the activity was top-resumed and focused; the same request 300 ms later went through: a request sent while the menu that asked for it is still closing is refused. A refusal while the window is still in the mode it is leaving is now sent once more after 300 ms (`FullScreenRequest`, `FullScreenRequestTest`, T141); enter and exit both work from the menu (`T-Request_Fullscreen_Mode-menu.png`, `…-entered.png`, `…-exited.png`) |
| T-Keyboard_Navigation | Pass | Tab moves a visible focus ring through the toolbar (`T-Keyboard_Navigation-focus-ring.png`) |
| T-Keyboard_Parity | Pass | Ctrl+/ lists the shortcuts; Ctrl+1–5, Escape, Space and the zoom keys drove this walk; the whole map is the desktop keyboard tests' (`T-Keyboard_Parity-shortcut-sheet.png`) |
| T-Custom_Cursors | Pass | Open hand over a clip, closed hand while it is dragged, I-beam over the search field, hand over buttons (`T-Custom_Cursors-grab-grabbing-text.png`) |
| T-Cursor_Target_Size | Pass | `PointerAreaTest` (every design-system control's hover area is its drawn shape, 44 dp targets kept) |
| T-Drag_Drop_Batch | Pass | Five WAVs dragged from the Files app onto the timeline become five tracks at the drop point (`T-Drag_Drop_Batch-dragging.png`, `…-dropped.png`) |
| T-File_Picker | Pass | The picker offers Downloads and a removable drive on both desktop images (`../us5-picker-roots-api37.png`, `../us5-picker-roots-api34.png`) |
| T-File_Handlers | Pass | Files → right-click a WAV → Open with → Tembra Audio opens it as a new project in the same window (`T-File_Handlers-open-with.png`, `…-new-project.png`) |
| T-Offline_Support | Pass | Airplane mode: Tuner, Metronome, Projects and Profile all work, nothing waits on the network (`T-Offline_Support-airplane.png`) |

Also found and fixed on the way (T142): a clip that starts left of the view was drawn over the track headers pinned
beside the lanes (feature 009 code, visible only where the headers are pinned: tablets in landscape and desktop
windows), `TrackLaneTest.aClipScrolledPastTheLeftEdgeDrawsNothingOverWhatSitsBesideTheLane`.

Left for the owner, not 011 defects: the playhead line crosses the expanded track's inline settings panel (feature
009 overlay design); "Tap tempo — Sync finger rhythm" reads touch-first in a desktop window; the rename dialog does
not put the caret in its field when it opens.
