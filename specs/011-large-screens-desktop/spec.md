# Feature Specification: Large Screens and Desktop

**Feature Branch**: `011-large-screens-desktop`

**Created**: 2026-11-03

**Status**: Draft

**Input**: User description: "Let's do that, let's start a spec for large screen sizes support. Looks like there is
even a desktop emulator on android. Maybe you can check if we can use it as well." Context from the same session:
the owner asked about desktop screenshots for Google Play; Play shows them to Chromebooks, and the app is offered today
only to Chromebooks with a touchscreen. The feature: make Tembra Audio a first-class app on Chromebooks and in Android
desktop windowing (free-form, resizable windows), including devices without a touchscreen, used with a mouse or
trackpad and a keyboard; give it a Google Play presence on those devices; verify it on Android's desktop emulator.
The owner's answers (Clarifications) add iPad windows with a keyboard and a trackpad, and drag and drop of audio files.

## What exists today *(evidence for every decision below)*

- **The constitution already promises it.** Principle IV: layouts are driven by the available window "so the same
  build works on phones, tablets, foldables and Android desktop mode (free-form resizable windows, external
  displays)", and "pointer and keyboard input MUST be first-class". This feature carries that promise to real desktop
  devices; it adds no new musical capability, so it pulls nothing forward in the build order.
- **Every screen's specification already covers its own window sizes and inputs.** Navigation (002), the tuner (003
  User Story 6), the metronome (004 User Story 7, including the tempo dial turned by the scroll wheel), tabletop and
  book postures (005) and the timeline and library (009 User Story 8: hover states, tooltips on icon-only controls,
  space, home, arrows, S, delete, undo, redo, plus and minus, tab order, screen readers) each declare layouts for
  windows narrower than 600 dp, 600 to 839 dp and 840 dp or wider, and for a phone held sideways.
- **What has never been checked:** those requirements were verified with windows of fixed sizes in tests and on
  phones, tablets and a foldable, never in a real desktop window that the user drags to any size while music plays,
  on a device without a touchscreen, with a mouse as the only pointer.
- **The app cannot reach most of those devices.** Google Play offers it only to devices with a touchscreen, because
  the app does not say it can do without one, and only to devices with a microphone, because it asks for microphone
  access. Many Chromebooks and Android desktop devices have no touchscreen.
- **Google Play's large-screen presence.** Play shows Chromebook screenshots in their own slot: at least four
  recommended, up to eight, landscape 16:9 or portrait 9:16, each side between 1,080 and 7,680 pixels, showing the
  real large-screen experience without added text (Play Console help, checked 2026-11-03). The store listing has none.
- **iPad has desktop-style windows too.** iPadOS lets apps run in freely resizable windows used with a hardware
  keyboard and a trackpad; the same shared screens serve it.
- **A desktop emulator exists.** Android's SDK ships desktop system images (API 32, 33 and 34, for ARM64 and x86_64)
  and desktop device profiles (small, medium, large), checked on this machine on 2026-11-03; the ARM64 images run
  natively on the owner's Mac. The owner's tablet also has a desktop mode.

## Clarifications

### Session 2026-11-03

- Q: Does Chromebook support mean a separate app? → A: No. The same build and the same Google Play listing serve
  phones, tablets, foldables, Chromebooks and Android desktop windows (constitution Principle IV).
- Q: Do iPad windows (resizable windows, a hardware keyboard and a trackpad on iPadOS) belong to this feature, or
  only Android desktop and Chromebooks? → A: iPad belongs to it. Every acceptance scenario applies to iPad windows
  with a keyboard and a trackpad as well, and the new performance journey runs on iPad too.
- Q: Can the user drag audio files from the desktop's file manager onto the app to import them? → A: Yes. Files
  dropped on the timeline become new tracks; dropped on Projects, they start a new project (User Story 5, FR-026).

## User Scenarios & Testing *(mandatory)*

The people this feature serves: a musician practising at a desk with a Chromebook or an Android laptop and no
touchscreen; a producer who keeps Tembra Audio in a window beside a score, a recording or a video call and resizes it
as they work; someone on an Android tablet in desktop mode or on an iPad in a window, with a keyboard case and a mouse
or a trackpad; and anyone choosing the app on a Chromebook in Google Play.

### User Story 1 - Install and use it on a desktop with no touchscreen (Priority: P1)

On a Chromebook or an Android desktop device without a touchscreen, the user finds Tembra Audio in Google Play,
installs it and uses every destination with only a mouse or trackpad and a keyboard.

**Why this priority**: Nothing else in this feature matters to a user who cannot install the app or cannot reach a
control without touching the screen.

**Independent Test**: On a desktop device or emulator with no touchscreen, install the release build, then with only a
mouse and a keyboard: tune a string by ear, start the metronome at 96 BPM in 3/4, open the demo project and play it,
import an audio file, split a clip and move one piece to another track, undo both, and rename and duplicate a project.

**Acceptance Scenarios**:

1. **Given** a Chromebook or Android desktop device without a touchscreen, **When** the user looks for Tembra Audio
   in Google Play, **Then** the app is offered and installs.
2. **Given** a device without a microphone, **When** the user installs and opens the app, **Then** everything except
   Auto Listen works, and Auto Listen says that no microphone is available instead of asking for access.
3. **Given** a mouse or trackpad and a keyboard only, **When** the user works through every destination, **Then** each
   action that touch reaches by a gesture (long-press, pinch, swipe, drag) has a pointer or keyboard equivalent that
   needs no touchscreen.
4. **Given** the app opens in a desktop window, **When** the first screen appears, **Then** keyboard focus is in the
   window at a predictable place, so the first tab or shortcut works without a click first.

---

### User Story 2 - Resize, maximise and snap the window while the music plays (Priority: P1)

On a Chromebook, in Android desktop windowing or in an iPad window, the user drags the window's edges to any size,
maximises it, restores it, snaps or tiles it, minimises it or moves it to another display, while the metronome, a project, a reference tone or the tuner is running. The layout
follows the window continuously and the music never stops.

**Why this priority**: Resizing is what a desktop user does constantly; a click that drops or a project that stops
when the window changes would make the app unusable for practice.

**Independent Test**: With the metronome at 120 BPM, and again with the demo project playing, drag the window across
the 600 dp and 840 dp widths and back twenty times, maximise and restore it, snap it to each half, minimise and
restore it, and move it to a second display of a different density; listen for gaps and check the place in the app.
Repeat the resizing in an iPad window.

**Acceptance Scenarios**:

1. **Given** audio is running (metronome, project playback, a reference tone or Auto Listen), **When** the window is
   resized, maximised, restored, snapped, minimised or moved to another display, **Then** the audio continues without
   an audible gap, a missed or late click, or a restart of playback.
2. **Given** any destination, **When** the window changes size, **Then** the layout matches the new size at once,
   using the same layouts the screens already define for each width, with nothing clipped, overlapping or hidden
   behind the transport bar or the navigation.
3. **Given** the user's place (destination, open project, playhead, timeline scroll and zoom, selected clip, open
   sheet, dialog or menu, text being typed, keyboard focus), **When** the window changes size, **Then** that place is
   unchanged afterwards; the timeline keeps the same moment under the left edge of the view.
4. **Given** the window is made as small as it allows, **When** the user tries to shrink it further, **Then** it stops
   at a minimum size at which every screen is still usable.
5. **Given** a very large or very wide window (up to a 4K display and an ultrawide 21:9 display), **When** any
   destination is shown, **Then** it uses the space without stretching controls or text lines to an unreadable width.
6. **Given** the window loses focus because the user clicks another app, **When** audio is running, **Then** it keeps
   running, and when the user returns, keyboard focus is where it was.

---

### User Story 3 - Work with a mouse or trackpad the way desktop apps work (Priority: P2)

Everything a finger does by touch the mouse does in the way desktop users expect: hovering explains, right-clicking
opens menus, dragging moves, the wheel scrolls and adjusts, and the pointer shape says what will happen.

**Why this priority**: The app is usable with P1 alone; this makes it feel native rather than like a phone app in a
window.

**Independent Test**: On a desktop device and on an iPad, with a mouse, then with a trackpad, hover every icon-only
control on every destination,
right-click a clip, a track header and a project card, move a clip by dragging it, scroll and zoom the timeline, turn
the tempo with the wheel, and watch the pointer shape over each kind of element.

**Acceptance Scenarios**:

1. **Given** a pointer, **When** it rests on any icon-only control on any destination, including the navigation
   items, **Then** a tooltip names it, and every interactive control shows a hover state.
2. **Given** an item that has a menu (a clip, a track header, a project card), **When** the user right-clicks it or
   taps with two fingers on a trackpad, **Then** the item's menu opens at the pointer, the same menu that its overflow
   button or a long-press opens.
3. **Given** a clip, **When** the user presses the mouse button on it and drags, **Then** the clip moves at once
   (no long-press needed), within its track or to another track, snapping as it does by touch; **When** the user
   presses Escape during the drag, **Then** the clip returns to where it was.
4. **Given** the timeline, **When** the user scrolls the wheel, **Then** the tracks scroll vertically; with Shift held
   or a horizontal trackpad swipe, time scrolls horizontally; with the platform's zoom modifier held, or a trackpad
   pinch, the timeline zooms around the pointer.
5. **Given** a dial or a slider (tempo, noise gate, gain, master volume), **When** the user turns the wheel over it,
   **Then** the value changes in the control's own steps, in the same direction everywhere.
6. **Given** the pointer over an element, **Then** its shape says what it does: a hand over something to click, an
   open and a closed hand over a clip that can be dragged and while dragging it, a text cursor over a text field.

---

### User Story 4 - Drive the app from the keyboard (Priority: P2)

The user switches destinations, starts and stops the music, sets the tempo and closes what is open without reaching
for the mouse, and can look up every shortcut.

**Why this priority**: Keyboard control is what makes a desktop app fast; the screens already have their own keys,
but nothing ties them together or tells the user they exist.

**Independent Test**: On a desktop device and on an iPad with a hardware keyboard, open each destination by its
shortcut, start and stop the metronome and
the project, change the tempo, tap a tempo, open and close a sheet and a dialog, search the projects, and open the
list of shortcuts; then type in a text field and confirm no shortcut fires.

**Acceptance Scenarios**:

1. **Given** any destination, **When** the user presses the platform's modifier with 1 to 5, **Then** the app opens
   the destination at that position in the navigation (Timeline, Tuner, Metronome, Projects, Profile).
2. **Given** the Metronome, **When** the user presses space, **Then** the click starts or stops; up and down arrows
   change the tempo by 1 BPM, with Shift by 10 BPM, unless focus is on a control that uses the arrows itself (a
   slider or a selector), which keeps them; T taps the tempo.
3. **Given** Timeline, **When** the user presses the keys feature 009 defines (space, home, arrows, S, delete, undo,
   redo, plus, minus), **Then** they work in a desktop window exactly as specified there.
4. **Given** a sheet, a dialog or a menu is open, **When** the user presses Escape, **Then** the topmost one closes
   and focus returns to the control that opened it.
5. **Given** Projects, **When** the user presses the platform's find shortcut, **Then** focus moves to the search
   field; the platform's new shortcut starts a new project.
6. **Given** any destination, **When** the user asks the system for the keyboard shortcut overview, or opens the
   list from the app, **Then** every shortcut the app offers is listed, grouped by destination.
7. **Given** focus is in a text field, **When** the user types, **Then** no app shortcut fires, except Escape to leave
   the field.

---

### User Story 5 - Bring audio from the desktop (Priority: P3)

The user imports audio that lives where desktop users keep it: the Downloads folder, a USB drive, a folder the system
syncs.

**Why this priority**: Import already works through the system file picker; this makes sure it reaches desktop
storage and adds the desktop way of doing it: dragging files onto the app.

**Independent Test**: On a desktop device, import a WAV, a FLAC and an MP3 from the Downloads folder and one from a USB
drive through the picker into the demo project; then drag two files from the file manager onto the timeline and three
onto Projects; repeat the drag from the Files app into an iPad window.

**Acceptance Scenarios**:

1. **Given** a desktop device, **When** the user imports audio, **Then** the system file picker offers the device's
   storage locations, including removable drives, and the files import as they do on a phone.
2. **Given** audio files dragged from the desktop's file manager (or the Files app on iPad), **When** the user drops
   them on the timeline, **Then** each becomes a new track, as a picker import of several files does; dropped on
   Projects, they start a new project with them.
3. **Given** files are dragged over the app, **When** they hover a place that accepts them, **Then** the place shows
   that it will accept the drop; over a place that does not, nothing suggests a drop is possible.
4. **Given** a dropped file the importer refuses (format, length, channels, size), **When** it is dropped, **Then** it
   is refused with the importer's existing message, and the other files of the same drop still import.

---

### User Story 6 - Show the desktop experience in Google Play (Priority: P3)

A person browsing Google Play on a Chromebook sees screenshots of Tembra Audio in a desktop window, not stretched
phone screens.

**Why this priority**: It changes no behaviour, but it decides whether a desktop user installs the app.

**Independent Test**: Open the store listing's Chromebook screenshots and check their count, shape and content.

**Acceptance Scenarios**:

1. **Given** the store listing, **When** it is viewed on a Chromebook, **Then** it shows at least four screenshots
   taken from the app in a desktop window: the timeline playing, the tuner, the metronome and the projects library.
2. **Given** those screenshots, **Then** each is landscape 16:9, at least 1,920 by 1,080 pixels, shows content a real
   user would have (no test data, no debug-only controls, no added text), and matches the current release.

---

### Edge Cases

- **A touchscreen Chromebook, or a tablet with a mouse:** touch, mouse and keyboard work together; a touch gesture and
  a mouse action on the same item give the same result, and using one never disables the other.
- **A convertible switched between laptop and tablet mode while music plays:** the layout follows like any resize and
  the audio continues.
- **Resizing during a clip drag, a slider drag or a dial turn:** the gesture either completes at the new size or is
  cancelled cleanly with the value or clip as it was; it never leaves a half-moved clip.
- **Resizing with a sheet, a dialog or a menu open:** it stays open, fitted to the new size; a menu anchored to an item
  stays with the item or closes, never floating detached.
- **A window narrower than 600 dp on a desktop:** the phone layout applies, as on a phone, with the pointer and
  keyboard behaviour of this feature.
- **A window shorter than the phone-landscape height:** the screens use their short-window layouts (009's phone held
  sideways) down to the minimum window size.
- **Two displays of different densities:** moving the window between them keeps sizes in the same physical
  proportion and the audio running.
- **The app opened again from the launcher while it is open:** the existing window comes to the front; no second
  copy starts.
- **Non-US keyboard layouts:** shortcuts follow the character on the key (S, T, plus, minus), not its position.
- **Keys held down:** holding up or down on the Metronome repeats the tempo change at the system's key-repeat rate,
  within 40 to 240 BPM.
- **No audio output device, or the output changes while playing** (headphones, USB, Bluetooth): the app behaves as
  the earlier features specify on phones; nothing new is defined here.
- **A screen reader on a desktop device** (ChromeOS's own or TalkBack): every element announces what the earlier
  features specify, and the shortcuts of User Story 4 do not collide with the screen reader's own.
- **Recording (feature 010) is not on main yet:** the controls it adds MUST meet this feature's rules once both
  exist; this feature's tests cover what is on main when it is implemented.

## Requirements *(mandatory)*

### Functional Requirements

**Reach**

- **FR-001**: The app MUST be offered on Google Play to Chromebooks and Android desktop devices with or without a
  touchscreen, and to every device it reaches today.
- **FR-002**: The app MUST NOT require hardware that desktop devices commonly lack (touchscreen, telephony, camera,
  location, motion sensors). A microphone MUST NOT be required to install; without one, Auto Listen MUST say that no
  microphone is available, and every other function MUST work.

**Windows**

- **FR-003**: The app MUST run in a free-form window that the user can resize to any size between a minimum and the
  full display, maximise, restore, snap or tile and minimise, on Chromebooks, in Android desktop windowing and in
  iPad windows, with whichever of these controls the platform offers.
- **FR-004**: The minimum window size MUST be one at which every screen stays usable, and no smaller than 360 dp wide
  and 480 dp tall.
- **FR-005**: Every destination, sheet, dialog and menu MUST adopt the layout for the window's current size at once
  as the window changes, using the layouts the earlier features define for each width class and for short windows,
  with no restart of the app or of the screen.
- **FR-006**: Changing the window (resize, maximise, restore, snap, minimise, move to another display, switching a
  convertible between laptop and tablet mode) MUST NOT interrupt, restart or audibly disturb running audio: the
  metronome's clicks stay on time, project playback continues without a gap, a reference tone continues, Auto Listen
  keeps listening.
- **FR-007**: Changing the window MUST keep the user's place: destination, open project, playhead, timeline scroll
  (the same moment at the left edge of the view) and zoom, selection, open sheet, dialog or menu, text being edited
  and keyboard focus.
- **FR-008**: In windows wider than the widest layout the earlier features define, content MUST stay readable: text
  lines and control groups keep a sensible maximum width, while the timeline uses the full width for time.
- **FR-009**: Losing window focus MUST NOT stop audio; returning MUST restore keyboard focus to where it was.
- **FR-010**: The app MUST run as a single window; opening it again MUST bring the existing window to the front.

**Pointer**

- **FR-011**: Every interactive control on every destination MUST show a hover state, and every icon-only control,
  including the navigation items, MUST show a tooltip naming it on hover.
- **FR-012**: Every item that has a menu (clips, track headers, project cards) MUST open that menu at the pointer on a
  secondary click (right-click, or a two-finger tap on a trackpad).
- **FR-013**: A clip MUST move with a press-and-drag of the mouse or trackpad, without a long-press, within its track
  or to another track, with the same snapping as by touch; Escape during the drag MUST return it to where it was.
- **FR-014**: Wheel and trackpad scrolling MUST scroll lists and the timeline (vertically by default, horizontally
  with Shift or a horizontal swipe); the platform's zoom modifier with the wheel, or a trackpad pinch, MUST zoom the
  timeline around the pointer; the wheel over a dial or a slider MUST change it in the control's own steps.
- **FR-015**: The pointer's shape MUST indicate the action: a hand over clickable elements, an open hand over a
  draggable clip and a closed hand while dragging, a text cursor over text fields.
- **FR-016**: Every action that touch reaches through a gesture MUST have a pointer or keyboard equivalent that needs
  no touchscreen.

**Keyboard**

- **FR-017**: The platform's modifier with 1 to 5 MUST open the destination at that position in the navigation.
- **FR-018**: On the Metronome, space MUST start and stop the click, up and down arrows MUST change the tempo by 1 BPM
  (10 BPM with Shift, repeating while held, within 40 to 240 BPM) unless focus is on a control that uses the arrows
  itself, and T MUST tap the tempo.
- **FR-019**: Timeline's keys from feature 009 MUST work unchanged in desktop windows.
- **FR-020**: Escape MUST close the topmost sheet, dialog or menu and return focus to the control that opened it.
- **FR-021**: In Projects, the platform's find shortcut MUST focus the search field and its new shortcut MUST start a
  new project.
- **FR-022**: The app MUST list every shortcut it offers, grouped by destination, in the system's keyboard shortcut
  overview and in a list the user can open from the app.
- **FR-023**: No shortcut except Escape MUST fire while focus is in a text field; shortcuts MUST follow the character
  on the key, not its position, and MUST use the platform's usual modifier (Ctrl on Android and ChromeOS, Command on
  iPad).
- **FR-024**: When the app's window appears, keyboard focus MUST be in it at a predictable place, so the first tab
  or shortcut works without a click.

**Files**

- **FR-025**: Audio import MUST reach the desktop device's storage locations through the system file picker,
  including removable drives.
- **FR-026**: Audio files dragged from the desktop's file manager or the iPad's Files app and dropped on the timeline
  MUST import as new tracks, as a multi-file picker import does; dropped on Projects, they MUST start a new project
  containing them. A place that accepts a drop MUST show it while files hover over it. Files the importer refuses MUST
  be refused with the importer's existing messages, without stopping the other files of the drop.

**Store presence**

- **FR-027**: The Google Play listing MUST include at least four Chromebook screenshots taken from the app in a
  desktop window (the timeline playing, the tuner, the metronome, the projects library), landscape 16:9, at least
  1,920 by 1,080 pixels, with real content, no debug-only controls and no added text.

**Verification**

- **FR-028**: The app's automated interface tests MUST pass on Android's desktop emulator and on an iPad simulator in a
  resizable window, in addition to the existing targets, and these targets MUST include tests of the window changes of
  FR-005 to FR-007 while audio runs, of mouse-only and keyboard-only operation, and of drag and drop (FR-026).
- **FR-029**: Before the feature is done, the owner's checks MUST cover User Stories 1 to 5 on the desktop emulator,
  on the owner's tablet in desktop mode and in an iPad window, by ear for FR-006.

### Performance instrumentation declared by this feature

- Journeys added: one new journey, `windowing`, run in a desktop window: open the Metronome and start the click, drag
  the window across the 600 dp and 840 dp widths and back, maximise and restore it, stop the click; open the demo
  project, press play, resize again across both widths, right-click a clip and close its menu, switch destinations by
  shortcut, and pause. The same journey runs on iPad in a resizable window, so the journey exists on both platforms.
- Sections added: window relayout (from a size change to the first frame at the new size, per width class crossed),
  destination switch by shortcut, and context menu open (secondary click to the first frame of the menu).
- Counters: the audio-thread health counters of feature 007 (underruns, callback load) during every window change;
  the journey's acceptance is zero underruns while resizing on the reference devices.
- Anchors: the window itself (for the drivers' resize steps), the navigation items, the metronome's start control,
  the first clip and the Projects search field carry stable identifiers.
- Baseline profile: if the desktop launch path or a main interaction changes, the Android baseline profile MUST be
  regenerated and committed in this feature (feature 008's rule).
- After the merge the feature MUST be measured with a labelled session on every available device and on the desktop
  emulator; the report is pull-request evidence.

### Key Entities

- **Window**: the app's single free-form window on a desktop device; its size decides the layout (width class, short
  window), and its changes must leave audio and the user's place untouched.
- **Keyboard shortcut**: a key or key combination, the destination where it applies (or everywhere), the action it
  performs and the name under which the shortcut overview lists it.
- **Store screenshot (Chromebook)**: an image for Google Play's Chromebook slot, captured from the app in a desktop
  window, with its shape, size and the screen it shows.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a desktop device without a touchscreen, a first-time user completes all seven tasks of User Story 1's
  test with only a mouse and a keyboard, without help, in under 10 minutes in total.
- **SC-002**: Across 50 consecutive window changes (resizes across both width thresholds, maximise, restore, snap,
  minimise, a move between displays) with the metronome running and again with a project playing, there are zero
  audible gaps, zero missed or late clicks and zero playback restarts.
- **SC-003**: After every one of those 50 window changes, the user's place (destination, project, playhead, the moment
  at the left edge of the timeline, zoom, selection, open sheet or dialog, focus) is unchanged.
- **SC-004**: The layout settles at the new size in the time of one screen refresh after the window stops changing, and
  no content is clipped, overlapped or hidden at any size from the minimum window to a 4K display.
- **SC-005**: 100% of icon-only controls show a tooltip on hover; 100% of items with a menu open it on a secondary
  click; 100% of touch gestures have a pointer or keyboard equivalent.
- **SC-006**: Every shortcut the app offers appears in the shortcut overview, and none fires while typing in a text
  field.
- **SC-007**: Google Play lists the app as available on Chromebooks, including models without a touchscreen, and the
  listing shows at least four Chromebook screenshots that meet FR-027.
- **SC-008**: The automated interface tests pass on the desktop emulator and on the iPad simulator in a resizable
  window with zero failures, as on the existing targets.
- **SC-009**: Dropping audio files onto the timeline or Projects imports 100% of the files the picker would accept,
  and refuses the rest with the importer's messages, on a desktop device and on iPad.

## Assumptions

- "Desktop" means Chromebooks and Android desktop windowing (desktop mode on tablets and on external displays, and
  Android devices sold as computers). Google Play Games on PC is for games and is not a target.
- The desktop emulator used for verification is the newest Android desktop system image available (API 34 on
  2026-11-03); the app's minimum Android version (8.0) is below it, so the app runs there unchanged. The owner's tablet
  in desktop mode is the physical check; a real Chromebook is used if one becomes available, and its absence does not
  block the feature.
- The platform's usual modifier is Ctrl on Android and ChromeOS and Command on iPad.
- iPad windows that the user resizes freely exist on the iPadOS versions that offer them; on older versions the same
  layouts cover Split View and full-screen sizes, and nothing extra is required there.
- One window and one running copy of the app; several windows (for example, the tuner beside the timeline) are out of
  scope.
- The minimum window size of 360 by 480 dp is the smallest at which the earlier features' layouts are specified to
  work.
- Audio device behaviour (default output and input, what happens when a device is plugged or unplugged) is unchanged
  from the earlier features.
- Out of scope: a desktop menu bar, several windows, keyboard remapping, MIDI and hardware controllers, stylus input,
  game controllers, and any new musical capability.
- The store screenshots are captured after the feature's behaviour is done, from the release build, and are uploaded
  by the owner (or by the publishing workflow, if it later learns to update the listing).
