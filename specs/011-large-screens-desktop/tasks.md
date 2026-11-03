---
description: "Task list for feature 011: large screens and desktop"
---

# Tasks: Large Screens and Desktop

**Input**: Design documents from `/specs/011-large-screens-desktop/` (spec.md clarified 2026-11-03 with planning
findings and the analysis remediation; plan.md, research.md R1–R23, data-model.md, contracts/, quickstart.md)

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Required. The constitution makes test-first non-negotiable (Principle III): every test task below is
written first and must fail before the implementation task that follows it. Window resizing and pointer input are
tested on a real emulator or device: the Android Desktop (Preview) emulator `Tembra_Desktop_API37` (primary, port
5582), the Android 14 desktop emulator `Tembra_Desktop_API34` (secondary: no touchscreen, port 5580) and an iPad
simulator.

**Organization**: one phase per user story in the spec's priority order; Setup and Foundational first; performance
instrumentation and polish last.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: the user story the task serves (US1–US6)
- Paths are relative to the repository root. Shorthand used below (always expand to the full path):
  - `UI` = `app/shared/ui/src/commonMain/kotlin/audio/tembra/ui`
  - `UITEST` = `app/shared/ui/src/uiTest/kotlin/audio/tembra/ui`
  - `CTEST` = `app/shared/ui/src/commonTest/kotlin/audio/tembra/ui`
  - `UIANDROID` = `app/shared/ui/src/androidMain/kotlin/audio/tembra/ui`
  - `UIIOS` = `app/shared/ui/src/iosMain/kotlin/audio/tembra/ui`
  - `IOSTEST` = `app/shared/ui/src/iosTest/kotlin/audio/tembra/ui`
  - `ANDROID` = `app/androidApp/src/main/kotlin/audio/tembra/android`
  - `ATEST` = `app/androidApp/src/androidTest/kotlin/audio/tembra/android`
- Skill contracts (Principle VIII): route every code task through `using-chrisbanes-skills`; the owners are named in
  plan.md (focus navigation, UI testing patterns, API design, control flow, component design, state and effects). Every
  Gradle command runs through `gradle-run`.

---

## Phase 1: Setup

**Purpose**: targets, test harness projects and the spikes whose outcomes the later tasks depend on.

- [X] T001 Document both Android desktop emulator targets in `app/README.md` (§ Build and run and § Test lanes): the
  primary AVD `Tembra_Desktop_API37` (`sdkmanager --channel=3 --install
  "system-images;android-37.1;android-desktop;arm64-v8a"`, `avdmanager create avd … -d desktop_api37`,
  `hw.keyboard=yes`, `disk.dataPartition.size=4G` (the emulator wants about twice that free on the host), `-port
  5582`, and `adb -s emulator-5582 shell am switch-user 10` after a cold boot: the image's desktop session is user 10)
  and the secondary `Tembra_Desktop_API34` (no touchscreen, `-port 5580`), one desktop emulator at a time, and the lane
  commands from `specs/011-large-screens-desktop/quickstart.md` §1–§2
- [X] T002 [P] Add `androidx-window` (androidx.window `WindowMetricsCalculator`, latest stable) as a test-only library to
  `app/gradle/libs.versions.toml` and to `androidTestImplementation` in `app/androidApp/build.gradle.kts`, with a
  comment naming feature 011's device tests
- [X] T003 [P] Create the XCUITest target `TembraDesktopTests` and its scheme in `app/iosApp/iosApp.xcodeproj`
  (iPad-only destinations, `XCUIDevice.supportsPointerInteraction` guard helper in
  `app/iosApp/TembraDesktopTests/DesktopTestCase.swift`)
- [X] T004 Spike R3: build `main`'s app with the decided manifest (`<layout android:minWidth="360dp"
  android:minHeight="480dp"/>`, ChromeOS `WindowManagerPreference:FreeformWindowSize=tablet`, no default size) and
  record first-launch bounds, and relaunch bounds after a user resize (a mouse drag of the window's corner: a size set
  with `am task resize` is never remembered) and a window close with the caption's button (not a force-stop), using
  the adb script in quickstart §4 step 6, on `Tembra_Desktop_API37`, `Tembra_Desktop_API34` and the owner's tablet in desktop mode; write the outcome
  into `specs/011-large-screens-desktop/research.md` R3 (keep or revise the decision)
- [ ] T005 [P] Spike R12: on the iPad simulator, drop a folder and an audio file from the Files app onto a throwaway
  `Modifier.dragAndDropTarget`, log each `UIDragItem.itemProvider`'s registered type identifiers and whether a
  folder's contents are readable; record the outcome in `specs/011-large-screens-desktop/research.md` R12
  (2026-11-03: moved next to T107/T117, its only consumers: the drop needs a Files window beside the app's, driven
  across apps on the iPad simulator, so it runs against the real drop target once T113 exists)
- [X] T006 [P] Spike R6: on the iPad simulator with a hardware keyboard, check whether a matched SwiftUI `.commands`
  Command chord also reaches a Compose `onPreviewKeyEvent`; record the outcome in
  `specs/011-large-screens-desktop/research.md` R6 (decides contracts/keyboard-shortcuts.md rule 5)
- [X] T007 [P] Spike R22: on the iOS 16 and iOS 26 simulators, check whether `UTType(filenameExtension: "flac")` resolves
  to a system-declared type (`org.xiph.flac` or another identifier) or a dynamic one; record in
  `specs/011-large-screens-desktop/research.md` R22 whether `Info.plist` needs a `UTImportedTypeDeclarations` entry

**Checkpoint**: targets documented, harnesses exist, R3/R6/R12/R22 settled.

---

## Phase 2: Foundational

**Purpose**: performance names and the device-test helpers every story's tests use.

- [X] T008 Add `SHELL` to `PerfFamily` and `SHELL_WINDOW_RELAYOUT`, `SHELL_SHORTCUT_NAVIGATE` (family SHELL),
  `TIMELINE_CONTEXT_MENU` (family TIMELINE) to
  `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine/perf/PerfSection.kt`, `PerfFamily.kt` and
  `app/shared/engine/perf-catalogue.txt` (lines `SHELL_WINDOW_RELAYOUT shell shell.windowRelayout`,
  `SHELL_SHORTCUT_NAVIGATE shell shell.shortcutNavigate`, `TIMELINE_CONTEXT_MENU timeline timeline.contextMenu`)
- [X] T009 [P] Teach the pipeline the `shell` family: write the failing test in `app/perf/tests/test_catalogue.py`,
  then extend `app/perf/tracepulse/catalogue.py`
- [X] T010 [P] Create the desktop-window test helpers in `ATEST/desktop/DesktopWindow.kt`: `launchFreeform()` (`am
  start --windowingMode 5 -n com.tembraaudio/audio.tembra.android.MainActivity`), `taskId()`, `resizeTask(left, top,
  right, bottom)` (`am task resize` through `UiAutomation.executeShellCommand`; not clamped to any minimum),
  `dragResize(toX, toY)` (a mouse drag of the window's bottom-right corner with `input mouse swipe`, which the platform
  clamps to the window's minimum), `windowBoundsDp()`
  (`WindowMetricsCalculator`), `bringOtherTaskToFront()` and `bringAppToFront()` (for focus-loss tests),
  `assumeFreeform()` (skips with a visible reason on devices without `FEATURE_FREEFORM_WINDOW_MANAGEMENT`)
- [X] T011 [P] Create mouse and key injection helpers in `ATEST/desktop/DesktopInput.kt`: hover move, secondary press
  and release, primary press-drag, vertical and horizontal scroll with `MotionEvent` `SOURCE_MOUSE` through
  `UiAutomation.injectInputEvent`, the resolved pointer icon under a point (`View.onResolvePointerIcon` on the root
  view), and key combinations through `input keycombination`

**Checkpoint**: foundation ready; user stories can start.

---

## Phase 3: User Story 1 - Install and use it on a desktop with no touchscreen (Priority: P1) 🎯 MVP

**Goal**: Google Play offers the app to Googlebooks, Chromebooks and desktop devices with or without a touchscreen or
microphone; it opens wide and is fully usable with a mouse and a keyboard.

**Independent Test**: on `Tembra_Desktop_API34` (no touchscreen), mouse and keyboard only, the seven tasks of the spec's
User Story 1 test (quickstart §3); on `Tembra_Desktop_API37`, taps and clicks mixed; `aapt2 dump badging` shows
touchscreen and microphone not required.

### Tests for User Story 1 ⚠️ (write first, must fail)

- [X] T012 [P] [US1] `ATEST/ManifestFeaturesTest.kt`: the installed package's `reqFeatures` declare
  `android.hardware.touchscreen` and `android.hardware.microphone` with `required = false`, and `MainActivity`'s
  meta-data carries `WindowManagerPreference:FreeformWindowSize` = `tablet` (the window minimum is asserted by
  T027)
- [X] T013 [P] [US1] `ATEST/MicrophoneAvailabilityTest.kt`: with a fake audio-device seam reporting no input devices,
  `AndroidMicrophonePermission` reports `MicrophonePermissionStatus.Unavailable` without prompting; with one input it
  reports the permission status as today
- [X] T014 [P] [US1] `IOSTEST/platform/IosMicrophonePermissionTest.kt`: `isInputAvailable == false` on a fake session
  maps to `Unavailable`
- [X] T015 [P] [US1] `CTEST/tuner/ListeningPolicyTest.kt` (extend): `Unavailable` never requests listening and never
  prompts; By Ear is unaffected
- [X] T016 [P] [US1] `UITEST/tuner/TunerNoMicrophoneTest.kt`: Auto Listen with `Unavailable` shows "No microphone is
  available on this device" and no permission card; By Ear works
- [X] T017 [P] [US1] `UITEST/navigation/InitialFocusTest.kt`: for each destination (Timeline, Tuner, Metronome, Projects,
  Profile) the content root is focused once it appears, so one Tab press focuses its first control
  (`performKeyInput`; the lane declares keyboard support via `supportsKeyboardInputMode`)

### Implementation for User Story 1

- [X] T018 [US1] Declare `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`,
  `<uses-feature android:name="android.hardware.microphone" android:required="false"/>` and the ChromeOS
  `<meta-data android:name="WindowManagerPreference:FreeformWindowSize" android:value="tablet"/>` (inside
  `MainActivity`) in `app/androidApp/src/main/AndroidManifest.xml`, per T004's outcome
- [X] T019 [US1] Add `Unavailable` to `UI/platform/MicrophonePermissionStatus.kt` (`isDenied` stays false for it) and
  handle it in every exhaustive `when` over the status (no `else`)
- [X] T020 [US1] Report `Unavailable` in `ANDROID/AndroidMicrophonePermission.kt` when
  `!packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)` or `AudioManager.getDevices(GET_DEVICES_INPUTS)`
  is empty, re-evaluated on resume, behind a small audio-device seam the test fakes
- [X] T021 [P] [US1] Report `Unavailable` in `UIIOS/platform/IosMicrophonePermission.kt` when
  `AVAudioSession.sharedInstance().isInputAvailable` is false
- [X] T022 [US1] Make the tuner honour `Unavailable`: `UI/tuner/ListeningPolicy.kt` (cannot listen) and the Auto Listen
  pane's message in `UI/tuner/` (copy "No microphone is available on this device" in the tuner copy file)
- [X] T023 [US1] Request initial focus on each destination's content root from a `LaunchedEffect` keyed to its
  presence, with `Modifier.focusRequester(...).focusable()` (not `clickable`): `UI/tuner/TunerScreen.kt`,
  `UI/metronome/MetronomeScreen.kt`, `UI/projects/ProjectsScreen.kt`, `UI/profile/ProfileScreen.kt` (the timeline's
  existing request in `UI/timeline/TimelineScreenContent.kt` stays)
- [X] T024 [US1] Audit every touch gesture (long-press, pinch, swipe, sheet drag) for a pointer or keyboard equivalent
  and complete the "Gesture equivalents" table in `specs/011-large-screens-desktop/contracts/pointer-input.md`
  (FR-016); add a task to this file for any gap found

**Checkpoint**: US1 works alone: installable without touch or microphone, opens wide (per T004), usable by mouse
and keyboard with today's controls.

---

## Phase 4: User Story 2 - Resize, maximise and snap the window while the music plays (Priority: P1)

**Goal**: any window change, full screen included, keeps the audio running and the user's place intact; the window has
a 360 × 480 dp minimum on every platform.

**Independent Test**: quickstart §4; `WindowResizeAudioTest` (fifty window changes) green on `Tembra_Desktop_API37`.

### Tests for User Story 2 ⚠️

- [ ] T025 [P] [US2] `ATEST/KeyboardAttachTest.kt`: a configuration change with `CONFIG_KEYBOARD | CONFIG_KEYBOARD_HIDDEN
  | CONFIG_NAVIGATION | CONFIG_TOUCHSCREEN` does not recreate `MainActivity` (same instance, no `onCreate`)
- [ ] T026 [P] [US2] `ATEST/WindowResizeAudioTest.kt` (uses T010): in a freeform window, metronome at
  120 BPM, fifty window changes (resizes across 600 and 840 dp, maximise and restore, full screen and back) as SC-002
  requires; the engine's xrun and starvation counters stay 0 and the click count matches elapsed time ± 1; again with
  the demo project playing (playhead advancing, no restart)
- [ ] T027 [P] [US2] `ATEST/MinimumWindowTest.kt`: `MainActivity`'s `ActivityInfo.windowLayout` declares `minWidth`
  360 dp and `minHeight` 480 dp, and `dragResize` towards 300 × 400 dp stops at no less than 360 × 480 dp (`am task
  resize` bypasses the minimum, research hands-on check "Desktop (Preview)")
- [ ] T028 [P] [US2] `ATEST/SingleWindowTest.kt`: launching from the launcher intent while running brings the existing
  task forward (same task id, one task, one `MainActivity`)
- [ ] T029 [P] [US2] `CTEST/tuner/ListeningForegroundTest.kt`: `ON_PAUSE` alone keeps listening, `ON_STOP` stops it,
  `ON_START` resumes it (FR-006 minimise, FR-009 focus loss)
- [ ] T030 [P] [US2] `ATEST/ForegroundStateTest.kt` (uses T010): with the app in a freeform window, bringing
  another task to the front (pause only) keeps `ForegroundState` true; minimising (stop) makes it false; restoring
  (start) makes it true again
- [ ] T031 [P] [US2] `IOSTEST/platform/IosForegroundTest.kt`: a resign-active notification alone keeps the iOS foreground
  source true; did-enter-background makes it false; will-enter-foreground makes it true
- [ ] T032 [P] [US2] `UITEST/timeline/PlaceAcrossLayoutsTest.kt`: demo project scrolled to bar 9, zoomed, a clip
  selected and its menu open; host width 1200 → 700 → 400 → 1200 dp; after each step the same beat is at the left
  edge, zoom and selection unchanged, the menu still open on that clip
- [ ] T033 [P] [US2] `UITEST/projects/PlaceAcrossLayoutsProjectsTest.kt`: search text "ba" typed and a card menu open
  survive the same width steps
- [ ] T034 [P] [US2] `UITEST/navigation/FocusAcrossLayoutsTest.kt`: a focused track header and a focused tuner control
  keep focus across width-class changes; fallback to the destination root when the control no longer exists
- [ ] T035 [P] [US2] `ATEST/desktop/WindowFocusReturnTest.kt` (uses T010, T011): with a
  track header focused by Tab and the demo project playing, bring another task to the front and back; the same header
  is focused and the playhead never stopped (FR-009)
- [ ] T036 [P] [US2] `app/iosApp/TembraDesktopTests/WindowMinimumTests.swift` (iPadOS 26+ simulator): launched with
  `-tembra.reportWindowMinimum`, the shell's reported scene minimum is 360 × 480
- [ ] T037 [P] [US2] `UITEST/designsystem/WideWindowTest.kt`: at 2560 dp wide, Projects shows at most four columns,
  the tuner and metronome cards keep their expanded width centred, the timeline fills the width (FR-008)
- [ ] T038 [P] [US2] `UITEST/timeline/FullScreenMenuItemTest.kt` (fake `WindowControls`): the Timeline's overflow
  menu shows "Full screen" while `fullScreenAvailable` and not full screen, "Exit full screen" while full screen, and
  neither when unavailable; choosing them calls `enterFullScreen()` / `exitFullScreen()` (FR-010a)
- [ ] T039 [P] [US2] `ATEST/desktop/FullScreenTest.kt` (uses T010): in a freeform window with the
  metronome running, "Full screen" makes the window bounds equal the display, "Exit full screen" returns the previous
  bounds, and the click count matches elapsed time ± 1 throughout

### Implementation for User Story 2

- [ ] T040 [US2] In `app/androidApp/src/main/AndroidManifest.xml` add `keyboard|keyboardHidden|navigation|touchscreen`
  to `MainActivity`'s `android:configChanges` and `<layout android:minWidth="360dp" android:minHeight="480dp"/>`
- [ ] T041 [P] [US2] iOS minimum window: `.frame(minWidth: 360, minHeight: 480)` on the Compose view in
  `app/iosApp/iosApp/ContentView.swift`, `windowScene.sizeRestrictions?.minimumSize = CGSize(width: 360, height: 480)`
  on iPadOS 26+ in `app/iosApp/iosApp/iOSApp.swift`, and, in debug builds only, the `-tembra.reportWindowMinimum`
  launch argument that exposes the scene's minimum as an accessibility value
- [ ] T042 [US2] Drive the tuner's `foreground` input from start/stop, not pause or resign-active: fix
  `ANDROID/ForegroundState.kt` (`ON_START`/`ON_STOP`) and its iOS counterpart in `UIIOS/platform/`
  (will-enter-foreground / did-enter-background); `UI/tuner/PolicyInputs.kt` documents it
- [ ] T043 [US2] Store the timeline's horizontal position as the beat at the left edge plus zoom in the per-project view
  state (`view.json`, owned by the session in `UI/session/`), and re-derive pixels from it in
  `UI/timeline/TimelineScreen.kt` for every layout (`UI/timeline/TimelineLayout.kt`)
- [ ] T044 [US2] Hoist the timeline's vertical `LazyListState` above the layout switch in
  `UI/timeline/TimelineScreen.kt`, keyed by project id
- [ ] T045 [P] [US2] Move open-menu state for clips, track headers, project cards and the toolbar overflow to the owning
  screen's `rememberSaveable`, keyed by item id: `UI/timeline/components/TrackLane.kt`, `TrackHeader.kt`,
  `TrackHeaderCompact.kt`, `TimelineToolbar.kt`, `UI/projects/components/ProjectCard.kt`
- [ ] T046 [US2] Cancel a clip drag in progress when the layout class changes (the clip returns via
  `onClipDragCancel`) in `UI/timeline/components/TrackLane.kt`
- [ ] T047 [US2] Restore keyboard focus by semantic id after a layout switch and when the window regains focus,
  falling back to the destination root, in `UI/navigation/NavigationShellContent.kt`
- [ ] T048 [P] [US2] Cap reading widths for very wide windows (FR-008): Projects at most four columns in
  `UI/projects/ProjectsLayout.kt`; tuner and metronome cards centred at their expanded width in
  `UI/tuner/TunerLayout.kt` and `UI/metronome/MetronomeLayout.kt`
- [ ] T049 [US2] Create the seam `UI/platform/WindowControls.kt` (interface: `fullScreenAvailable: StateFlow<Boolean>`,
  `isFullScreen: StateFlow<Boolean>`, `enterFullScreen()`, `exitFullScreen()`) and the iOS binding
  `UIIOS/platform/IosWindowControls.kt` (always unavailable), bound in the iOS graph
- [ ] T050 [US2] Create `ANDROID/AndroidWindowControls.kt`: available on API 34+ while `isInMultiWindowMode` or the
  window is free-form; `Activity.requestFullscreenMode(FULLSCREEN_MODE_REQUEST_ENTER/EXIT, receiver)`; state refreshed
  from configuration and multi-window changes; bind it in `ANDROID/AndroidAppGraph.kt` from `ANDROID/MainActivity.kt`
- [ ] T051 [US2] Add the "Full screen" / "Exit full screen" item to the Timeline's overflow menu in
  `UI/timeline/components/TimelineToolbar.kt` (copy in `UI/timeline/TimelineCopy.kt`), reading `WindowControls`
- [ ] T052 [US2] Emit `SHELL_WINDOW_RELAYOUT` in `UI/navigation/NavigationShellContent.kt`: begin when the observed
  window size crosses a width or height class, end at the first frame at the new size

**Checkpoint**: US1 + US2: the app is a resilient desktop window.

---

## Phase 5: User Story 3 - Work with a mouse or trackpad the way desktop apps work (Priority: P2)

**Goal**: hover states, tooltips, menus at the pointer, plain drags, wheel and pinch everywhere; scrollbars while
scrolling by pointer; pointer areas that match what is drawn; pointer shapes, the busy pointer included, on Android and
ChromeOS.

**Independent Test**: quickstart §5 (pointer part); the US3 tests on both platforms and the desktop emulator.

### Tests for User Story 3 ⚠️

- [ ] T053 [P] [US3] `UITEST/designsystem/SecondaryClickTest.kt` (`performMouseInput { rightClick() }`): a clip, a
  track header (both layouts) and a project card open their menus at the click position; the primary `onClick` is not
  called on a right-click
- [ ] T054 [P] [US3] `UITEST/designsystem/TooltipCoverageTest.kt`: walking Timeline, Tuner, Metronome, Projects and the
  shell, every icon-only control shows a tooltip after an injected `HoverInteraction.Enter`, and every interactive
  control renders a hover state (screenshot of each design-system component hovered through an injected
  `MutableInteractionSource`)
- [ ] T055 [P] [US3] `UITEST/timeline/ViewportGesturesTest.kt` (extend): wheel scrolls tracks; Shift+wheel scrolls time;
  Ctrl+wheel and ⌘+wheel zoom keeping the beat under the pointer; an Android `PointerEventType.Scale` event zooms and a
  `Pan` event scrolls
- [ ] T056 [P] [US3] `UITEST/timeline/ClipDragEscapeTest.kt`: Escape during a mouse drag returns the clip to its start
- [ ] T057 [P] [US3] `UITEST/designsystem/TembraSliderWheelTest.kt`: one wheel notch up raises each slider (gain, noise
  gate, master volume) by one step, down lowers it
- [ ] T058 [P] [US3] `IOSTEST/timeline/TrackpadPinchTest.kt`: a `TrackpadPinch(scale = 2f, focus)` from a fake
  `TrackpadPinchSource` zooms the viewport around `focus`
- [ ] T059 [P] [US3] `ATEST/desktop/PointerOnDesktopTest.kt` (uses T011): on the desktop emulator, a real
  secondary mouse press on the first clip opens its menu, a real wheel event scrolls the timeline
- [ ] T060 [P] [US3] `ATEST/desktop/PointerShapesDesktopTest.kt` (uses T011; written before
  `PointerShapes`): the resolved pointer icon over a design-system button is `TYPE_HAND`, over the Projects search
  field `TYPE_TEXT`, over a clip `TYPE_GRAB` and `TYPE_GRABBING` while dragging it, and over the window while a long
  import runs `TYPE_WAIT` (FR-015, FR-015a)
- [ ] T061 [P] [US3] `CTEST/session/ProjectSessionBusyTest.kt`: `ProjectSession.busy` is true while an import copies files
  (a suspended fake importer) and false after it ends or fails
- [ ] T062 [P] [US3] `UITEST/designsystem/TembraScrollbarTest.kt`: a mouse wheel scroll over a list shows the scrollbar,
  it fades after a second at rest, a touch scroll does not show it, dragging the thumb with the mouse scrolls the
  content (FR-014a)
- [ ] T063 [P] [US3] `UITEST/designsystem/PointerAreaTest.kt`: for every design-system interactive component, the
  clickable node's bounds equal the bounds its hover indication is drawn over (FR-016a)

### Implementation for User Story 3

- [ ] T064 [US3] Create `Modifier.onSecondaryClick(key: Any?, onSecondaryClick: (Offset) -> Unit)` in
  `UI/designsystem/OnSecondaryClick.kt`: `PointerEventPass.Initial`, `PointerEventType.Press` with
  `buttons.isSecondaryPressed`, consume, report the local position
- [ ] T065 [US3] Replace the inline secondary-click code in `UI/timeline/components/TrackLane.kt` (lines ~224–228) with
  `onSecondaryClick`, opening the clip menu at the pointer
- [ ] T066 [US3] Add `offset: DpOffset = DpOffset.Zero` to `UI/designsystem/TembraMenu.kt` so a menu opened by a
  secondary click drops at the pointer
- [ ] T067 [P] [US3] Open the track menu at the pointer on secondary click in
  `UI/timeline/components/TrackHeader.kt` and `UI/timeline/components/TrackHeaderCompact.kt`
- [ ] T068 [P] [US3] Open the card menu at the pointer on secondary click in
  `UI/projects/components/ProjectCard.kt`
- [ ] T069 [P] [US3] Create `expect object PointerShapes { val grab: PointerIcon; val grabbing: PointerIcon; val busy:
  PointerIcon }` in `UI/designsystem/PointerShapes.kt` with actuals in
  `UIANDROID/designsystem/PointerShapes.android.kt` (`PointerIcon(android.view.PointerIcon.TYPE_GRAB / TYPE_GRABBING /
  TYPE_WAIT)`) and `UIIOS/designsystem/PointerShapes.ios.kt` (`PointerIcon.Default`)
- [ ] T070 [US3] Apply pointer shapes: `PointerIcon.Hand` on the design system's clickable components
  (`UI/designsystem/` buttons, pills, chips, toggles, menu items, navigation items), `PointerIcon.Text` on text
  fields, grab/grabbing on clips in `UI/timeline/components/TrackLane.kt`
- [ ] T071 [US3] Add `busy: StateFlow<Boolean>` to `UI/session/ProjectSession.kt`, true while an import copies files or
  waveform overviews are built (a counter of in-flight jobs, decremented in `finally`)
- [ ] T072 [US3] Show `PointerShapes.busy` over the shell's root in `UI/navigation/NavigationShellContent.kt` while
  `ProjectSession.busy` is true (FR-015a)
- [ ] T073 [P] [US3] Create `expect fun scrollNotches(delta: Offset): Offset` in `UI/designsystem/ScrollNotches.kt`
  with actuals in `UIANDROID/designsystem/` (pass through) and `UIIOS/designsystem/` (divide by the 0.01 pan
  multiplier)
- [ ] T074 [US3] Extend `UI/timeline/components/ViewportGestures.kt`: use `scrollNotches`, Shift swaps axes, handle
  `PointerEventType.Scale` (zoom around the pointer) and `PointerEventType.Pan` (scroll) for Android trackpads
- [ ] T075 [US3] Add wheel stepping to `UI/designsystem/TembraSlider.kt` (one notch = one step, up increases)
- [ ] T076 [US3] Fix the tooltip and hover-state gaps T054 finds, using `UI/designsystem/TembraTooltip.kt` and
  the components' interaction sources
- [ ] T077 [US3] Make every design-system interactive component draw its hover indication over its whole interactive
  bounds, with no invisible growth beyond the drawn shape and the 44 dp targets kept, fixing what T063
  finds in `UI/designsystem/` (research R20)
- [ ] T078 [P] [US3] Create `UI/designsystem/TembraScrollbar.kt` (`modifier` first, tokens only, vertical and
  horizontal) and `UI/designsystem/ScrollbarAdapter.kt` (from `LazyListState`, `ScrollState` and the timeline
  viewport's position and extent): visible while content scrolls from a pointer, fading after one second at rest,
  draggable with the mouse (research R18)
- [ ] T079 [US3] Apply `TembraScrollbar` to the timeline's track list and time axis (`UI/timeline/TimelineScreen.kt`),
  the Projects grid (`UI/projects/ProjectsScreen.kt`) and the tuner's and metronome's scrolling panes
  (`UI/tuner/`, `UI/metronome/`)
- [ ] T080 [US3] Cancel a clip drag on Escape (`onClipDragCancel`) in `UI/timeline/components/TrackLane.kt` and the
  timeline key handling
- [ ] T081 [US3] Create the iOS trackpad pinch path: interface `TrackpadPinchSource` (`Flow<TrackpadPinch>`) in
  `UI/platform/TrackpadPinchSource.kt` and `UI/platform/TrackpadPinch.kt` (data: `scale: Float`, `focus: Offset`), the
  iOS binding `UIIOS/platform/IosTrackpadPinchSource.kt`, the Swift bridge `app/iosApp/iosApp/TrackpadPinchBridge.swift`
  (`UIPinchGestureRecognizer`, `allowedTouchTypes = [.indirectPointer]`), and its consumption in
  `UI/timeline/components/ViewportGestures.kt`; add a comment that the bridge is deleted on Compose Multiplatform 1.13
- [ ] T082 [US3] Emit `TIMELINE_CONTEXT_MENU` from the secondary press on a clip or track header to the menu's first
  frame (`UI/timeline/components/`)

**Checkpoint**: US1–US3: desktop-grade pointer behaviour.

---

## Phase 6: User Story 4 - Drive the app from the keyboard (Priority: P2)

**Goal**: one shortcut catalogue feeds every handler, the shortcut sheet, Android's system list and the iPad menu bar.

**Independent Test**: quickstart §5 (keyboard part); the US4 tests on both platforms.

### Tests for User Story 4 ⚠️

- [ ] T083 [P] [US4] `CTEST/shortcuts/ShortcutCatalogueTest.kt`: no duplicate chord within `Everywhere` plus any one
  destination; no chord uses Alt or Meta; every label non-empty; every `ShortcutAction` has a handler
- [ ] T084 [P] [US4] `CTEST/shortcuts/KeyChordTest.kt`: `Character('s')` matches by `utf16CodePoint` even when `Key`
  differs (AZERTY); `primary` matches Ctrl on Android-like events and Meta on iOS-like events; KeyDown only
- [ ] T085 [P] [US4] `UITEST/shortcuts/ShellKeyHandlingTest.kt`: Ctrl+1–5 open Timeline, Tuner, Metronome, Projects,
  Profile; Ctrl+/ opens the sheet; Escape closes the topmost overlay (menu, sheet, sheet, dialog order) and returns
  focus to its opener; with focus in the Projects search field, "2" and "/" are typed and Ctrl+2 does nothing
- [ ] T086 [P] [US4] `UITEST/metronome/MetronomeKeyHandlingTest.kt`: Space starts and stops; ↑/↓ ±1 BPM; Shift ±10;
  repeat clamps to 40–240 BPM; T taps; arrows go to a focused slider instead
- [ ] T087 [P] [US4] `UITEST/tuner/TunerKeyHandlingTest.kt`: A and E switch modes; in By Ear, 1 plays string I (the
  highest) and 6 string VI on a 6-string guitar; 7 does nothing on 6 strings and plays on a 7-string; 4 is the last on
  cello; Space stops the tone; L toggles Loop Tone
- [ ] T088 [P] [US4] `UITEST/projects/ProjectsKeyHandlingTest.kt` (extend): Ctrl+F focuses the search; Ctrl+N starts a
  new project
- [ ] T089 [P] [US4] `UITEST/shortcuts/ShortcutSheetTest.kt`: every catalogue entry is listed, grouped by scope in
  navigation order; Profile's "Keyboard shortcuts" row opens the sheet; the sheet stays open across a width change;
  a wheel scroll over a long sheet shows its scrollbar
- [ ] T090 [P] [US4] `ATEST/KeyboardShortcutsHelperTest.kt`: `MainActivity.onProvideKeyboardShortcuts` returns one
  `KeyboardShortcutGroup` per scope containing every catalogue entry with Ctrl
- [ ] T091 [P] [US4] `app/iosApp/TembraDesktopTests/KeyboardTests.swift`: `typeKey("1", modifierFlags: .command)` opens
  Timeline, `typeKey("/", modifierFlags: .command)` opens the sheet, `typeKey(.escape, modifierFlags: [])` closes it; a
  Command chord dispatches exactly once (per T006)

### Implementation for User Story 4

- [ ] T092 [P] [US4] Create the catalogue types in `UI/shortcuts/`: `ShortcutScope.kt` (enum `Everywhere`, `Timeline`,
  `Tuner`, `Metronome`, `Projects`), `ChordKey.kt` (sealed `Character(char: Char)` lower-case, `Named(key: Key)`),
  `KeyChord.kt` (`key`, `primary: Boolean`, `shift: Boolean`, `matches(event)`: code point for `Character`, `Key` for
  `Named`, KeyDown only), `ShortcutAction.kt`, `Shortcut.kt`, `ShortcutCopy.kt`
- [ ] T093 [US4] Create `UI/shortcuts/ShortcutCatalogue.kt` with every entry of data-model.md's table (timeline entries
  mirroring `UI/timeline/TimelineKeyHandling.kt`)
- [ ] T094 [US4] Create `UI/shortcuts/ShortcutDispatcher.kt` (exhaustive `when` over `ShortcutAction`, no `else`) and
  `UI/shortcuts/ShellKeyHandling.kt` (`Modifier.shellKeyHandling`, preview pass, global chords, text-field rule, Escape
  order and focus return), attached in `UI/navigation/NavigationShellContent.kt`
- [ ] T095 [P] [US4] Create `UI/metronome/MetronomeKeyHandling.kt` and attach it to the metronome's content root in
  `UI/metronome/MetronomeScreen.kt`
- [ ] T096 [P] [US4] Create `UI/tuner/TunerKeyHandling.kt` (string number = the card's number, 1 = string I) and attach it
  in `UI/tuner/TunerScreen.kt`
- [ ] T097 [P] [US4] Create `UI/projects/ProjectsKeyHandling.kt` and attach it in `UI/projects/ProjectsScreen.kt`
- [ ] T098 [US4] Make `UI/timeline/TimelineKeyHandling.kt` read its chords from `ShortcutCatalogue` (behaviour unchanged;
  `TimelineKeyboardTest` stays green)
- [ ] T099 [US4] Create `UI/shortcuts/ShortcutSheet.kt` and `UI/shortcuts/ShortcutSheetGroup.kt` (`modifier` first, key
  caps per chord, groups as data, a `TembraScrollbar` from T078); its open state is `rememberSaveable` in
  `UI/navigation/NavigationShellContent.kt`
- [ ] T100 [US4] Add the "Keyboard shortcuts" row to `UI/profile/ProfileScreen.kt` (no ViewModel; callback from the
  shell)
- [ ] T101 [US4] Override `onProvideKeyboardShortcuts` in `ANDROID/MainActivity.kt`, building groups from
  `ShortcutCatalogue`
- [ ] T102 [US4] iPad menu commands: export the catalogue's Command chords and `ShortcutDispatcher` to Swift through the
  shared framework (`UIIOS/shortcuts/IosShortcutBridge.kt`), and build one `CommandMenu` per scope in
  `app/iosApp/iosApp/iOSApp.swift`; ignore Command chords in the Compose handlers on iOS if T006 showed double
  dispatch
- [ ] T103 [US4] Emit `SHELL_SHORTCUT_NAVIGATE` in `UI/shortcuts/ShortcutDispatcher.kt` for destination chords, ending
  at the destination's first frame

**Checkpoint**: US1–US4: the full desktop input set.

---

## Phase 7: User Story 5 - Bring audio from the desktop (Priority: P3)

**Goal**: the picker reaches desktop storage; files dropped on a track, on empty space or on Projects import by the
clarified rules; audio opened with Tembra Audio from the Files app starts a new project.

**Independent Test**: quickstart §5 (drops and open with); the US5 tests.

### Tests for User Story 5 ⚠️

- [ ] T104 [P] [US5] `CTEST/drop/DropPlannerTest.kt`: "exactly one audio file on a lane → `AddToTrack`" at the snapped
  beat; two files on a lane → `NewTracks`; files on space → `NewTracks`; Projects → `NewProject`; a readable folder with
  3 WAVs, a text file and a sub-folder → 3 files and 2 skipped (sub-folders not entered); an unreadable folder →
  `SkippedItem(name, UnreadableFolder)`; only non-audio → `Nothing`
- [ ] T105 [P] [US5] `UITEST/drop/AudioDropTargetTest.kt` (fake reader and event): hover shows "New track", "Add to
  <track> at bar N" or "New project" per zone; exit and end clear it; drop calls `ProjectSession.importAudio` with the
  planned `ImportTarget`; one message lists skipped names with reasons; no audio → "No audio files were found"
- [ ] T106 [P] [US5] `ATEST/AndroidDroppedFilesReaderTest.kt`: `content://` audio URIs from a test provider are read
  under granted permissions and released; a `vnd.android.document/directory` URI yields `Folder(contents = null)`;
  `application/x-arc-uri-list` is accepted
- [ ] T107 [P] [US5] `IOSTEST/drop/IosDroppedFilesReaderTest.kt`: item providers for an audio file and a folder map as
  T005 found
- [ ] T108 [P] [US5] `CTEST/drop/OpenedFilesFlowTest.kt`: files from a fake `OpenedFiles` become a new project with one
  track per audio file; with a project playing, the stop-and-leave confirmation is requested first and nothing happens
  on cancel; non-audio items are named in the skipped message (FR-026a)
- [ ] T109 [P] [US5] `ATEST/OpenWithTest.kt`: an `ACTION_VIEW` intent for a WAV `content://` URI from a test provider
  reaches `OpenedFiles` once; the package's intent filters resolve `ACTION_VIEW` for `audio/wav`, `audio/x-wav`,
  `audio/flac` and `audio/mpeg`; with the app running, a second `ACTION_VIEW` intent (with `FLAG_ACTIVITY_NEW_TASK`)
  arrives through `onNewIntent` in the same task (one task, one `MainActivity`)
- [ ] T110 [P] [US5] `IOSTEST/drop/IosOpenedFilesTest.kt`: a file URL handed to `IosOpenedFiles` produces one
  `DroppedItem.AudioFile` emission; a non-audio URL produces `Other(name)`

### Implementation for User Story 5

- [ ] T111 [P] [US5] Create the drop types in `UI/drop/`: `DropZone.kt` (sealed `TrackLane(trackId)`, `TimelineSpace`,
  `ProjectsGrid`), `DroppedItem.kt` (sealed `AudioFile(file: PickedFile)`, `Folder(name, contents:
  List<DroppedItem>?)` "null contents = the platform does not let the app read it", `Other(name)`), `DropPlan.kt`
  (sealed `AddToTrack(trackId, atBeats: Beats, file)`, `NewTracks(files, atBeats)`, `NewProject(files)`, `Nothing`),
  `DropOutcome.kt`, `SkippedItem.kt` (reasons `NotAudio`, `UnreadableFolder`), `DropHover.kt`
- [ ] T112 [US5] Create `UI/drop/DropPlanner.kt` (`plan(zone, items, snappedBeats)`: flatten readable folders one
  level; exactly one audio file on a lane → `AddToTrack`; otherwise lanes and space → `NewTracks`; Projects →
  `NewProject`; no audio → `Nothing`)
- [ ] T113 [US5] Create the seam `UI/drop/DroppedFilesReader.kt` (`accepts(event)`, `suspend read(event):
  List<DroppedItem>`) and `UI/drop/DropTargetModifier.kt` (`Modifier.audioDropTarget(zone, reader, onHover, onDrop)` over
  `dragAndDropTarget`, hover feedback via `onEntered`/`onMoved`/`onExited`/`onEnded`)
- [ ] T114 [US5] Add drop zones: each lane and the empty space in `UI/timeline/components/TrackLane.kt` and
  `UI/timeline/TimelineScreen.kt` (drop position → snapped beats through the viewport), and the grid in
  `UI/projects/ProjectsScreen.kt` (new project, open it, import as new tracks)
- [ ] T115 [US5] Messages: extend `UI/timeline/TimelineCopy.kt` (`filesRefused` merges skipped items with the importer's
  refusals; "No audio files were found"; hover labels)
- [ ] T116 [US5] Create `ANDROID/AndroidDroppedFilesReader.kt` (accept `audio/*`, `application/x-arc-uri-list`,
  `vnd.android.document/directory`; `activity.requestDragAndDropPermissions`, read, release after the import copied the
  files; directories → `Folder(contents = null)`) and bind it in `ANDROID/AndroidAppGraph.kt`
- [ ] T117 [US5] Create `UIIOS/drop/IosDroppedFilesReader.kt` (`event.items` providers,
  `loadFileRepresentationForTypeIdentifier("public.audio")` copied to the cache inside the callback; folders per
  T005) and bind it in the iOS graph
- [ ] T118 [US5] Create the seam `UI/drop/OpenedFiles.kt` (interface: `files: Flow<List<DroppedItem>>`) and collect it
  in `UI/navigation/NavigationShellContent.kt`: plan each emission as `DropZone.ProjectsGrid` (`NewProject`), behind
  the existing stop-and-leave confirmation when a project is playing
- [ ] T119 [US5] Android open with: in `app/androidApp/src/main/AndroidManifest.xml` set `MainActivity`'s
  `android:launchMode="singleTask"` and add the `VIEW` intent filter (category DEFAULT, scheme `content`, MIME types
  `audio/wav`, `audio/x-wav`, `audio/flac`, `audio/mpeg`); create `ANDROID/AndroidOpenedFiles.kt` fed from
  `ANDROID/MainActivity.kt`'s `onCreate` and `onNewIntent` (URIs read under the intent's grant, including `ClipData`
  for several files), bound in `ANDROID/AndroidAppGraph.kt`
- [ ] T120 [US5] iOS open with: `CFBundleDocumentTypes` for `com.microsoft.waveform-audio`, `public.mp3` and FLAC (plus
  `UTImportedTypeDeclarations` if T007 requires it), `LSHandlerRank` Alternate,
  `LSSupportsOpeningDocumentsInPlace` false in `app/iosApp/iosApp/Info.plist`; `.onOpenURL` in
  `app/iosApp/iosApp/iOSApp.swift` forwarding to `UIIOS/drop/IosOpenedFiles.kt`, bound in the iOS graph
- [ ] T121 [US5] Verify FR-025 by hand on the desktop emulators and the tablet: the picker offers Downloads and a
  removable drive (emulated USB storage); record in `specs/011-large-screens-desktop/quickstart.md` §5 notes

**Checkpoint**: US1–US5 complete.

---

## Phase 8: User Story 6 - Show the desktop experience in Google Play (Priority: P3)

**Goal**: four Chromebook-slot screenshots (the slot Googlebooks and Chromebooks show) from real desktop windows, and
the desktop app quality guideline tests in scope passing (SC-010), so the app meets the bar of Google's "Optimized for
desktop" review; the label itself is Google's to assign.

**Independent Test**: the screenshots meet FR-027 (count, 16:9, ≥ 1920×1080, real content, no debug UI, no text); the
guideline self-check (quickstart §6) records a pass for every test listed in SC-010.

- [ ] T122 [US6] Prepare realistic content on `Tembra_Desktop_API34` (its 3840×2160 display is 16:9; the Desktop
  (Preview) display is 16:10) with the release build (`tools/release-android.sh --serial emulator-5580`): the demo
  project and four projects with real names and imported audio (no seed or test data)
- [ ] T123 [US6] Capture the demo project playing, the tuner in By Ear, the metronome running and the library with the
  window maximised (`adb -s emulator-5580 exec-out screencap -p`), scale to 2560×1440 and save as JPEG without alpha in
  the local, git-excluded `play-store-listing/chromebook/c1-…c4-*.jpg` (quickstart §8); check each for debug chips,
  test data and added text
- [ ] T124 [US6] Hand the four files to the owner for Play Console's Chromebook slot and note them in
  `play-store-listing/store-listing.md` (local)
- [ ] T125 [US6] Guideline self-check (SC-010) on `Tembra_Desktop_API37` with the release build (quickstart §6): walk
  T-Scrollbar_Display, T-Hover_Parity, T-Desktop_Menus, T-Request_Fullscreen_Mode, T-Keyboard_Navigation,
  T-Keyboard_Parity, T-Custom_Cursors, T-Cursor_Target_Size, T-Drag_Drop_Batch (into the app), T-File_Picker,
  T-File_Handlers and T-Offline_Support (airplane mode); record pass or fail with a screenshot each in
  `specs/011-large-screens-desktop/screenshots/guidelines/` and `specs/011-large-screens-desktop/screenshots/NOTES.md`;
  any failure becomes a task in this file before the merge

---

## Phase 9: Performance instrumentation (cross-cutting, constitution)

- [ ] T126 [P] Add anchors `METRONOME_START`, `TIMELINE_FIRST_CLIP` (if absent), `PROJECTS_SEARCH`, `SHORTCUT_SHEET`
  to `UI/perf/PerfAnchors.kt`, tag the controls, and extend `CTEST/perf/PerfAnchorsCatalogueTest.kt`
- [ ] T127 Create the Android `windowing` journey `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/journeys/WindowingJourney.kt`
  and `WindowingJourneyBenchmark.kt` (contracts/perf-instrumentation.md steps; `am task resize` on freeform devices,
  rotation on phones)
- [ ] T128 [P] Create the iOS journey `app/iosApp/TembraPerfTests/Journeys/WindowingJourney.swift` (rotation as the
  window change, `rightClick()`, `typeKey(_:modifierFlags: .command)`; iPhone falls back to rotation and keys)
- [ ] T129 Add `windowing` to `journeys` in `app/tracepulse.yml` and to the pipeline's journey list; run one trial round
  on the Pixel 6 Pro and the iPhone 14; the round fails if `shell.windowRelayout`, `shell.shortcutNavigate` or
  `timeline.contextMenu` is absent from either trace, or if any xrun is recorded (this is the sections' test:
  `PerfTrace` records only in measurement builds through a sink internal to `:shared:engine`)
- [ ] T130 Compare the startup family with `tracepulse profile compare`; regenerate and commit the baseline profile
  (`app/androidApp/src/main/generated/baselineProfiles/`) only if it regresses beyond its gate

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T131 [P] Add README rows for the shortcut catalogue, drop import and open with, pointer helpers, scrollbars,
  window controls and the desktop lanes in `app/README.md` (module map and "where does X go")
- [ ] T132 [P] Add the desktop emulator commands (`Tembra_Desktop_API37` primary, `Tembra_Desktop_API34`) to `CLAUDE.md`'s
  command list and correct its build-order note (the mixer is now feature 012)
- [ ] T133 Screenshots for UI verification (constitution): compact and expanded widths on Android and the iOS
  simulator, plus desktop-window captures on `Tembra_Desktop_API37` (windowed and full screen) and an iPad window, into
  `specs/011-large-screens-desktop/screenshots/` with `NOTES.md`
- [ ] T134 Produce the Compose compiler report (`-PcomposeReports=true`) and explain any new non-skippable composable in
  `specs/011-large-screens-desktop/screenshots/NOTES.md`
- [ ] T135 Run every lane on every attached target: `checkAndroidHost`, `checkIos` (iPhone and iPad simulators),
  `checkAndroidDevice` and `:androidApp:connectedDebugAndroidTest` on `emulator-5582` (Desktop (Preview)),
  `:androidApp:connectedDebugAndroidTest` on `emulator-5580` (Android 14 desktop), the tablet and the phones,
  `TembraDesktopTests` on the iPad simulator; zero warnings (`--warning-mode all`); record each target's result in
  `specs/011-large-screens-desktop/quickstart.md` §2
- [ ] T136 Owner checks (FR-029): quickstart §3–§5 on both desktop emulators, the tablet in desktop mode and an iPad
  window, by ear for FR-006, including the relaunch-size check (§4 step 6), which instrumentation cannot make; record
  results in `specs/011-large-screens-desktop/quickstart.md`
- [ ] T137 After the merge: a labelled performance session (`--label 011-large-screens`) on each available device and
  the Desktop (Preview) emulator; attach the generated report from `app/perf/reports/` to the pull request

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none. The spikes settle decisions used later: T004 by T018;
  T006 by T091 and T102; T005 by T107 and T117;
  T007 by T120.
- **Foundational (Phase 2)**: after Setup; blocks every story (perf names, device-test helpers).
- **US1 (P1)** and **US2 (P1)**: after Foundational; independent of each other. Three tasks edit the manifest:
  T018 before T040 before T119.
- **US2 inside**: T030 and T031 fail before T042; T026 needs
  T050 for its full-screen changes.
- **US3 (P2)**: after Foundational; T045 (US2, menu state) before T067/T068 if both
  are in flight; T060 fails before T069; T061 fails before T071.
- **US4 (P2)**: after Foundational; T080 (US3, Escape cancels a drag) uses the Escape order from
  T094; T099 uses T078 (US3).
- **US5 (P3)**: after Foundational; T118 uses T112.
- **US6 (P3)**: after US1–US5 (screenshots and the guideline self-check need the finished behaviour).
- **Performance (Phase 9)**: after the sections exist (T052, T082, T103).
- **Polish (Phase 10)**: last; T137 after the merge.

### Within Each Story

Tests first and failing → types → logic → screens → platform bindings → perf emission.

### Parallel Opportunities

- Setup: T002, T003, T005, T006, T007 together.
- Foundational: T009, T010, T011 together after T008.
- US1 tests T012–T017 together; T020 and T021 together.
- US2 tests T025–T039 together; T041, T045, T048 together.
- US3 tests T053–T063 together; T067, T068, T069,
  T073, T078 together.
- US4 tests T083–T091 together; T095, T096, T097
  together after T094.
- US5 tests T104–T110 together; T116, T117, T119,
  T120 together after T113 and T118.

---

## Parallel Example: User Story 4

```bash
# Tests first, together:
Task: "CTEST/shortcuts/ShortcutCatalogueTest.kt"
Task: "CTEST/shortcuts/KeyChordTest.kt"
Task: "UITEST/shortcuts/ShellKeyHandlingTest.kt"
Task: "UITEST/metronome/MetronomeKeyHandlingTest.kt"
Task: "UITEST/tuner/TunerKeyHandlingTest.kt"
# Then the destination handlers, together, after the dispatcher (T094):
Task: "UI/metronome/MetronomeKeyHandling.kt"
Task: "UI/tuner/TunerKeyHandling.kt"
Task: "UI/projects/ProjectsKeyHandling.kt"
```

---

## Implementation Strategy

### MVP first (User Stories 1 and 2, both P1)

1. Setup and Foundational, including the four spikes.
2. US1: the app reaches non-touch desktops and Googlebooks, opens wide and is usable by mouse and keyboard with today's
   controls.
3. US2: window changes, full screen included, keep audio and place. **Stop and validate** on both desktop emulators
   and the tablet (quickstart §3–§4); this pair is releasable to internal testing on its own.

### Incremental delivery

4. US3 (pointer) and US4 (keyboard), each validated independently.
5. US5 (drops and open with), then US6 (store screenshots and the guideline self-check from the finished app).
6. Performance journey, polish, lanes on every target, owner checks, the post-merge session.
