# Feature Specification: Project Scaffold

**Feature Branch**: `001-project-scaffold`

**Created**: 2026-09-19

**Status**: Draft

**Input**: User description: "Initial scaffold for the application architecture. This should include all necessary dependencies, including native ones for audio processing and mixing. This initial part should not have any specific functionality implemented for the app, rather simple structure for the kotlin multi platform app. The architecture should be built based on the results that were made during the investigation phase via notebooklm that you did. Make sure that the app compiles. Add a blank screen with a button for instance, and verify whether tests are passing for both Android and iOS. You also know where the design guidelines and recommendations retrieved from Google Stitch live, so please take the screenshot with color palette, and create a design system in the shared component. This design scheme will be used both for Android and iOS apps for compose multiplatform UI implementation."

## User Scenarios & Testing *(mandatory)*

The users of this feature are the people who will build Tembra Audio: the project owner and
open-source contributors. No end-user functionality ships in this feature; its value is that
every later feature starts from a codebase that builds, runs, tests and looks right on both
platforms, and that the agreed architecture and design language exist as code rather than as
documents.

### User Story 1 - Build and launch on both platforms (Priority: P1)

A contributor clones the repository on a machine with the documented prerequisites, runs one
documented command per platform, and the app installs and opens on an Android emulator and on
an iOS simulator, showing the placeholder screen.

**Why this priority**: Nothing else can be verified until the two apps build and launch. This
is the riskiest integration in the whole build order (two platforms, one shared codebase, one
native component) and the constitution requires it to be proven first.

**Independent Test**: From a fresh clone, run the documented Android command and the documented
iOS command; both apps launch to the placeholder screen. Delivers a verified starting point for
every later feature.

**Acceptance Scenarios**:

1. **Given** a fresh clone and the documented prerequisites, **When** the contributor runs the
   documented Android build-and-run command, **Then** the app installs on the emulator and
   shows the placeholder screen within the success-criteria time budget.
2. **Given** the same clone, **When** the contributor runs the documented iOS build-and-run
   command, **Then** the app launches on the simulator and shows the same placeholder screen.
3. **Given** a machine missing one prerequisite, **When** the contributor runs a build command,
   **Then** the failure names the missing prerequisite and points to the setup section of the
   documentation.

---

### User Story 2 - Run the automated tests on both platforms (Priority: P1)

A contributor runs one documented test command per platform and sees every test pass. The
tests cover the shared logic, the placeholder screen's behaviour, the design tokens and the
link to the native audio engine component. Introducing a failing test makes the run fail.

**Why this priority**: The constitution makes test-first non-negotiable. The scaffold must
prove that tests can be written, run and made to fail on both platforms before any feature
code exists, otherwise later features cannot follow the rule.

**Independent Test**: Run the documented test commands; all pass. Change an expected value in
one test; the run reports exactly that failure on each platform.

**Acceptance Scenarios**:

1. **Given** the scaffold, **When** the contributor runs the documented shared-logic test
   command for the Android side, **Then** all tests pass and a summary shows the count.
2. **Given** the scaffold, **When** the contributor runs the documented shared-logic test
   command for the iOS side, **Then** the same tests pass on the iOS simulator.
3. **Given** the scaffold, **When** the contributor runs the placeholder-screen test,
   **Then** it verifies that pressing the button produces the visible change described in
   User Story 3.
4. **Given** the scaffold, **When** the contributor runs the native engine component's own
   test target, **Then** at least one test executes and passes without opening any audio
   device.
5. **Given** a deliberately broken assertion, **When** the tests run, **Then** the run fails
   and names the failing test.

---

### User Story 3 - See the shared design system rendered (Priority: P2)

A contributor or designer opens the app and sees a placeholder screen that uses the shared
design system: the product name, one button, a small caption reporting the native engine
component's name and version, and a preview of the design tokens (colour palette swatches
with their role names, and the two type families). Pressing the button produces a visible
change (a counter increments). Screenshots of this screen at phone and tablet widths on both
platforms are captured and compared against the Google Stitch design reference.

**Why this priority**: The design tokens must exist once, in shared code, before the first
real screen is built, and the only way to know they are right is to render them and compare
against the reference. The button proves that interaction and its test wiring work.

**Independent Test**: Launch the app, compare the palette preview against the values in the
design source of truth, press the button and watch the counter change, hover the button with
a mouse and see a tooltip.

**Acceptance Scenarios**:

1. **Given** the placeholder screen, **When** it is rendered at a phone width, **Then** the
   product name, the button, the engine caption and the token preview are all visible with
   no overlapping or clipped elements.
2. **Given** the placeholder screen, **When** it is rendered at a tablet or desktop-mode
   width, **Then** the layout adapts to the wider window (content is not stretched edge to
   edge and nothing overlaps).
3. **Given** the palette preview, **When** each swatch is compared with the design source of
   truth, **Then** every swatch's colour equals the token value it is labelled with.
4. **Given** the placeholder screen, **When** the user presses the button, **Then** the
   counter shown next to it increases by one.
5. **Given** a mouse or trackpad pointer, **When** it hovers over the button, **Then** the
   button shows a hover state and a tooltip describing the action.
6. **Given** a keyboard, **When** focus is moved to the button and the activation key is
   pressed, **Then** the counter increases and the focus indicator is visible.
7. **Given** the app on both platforms, **When** screenshots are captured at a compact and an
   expanded width, **Then** they are stored with the feature for review and the reviewer can
   confirm them against the Stitch reference screens.

---

### User Story 4 - Confirm the native audio component is part of the build (Priority: P2)

A contributor confirms that the shared audio engine component is compiled for both platforms
and reachable from the shared code, without any audio being produced. The engine reports its
name and version to the shared code; that value appears in the placeholder screen's caption
and is asserted by an automated test on each platform.

**Why this priority**: The native component is the hardest dependency to integrate and the
next feature (the audio spine) builds directly on it. Proving the link now removes the risk
from that feature while keeping this one free of audio functionality.

**Independent Test**: Run the engine link test on each platform; it passes. Launch the app;
the caption shows the engine's name and version.

**Acceptance Scenarios**:

1. **Given** the Android build, **When** the engine link test runs, **Then** the shared code
   receives the engine's name and version from the native component.
2. **Given** the iOS build, **When** the engine link test runs, **Then** the same values are
   received.
3. **Given** the running app on either platform, **When** the placeholder screen is shown,
   **Then** the caption displays the same name and version.
4. **Given** the scaffold, **When** the app runs, **Then** no audio device is opened and no
   microphone or audio permission is requested.

---

### User Story 5 - Understand where things go (Priority: P3)

A new contributor reads the application README and can tell, without asking, where a new
screen, a new domain rule, a new persistence concern and a new engine command belong; how to
build, run and test each platform; how to capture verification screenshots; and which coding
conventions apply.

**Why this priority**: The scaffold is the template every later feature copies. Clear
documentation of the structure prevents the architecture from eroding on the first real
feature.

**Independent Test**: Give the README to someone unfamiliar with the project and ask the four
"where does this go" questions; they answer correctly using only the README.

**Acceptance Scenarios**:

1. **Given** the README, **When** a contributor looks for the build, run, test and screenshot
   commands, **Then** each is present, copy-pasteable and works as written.
2. **Given** the README, **When** a contributor asks where domain logic, engine bridging,
   persistence and screens live, **Then** the module map answers each question.
3. **Given** the repository's agent guidance file, **When** an agent starts work, **Then** it
   finds the same build and test commands recorded there.

---

### Edge Cases

- A contributor on a machine without Apple tooling can still build the Android app and run the
  shared tests; iOS steps fail with a clear message rather than a cryptic error.
- The Android emulator uses a different processor architecture from phones; the native
  component must be available for the emulator architecture as well, otherwise the app
  crashes on launch.
- The window is resized live in Android desktop mode or on a foldable; the placeholder screen
  re-lays out without clipping or overlap at every intermediate width.
- The device or simulator is in light mode; the app keeps the design's dark theme (the design
  defines only a dark palette).
- Very large system font sizes are enabled; text wraps and the button stays reachable.
- A dependency version is bumped by accident; the build fails or warns because versions are
  pinned in one place.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST build an installable Android app and an installable iOS app
  from a fresh clone using one documented command per platform.
- **FR-002**: Both apps MUST open to the same placeholder screen containing the product name
  "Tembra Audio", one button with a visible counter, a caption with the native engine
  component's name and version, and a preview of the design tokens (colour swatches labelled
  with their role names, and samples of both type families).
- **FR-003**: Pressing the button MUST increase the counter by one; the counter MUST also be
  operable by keyboard and MUST show a tooltip and hover state under a pointer.
- **FR-004**: A single shared design system MUST define every colour role (including the
  DAW-specific roles such as playhead and clip fills), the type scale, the spacing scale, the
  radius scale and the elevation rule exactly as specified in `design/stitch/DESIGN.md` and
  `design/stitch/theme.json`, and MUST be the only source of visual values for screens. The
  placeholder screen MUST contain no literal colour, size, or font values.
- **FR-005**: The placeholder screen MUST adapt to compact and expanded window widths, MUST
  keep every interactive element at least 44 dp in size, MUST show no overlapping or clipped
  content at any supported width, and MUST expose accessibility semantics for every control.
- **FR-006**: The shared native audio engine component MUST be compiled for both platforms
  (including the Android emulator architecture), linked into both apps, and reachable from
  shared code through one call that returns the component's name and version. It MUST NOT
  open an audio device, request permissions, or perform any audio processing in this feature.
- **FR-007**: The scaffold MUST include automated tests that (a) exercise shared logic and run
  on both the Android side and the iOS simulator, (b) verify the button behaviour on the
  placeholder screen, (c) verify that the design tokens equal the values in the design source
  of truth, (d) verify the engine link on each platform, and (e) run inside the native
  component's own test target. One documented command per platform MUST run all tests that
  apply to that platform.
- **FR-008**: The codebase MUST be organised into the separately buildable parts named in the
  constitution (domain, engine bridge, persistence, user interface, and one thin app per
  platform), with dependencies pointing inward toward the domain part; a dependency in the
  wrong direction MUST fail the build. The persistence and engine-bridge parts MAY be empty
  shells in this feature but MUST exist with their tests wired.
- **FR-009**: All third-party dependencies MUST be declared in one place with pinned versions,
  and a licence inventory listing each dependency with its licence MUST be part of the
  repository. Every listed licence MUST be one the constitution permits.
- **FR-010**: Screenshots of the placeholder screen at a compact width and at an expanded width
  MUST be captured on Android (emulator or device) and on the iOS simulator, stored under the
  feature directory, and reviewed against the Stitch reference screens and palette before the
  feature is accepted. The capture procedure MUST be documented so it can be repeated for
  later features.
- **FR-011**: The application README MUST document prerequisites, the build, run, test and
  screenshot commands for each platform, the module map with "where does X go" guidance, and
  the coding conventions from the constitution. The repository's agent guidance file MUST
  record the same build and test commands.
- **FR-012**: The feature MUST NOT implement any product functionality: no navigation between
  destinations, no transport bar, no timeline, no audio playback or recording, no project
  files, no settings.

### Key Entities *(include if feature involves data)*

- **Design Token Set**: the named visual values shared by every screen: colour roles (base
  scheme roles plus DAW-specific roles such as surface levels, playhead, clip fills and pad
  tones), type styles for the two families including tabular numerals for live values, the
  spacing scale, the radius scale, and the elevation rule. Derived one-to-one from
  `design/stitch/DESIGN.md` and `theme.json`.
- **Placeholder Screen State**: the number of times the button has been pressed (starts at
  zero, survives rotation and window resizing).
- **Engine Descriptor**: the native engine component's human-readable name and version string,
  reported through the shared code and displayed in the caption.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A contributor with the documented prerequisites installed goes from fresh clone
  to the placeholder screen running on the Android emulator and on the iOS simulator in under
  15 minutes of wall-clock time on a current Apple-silicon laptop, following only the README.
- **SC-002**: The full test run for each platform completes with zero failures in under 10
  minutes, and a deliberately broken assertion is reported as exactly one failure.
- **SC-003**: Screenshots at a 360 dp-wide phone layout and at an 840 dp-or-wider tablet layout,
  on both platforms, show zero overlapping, clipped or off-screen elements on visual review.
- **SC-004**: 100% of the colour swatches in the palette preview match the corresponding values
  in the design source of truth exactly, and the placeholder screen contains zero literal
  visual values (verified by review of its source).
- **SC-005**: The engine link test passes on both platforms and the caption shows the same name
  and version on both.
- **SC-006**: A contributor unfamiliar with the project answers the four "where does this go"
  questions correctly using only the README, in under 5 minutes.
- **SC-007**: The build produces zero compiler or lint warnings and the licence inventory
  contains zero disallowed licences.

## Assumptions

- Minimum supported versions are Android 8.0 (API 26) and iOS 16; these give access to the
  low-latency audio paths the later features need and cover the large majority of devices in
  use. They can be revised in the plan if the research recommends otherwise.
- The design defines only a dark palette, so the app renders the dark theme regardless of the
  system light/dark setting in this feature.
- "Native dependencies for audio processing and mixing" means the shared engine component and
  its permitted libraries are compiled and linked so that later features can use them; no
  audio input or output happens in this feature, and no audio permission is requested.
- "Tests passing on iOS" means tests running on the iOS simulator on a Mac; physical-device
  testing starts with the audio-spine feature, where real audio hardware matters.
- Screenshot verification in this feature is a documented, repeatable capture (using the
  locally installed `android` CLI for Android and the simulator for iOS) reviewed by a person
  against the Stitch reference; automated pixel-comparison against golden images is a later
  feature once the design system has stabilised.
- Continuous-integration configuration is out of scope until the repository has a remote; the
  documented commands are written so a pipeline can call them unchanged.
- The architecture follows the decisions already recorded in
  `docs/research/kmp-code-architecture.md`, `docs/research/kmp-architecture-tools.md` and the
  constitution; this specification does not re-open them. Technology choices belong in the
  plan.
- The navigation shell with the five primary destinations and the transport bar is deferred to
  the transport-and-timeline feature; this feature ships exactly one screen, as requested.
- No git branch was created for this feature because the repository has no commits yet and no
  branching hook is configured; the spec directory is `specs/001-project-scaffold`.
- Medium window widths (600–839 dp) render the same as expanded widths ("wide"); only
  compact widths (under 600 dp) use the single-column layout.
- User-visible text is English only.
