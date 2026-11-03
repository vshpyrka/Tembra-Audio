# Research: Large Screens and Desktop (feature 011)

Evidence gathered on 2026-11-03 by four parallel surveys (Android and ChromeOS platform; iPadOS windowing; Compose
Multiplatform 1.12.2 input APIs read from the pinned artifacts' sources and klib ABIs; the repository's existing input
code) and one hands-on check on Android's desktop emulator. "Unverified" marks what no source settled; each has a
spike or a device check in the tasks.

## Hands-on check: the desktop emulator (2026-11-03)

- Installed `system-images;android-34;android-desktop;arm64-v8a` (4.2 GB; Android 14, build UE2A.240712.001) and
  created AVD `Tembra_Desktop_API34` from the `desktop_medium` profile (3840×2160 at 320 dpi, 1920×1080 dp), with
  `hw.keyboard=yes`. It boots headless in under a minute on the owner's Mac (`-port 5580 -no-window`).
- It reports `android.hardware.type.pc`, `android.software.freeform_window_management`, a microphone, and **no**
  `android.hardware.touchscreen`: exactly the device Google Play filters the app away from today.
- `main`'s debug build installs and launches into a **freeform** task at **412×732 dp, portrait** (no `<layout>` in the
  manifest), a phone-shaped window on a 1920×1080 dp desktop.
- `adb shell am task resize 3 200 120 2600 1720` resized it to 1200×800 dp: same process, same `ActivityRecord` (no
  recreation, as the manifest's `configChanges` promise), and the layout switched to the rail and the wide timeline
  under the window's caption bar. Tests can therefore drive real window changes.

## R1. Reach on Google Play: declared hardware features

**Decision**: Add `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>` and
`<uses-feature android:name="android.hardware.microphone" android:required="false"/>`. Do not touch `faketouch`.

**Rationale**: `aapt2 dump badging` of the current APK lists two implied features: `faketouch` (default for all apps)
and `microphone` (implied by `RECORD_AUDIO`); touchscreen is implied for every app without a declaration. Explicit
declarations "always take precedence over features implied by the permissions" (uses-feature reference), and
chromeos.dev tells apps to make touchscreen not required. Marking `faketouch` not required would also open the app to
D-pad-only televisions, which it does not support. Nothing else (Play Console opt-in, quality tier) gates Chromebooks
(R16).

**Alternatives**: Excluding devices in Play Console's device catalog (manual, opposite of the goal).

## R2. A microphone that may not exist (FR-002)

**Decision**: Extend the existing seam rather than add one: `MicrophonePermissionStatus` gains `Unavailable`, reported
by the platform bindings before any permission prompt. Android: `PackageManager.hasSystemFeature(FEATURE_MICROPHONE)`
and at least one input in `AudioManager.getDevices(GET_DEVICES_INPUTS)`, re-checked on resume (USB microphones come and
go). iOS: `AVAudioSession.isInputAvailable`. The tuner shows "No microphone is available on this device" in Auto Listen
and never prompts; By Ear and every other destination are untouched.

**Rationale**: `kotlin-api-design` (keep the platform detail behind the semantic seam the tuner already uses:
`platform/MicrophonePermission.kt`). Today `TunerViewModel.startAudio` falls back to Idle silently when listening fails
(survey §5), which would leave a user without a microphone with no explanation.

## R3. Desktop windows on Android and ChromeOS: launch size, minimum, remembered size (FR-003, FR-004, FR-004a)

**Decision**:
- Add `<layout android:minWidth="360dp" android:minHeight="480dp"/>` to `MainActivity`: the minimum is enforced "in
  both split-screen and free-form modes".
- Do **not** declare `defaultWidth`/`defaultHeight`. In AOSP, a `<layout>` default size overrides the bounds the
  system saved for the task (`TaskLaunchParamsModifier` over `LaunchParamsPersister`), which would defeat "later
  launches reopen at the size and place the system remembers"; without one, Android's desktop windowing opens a new
  task at 75% of the display (`DesktopModeBoundsCalculator`), a wide landscape window on any desktop display.
- Add ChromeOS's `<meta-data android:name="WindowManagerPreference:FreeformWindowSize" android:value="tablet"/>`
  (chromeos.dev window management) so ChromeOS opens the app at its landscape tablet size the first time.
- Spike before implementation (tasks): measure first-launch and relaunch bounds on the desktop emulator (Android 14)
  and on the owner's tablet in desktop mode with the decided manifest; Android 14's freeform launches phone-sized
  (412×732 dp above). If a platform still opens portrait, record it; FR-004a's "where the platform lets the app choose"
  covers platforms that do not honour a hint, and declaring a default size is rejected because it breaks restoring.

**Rationale**: FR-004a has two halves; the platform decides the second, and the first is achievable without
sacrificing it on every platform the evidence covers.

**Alternatives**: `defaultWidth=1200dp defaultHeight=800dp` (reliable first launch, but the saved size is ignored on
every launch); `ActivityOptions.setLaunchBounds` (only when the app launches itself, never from the launcher).

## R4. Window changes and the lifecycle on Android (FR-005 to FR-009)

**Decision**:
- Add `keyboard|keyboardHidden|navigation|touchscreen` to `MainActivity`'s `configChanges`. Today attaching or detaching
  a hardware keyboard, or a convertible switching to tablet mode, recreates the Activity (Compose research §7; R.attr):
  `ViewModel` and `rememberSaveable` survive, plain `remember` (scroll positions, open menus, a drag) does not.
- Resize, maximise, snap and moving to a display of another density already arrive as `onConfigurationChanged`
  (verified above): the window size flows through `LocalWindowInfo`/`currentWindowAdaptiveInfoV2()` and the layouts
  re-pick (`theme/WindowWidth.kt`, `navigation/RememberLayoutMode.kt`, `timeline/TimelineLayout.kt`).
- "Leaves the foreground" stays `ON_STOP` (minimising takes the window off screen: `onStop`). Losing focus to another
  window keeps every visible activity RESUMED (Android 10+ multi-resume), firing `onTopResumedActivityChanged(false)`
  only, so FR-009's "audio keeps running while visible" holds with the tuner's existing `ListeningPolicy`
  (`screenVisible`, `foreground` inputs), provided its `foreground` input is driven by start/stop and not by pause;
  a test pins that.

**Rationale**: FR-006 and FR-007 need no restart for any window change; the audio lives in the engine and the session,
which never observe the window.

## R5. iPad windows (FR-003, FR-004, FR-004a, FR-006)

**Decision**: Keep the shell's declarations (scene lifecycle through the SwiftUI `App`, `UIApplicationSupportsMultipleScenes`
false, all four iPad orientations, launch screen, no `UIRequiresFullScreen`): TN3192 lists exactly these for resizable
windows on iPadOS 26+, and the same keys give Split View, Slide Over and Stage Manager on iPadOS 16–25. Set the minimum
with `.frame(minWidth: 360, minHeight: 480)` on the hosting view plus `windowScene.sizeRestrictions?.minimumSize`
(best effort, iPadOS 26+). iPadOS has no API for an initial window size and remembers sizes itself (HIG Windows), so
FR-004a's first half applies to Android and ChromeOS only (spec updated, "where the platform lets the app choose").
Leaving the foreground maps to Compose's `ON_STOP` (`didEnterBackground`/`viewDidDisappear`); `ON_PAUSE` also fires for
system alerts and is too eager. Losing focus to another window does not resign active.

**Unverified**: whether minimising an iPad window sends the scene to the background (device check in the quickstart).

## R6. Keyboard shortcuts: one catalogue, three consumers (FR-017 to FR-024, FR-018a)

**Decision**: A `ShortcutCatalogue` in `:shared:ui` `commonMain` (data-model.md) is the single source of every
shortcut: its chord, scope (everywhere or one destination), the action id and its label. Three consumers read it:
1. Compose key handling: one `onPreviewKeyEvent` at the navigation shell's root for global chords (destinations, help,
   Escape) plus each destination's own handler for its scope (the timeline's existing `timelineKeyHandling` keeps its
   keys and is listed in the catalogue; the metronome, tuner and projects gain handlers). Each handler consumes exactly
   the event it handles (`compose-focus-navigation`).
2. The in-app shortcut sheet (R10).
3. The system lists: Android `Activity.onProvideKeyboardShortcuts` builds `KeyboardShortcutGroup`s from the catalogue
   (Meta+/ shows them; whether ChromeOS lists them is unverified, which the in-app sheet covers); iPad publishes the
   Command chords as SwiftUI `.commands` (the iPadOS 26 menu bar replaces the hold-⌘ list) whose actions call back into
   Kotlin through the same action ids.

On iPad, Command chords are dispatched by the menu commands and plain keys by Compose, so no chord is handled twice;
whether a matched `UIKeyCommand` still reaches Compose is unverified and pinned by an iOS test.

**Reserved chords** (research): Android desktop and ChromeOS reserve Meta/Search combinations, Alt+Tab, Alt+1–9,
Alt+[ ] = -, Ctrl+Space and Ctrl+Shift+Space; iPadOS reserves ⌘Space, ⌘Tab, ⌘W, ⌘M, ⇧⌘3/4 and Globe combinations.
Free on all three: Ctrl/⌘+1–5, Ctrl/⌘+/, Ctrl/⌘+F, Ctrl/⌘+N, Ctrl/⌘+Z, Ctrl/⌘+Shift+Z, Escape, Space. Android's
Meta+/ is the system's own shortcut list, so the app's help chord is Ctrl+/ there and ⌘/ on iPad.

**Matching by character** (FR-023): letter and symbol chords match `KeyEvent.utf16CodePoint` (lower-cased) first and
fall back to `Key` only for non-character keys (arrows, Escape, Space, digits on the number row): on iOS `Key` is the
physical position (an AZERTY "A" reports `Key.Q`), on Android it is layout-mapped.

**Alternatives**: hard-coding keys per screen (today's state, three lists to keep in sync); `UIKeyCommand` for every
key on iPad (would fight Compose's focus for plain keys).

## R7. Pointer shapes (FR-015)

**Decision**: `Modifier.pointerHoverIcon` with `PointerIcon.Hand` on clickable controls and `PointerIcon.Text` on text
fields from common code; the grab and grabbing shapes through a small `expect` value pair (`PointerShapes.grab`,
`PointerShapes.grabbing`): Android `PointerIcon(android.view.PointerIcon.TYPE_GRAB / TYPE_GRABBING)`, iOS
`PointerIcon.Default`. Compose 1.12.2 makes pointer icons a no-op on iOS ("uikit doesn't seem to have NSCursor"), and
iPadOS's own pointer adapts to controls; the spec now scopes app-chosen shapes to Android and ChromeOS.

**Rationale**: `kotlin-api-design` (a compile-time specialisation of a constant: `expect`/`actual` value).

## R8. Secondary click and menus at the pointer (FR-012)

**Decision**: Lift the clip lane's existing handling (`timeline/components/TrackLane.kt:219-229`) into one
`Modifier.onSecondaryClick(key) { position -> }` in `ui.designsystem`: it watches the `Initial` pass for a press with
`buttons.isSecondaryPressed`, consumes it (on Android a right-click otherwise also fires `clickable`'s `onClick`), and
reports the position. Apply it to clips (replacing the inline code), track headers (both layouts) and project cards.
`TembraMenu` gains an optional anchor offset so a menu opened by a secondary click drops at the pointer; overflow
buttons keep opening it at the button.

**Rationale**: Compose research §2: `PointerButton`, `PointerMatcher`, `onClick(matcher)` and `ContextMenuArea` are not
in common code; `isSecondaryPressed` is, and iOS maps `UIEvent.buttonMask` to it (two-finger trackpad click included,
since iOS 17 treats the missing `UIApplicationSupportsIndirectInputEvents` as YES).

## R9. Wheel, trackpad scroll and pinch (FR-014)

**Decision**:
- Normalise wheel deltas per platform behind an `expect fun scrollNotches(delta: Offset): Offset` (Android reports
  notches, iOS pan translation × 0.01); Shift swaps the axes in common code (neither platform does it).
- Timeline zoom modifier: Ctrl on Android and ChromeOS, ⌘ on iPad (`isCtrlPressed || isMetaPressed`, as
  `ViewportGestures.kt:70-89` already reads), around the pointer.
- Android trackpads (API 34+) deliver two-finger swipes as `Pan*` and pinches as `Scale*` pointer events, which
  `detectTransformGestures` ignores: `ViewportGestures` handles `PointerEventType.Scale`/`Pan` explicitly.
- iOS: trackpad pinch never reaches Compose 1.12.2 (no pinch recogniser; added upstream in 1.13.0-alpha02). The iOS
  shell adds a `UIPinchGestureRecognizer` restricted to indirect pointer input on the hosting view and forwards scale and
  location to a `TrackpadPinch` stream in Kotlin that the timeline viewport consumes. When the catalogue reaches Compose
  Multiplatform 1.13 stable, the bridge is deleted (a task note; Renovate raises the version).
- Sliders and dials (gain, noise gate, master volume, tempo) step with the wheel in their own steps, up or away from
  the user increases, everywhere (`TempoDial` already does; `TembraSlider` gains it).

**Alternatives**: waiting for Compose 1.13 for iPad pinch (leaves Principle IV unmet on iPad for an unknown time).

## R10. Shortcut sheet and its entry points (FR-022)

**Decision**: A `ShortcutSheet` composable renders the catalogue grouped by scope, opened by the help chord from any
destination and by a "Keyboard shortcuts" row on Profile. The sheet's open state lives in the navigation shell (a
`rememberSaveable` flag), so it survives window changes; Escape closes it (R6). Profile stays a destination without a
ViewModel: its content becomes the placeholder message plus the row, whose callback the shell supplies.

**Rationale**: `compose-state-and-effects` (shell owns the overlay state, Profile content stays plain);
`compose-component-design` (sheet takes `modifier` first, groups as data, no Boolean shape flags).

## R11. Initial and restored focus (FR-020, FR-024, FR-009)

**Decision**: Each destination's content root is `focusable()` with a `FocusRequester` requested from a `LaunchedEffect`
keyed to the condition that makes it present (the timeline already does this when `state.loaded`); the shell restores
focus to the destination root after Escape closes an overlay that has no originating control, and to the originating
control otherwise. `focusable()`, not `clickable`, because both platforms start in touch input mode, where
system-decided focusability is refused (Compose research §5).

## R12. Drag and drop of audio files (FR-026)

**Decision**: Common `Modifier.dragAndDropTarget` on three kinds of zone (a track's lane, the timeline's empty space,
the Projects grid), with `onEntered`/`onMoved`/`onExited`/`onEnded` driving the hover feedback that names what the drop
will do. A `DroppedFilesReader` interface turns a `DragAndDropEvent` into `PickedFile`s (the importer's existing input):
- Android binding (Activity-owned): accept `audio/*` and ChromeOS's `application/x-arc-uri-list`; in `onDrop` call
  `activity.requestDragAndDropPermissions(event.toAndroidDragEvent())`, read each `content://` item, release. A dragged
  folder arrives as one `vnd.android.document/directory` URI whose children are not readable: it is refused by name.
- iOS binding: Compose's iOS drop manager (a `UIDropInteraction` behind the same modifier) exposes `event.items`
  (`UIDragItem`, experimental API); each item's provider is read with `loadFileRepresentation(forTypeIdentifier:)` and
  copied into the app's cache inside the callback (the system deletes the original after it returns). How a folder
  arrives is unverified: a spike task reads it; if its contents are readable, its direct audio files are imported,
  otherwise it is refused by name.
- Drop plans are resolved in common code (data-model.md `DropPlan`): one file on a lane → `ImportTarget.Track(trackId,
  atBeats)` at the snapped drop position; several files, or empty space → `ImportTarget.NewTracks`; Projects → a new
  project then `NewTracks`. Import, overlap handling (`ProjectEditor.importToTrack` moves to `firstFreeStart`) and the
  refusal messages (`ProjectSession.kt:708-713`, `TimelineCopy.filesRefused`) are reused unchanged.

**Spec impact**: the folder rule now reads "where the platform lets the app read a dropped folder; otherwise the folder
is refused by name in the same message" (spec FR-026, planning findings).

## R13. Window size observation and place-keeping (FR-005, FR-007, SC-003)

**Decision**: No new size plumbing: `LocalWindowInfo`/`currentWindowAdaptiveInfoV2()` already update live on both
platforms (iOS steps through intermediate sizes during a resize animation, so a layout may flip mid-resize and must not
lose state). An audit (contracts/window-behaviour.md) lists every piece of the user's place and where it lives; anything
in plain `remember` that a layout switch disposes (timeline scroll and zoom anchor, open menus, a drag in progress)
moves to the session, the destination's ViewModel or `rememberSaveable`, keyed by semantic identity.

## R14. Testing in desktop windows (FR-028, FR-029)

**Decision**:
- Android desktop emulator lane: `tools/gradlew-device.sh emulator-5580 checkAndroidDevice` for the shared modules'
  device tests and `:androidApp:connectedDebugAndroidTest` for the app-level tests that need the real Activity (window
  resize while audio runs, drops, the system shortcut list). Tests launch the app with `am start --windowingMode 5`,
  resize with `am task resize <taskId> L T R B` through `UiAutomation.executeShellCommand`, read bounds from
  `WindowMetricsCalculator`, inject mouse hover, secondary press and scroll with `UiAutomation.injectInputEvent`
  (`SOURCE_MOUSE`), and keys with `input keycombination`.
- Compose UI tests (`uiTest`, both platforms): pointer and key behaviour with `performMouseInput` (`moveTo`,
  `rightClick`, `scroll`) and `performKeyInput`; hover appearance through injected `MutableInteractionSource`
  (`compose-ui-testing-patterns`); layout continuity by changing the test host's size.
- iPad: XCUITest (`hover`, `rightClick`, `scroll(byDeltaX:deltaY:)`, `typeKey(_:modifierFlags:)`, guarded by
  `XCUIDevice.supportsPointerInteraction`) in a new `TembraDesktopTests` target on an iPad simulator. Nothing can
  resize an iPad window or drag from Files in tests: those are manual checks (quickstart); rotation is the automated
  window change on iPad.
- Caveat: Google now calls "Desktop (Preview)" in Android Studio Canary the desktop test emulator; whether the API 34
  image runs Android 15/16's desktop windowing behaviour is unverified, so the owner's tablet in desktop mode is the
  second Android target.

## R15. Performance instrumentation (spec section, constitution)

**Decision**: A new family `SHELL` with sections `SHELL_WINDOW_RELAYOUT` (`shell.windowRelayout`: a window size change
to the first frame at the new size, per width class crossed) and `SHELL_SHORTCUT_NAVIGATE` (`shell.shortcutNavigate`:
a destination chord to the first frame of the destination); `TIMELINE_CONTEXT_MENU` (`timeline.contextMenu`: secondary
press to the menu's first frame). A new `windowing` journey on both platforms: on a freeform window it resizes with
`am task resize`; on phones and in the iOS journey the window change is a rotation. The audio counters of features 007
and 009 (xruns, starvation, callback load) gate it at zero underruns while the window changes. Anchors: navigation
items (exist), metronome start, first clip, Projects search; the window itself is addressed by its task id. The
pipeline's `catalogue.py` learns the new family; `tracepulse.yml` lists the journey.

## R16. Google Play large-screen presence (FR-027)

**Decision**: Chromebook screenshots are captured on the desktop emulator from the release build after the behaviour is
done: the window maximised to the 1920×1080 dp desktop with the taskbar visible, captured at 3840×2160 and scaled to
2560×1440 (16:9, within 1,080–7,680 px), showing the demo project playing, the tuner (By Ear, since Auto Listen captures
need a real tone and debug captures show the debug-only test-signal chip), the metronome running and a library of
realistic projects (not the test seed data). Nothing else is required for Chromebook availability: no opt-in exists;
the quality tiers and the large-screen badge are optional.

## R17. Dependencies

No new libraries in the shipped app. Everything uses Compose Multiplatform 1.12.2, the JetBrains and androidx Material 3
already pinned, and platform APIs. The app's device tests may need `androidx.window` (`WindowMetricsCalculator`) on their
classpath; it is a Jetpack library from the decided stack and test-only (the plan's dependency table records it). Compose Multiplatform 1.13 would remove the iPad pinch bridge (R9).
