# Feature Specification: Tabletop Posture for the Tuner and the Metronome

**Feature Branch**: `005-tabletop-posture`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "Check the foldable devices, specifically the tabletop posture, and improve the UI for
such devices (official guidance: developer.android.com postures-and-orientation and make-your-app-fold-aware). In
tabletop posture the tuner shows the frequency gauge at the top and all the settings (guitar/cello toggle, mic active,
strings, Auto Listen / By Ear, the top settings) at the bottom. By Ear shows the grid of string cards at the top and all
the settings, including the tuning presets and loop tone, at the bottom. The metronome shows the BPM widget at the top
and the accent bars with only the accents at the top; all other settings at the bottom, including tap tempo, the
slider, play/stop, subdivisions, 4/4, styles. The top should only display the blinking accents and the BPM widget.
Research how to detect the posture with the latest documentation; test on the connected Pixel 9 Pro Fold emulator;
do not break existing functionality; run on the other connected devices and simulators (Pixel 6, iPhone, iPad). At the
end, push the screens to Google Stitch."

## What tabletop posture is *(evidence for every decision below)*

The two Android guides the user linked were read on 2026-10-02:

- *Postures and orientation* (design guidance): "A tabletop posture is partially folded in a horizontal position. This
  posture lets you use unique layouts, such as large controls and landscape video." Apps should be hinge-aware "so
  content flows smoothly with continuity and doesn't get lost in the hinge", and leave a wider gutter where content
  meets the hinge.
- *Make your app fold aware*: tabletop is a fold that is **half-opened** and **horizontal**. The guide's pattern places
  the primary content above the fold and the controls below it (media players, video calls, cameras), positions both
  from the fold's own location rather than from half the screen, and keeps controls out of the fold area.

For a musician this posture is a music stand: the lower half lies on the table or the music stand's ledge within reach
of the hands, the upper half stands up facing the eyes. The tuner's gauge and the metronome's beat are watched from a
distance while playing; the settings are touched between takes.

The posture was observed on the Pixel 9 Pro Fold emulator on 2026-10-02: inner display half-opened and rotated, a
horizontal fold across a window of about 883 × 852 dp, so each half is roughly 420 dp tall. Today that window gets the
wide tablet layouts of both screens (settings in a column on the left, gauge or dial on the right), which put the gauge
and the dial across the fold.

## User Scenarios & Testing *(mandatory)*

Every story is checked on a foldable that can be half-opened with a horizontal fold. "Upper half" is the part of the
screen above the fold, "lower half" the part below it.

### User Story 1 - Tune with the gauge standing up (Priority: P1)

A guitarist stands the half-opened phone on a table in Auto Listen. The gauge (note name, cents needle, frequency)
fills the upper half, which faces them; the instrument choice, the microphone status and the mode switch sit in the
lower half. Nothing they read is bent over the fold.

**Why this priority**: tuning hands-free is the use the posture is made for, and the tuner is the screen musicians
leave open while playing.

**Independent Test**: on the foldable in tabletop, open the Tuner in Auto Listen with the test signal; the gauge is
wholly above the fold and every setting is wholly below it.

**Acceptance Scenarios**:

1. **Given** the Tuner in Auto Listen and the device in tabletop posture, **When** the screen shows, **Then** the
   gauge with its note, cents and frequency is the only content of the upper half, and the instrument control, the
   microphone status and the mode control are in the lower half.
2. **Given** the gauge is following a pitch, **When** the device goes from flat to tabletop or back, **Then** the
   listening does not stop and the gauge keeps moving through the change.
3. **Given** microphone access was refused, **When** the Tuner is in tabletop, **Then** the refusal message and its
   button take the gauge's place in the upper half, and the settings stay in the lower half.

---

### User Story 2 - Tune by ear with the string cards standing up (Priority: P1)

In By Ear the upper half shows the grid of string cards; the lower half holds the instrument control, the mode control,
the string count (guitar only), the tuning preset and the loop-tone switch.

**Why this priority**: the string cards are what the musician touches to hear a reference and watches while it plays;
the settings are set once.

**Independent Test**: on the foldable in tabletop, switch to By Ear; every card is above the fold, every setting below
it; tapping a card plays its tone.

**Acceptance Scenarios**:

1. **Given** By Ear in tabletop, **When** the screen shows, **Then** all string cards are in the upper half and the
   instrument, mode, string count, preset and loop-tone controls are in the lower half.
2. **Given** a 7-string guitar preset, **When** the cards do not fit the upper half at their usual size, **Then** they
   shrink to fit or scroll within the upper half; no card crosses the fold.
3. **Given** a card's tone is playing, **When** the posture changes, **Then** the tone keeps playing and its card stays
   marked as playing.

---

### User Story 3 - Watch the beat standing up (Priority: P1)

A drummer sets the half-opened phone on the music stand with the metronome running. The upper half shows only the
tempo dial with its BPM readout and tempo name, and the row of accent cells that light up on each beat. The lower half
holds everything else: the tempo slider, tap tempo, play/stop, the time signature, the subdivision, the style and the
click sound.

**Why this priority**: the beat is watched from a distance; the controls are handled between songs.

**Independent Test**: on the foldable in tabletop, start the metronome; the dial and the accent cells are above the
fold and the cells light on each beat; every other control is below the fold and works.

**Acceptance Scenarios**:

1. **Given** the Metronome in tabletop, **When** the screen shows, **Then** the upper half contains only the tempo dial
   (with its readout, tempo name and − / + steps) and the accent cells, and the lower half contains the slider, tap
   tempo, play/stop, time signature, subdivision, style and click sound.
2. **Given** the metronome is running, **When** a beat sounds, **Then** that beat's accent cell lights in the upper half
   as it does in the other layouts.
3. **Given** the metronome is running, **When** the device goes from flat to tabletop or back, **Then** the click does
   not stop or skip and the tempo, signature, accents, style and sound are unchanged.
4. **Given** tabletop, **When** the musician taps an accent cell, **Then** its level cycles exactly as in the other
   layouts.

---

### User Story 4 - Everything else stays as it is (Priority: P2)

On phones, tablets, an iPhone, an iPad and on a foldable that is flat, closed, or half-opened with a vertical fold
(book posture), both screens look and behave exactly as before this feature.

**Why this priority**: the tabletop layouts must not cost any existing layout.

**Independent Test**: the existing layout tests and captures on the Pixel 6 Pro, the Android emulator, the iPhone 17
and the iPad Pro simulators are unchanged; on the foldable, flat and book postures show the existing layouts.

**Acceptance Scenarios**:

1. **Given** any device that is not in tabletop posture, **When** either screen shows, **Then** its layout is the one it
   had before this feature.
2. **Given** the foldable in tabletop, **When** it is unfolded flat, **Then** the screen returns to the existing layout
   for that window within one posture change, with no state lost.

### Edge Cases

- **The window does not reach across the fold** (split screen or a floating window on one half): the tabletop layout
  applies only when the fold runs across the screen's content; otherwise the existing layout is used.
- **The fold is not in the middle** or has a visible width: the halves are measured from the fold's actual position, and
  a gutter keeps every piece of content clear of the fold's area.
- **One half is too short for its content** (large fonts, a small foldable, the shell's top bar in the upper half):
  that half scrolls on its own; nothing moves across the fold to make room.
- **The navigation rail**: it stays where the shell puts it, beside both halves; only the screen's own content is split.
- **Rotating while in tabletop**: a vertical fold is book posture and gets the existing layout.
- **Menus and pickers opened in the lower half** (time signature, style, sound, tuning preset) open as they do today
  and keep the height bound that prevents the iOS menu crash; they may overlap the upper half while open.
- **Snackbars** (metronome messages) appear at the bottom of the lower half, as today.
- **Keyboard and screen-reader order** follows what is seen: the upper half first, then the lower half.
- **Platforms that report no fold** (iOS, non-foldable Android) never enter the tabletop layout.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST recognise tabletop posture: a fold that is half-opened and horizontal and runs across the
  screen's content. Any other state (flat, closed, vertical fold, no fold) is not tabletop.
- **FR-002**: In tabletop posture the Tuner and the Metronome MUST split their content at the fold into an upper and a
  lower region, measured from the fold's reported position, with no content in the fold's area or within a gutter of
  it.
- **FR-003**: Tuner, Auto Listen, in tabletop: the upper region MUST show only the gauge (note, cents, frequency, the
  flat/sharp labels and the in-tune state); the lower region MUST show the instrument control, the microphone status
  and the mode control. When microphone access is refused, the refusal message and its action replace the gauge in the
  upper region.
- **FR-004**: Tuner, By Ear, in tabletop: the upper region MUST show only the string cards; the lower region MUST show
  the instrument control, the mode control, the string count (when the instrument has one), the tuning preset picker
  and the loop-tone switch. The cards keep their tap-to-play behaviour.
- **FR-005**: Metronome, in tabletop: the upper region MUST show only the tempo dial (readout, tempo name, − / + steps,
  drag) and the accent cells; the lower region MUST show the tempo slider, tap tempo, play/stop, the time signature,
  the subdivision, the style and the click sound. The accent cells keep lighting on each beat and keep their
  tap-to-cycle behaviour.
- **FR-006**: The upper region's content MUST be sized to the region's height so that, on the reference foldable at the
  default font size, the gauge, the dial and every string card are wholly visible without scrolling.
- **FR-007**: The lower region's controls MUST keep their usual touch target sizes; when they do not fit the region
  they MUST scroll within it.
- **FR-008**: Entering or leaving tabletop posture MUST change only the arrangement: listening, a playing reference tone,
  a running metronome click and every selection carry on unchanged.
- **FR-009**: Outside tabletop posture both screens MUST keep their existing layouts on every window size and platform.
- **FR-010**: Keyboard focus and screen-reader order in tabletop MUST read the upper region before the lower region.

### Key Entities

- **Posture**: whether the window is in tabletop posture, and where the fold sits within the screen's content. Derived
  from the device at every frame of layout, never stored.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the reference foldable in tabletop at the default font size, 100% of the gauge, the dial, the accent
  cells and the string cards are drawn above the fold and 100% of the listed settings below it, with none of them
  intersecting the fold's area.
- **SC-002**: A running metronome keeps an unbroken click and a listening tuner keeps updating through ten consecutive
  flat ↔ tabletop changes.
- **SC-003**: The screen switches between the existing layout and the tabletop layout within one second of the posture
  change.
- **SC-004**: Every existing automated layout and behaviour test of the Tuner and the Metronome passes unchanged on all
  attached targets, and the captures of the existing layouts on the Pixel 6 Pro, the iPhone 17 and the iPad Pro show
  no change.
- **SC-005**: At the largest supported font size in tabletop, every control remains reachable by scrolling its region,
  and nothing crosses the fold.

## Assumptions

- The reference foldable is the Pixel 9 Pro Fold emulator attached on 2026-10-02 (inner display, half-opened, rotated
  so the fold is horizontal). No physical foldable is available.
- iOS has no foldable devices; the iPhone and iPad keep their layouts and are checked for regressions only.
- The shell (top bar, navigation rail or bar) is not changed by this feature; the top bar stays in the upper half and
  the screen content is split below it.
- The accent cells in the upper region stay interactive (tap to cycle); the user asked for them to be shown there, and
  removing their only control would lose a feature.
- The tempo dial keeps its − / + steps and drag in the upper region, since they are part of the dial; the slider moves to
  the lower region as the user asked.
- The rear display and the closed (cover screen) state are ordinary phone windows and get the existing layouts.
- The Stitch design project gets tabletop screens for the Tuner (Auto Listen and By Ear) and the Metronome after the
  layouts are built, following the app's captures, as was done for the metronome on 2026-10-01.
