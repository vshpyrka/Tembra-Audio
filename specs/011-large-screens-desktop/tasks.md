---
description: "Task list for feature 011: large screens and desktop"
---

# Tasks: Large Screens and Desktop

**Input**: Design documents from `/specs/011-large-screens-desktop/` (spec.md clarified 2026-11-03 with planning
findings; plan.md, research.md R1–R17, data-model.md, contracts/, quickstart.md)

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Required. The constitution makes test-first non-negotiable (Principle III): every test task below is
written first and must fail before the implementation task that follows it. Window resizing and pointer input are
tested on a real emulator or device (Android desktop emulator `Tembra_Desktop_API34`, iPad simulator).

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

**Purpose**: targets, test harness projects and the three spikes whose outcomes the later tasks depend on.

- [ ] T001 Document the Android desktop emulator target in `app/README.md` (§ Build and run and § Test lanes): the AVD
  `Tembra_Desktop_API34` creation commands, `hw.keyboard=yes`, booting with `-port 5580`, and the lane commands from
  `specs/011-large-screens-desktop/quickstart.md` §1–§2
- [ ] T002 [P] Add `androidx-window` (androidx.window `WindowMetricsCalculator`, latest stable) as a test-only library to
  `app/gradle/libs.versions.toml` and to `androidTestImplementation` in `app/androidApp/build.gradle.kts`, with a
  comment naming feature 011's device tests
- [ ] T003 [P] Create the XCUITest target `TembraDesktopTests` and its scheme in `app/iosApp/iosApp.xcodeproj`
  (iPad-only destinations, `XCUIDevice.supportsPointerInteraction` guard helper in
  `app/iosApp/TembraDesktopTests/DesktopTestCase.swift`)
- [ ] T004 Spike R3: build `main`'s app with the decided manifest (`<layout android:minWidth="360dp"
  android:minHeight="480dp"/>`, ChromeOS `WindowManagerPreference:FreeformWindowSize=tablet`, no default size) and
  record first-launch and relaunch bounds after a resize on `Tembra_Desktop_API34` and on the owner's tablet in desktop
  mode; write the outcome into `specs/011-large-screens-desktop/research.md` R3 (keep or revise the decision)
- [ ] T005 [P] Spike R12: on the iPad simulator, drop a folder and an audio file from the Files app onto a throwaway
  `Modifier.dragAndDropTarget`, log each `UIDragItem.itemProvider`'s registered type identifiers and whether a
  folder's contents are readable; record the outcome in `specs/011-large-screens-desktop/research.md` R12
- [ ] T006 [P] Spike R6: on the iPad simulator with a hardware keyboard, check whether a matched SwiftUI `.commands`
  Command chord also reaches a Compose `onPreviewKeyEvent`; record the outcome in
  `specs/011-large-screens-desktop/research.md` R6 (decides contracts/keyboard-shortcuts.md rule 5)

**Checkpoint**: targets documented, harnesses exist, R3/R6/R12 settled.

---

## Phase 2: Foundational

**Purpose**: performance names and the device-test helpers every story's tests use.

- [ ] T007 Add `SHELL` to `PerfFamily` and `SHELL_WINDOW_RELAYOUT`, `SHELL_SHORTCUT_NAVIGATE` (family SHELL),
  `TIMELINE_CONTEXT_MENU` (family TIMELINE) to
  `app/shared/engine/src/commonMain/kotlin/audio/tembra/engine/perf/PerfSection.kt`, `PerfFamily.kt` and
  `app/shared/engine/perf-catalogue.txt` (lines `SHELL_WINDOW_RELAYOUT shell shell.windowRelayout`,
  `SHELL_SHORTCUT_NAVIGATE shell shell.shortcutNavigate`, `TIMELINE_CONTEXT_MENU timeline timeline.contextMenu`)
- [ ] T008 [P] Teach the pipeline the `shell` family: write the failing test in `app/perf/tests/test_catalogue.py`,
  then extend `app/perf/tracepulse/catalogue.py`
- [ ] T009 [P] Create the desktop-window test helpers in `ATEST/desktop/DesktopWindow.kt`: `launchFreeform()` (`am
  start --windowingMode 5 -n com.tembraaudio/audio.tembra.android.MainActivity`), `taskId()`, `resizeTask(left, top,
  right, bottom)` (`am task resize` through `UiAutomation.executeShellCommand`), `windowBoundsDp()`
  (`WindowMetricsCalculator`), `assumeFreeform()` (skips with a visible reason on devices without
  `FEATURE_FREEFORM_WINDOW_MANAGEMENT`)
- [ ] T010 [P] Create mouse and key injection helpers in `ATEST/desktop/DesktopInput.kt`: hover move, secondary press
  and release, vertical and horizontal scroll with `MotionEvent` `SOURCE_MOUSE` through
  `UiAutomation.injectInputEvent`, and key combinations through `input keycombination`

**Checkpoint**: foundation ready; user stories can start.

---

## Phase 3: User Story 1 - Install and use it on a desktop with no touchscreen (Priority: P1) 🎯 MVP

**Goal**: Google Play offers the app to Chromebooks and desktop devices without a touchscreen or microphone; it opens
wide and is fully usable with a mouse and a keyboard.

**Independent Test**: on `Tembra_Desktop_API34`, mouse and keyboard only, the seven tasks of the spec's User Story 1
test (quickstart §3); `aapt2 dump badging` shows touchscreen and microphone not required.

### Tests for User Story 1 ⚠️ (write first, must fail)

- [ ] T011 [P] [US1] `ATEST/ManifestFeaturesTest.kt`: the installed package's `reqFeatures` declare
  `android.hardware.touchscreen` and `android.hardware.microphone` with `required = false`, and `MainActivity`'s
  `windowLayout` declares `minWidth` 360 dp and `minHeight` 480 dp
- [ ] T012 [P] [US1] `ATEST/MicrophoneAvailabilityTest.kt`: with a fake audio-device seam reporting no input devices,
  `AndroidMicrophonePermission` reports `MicrophonePermissionStatus.Unavailable` without prompting; with one input it
  reports the permission status as today
- [ ] T013 [P] [US1] `IOSTEST/platform/IosMicrophonePermissionTest.kt`: `isInputAvailable == false` on a fake session
  maps to `Unavailable`
- [ ] T014 [P] [US1] `CTEST/tuner/ListeningPolicyTest.kt` (extend): `Unavailable` never requests listening and never
  prompts; By Ear is unaffected
- [ ] T015 [P] [US1] `UITEST/tuner/TunerNoMicrophoneTest.kt`: Auto Listen with `Unavailable` shows "No microphone is
  available on this device" and no permission card; By Ear works
- [ ] T016 [P] [US1] `UITEST/navigation/InitialFocusTest.kt`: for each destination (Timeline, Tuner, Metronome, Projects,
  Profile) the content root is focused once it appears, so one Tab press focuses its first control
  (`performKeyInput`; the lane declares keyboard support via `supportsKeyboardInputMode`)

### Implementation for User Story 1

- [ ] T017 [US1] Declare `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`,
  `<uses-feature android:name="android.hardware.microphone" android:required="false"/>` and the ChromeOS
  `<meta-data android:name="WindowManagerPreference:FreeformWindowSize" android:value="tablet"/>` (inside
  `MainActivity`) in `app/androidApp/src/main/AndroidManifest.xml`, per T004's outcome
- [ ] T018 [US1] Add `Unavailable` to `UI/platform/MicrophonePermissionStatus.kt` (`isDenied` stays false for it) and
  handle it in every exhaustive `when` over the status (no `else`)
- [ ] T019 [US1] Report `Unavailable` in `ANDROID/AndroidMicrophonePermission.kt` when
  `!packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)` or `AudioManager.getDevices(GET_DEVICES_INPUTS)`
  is empty, re-evaluated on resume, behind a small audio-device seam the test fakes
- [ ] T020 [P] [US1] Report `Unavailable` in `UIIOS/platform/IosMicrophonePermission.kt` when
  `AVAudioSession.sharedInstance().isInputAvailable` is false
- [ ] T021 [US1] Make the tuner honour `Unavailable`: `UI/tuner/ListeningPolicy.kt` (cannot listen) and the Auto Listen
  pane's message in `UI/tuner/` (copy "No microphone is available on this device" in the tuner copy file)
- [ ] T022 [US1] Request initial focus on each destination's content root from a `LaunchedEffect` keyed to its
  presence, with `Modifier.focusRequester(...).focusable()` (not `clickable`): `UI/tuner/TunerScreen.kt`,
  `UI/metronome/MetronomeScreen.kt`, `UI/projects/ProjectsScreen.kt`, `UI/profile/ProfileScreen.kt` (the timeline's
  existing request in `UI/timeline/TimelineScreenContent.kt` stays)
- [ ] T023 [US1] Audit every touch gesture (long-press, pinch, swipe, sheet drag) for a pointer or keyboard equivalent
  and list them with their equivalents in `specs/011-large-screens-desktop/contracts/pointer-input.md` (a "Gesture
  equivalents" table); add a task note for any gap found (FR-016)

**Checkpoint**: US1 works alone: installable without touch or microphone, opens wide (per T004), usable by mouse and
keyboard with today's controls.

---

## Phase 4: User Story 2 - Resize, maximise and snap the window while the music plays (Priority: P1)

**Goal**: any window change keeps the audio running and the user's place intact; the window has a 360 × 480 dp
minimum on every platform.

**Independent Test**: quickstart §4 steps 1–8; `WindowResizeAudioTest` green on the desktop emulator.

### Tests for User Story 2 ⚠️

- [ ] T024 [P] [US2] `ATEST/KeyboardAttachTest.kt`: a configuration change with `CONFIG_KEYBOARD | CONFIG_KEYBOARD_HIDDEN
  | CONFIG_NAVIGATION | CONFIG_TOUCHSCREEN` does not recreate `MainActivity` (same instance, no `onCreate`)
- [ ] T025 [P] [US2] `ATEST/WindowResizeAudioTest.kt` (uses T009): in a freeform window, metronome at 120 BPM, resize
  across 600 and 840 dp twenty times, maximise and restore; the engine's xrun and starvation counters stay 0 and the
  click count matches elapsed time ± 1; again with the demo project playing (playhead advancing, no restart)
- [ ] T026 [P] [US2] `ATEST/MinimumWindowTest.kt`: `resizeTask` to 300 × 400 dp is clamped to at least 360 × 480 dp
- [ ] T027 [P] [US2] `ATEST/SingleWindowTest.kt`: launching from the launcher intent while running brings the existing
  task forward (same task id, one task)
- [ ] T028 [P] [US2] `CTEST/tuner/ListeningForegroundTest.kt`: `ON_PAUSE` alone keeps listening, `ON_STOP` stops it,
  `ON_START` resumes it (FR-006 minimise, FR-009 focus loss)
- [ ] T029 [P] [US2] `UITEST/timeline/PlaceAcrossLayoutsTest.kt`: demo project scrolled to bar 9, zoomed, a clip
  selected and its menu open; host width 1200 → 700 → 400 → 1200 dp; after each step the same beat is at the left
  edge, zoom and selection unchanged, the menu still open on that clip
- [ ] T030 [P] [US2] `UITEST/projects/PlaceAcrossLayoutsProjectsTest.kt`: search text "ba" typed and a card menu open
  survive the same width steps
- [ ] T031 [P] [US2] `UITEST/navigation/FocusAcrossLayoutsTest.kt`: a focused track header and a focused tuner control
  keep focus across width-class changes; fallback to the destination root when the control no longer exists
- [ ] T032 [P] [US2] `UITEST/designsystem/WideWindowTest.kt`: at 2560 dp wide, Projects shows at most four columns,
  the tuner and metronome cards keep their expanded width centred, the timeline fills the width (FR-008)

### Implementation for User Story 2

- [ ] T033 [US2] In `app/androidApp/src/main/AndroidManifest.xml` add `keyboard|keyboardHidden|navigation|touchscreen`
  to `MainActivity`'s `android:configChanges` and `<layout android:minWidth="360dp" android:minHeight="480dp"/>`
- [ ] T034 [P] [US2] iOS minimum window: `.frame(minWidth: 360, minHeight: 480)` on the Compose view in
  `app/iosApp/iosApp/ContentView.swift`, and `windowScene.sizeRestrictions?.minimumSize = CGSize(width: 360, height:
  480)` on iPadOS 26+ in `app/iosApp/iosApp/iOSApp.swift`
- [ ] T035 [US2] Drive the tuner's `foreground` input from start/stop, not pause: verify and fix
  `ANDROID/ForegroundState.kt` and its iOS counterpart in `UIIOS/platform/`; `UI/tuner/PolicyInputs.kt` documents it
- [ ] T036 [US2] Store the timeline's horizontal position as the beat at the left edge plus zoom in the per-project view
  state (`view.json`, owned by the session in `UI/session/`), and re-derive pixels from it in
  `UI/timeline/TimelineScreen.kt` for every layout (`UI/timeline/TimelineLayout.kt`)
- [ ] T037 [US2] Hoist the timeline's vertical `LazyListState` above the layout switch in
  `UI/timeline/TimelineScreen.kt`, keyed by project id
- [ ] T038 [P] [US2] Move open-menu state for clips, track headers, project cards and the toolbar overflow to the owning
  screen's `rememberSaveable`, keyed by item id: `UI/timeline/components/TrackLane.kt`, `TrackHeader.kt`,
  `TrackHeaderCompact.kt`, `TimelineToolbar.kt`, `UI/projects/components/ProjectCard.kt`
- [ ] T039 [US2] Cancel a clip drag in progress when the layout class changes (the clip returns via
  `onClipDragCancel`) in `UI/timeline/components/TrackLane.kt`
- [ ] T040 [US2] Restore keyboard focus by semantic id after a layout switch, falling back to the destination root, in
  `UI/navigation/NavigationShellContent.kt`
- [ ] T041 [P] [US2] Cap reading widths for very wide windows (FR-008): Projects at most four columns in
  `UI/projects/ProjectsLayout.kt`; tuner and metronome cards centred at their expanded width in
  `UI/tuner/TunerLayout.kt` and `UI/metronome/MetronomeLayout.kt`
- [ ] T042 [US2] Emit `SHELL_WINDOW_RELAYOUT` in `UI/navigation/NavigationShellContent.kt`: begin when the observed
  window size crosses a width or height class, end at the first frame at the new size

**Checkpoint**: US1 + US2: the app is a resilient desktop window.

---

## Phase 5: User Story 3 - Work with a mouse or trackpad the way desktop apps work (Priority: P2)

**Goal**: hover, tooltips, menus at the pointer, plain drags, wheel and pinch everywhere; pointer shapes on Android and
ChromeOS.

**Independent Test**: quickstart §5 (pointer part); the US3 UI tests on both platforms.

### Tests for User Story 3 ⚠️

- [ ] T043 [P] [US3] `UITEST/designsystem/SecondaryClickTest.kt` (`performMouseInput { rightClick() }`): a clip, a
  track header (both layouts) and a project card open their menus at the click position; the primary `onClick` is not
  called on a right-click
- [ ] T044 [P] [US3] `UITEST/designsystem/TooltipCoverageTest.kt`: walking Timeline, Tuner, Metronome, Projects and the
  shell, every icon-only control shows a tooltip after an injected `HoverInteraction.Enter`
- [ ] T045 [P] [US3] `UITEST/timeline/ViewportGesturesTest.kt` (extend): wheel scrolls tracks; Shift+wheel scrolls time;
  Ctrl+wheel and ⌘+wheel zoom keeping the beat under the pointer; an Android `PointerEventType.Scale` event zooms and a
  `Pan` event scrolls
- [ ] T046 [P] [US3] `UITEST/timeline/ClipDragEscapeTest.kt`: Escape during a mouse drag returns the clip to its start
- [ ] T047 [P] [US3] `UITEST/designsystem/TembraSliderWheelTest.kt`: one wheel notch up raises each slider (gain, noise
  gate, master volume) by one step, down lowers it
- [ ] T048 [P] [US3] `IOSTEST/timeline/TrackpadPinchTest.kt`: a `TrackpadPinch(scale = 2f, focus)` from a fake
  `TrackpadPinchSource` zooms the viewport around `focus`
- [ ] T049 [P] [US3] `ATEST/desktop/PointerOnDesktopTest.kt` (uses T010): on the desktop emulator, a real secondary
  mouse press on the first clip opens its menu, a real wheel event scrolls the timeline

### Implementation for User Story 3

- [ ] T050 [US3] Create `Modifier.onSecondaryClick(key: Any?, onSecondaryClick: (Offset) -> Unit)` in
  `UI/designsystem/OnSecondaryClick.kt`: `PointerEventPass.Initial`, `PointerEventType.Press` with
  `buttons.isSecondaryPressed`, consume, report the local position
- [ ] T051 [US3] Replace the inline secondary-click code in `UI/timeline/components/TrackLane.kt` (lines ~224–228) with
  `onSecondaryClick`, opening the clip menu at the pointer
- [ ] T052 [US3] Add `offset: DpOffset = DpOffset.Zero` to `UI/designsystem/TembraMenu.kt` so a menu opened by a
  secondary click drops at the pointer
- [ ] T053 [P] [US3] Open the track menu at the pointer on secondary click in
  `UI/timeline/components/TrackHeader.kt` and `UI/timeline/components/TrackHeaderCompact.kt`
- [ ] T054 [P] [US3] Open the card menu at the pointer on secondary click in
  `UI/projects/components/ProjectCard.kt`
- [ ] T055 [P] [US3] Create `expect object PointerShapes { val grab: PointerIcon; val grabbing: PointerIcon }` in
  `UI/designsystem/PointerShapes.kt` with actuals in `UIANDROID/designsystem/PointerShapes.android.kt`
  (`PointerIcon(android.view.PointerIcon.TYPE_GRAB / TYPE_GRABBING)`) and `UIIOS/designsystem/PointerShapes.ios.kt`
  (`PointerIcon.Default`)
- [ ] T056 [US3] Apply pointer shapes: `PointerIcon.Hand` on the design system's clickable components
  (`UI/designsystem/` buttons, pills, chips, toggles, menu items, navigation items), `PointerIcon.Text` on text
  fields, grab/grabbing on clips in `UI/timeline/components/TrackLane.kt`
- [ ] T057 [P] [US3] Create `expect fun scrollNotches(delta: Offset): Offset` in `UI/designsystem/ScrollNotches.kt`
  with actuals in `UIANDROID/designsystem/` (pass through) and `UIIOS/designsystem/` (divide by the 0.01 pan
  multiplier)
- [ ] T058 [US3] Extend `UI/timeline/components/ViewportGestures.kt`: use `scrollNotches`, Shift swaps axes, handle
  `PointerEventType.Scale` (zoom around the pointer) and `PointerEventType.Pan` (scroll) for Android trackpads
- [ ] T059 [US3] Add wheel stepping to `UI/designsystem/TembraSlider.kt` (one notch = one step, up increases)
- [ ] T060 [US3] Fix the tooltip gaps T044 finds, using `UI/designsystem/TembraTooltip.kt`
- [ ] T061 [US3] Cancel a clip drag on Escape (`onClipDragCancel`) in `UI/timeline/components/TrackLane.kt` and the
  timeline key handling
- [ ] T062 [US3] Create the iOS trackpad pinch path: interface `TrackpadPinchSource` (`Flow<TrackpadPinch>`) in
  `UI/platform/TrackpadPinchSource.kt` and `UI/platform/TrackpadPinch.kt` (data: `scale: Float`, `focus: Offset`), the
  iOS binding `UIIOS/platform/IosTrackpadPinchSource.kt`, the Swift bridge `app/iosApp/iosApp/TrackpadPinchBridge.swift`
  (`UIPinchGestureRecognizer`, `allowedTouchTypes = [.indirectPointer]`), and its consumption in
  `UI/timeline/components/ViewportGestures.kt`; add a comment that the bridge is deleted on Compose Multiplatform 1.13
- [ ] T063 [US3] Emit `TIMELINE_CONTEXT_MENU` from the secondary press on a clip or track header to the menu's first
  frame (`UI/timeline/components/`)

**Checkpoint**: US1–US3: desktop-grade pointer behaviour.

---

## Phase 6: User Story 4 - Drive the app from the keyboard (Priority: P2)

**Goal**: one shortcut catalogue feeds every handler, the shortcut sheet, Android's system list and the iPad menu bar.

**Independent Test**: quickstart §5 (keyboard part); the US4 tests on both platforms.

### Tests for User Story 4 ⚠️

- [ ] T064 [P] [US4] `CTEST/shortcuts/ShortcutCatalogueTest.kt`: no duplicate chord within `Everywhere` plus any one
  destination; no chord uses Alt or Meta; every label non-empty; every `ShortcutAction` has a handler
- [ ] T065 [P] [US4] `CTEST/shortcuts/KeyChordTest.kt`: `Character('s')` matches by `utf16CodePoint` even when `Key`
  differs (AZERTY); `primary` matches Ctrl on Android-like events and Meta on iOS-like events; KeyDown only
- [ ] T066 [P] [US4] `UITEST/shortcuts/ShellKeyHandlingTest.kt`: Ctrl+1–5 open Timeline, Tuner, Metronome, Projects,
  Profile; Ctrl+/ opens the sheet; Escape closes the topmost overlay (menu, sheet, sheet, dialog order) and returns
  focus to its opener; with focus in the Projects search field, "2" and "/" are typed and Ctrl+2 does nothing
- [ ] T067 [P] [US4] `UITEST/metronome/MetronomeKeyHandlingTest.kt`: Space starts and stops; ↑/↓ ±1 BPM; Shift ±10;
  repeat clamps to 40–240 BPM; T taps; arrows go to a focused slider instead
- [ ] T068 [P] [US4] `UITEST/tuner/TunerKeyHandlingTest.kt`: A and E switch modes; in By Ear, 1 plays string I (the
  highest) and 6 string VI on a 6-string guitar; 7 does nothing on 6 strings and plays on a 7-string; 4 is the last on
  cello; Space stops the tone; L toggles Loop Tone
- [ ] T069 [P] [US4] `UITEST/projects/ProjectsKeyHandlingTest.kt` (extend): Ctrl+F focuses the search; Ctrl+N starts a
  new project
- [ ] T070 [P] [US4] `UITEST/shortcuts/ShortcutSheetTest.kt`: every catalogue entry is listed, grouped by scope in
  navigation order; Profile's "Keyboard shortcuts" row opens the sheet; the sheet stays open across a width change
- [ ] T071 [P] [US4] `ATEST/KeyboardShortcutsHelperTest.kt`: `MainActivity.onProvideKeyboardShortcuts` returns one
  `KeyboardShortcutGroup` per scope containing every catalogue entry with Ctrl
- [ ] T072 [P] [US4] `app/iosApp/TembraDesktopTests/KeyboardTests.swift`: `typeKey("1", modifierFlags: .command)` opens
  Timeline, `typeKey("/", modifierFlags: .command)` opens the sheet, `typeKey(.escape, modifierFlags: [])` closes it; a
  Command chord dispatches exactly once (per T006)

### Implementation for User Story 4

- [ ] T073 [P] [US4] Create the catalogue types in `UI/shortcuts/`: `ShortcutScope.kt` (enum `Everywhere`, `Timeline`,
  `Tuner`, `Metronome`, `Projects`), `ChordKey.kt` (sealed `Character(char: Char)` lower-case, `Named(key: Key)`),
  `KeyChord.kt` (`key`, `primary: Boolean`, `shift: Boolean`, `matches(event)`: code point for `Character`, `Key` for
  `Named`, KeyDown only), `ShortcutAction.kt`, `Shortcut.kt`, `ShortcutCopy.kt`
- [ ] T074 [US4] Create `UI/shortcuts/ShortcutCatalogue.kt` with every entry of data-model.md's table (timeline entries
  mirroring `UI/timeline/TimelineKeyHandling.kt`)
- [ ] T075 [US4] Create `UI/shortcuts/ShortcutDispatcher.kt` (exhaustive `when` over `ShortcutAction`, no `else`) and
  `UI/shortcuts/ShellKeyHandling.kt` (`Modifier.shellKeyHandling`, preview pass, global chords, text-field rule, Escape
  order and focus return), attached in `UI/navigation/NavigationShellContent.kt`
- [ ] T076 [P] [US4] Create `UI/metronome/MetronomeKeyHandling.kt` and attach it to the metronome's content root in
  `UI/metronome/MetronomeScreen.kt`
- [ ] T077 [P] [US4] Create `UI/tuner/TunerKeyHandling.kt` (string number = the card's number, 1 = string I) and attach it
  in `UI/tuner/TunerScreen.kt`
- [ ] T078 [P] [US4] Create `UI/projects/ProjectsKeyHandling.kt` and attach it in `UI/projects/ProjectsScreen.kt`
- [ ] T079 [US4] Make `UI/timeline/TimelineKeyHandling.kt` read its chords from `ShortcutCatalogue` (behaviour unchanged;
  `TimelineKeyboardTest` stays green)
- [ ] T080 [US4] Create `UI/shortcuts/ShortcutSheet.kt` and `UI/shortcuts/ShortcutSheetGroup.kt` (`modifier` first, key
  caps per chord, groups as data); its open state is `rememberSaveable` in `UI/navigation/NavigationShellContent.kt`
- [ ] T081 [US4] Add the "Keyboard shortcuts" row to `UI/profile/ProfileScreen.kt` (no ViewModel; callback from the
  shell)
- [ ] T082 [US4] Override `onProvideKeyboardShortcuts` in `ANDROID/MainActivity.kt`, building groups from
  `ShortcutCatalogue`
- [ ] T083 [US4] iPad menu commands: export the catalogue's Command chords and `ShortcutDispatcher` to Swift through the
  shared framework (`UIIOS/shortcuts/IosShortcutBridge.kt`), and build one `CommandMenu` per scope in
  `app/iosApp/iosApp/iOSApp.swift`; ignore Command chords in the Compose handlers on iOS if T006 showed double dispatch
- [ ] T084 [US4] Emit `SHELL_SHORTCUT_NAVIGATE` in `UI/shortcuts/ShortcutDispatcher.kt` for destination chords, ending
  at the destination's first frame

**Checkpoint**: US1–US4: the full desktop input set.

---

## Phase 7: User Story 5 - Bring audio from the desktop (Priority: P3)

**Goal**: the picker reaches desktop storage; files dropped on a track, on empty space or on Projects import by the
clarified rules.

**Independent Test**: quickstart §5 (drops); the US5 tests.

### Tests for User Story 5 ⚠️

- [ ] T085 [P] [US5] `CTEST/drop/DropPlannerTest.kt`: "exactly one audio file on a lane → `AddToTrack`" at the snapped
  beat; two files on a lane → `NewTracks`; files on space → `NewTracks`; Projects → `NewProject`; a readable folder with
  3 WAVs, a text file and a sub-folder → 3 files and 2 skipped (sub-folders not entered); an unreadable folder →
  `SkippedItem(name, UnreadableFolder)`; only non-audio → `Nothing`
- [ ] T086 [P] [US5] `UITEST/drop/AudioDropTargetTest.kt` (fake reader and event): hover shows "New track", "Add to
  <track> at bar N" or "New project" per zone; exit and end clear it; drop calls `ProjectSession.importAudio` with the
  planned `ImportTarget`; one message lists skipped names with reasons; no audio → "No audio files were found"
- [ ] T087 [P] [US5] `ATEST/AndroidDroppedFilesReaderTest.kt`: `content://` audio URIs from a test provider are read
  under granted permissions and released; a `vnd.android.document/directory` URI yields `Folder(contents = null)`;
  `application/x-arc-uri-list` is accepted
- [ ] T088 [P] [US5] `IOSTEST/drop/IosDroppedFilesReaderTest.kt`: item providers for an audio file and a folder map as
  T005 found

### Implementation for User Story 5

- [ ] T089 [P] [US5] Create the drop types in `UI/drop/`: `DropZone.kt` (sealed `TrackLane(trackId)`, `TimelineSpace`,
  `ProjectsGrid`), `DroppedItem.kt` (sealed `AudioFile(file: PickedFile)`, `Folder(name, contents:
  List<DroppedItem>?)` "null contents = the platform does not let the app read it", `Other(name)`), `DropPlan.kt`
  (sealed `AddToTrack(trackId, atBeats: Beats, file)`, `NewTracks(files, atBeats)`, `NewProject(files)`, `Nothing`),
  `DropOutcome.kt`, `SkippedItem.kt` (reasons `NotAudio`, `UnreadableFolder`), `DropHover.kt`
- [ ] T090 [US5] Create `UI/drop/DropPlanner.kt` (`plan(zone, items, snappedBeats)`: flatten readable folders one
  level; exactly one audio file on a lane → `AddToTrack`; otherwise lanes and space → `NewTracks`; Projects →
  `NewProject`; no audio → `Nothing`)
- [ ] T091 [US5] Create the seam `UI/drop/DroppedFilesReader.kt` (`accepts(event)`, `suspend read(event):
  List<DroppedItem>`) and `UI/drop/DropTargetModifier.kt` (`Modifier.audioDropTarget(zone, reader, onHover, onDrop)` over
  `dragAndDropTarget`, hover feedback via `onEntered`/`onMoved`/`onExited`/`onEnded`)
- [ ] T092 [US5] Add drop zones: each lane and the empty space in `UI/timeline/components/TrackLane.kt` and
  `UI/timeline/TimelineScreen.kt` (drop position → snapped beats through the viewport), and the grid in
  `UI/projects/ProjectsScreen.kt` (new project, open it, import as new tracks)
- [ ] T093 [US5] Messages: extend `UI/timeline/TimelineCopy.kt` (`filesRefused` merges skipped items with the importer's
  refusals; "No audio files were found"; hover labels)
- [ ] T094 [US5] Create `ANDROID/AndroidDroppedFilesReader.kt` (accept `audio/*`, `application/x-arc-uri-list`,
  `vnd.android.document/directory`; `activity.requestDragAndDropPermissions`, read, release after the import copied the
  files; directories → `Folder(contents = null)`) and bind it in `ANDROID/AndroidAppGraph.kt`
- [ ] T095 [US5] Create `UIIOS/drop/IosDroppedFilesReader.kt` (`event.items` providers,
  `loadFileRepresentationForTypeIdentifier("public.audio")` copied to the cache inside the callback; folders per T005)
  and bind it in the iOS graph
- [ ] T096 [US5] Verify FR-025 by hand on the desktop emulator and the tablet: the picker offers Downloads and a
  removable drive (emulated USB storage); record in `specs/011-large-screens-desktop/quickstart.md` §5 notes

**Checkpoint**: US1–US5 complete.

---

## Phase 8: User Story 6 - Show the desktop experience in Google Play (Priority: P3)

**Goal**: four Chromebook screenshots from real desktop windows.

**Independent Test**: the screenshots meet FR-027 (count, 16:9, ≥ 1920×1080, real content, no debug UI, no text).

- [ ] T097 [US6] Prepare realistic content on `Tembra_Desktop_API34` with the release build
  (`tools/release-android.sh --serial emulator-5580`): the demo project and four projects with real names and imported
  audio (no seed or test data)
- [ ] T098 [US6] Capture the demo project playing, the tuner in By Ear, the metronome running and the library with the
  window maximised (`adb -s emulator-5580 exec-out screencap -p`), scale to 2560×1440 and save as JPEG without alpha in
  the local, git-excluded `play-store-listing/chromebook/c1-…c4-*.jpg` (quickstart §7); check each for debug chips, test
  data and added text
- [ ] T099 [US6] Hand the four files to the owner for Play Console's Chromebook slot and note them in
  `play-store-listing/store-listing.md` (local)

---

## Phase 9: Performance instrumentation (cross-cutting, constitution)

- [ ] T100 [P] Add anchors `METRONOME_START`, `TIMELINE_FIRST_CLIP` (if absent), `PROJECTS_SEARCH`, `SHORTCUT_SHEET`
  to `UI/perf/PerfAnchors.kt`, tag the controls, and extend `CTEST/perf/PerfAnchorsCatalogueTest.kt`
- [ ] T101 Create the Android `windowing` journey `app/perf-benchmarks/src/main/kotlin/audio/tembra/perf/journeys/WindowingJourney.kt`
  and `WindowingJourneyBenchmark.kt` (contracts/perf-instrumentation.md steps; `am task resize` on freeform devices,
  rotation on phones)
- [ ] T102 [P] Create the iOS journey `app/iosApp/TembraPerfTests/Journeys/WindowingJourney.swift` (rotation as the
  window change, `rightClick()`, `typeKey(_:modifierFlags: .command)`; iPhone falls back to rotation and keys)
- [ ] T103 Add `windowing` to `journeys` in `app/tracepulse.yml` and to the pipeline's journey list; run one trial round
  on the Pixel 6 Pro and the iPhone 14 and confirm the three sections and zero xruns
- [ ] T104 Compare the startup family with `tracepulse profile compare`; regenerate and commit the baseline profile
  (`app/androidApp/src/main/generated/baselineProfiles/`) only if it regresses beyond its gate

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T105 [P] Add README rows for the shortcut catalogue, drop import, pointer helpers and the desktop lanes in
  `app/README.md` (module map and "where does X go")
- [ ] T106 [P] Add the desktop emulator command to `CLAUDE.md`'s command list and correct its build-order note (the
  mixer is now feature 012)
- [ ] T107 Screenshots for UI verification (constitution): compact and expanded widths on Android and the iOS
  simulator, plus desktop-window captures on `Tembra_Desktop_API34` and an iPad window, into
  `specs/011-large-screens-desktop/screenshots/` with `NOTES.md`
- [ ] T108 Produce the Compose compiler report (`-PcomposeReports=true`) and explain any new non-skippable composable in
  `specs/011-large-screens-desktop/screenshots/NOTES.md`
- [ ] T109 Run every lane on every attached target: `checkAndroidHost`, `checkIos` (iPhone and iPad simulators),
  `checkAndroidDevice` and `:androidApp:connectedDebugAndroidTest` on `emulator-5580`, the tablet and the phones,
  `TembraDesktopTests` on the iPad simulator; zero warnings (`--warning-mode all`); record each target's result in
  `specs/011-large-screens-desktop/quickstart.md` §2
- [ ] T110 Owner checks (FR-029): quickstart §3–§5 on the desktop emulator, the tablet in desktop mode and an iPad
  window, by ear for FR-006; record results in `specs/011-large-screens-desktop/quickstart.md`
- [ ] T111 After the merge: a labelled performance session (`--label 011-large-screens`) on each available device and
  the desktop emulator; attach the generated report from `app/perf/reports/` to the pull request

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none. T004–T006 (spikes) settle decisions used by T017, T072/T083 and T088/T095.
- **Foundational (Phase 2)**: after Setup; blocks every story (perf names, device-test helpers).
- **US1 (P1)** and **US2 (P1)**: after Foundational; independent of each other (both edit the manifest: T017 before
  T033 to avoid conflicts).
- **US3 (P2)**: after Foundational; T038 (US2, menu state) before T053/T054 if both are in flight.
- **US4 (P2)**: after Foundational; T061 (US3, Escape cancels a drag) uses the Escape order from T075.
- **US5 (P3)**: after Foundational; independent.
- **US6 (P3)**: after US1–US5 (screenshots show the finished behaviour).
- **Performance (Phase 9)**: after the sections exist (T042, T063, T084).
- **Polish (Phase 10)**: last; T111 after the merge.

### Within Each Story

Tests first and failing → types → logic → screens → platform bindings → perf emission.

### Parallel Opportunities

- Setup: T002, T003, T005, T006 together.
- Foundational: T008, T009, T010 together after T007.
- US1 tests T011–T016 together; T019 and T020 together.
- US2 tests T024–T032 together; T034, T038, T041 together.
- US3 tests T043–T049 together; T053, T054, T055, T057 together.
- US4 tests T064–T072 together; T076, T077, T078 together after T075.
- US5 tests T085–T088 together; T094 and T095 together after T091.

---

## Parallel Example: User Story 4

```bash
# Tests first, together:
Task: "CTEST/shortcuts/ShortcutCatalogueTest.kt"
Task: "CTEST/shortcuts/KeyChordTest.kt"
Task: "UITEST/shortcuts/ShellKeyHandlingTest.kt"
Task: "UITEST/metronome/MetronomeKeyHandlingTest.kt"
Task: "UITEST/tuner/TunerKeyHandlingTest.kt"
# Then the destination handlers, together, after the dispatcher (T075):
Task: "UI/metronome/MetronomeKeyHandling.kt"
Task: "UI/tuner/TunerKeyHandling.kt"
Task: "UI/projects/ProjectsKeyHandling.kt"
```

---

## Implementation Strategy

### MVP first (User Stories 1 and 2, both P1)

1. Setup and Foundational, including the three spikes.
2. US1: the app reaches non-touch desktops, opens wide and is usable by mouse and keyboard with today's controls.
3. US2: window changes keep audio and place. **Stop and validate** on the desktop emulator and the tablet (quickstart
   §3–§4); this pair is releasable to internal testing on its own.

### Incremental delivery

4. US3 (pointer) and US4 (keyboard), each validated independently.
5. US5 (drops), then US6 (store screenshots from the finished app).
6. Performance journey, polish, lanes on every target, owner checks, the post-merge session.
