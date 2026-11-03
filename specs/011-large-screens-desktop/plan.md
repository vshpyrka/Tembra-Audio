# Implementation Plan: Large Screens and Desktop

**Branch**: `011-large-screens-desktop` | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-large-screens-desktop/spec.md` (clarified 2026-11-03: iPad windows
and drag and drop included; one file dropped on a track becomes a clip at the drop point; the shortcut sheet opens by
Ctrl/⌘+/ and from Profile; the first desktop window is wide and later the system-remembered size; dropped folders
contribute their direct audio files; the tuner gains A/E, number keys, Space and L). The owner asked to check whether
Android's desktop emulator can be used: it can, and it was installed and exercised during planning (research.md).

## Summary

Tembra Audio already lays itself out for every width, and the timeline already speaks mouse and keyboard; what is
missing is reach, real desktop windows, and the input conventions that tie the screens together. The manifest stops
implying a touchscreen and a microphone, declares a 360 × 480 dp minimum and a ChromeOS tablet-size hint, and stops
recreating the activity when a keyboard comes and goes; iPad keeps its scene setup and gains the same minimum. A
`ShortcutCatalogue` becomes the single source of every shortcut, feeding the shell's and each destination's key
handlers, a new shortcut sheet (Ctrl/⌘+/ or Profile), Android's system shortcut list and the iPadOS menu bar. One
`onSecondaryClick` modifier lifted from the clip lane brings menus at the pointer to track headers and project cards;
pointer shapes, wheel stepping on sliders, Android trackpad pinch and an iPad pinch bridge complete pointer input.
Drag and drop reuses the importer through a `DroppedFilesReader` per platform and a common `DropPlanner`. The user's
place is moved out of layout-local state so any window change keeps it, and the audio is untouched by construction.
Desktop conventions from Google's desktop app quality guidelines (the bar for Googlebooks, Android laptops on ChromeOS
foundations) complete the set: scrollbars for pointer scrolling, a busy pointer during long work, hover areas equal to
click areas, a "Full screen" menu item in desktop windows, and opening audio files from the Files app into a new
project. Verification runs on the Android desktop emulator (installed: the Android 17 Desktop (Preview) image,
`Tembra_Desktop_API37`, primary, with a touchscreen like a Googlebook; the Android 14 desktop image,
`Tembra_Desktop_API34`, secondary; real freeform windows resized from tests), the owner's tablet in desktop mode and an
iPad simulator; a `windowing` journey and three sections make it measurable. Evidence: [research.md](research.md)
R1–R23.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (Multiplatform; Kotlin/Native for iOS); Swift for the iOS shell's menu commands,
minimum size and pinch bridge, and the new XCUITest target; Python for the pipeline's family entry.

**Primary Dependencies**: no new library in the shipped app (research R17). Used: Compose Multiplatform 1.12.2
(`dragAndDropTarget`, `pointerHoverIcon`, pointer events, key events, `TooltipBox`), JetBrains Material 3
1.12.0-alpha03 / androidx Material 3 1.5.0-beta02, adaptive 1.3.0, Navigation 3 1.2.0, Metro 1.5.0; platform:
Android `onProvideKeyboardShortcuts`, `requestDragAndDropPermissions`, `requestFullscreenMode` (API 34+), the `VIEW`
intent filter, `PackageManager`/`AudioManager`, the `<layout>` element; iOS SwiftUI `.commands` and `.onOpenURL`,
`CFBundleDocumentTypes`, `UISceneSizeRestrictions`, `UIPinchGestureRecognizer`, `NSItemProvider`, `AVAudioSession`. Test-only: `androidx.window` (`WindowMetricsCalculator`) in the app's device tests.

**Storage**: none new; the timeline's scroll anchor joins the per-project view state already saved as `view.json`.

**Testing**: `kotlin.test` in `commonTest` (catalogue, chords, drop planning, listening policy); `runComposeUiTest` in
`uiTest` with `performKeyInput`, `performMouseInput` and injected `MutableInteractionSource` (shell keys, destination
keys, sheet, secondary clicks, tooltips and hover states, wheel, scrollbars, pointer areas, place across sizes, drop
targets, the full-screen item); `androidApp/src/androidTest` on the desktop emulator (manifest features, keyboard
attach, system shortcut list, window resize while audio runs, window focus return, full screen, minimum size, pointer
shapes, foreground state, open-with, drop reader); `iosTest` (pinch, microphone, drop reader, foreground, opened
files); XCUITest `TembraDesktopTests` on an iPad simulator (menu commands, keys, right-click, hover, scroll, minimum
window); manual checks by ear and by hand (quickstart §3–§6).

**Target Platform**: Android 8.0+ phones, foldables, tablets, Android desktop windowing and Chromebooks; iOS 16+ iPhone
and iPad (resizable windows on iPadOS 26+). Verification targets: `Tembra_Desktop_API37` (Android 17 Desktop (Preview)
image, 1920×1200 dp, touchscreen, primary), `Tembra_Desktop_API34` (Android 14 desktop image, 1920×1080 dp, no
touchscreen, secondary: the no-touchscreen case), the owner's tablet in desktop mode, `iPad Pro 13-inch (M5)`
simulator; regression on the existing phones, foldables and simulators.

**Project Type**: mobile app; changes in `:shared:ui` (new packages `shortcuts`, `drop`; edits in `navigation`,
`designsystem`, `timeline`, `tuner`, `metronome`, `projects`, `profile`, `platform`, `perf`), `:shared:engine`
(perf names only), the Android and iOS shells, `perf-benchmarks`, `TembraPerfTests`, `perf/`.

**Performance Goals** (spec SC-002, SC-004): zero xruns, starvation or restarts across 50 window changes with the
metronome or a project playing; the layout settles in one frame after the window stops changing; shortcut navigation
and menus at frame rate (measured by the new sections).

**Constraints**: Principle I untouched (no engine change); one window, one task; no Alt or Meta chords; letters matched
by character; Command chords on iPad dispatched once (menu); minimising follows the existing background rules; tokens
only; one type per file; zero warnings; Kotlin tests never link the engine.

**Scale/Scope**: about 36 new Kotlin files (shortcuts 11, drop 11, design-system 5, platform 9) and 17 edited; 3 Swift
files and `Info.plist` edited, 2 new (pinch bridge, desktop tests); manifest edits; 1 new device-test suite of ~12
classes, ~15 new UI test classes, ~7 common tests; 3 catalogue sections, 1 family, 1 journey on each platform; README rows; the Chromebook
screenshots.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Real-Time Engine Isolation | No engine or callback change. Window changes never reach the engine or `ProjectSession`; the device test proves zero xruns and starvation while resizing (contracts/window-behaviour.md). | PASS |
| II. Shared Kotlin Control Plane, Compose UI | Every behaviour is common Compose/Kotlin in `:shared:ui`; platform code is declarations, OS entry points and bridges (manifest, `onProvideKeyboardShortcuts`, drop permissions, SwiftUI commands, pinch bridge). Seams are interfaces bound per platform (`DroppedFilesReader`, `TrackpadPinchSource`, the extended `MicrophonePermission`); `expect`/`actual` only for platform constants and conversions (`PointerShapes`, `scrollNotches`). Profile stays without a ViewModel (static content plus a row whose callback the shell owns). | PASS |
| III. Test-First | Every contract lists its failing tests first; window resizing and pointer input are tested on the desktop emulator and an iPad simulator as Principle III requires; Kotlin tests use fakes (reader, pinch source, audio-device seam). | PASS |
| IV. Adaptive, Input-Agnostic UI | The feature is this principle on real desktop devices: hover and tooltips on every control (audited), wheel and trackpad scroll and zoom on both platforms (Android `Scale` events, iPad bridge), visible focus and keyboard traversal with initial focus per destination, 44 dp targets unchanged; screenshots at compact and expanded widths and in desktop windows (quickstart). | PASS |
| V. Document Model in Beats | No document change; the drop position is converted to beats by the existing viewport and snapped by the grid; the scroll anchor is stored in beats. | PASS |
| VI. Simplicity | No new shipped dependency; one catalogue instead of three key lists; the folder rule and the window hints follow what each platform allows instead of workarounds; the iPad pinch bridge is temporary until Compose 1.13 (Complexity Tracking). | PASS |
| VII. Code Organisation | One type per file; new packages `ui.shortcuts` and `ui.drop`; platform files beside the existing picker and permission bindings; `internal` by default. | PASS |
| VIII. Skill-Guided Kotlin and Compose | Routed through `using-chrisbanes-skills` on 2026-11-03: `compose-focus-navigation` (focus requested from keyed effects, handlers consume exactly the handled event, Escape's focus restore by identity, key-driven tests), `compose-ui-testing-patterns` (content composables with controlled state, injected `MutableInteractionSource` for hover, `performKeyInput`/`performMouseInput` for input, semantics before pixels, lanes declare keyboard support), `kotlin-api-design` (catalogue owned by `ShortcutCatalogue`; semantic common seams with Activity-owned or shell-owned bindings; `expect` only for constants), plus `kotlin-control-flow` for the exhaustive `when` over `ShortcutAction`, `DropZone`, `DroppedItem` and `DropPlan`, `compose-component-design` for `ShortcutSheet`, `onSecondaryClick` and the `TembraMenu`/`TembraSlider` additions, and `compose-state-and-effects` for the place audit. Every Gradle command runs through `gradle-run`. | PASS |
| Technology & Architecture Constraints | KMP and Compose Multiplatform for Android and iOS (Android desktop mode and Chromebooks are Android; "Desktop" in the constraints means a desktop JVM target, which stays out); Navigation 3, Metro, design tokens; the navigation's five items unchanged; nothing forbidden. | PASS |
| Development Workflow & Quality Gates | Build order untouched: no musical capability is added or pulled forward; opening audio from the Files app reuses the existing importer; Principle IV's promise is delivered on real devices. Instrumentation: `windowing` journey on both platforms, three sections, a new family, anchors, the labelled session after merge; the trial round fails if a section is absent (contracts/perf-instrumentation.md). Definition of done: lanes plus both desktop emulators and the iPad simulator, the guideline self-check (SC-010), screenshots, the compose report. | PASS |

**Post-design re-check (after Phase 1)**: unchanged. Planning found three platform limits that change the spec's
wording, recorded in the spec's planning findings: iPad has no app-chosen first window size (FR-004a scoped to Android
and ChromeOS), iPad pointer shapes are the system's (FR-015 scoped to Android and ChromeOS), and a dropped folder's
contents are readable only where the platform allows (FR-026: otherwise refused by name). Both clarification answers
they touch keep their intent.

**Analysis remediation (2026-11-03)**: after `/speckit-analyze` and Google's Googlebook post (research "Googlebook and
the desktop app quality guidelines"), the owner chose the recommended fixes: the cheap desktop-guideline items join this
feature (scrollbars FR-014a, busy pointer FR-015a, hover area equals click area FR-016a, full screen FR-010a, opening
audio from the Files app FR-026a, the guideline self-check SC-010) and the large ones are deferred (research R23); the
primary emulator becomes the Android 17 Desktop (Preview) image (`Tembra_Desktop_API37`), Android 14 desktop stays a
secondary target. The re-check is unchanged: every new behaviour is common Compose behind interfaces bound by the
shells (`WindowControls`, `OpenedFiles`), each with tests listed first in its contract.

## Project Structure

### Documentation (this feature)

```text
specs/011-large-screens-desktop/
├── plan.md              # This file
├── research.md          # Phase 0: R1–R23, Googlebook, the desktop emulators' hands-on checks
├── data-model.md        # Phase 1: shortcuts, drops, pointer, microphone, window values, shell state, perf names
├── quickstart.md        # Phase 1: targets, lanes, every user story, performance, Chromebook screenshots
├── contracts/
│   ├── platform-shells.md        # manifest, MainActivity, iOS shell (menu commands, minimum size, pinch bridge), tests
│   ├── keyboard-shortcuts.md     # catalogue, dispatch rules, matching, sheet, tests
│   ├── pointer-input.md          # secondary click, shapes, menus at the pointer, wheel and pinch, coverage table, tests
│   ├── window-behaviour.md       # sizes, place-keeping audit, audio across window changes, tests
│   ├── drop-import.md            # zones, readers, planner, messages, tests
│   └── perf-instrumentation.md   # sections, family, windowing journey, anchors, session
├── checklists/requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/
├── androidApp/src/main/AndroidManifest.xml           uses-feature ×2, configChanges +4, <layout> min, ChromeOS size hint,
│                                                     VIEW intent filter for WAV/FLAC/MP3
├── androidApp/src/main/kotlin/audio/tembra/android/
│   ├── MainActivity.kt                               onProvideKeyboardShortcuts; reader, availability, window-controls
│   │                                                 and opened-files bindings; ACTION_VIEW in onCreate/onNewIntent
│   ├── AndroidDroppedFilesReader.kt                  new
│   ├── AndroidWindowControls.kt                      new (requestFullscreenMode)
│   ├── AndroidOpenedFiles.kt                         new
│   └── AndroidMicrophonePermission.kt                Unavailable status
├── androidApp/src/androidTest/kotlin/…               ManifestFeatures, KeyboardAttach, KeyboardShortcutsHelper,
│                                                     WindowResizeAudio, WindowFocusReturn, FullScreen, MinimumWindow,
│                                                     PointerShapesDesktop, ForegroundState, OpenWith,
│                                                     MicrophoneAvailability, AndroidDroppedFilesReader tests;
│   └── desktop/DesktopWindow.kt, DesktopInput.kt     helpers: am task resize / windowing mode, mouse and key injection
├── shared/ui/src/commonMain/kotlin/audio/tembra/ui/
│   ├── shortcuts/                                    new: catalogue, chords, dispatcher, shell handling, sheet
│   ├── drop/                                         new: zones, items, planner, outcome, reader seam, target modifier,
│   │                                                 OpenedFiles seam
│   ├── designsystem/                                 OnSecondaryClick, PointerShapes (expect, + busy), ScrollNotches
│   │                                                 (expect), TembraScrollbar; TembraMenu offset, TembraSlider wheel,
│   │                                                 tooltip and hover audit fixes, pointer areas
│   ├── navigation/                                   NavigationShellContent: shell keys, sheet state, focus restore,
│   │                                                 busy pointer at the root, opened files into a new project
│   ├── timeline/                                     key handling from the catalogue; scroll anchor in view state;
│   │                                                 ViewportGestures Scale/Pan + pinch source; drop zones; TrackLane
│   │                                                 uses onSecondaryClick; TrackHeader(+Compact) menus at pointer;
│   │                                                 "Full screen" overflow item; scrollbars
│   ├── tuner/                                        TunerKeyHandling; no-microphone message; foreground on start/stop
│   ├── metronome/                                    MetronomeKeyHandling
│   ├── projects/                                     ProjectsKeyHandling; card menu at pointer; drop zone
│   ├── profile/ProfileScreen.kt                      "Keyboard shortcuts" row
│   ├── platform/MicrophonePermissionStatus.kt        + Unavailable; TrackpadPinchSource and WindowControls interfaces
│   └── perf/PerfAnchors.kt                           + anchors
├── shared/ui/src/{androidMain,iosMain}/…             PointerShapes, scrollNotches actuals; IosDroppedFilesReader,
│                                                     IosTrackpadPinchSource, IosOpenedFiles, IosShortcutBridge,
│                                                     IosMicrophonePermission Unavailable; foreground on start/stop
├── shared/ui/src/{uiTest,commonTest,iosTest}/…       tests per contract
├── shared/engine/perf-catalogue.txt, …/perf/PerfSection.kt, PerfFamily.kt     three sections, SHELL family
├── iosApp/iosApp/{iOSApp.swift,ContentView.swift}    menu commands, minimum size, .onOpenURL, debug window-minimum report
├── iosApp/iosApp/Info.plist                          CFBundleDocumentTypes (WAV, MP3, FLAC)
├── iosApp/iosApp/TrackpadPinchBridge.swift           new (temporary until Compose Multiplatform 1.13)
├── iosApp/TembraDesktopTests/                        new XCUITest target (iPad simulator): menu commands, keys, pointer,
│                                                     WindowMinimumTests
├── perf-benchmarks/src/main/kotlin/audio/tembra/perf/journeys/WindowingJourney.kt (+ benchmark)
├── iosApp/TembraPerfTests/Journeys/WindowingJourney.swift
├── perf/tracepulse/catalogue.py (+ tests), tracepulse.yml   SHELL family, windowing journey
├── gradle/libs.versions.toml                         androidx-window (test only)
└── README.md                                         desktop emulator targets (API 37 primary, API 34) and lane rows
```

**Structure Decision**: the existing four-module layout; new code in two feature packages of `:shared:ui`
(`shortcuts`, `drop`) and small additions where each concern already lives.

## Complexity Tracking

| Choice a reviewer could question | Why needed | Simpler alternative rejected because |
|---|---|---|
| A Swift trackpad pinch bridge on iPad (`TrackpadPinchBridge.swift` + `TrackpadPinchSource`) | Principle IV requires trackpad zoom; Compose Multiplatform 1.12.2 delivers no trackpad pinch on iOS | Waiting for Compose 1.13 leaves iPad without pinch for an unknown time; the bridge is one file and one interface, deleted on upgrade |
| A custom `TembraScrollbar` in common code | Compose Multiplatform's `VerticalScrollbar` exists only for Skiko targets (iOS, desktop), not Android, and the guidelines want scrollbars where content scrolls by pointer (research R18) | Using the Skiko scrollbar on iOS alone leaves Android and ChromeOS, the main desktop target, without one; Android's View scrollbars do not draw for Compose lists |
| Command shortcuts on iPad through SwiftUI menu commands, plain keys through Compose | The iPadOS 26 menu bar is the only system list of an app's shortcuts; Compose handles everything else | All keys as `UIKeyCommand`s would fight Compose's focus for plain keys; all keys in Compose would leave iPad without a system list |
