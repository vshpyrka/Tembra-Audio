# Feature Specification: Metronome

**Feature Branch**: `004-metronome`

**Created**: 2026-09-29

**Status**: Draft

**Input**: User description: "Now I would like to implement the metronome functionality, which is the part of the global navigation in the app. Please go ahead and check the existing Google Stage designs that contain UI screens for mobile, portrait, landscape, and tablet devices. This functionality will contain: the time signature rhythm subdivisions; the accents control; the style, whether it's standard, downbeat, or syncopated; the ability to choose a different sound for the metronome; a widget that helps to set the BPM, and this widget also displays the tempo name, whether it's moderato or something similar; a slider that can be used to change the BPM roughly; plus and minus icons that will help to increment or decrement the BPM. Another thing is the tap tempo button, which will allow the user to tap on this button several times, and it will help to identify the exact or nearest tempo to what the user tapped. There is also the play and pause button that will simply start and stop the metronome click. There should be a way to upload my own wave file for the sound of the metronome, so I can use my own custom sound. I want this sound to work nicely even if I change the BPM. Maybe you will find a way to play or analyze this wave file and play it nicely even if there is a low or high speed of the metronome. In addition to that, I want you to check if there is any sense or conflict with having both time signature rhythm subdivisions and the accent control with style like standard downbeat syncopated if they do not overlap each other or mutually exclusive."

## What the design shows *(evidence for every decision below)*

The three Stitch metronome screens under `design/stitch/screens/metronome/` were reviewed element
by element: `phone` (portrait, 390 wide), `phone-landscape` (a phone held sideways, added
2026-09-29 as an uploaded HTML screen) and `tablet` (1280 wide). All three are the one Metronome
destination that feature 002 already placed in the navigation; none of them adds a destination.
The metronome research brief (`docs/research/metronome-design.md`, summarised in
`docs/DAW-App-Research-Report.md` §5) supplies the timing, tempo-change and sound-handling
behaviour used in the requirements and success criteria.

| Element | Where it appears | Verdict |
|---|---|---|
| "Time Signature" card captioned "Common & Complex" with six chips: 4/4 (selected), 3/4, 6/8, 2/4, 5/4, 7/8 | All three screens (one row on phone and tablet, two rows of three in landscape) | **Kept**; the six signatures are the fixed list (FR-006) |
| "Rhythm Subdivision" card with four chips, each a note glyph and a label: 1/4 (selected), 1/8, 1/16, 3let | All three screens | **Kept**; the labels shown are the ones for a quarter-note beat, and follow the beat unit for x/8 signatures (FR-007) |
| Accent block: four cells labelled "ACCENT" (tallest, rose, glowing), "CLICK" (lavender), "SUB" (short, muted), "CLICK" | All three screens, with 4/4 selected | **Kept** as one cell per beat of the bar showing that beat's level; the levels drawn are an illustration of the three levels, not the definition of the Standard style (FR-008, FR-009) |
| "STYLE" row with pills Standard (selected), Downbeat, Syncopated | All three screens | **Kept** as presets that fill the accent cells (FR-010) |
| Sound picker: a pill "Classic Wood" with a waveform glyph and a chevron opening a list (Classic Wood ✓, 808 Click, Studio Rim, Digital Beep, Mechanical, Cowbell) on the phone; a "Sound Engine / Classic Wood" dropdown in landscape; a "Click Timbre" grid of six tiles (Classic Wood selected, 808 Rim, Digital Beep, Cowbell, Mechanical, Custom with a "+" glyph) on the tablet | All three screens, three presentations | **Kept** as one picker shown as a dropdown in compact windows and as the tile grid in expanded windows. The lists disagree: the tablet's five sounds plus "Custom" are taken as the list (FR-021); the phone's "808 Click" is the tablet's "808 Rim" under another name and "Studio Rim" is dropped as drift; "Custom" is the import the user asked for |
| Tempo dial: a gradient arc around a large "120" with "BPM", the tempo name "Moderato (108-120)" under it, a "−" and a "+" round button inside the ring at the left and right (titled "Decrease Tempo" / "Increase Tempo" in landscape) | All three screens | **Kept**; the arc shows where the tempo sits in the range and can be dragged (FR-002, FR-003) |
| Slider under the dial with end labels 40 and 240 | All three screens | **Kept** as the rough tempo control with the range 40 to 240 (FR-002, FR-004) |
| "TAP TEMPO" wide button with a touch glyph and the caption "Sync Finger Rhythm" | All three screens | **Kept** (FR-015 to FR-017) |
| Round rose play button (titled "Start Metronome" in landscape) | All three screens | **Kept**; it becomes the pause button while the click runs (FR-011) |
| Bottom bar with six items (Timeline, Tuner, Metronome, Effects, Pads, Projects); landscape rail with Home, Timeline, Tuner, Metronome, Projects, Profile; tablet rail with Timeline, Tuner, Effects, Pads, Remote, Projects and no Metronome item; tablet top bar with menu, logo, project name, help, cloud sync, share and avatar | Vary between screens | **Excluded**: the navigation and its items were decided in feature 002 and are not changed here |
| Metronome glyph: a speedometer in the phone bottom bar, a clock in the landscape rail | Two screens, two different glyphs | **Excluded**: feature 002 chose the navigation glyph; the two screens do not agree on another |
| Transport bar | On none of the three screens | **Excluded**, as for the tuner |

Export artefacts to be aware of: the tablet `index.html` carries the title "TEMBRA - Instrument
Tuner" and the phone `index.html` has no title; both bodies are the metronome. The
phone-landscape screen was uploaded from this repository, not generated by Stitch (see
`design/stitch/README.md`).

## How the rhythm controls fit together

The user asked whether the time signature, the subdivision, the accent cells and the style
overlap or exclude each other. They do not conflict; they are four layers of one grid, and each
layer answers one question:

| Control | Question it answers | What it changes |
|---|---|---|
| Time signature | How many beats make a bar, and which note is the beat | The number of accent cells and the note the tempo counts |
| Rhythm subdivision | How many ticks sound inside each beat | Extra soft ticks between the beats; never a cell |
| Accent cells | How strong each beat is | The level of one beat: Accent, Click or Sub |
| Style | A quick way to fill every cell at once | All cells; nothing else |

Two places do overlap, and the spec resolves both:

1. **Style and the accent cells set the same thing.** A style is a preset for the cells. Choosing
   a style rewrites every cell; tapping a cell edits the pattern by hand, and when the result no
   longer matches any style, no style pill is selected (FR-010). Neither control is ever
   disabled by the other.
2. **"Sub" names both a beat level and the subdivision ticks.** They are the same sound: the
   subdivision ticks between beats always sound at the Sub level, and a beat whose cell is set
   to Sub sounds like one of them (FR-009). Subdivision ticks are not shown as cells and cannot be
   accented individually.

Worked examples (A = Accent, C = Click, s = Sub; subdivision ticks in lower case between beats):

| Signature | Subdivision | Style | One bar sounds as |
|---|---|---|---|
| 4/4 | 1/4 | Standard | A C C C |
| 4/4 | 1/8 | Standard | A s C s C s C s |
| 4/4 | 1/4 | Downbeat | A s s s |
| 4/4 | 1/4 | Syncopated | C A C A |
| 6/8 | 1/8 | Standard | A C C A C C |
| 7/8 | 1/8 | Syncopated | C A C A C A C |
| 3/4 | 3let | Standard | A s s C s s C s s |

## Clarifications

### Session 2026-09-29

- Q: When the click is running and the phone's screen locks or the user switches to another
  app, should the metronome keep playing? → A: Yes. It keeps running while the screen is locked
  and while another app is in front, until the user stops it, a call or another app takes the
  audio, or the user leaves the Metronome destination inside the app (FR-027).
- Q: Should the Syncopated style move the accents onto the off-beats between the main beats, or
  onto the even-numbered main beats such as 2 and 4? → A: The even-numbered main beats, by
  default (recommended); the two other readings (off-beat ticks accented with the beats soft;
  every beat except beat 1 accented) are built as well and reachable only through a
  developer-only switch in debug builds, so the user can audition them before one is fixed
  (FR-010, FR-037).
- Q: In 6/8 and 7/8, should the metronome count every eighth note as a beat with one cell each,
  or count compound beats such as the two dotted-quarters of 6/8? → A: One cell per eighth
  note: 6/8 shows six cells and 7/8 seven, the tempo counts eighth notes, and Standard accents
  beat 1 (and 4 in 6/8); no grouping control (FR-002, FR-006).
- Q: When a custom sound is longer than the gap between ticks at a fast tempo, what should
  happen to the part still ringing when the next tick arrives? → A: Play the sound itself
  unchanged at every tempo, as the Soundbrenner app does: no pitch or speed change, the tempo
  only sets the spacing, so at a slow tempo there is silence between ticks and at a fast tempo
  the ticks simply come faster. When the next tick arrives before the sound has ended, the
  ringing sound is faded out in the few milliseconds before that tick so every tick starts
  clean and only one sound plays at a time (option A) (FR-013, FR-019).
- Q: Should the accent cells light up in time with each audible click while the metronome runs,
  or stay static? → A: Light the cell of the beat being sounded, timed to the audible click
  with the output delay of the current route compensated (FR-014).
- Q (planning, following the official platform guidance the user asked for): What happens when
  headphones are unplugged while the click runs? → A: The click stops and shows play, as Apple's
  route-change guidance and Android's becoming-noisy convention require; connecting a new output
  still continues the click on it (FR-027, User Story 6).

## User Scenarios & Testing *(mandatory)*

The users of this feature are musicians who open Tembra Audio to practise or warm up to a steady
click: on a phone propped on a music stand or lying face-down with the screen locked, on a
tablet, or in a desktop-mode window with a mouse and keyboard. The feature turns the Metronome
placeholder from feature 002 into a working metronome: a tempo the user can set four ways (dial,
slider, steppers, tap), a bar shaped by time signature, subdivision, accents and style, a choice
of click sounds including the user's own recording, and a play/pause control.

### User Story 1 - Start a steady click at a chosen tempo (Priority: P1)

A musician opens Metronome, sees 120 BPM "Moderato" on the dial, adjusts the tempo with the
"+" and "−" buttons for single steps, the slider for big jumps, or by dragging the dial's arc,
watches the tempo name change as the number crosses each range, and presses play. A steady,
even click sounds at once, the beat cells light in turn with each click, and pressing the same
button pauses it. Changing the tempo while it runs takes effect on the very next beat without a
stutter.

**Why this priority**: A steady click at a settable tempo is the metronome. Nothing else in the
feature has value without it.

**Independent Test**: Set several tempos with each control, record the output for ten minutes at
40, 120 and 240 BPM, and confirm every click lands on its nominal time with no drift; change the
tempo while running and confirm the spacing changes from the next beat with no gap or double
click.

**Acceptance Scenarios**:

1. **Given** Metronome is opened for the first time, **When** it appears, **Then** the dial
   reads 120 BPM with the name "Moderato (108-120)", the arc is filled to the position of 120
   within 40 to 240, the slider knob sits at the same position, 4/4, 1/4, the Standard style
   and Classic Wood are selected, and the click is stopped.
2. **Given** the click is stopped, **When** the user presses play, **Then** the first click (an
   Accent, beat 1) sounds within a tenth of a second, the play button shows pause, and the
   clicks continue evenly at the set tempo.
3. **Given** the click is running, **When** the user presses pause, **Then** the click stops at
   once with no click or pop, the button shows play, and pressing play again starts from beat 1.
4. **Given** the tempo is 120, **When** the user taps "+", **Then** the tempo reads 121 and the
   arc and slider move to match; **When** the user holds "+", **Then** the tempo keeps rising
   step by step until released or 240 is reached.
5. **Given** any tempo, **When** the user drags the slider or the dial's arc, **Then** the tempo
   follows in whole numbers between 40 and 240, the number and name update while dragging, and
   the running click adopts each new tempo from its next tick.
6. **Given** the tempo is 120 and the click is running, **When** the user sets 121,
   **Then** the name changes to "Allegro (120-156)" and the next click after the change already
   arrives at the new spacing; no click is skipped, doubled or moved.
7. **Given** the click runs at any tempo, **When** ten minutes pass, **Then** the last click is
   within a millisecond of where an ideal metronome would place it.

---

### User Story 2 - Shape the bar: signature, subdivision, accents, style (Priority: P1)

A musician picks a time signature, chooses how finely each beat is subdivided, taps beat cells
to make a beat stronger or softer, or picks a style that fills every cell in one go. The click
plays the pattern: strong accents where the cells say Accent, plain clicks where they say Click,
soft ticks where they say Sub, and soft ticks for every subdivision.

**Why this priority**: The bar shape is what the user listed first, and without accents a
metronome cannot mark the bar; it is independent of the sound and tempo choices.

**Independent Test**: For every signature, subdivision and style, start the click, record one
bar and confirm the sequence of levels matches the worked examples above; tap cells and confirm
the pattern and the style pills follow.

**Acceptance Scenarios**:

1. **Given** 4/4 is selected, **When** the user selects 7/8, **Then** seven cells are shown,
   the selected style is applied to the new length (Standard: Accent, then six Clicks), the
   subdivision chips read 1/8, 1/16, 1/32 and 3let with 1/8 selected, and a running click
   restarts its bar at beat 1 on the next beat.
2. **Given** 4/4 with 1/4, **When** the user selects 1/8, **Then** one soft tick sounds
   halfway between consecutive beats, the beats keep their levels, and the cells do not change.
3. **Given** the Standard style, **When** the user taps the second cell, **Then** it cycles
   from Click to Sub, the pattern now matches no style and no style pill is selected; tapping
   the cell again cycles Sub to Accent, and again Accent to Click.
4. **Given** cells edited by hand, **When** the user selects "Downbeat", **Then** every cell is
   rewritten (Accent, then Sub for every other beat) and "Downbeat" is the selected pill.
5. **Given** 6/8 and Standard, **When** the click runs, **Then** beats 1 and 4 are accents and
   the rest are clicks.
6. **Given** 4/4 and Syncopated, **When** the click runs, **Then** beats 2 and 4 are accents
   and beats 1 and 3 are clicks.
7. **Given** the click is running, **When** the user changes the subdivision or a cell, **Then**
   the change is heard from the next beat without stopping the click.
8. **Given** the click is running, **When** each beat sounds, **Then** that beat's cell lights
   in time with the audible click and the previous one dims, so the eye and the ear agree.

---

### User Story 3 - Find the tempo by tapping (Priority: P2)

A musician who has a tempo in their head, or is listening to a recording, taps the "Tap Tempo"
button in time. From the second tap on, the dial shows the tempo of the taps, rounded to the
nearest whole BPM, and settles as the taps continue. A pause of a couple of seconds ends the
sequence so the next tap starts a fresh one.

**Why this priority**: Tap tempo is the user's stated way to "identify the exact or nearest
tempo"; it is a second way to set the tempo of User Story 1.

**Independent Test**: Tap eight times at a steady 100 BPM (with the natural jitter of a finger)
and confirm the dial settles on 100; tap at 300 BPM and at 30 BPM and confirm the result clamps
to 240 and 40; wait three seconds and tap once and confirm the tempo does not change.

**Acceptance Scenarios**:

1. **Given** any tempo, **When** the user taps the button once, **Then** the tempo does not
   change, the button shows the tap visibly and its caption invites another tap.
2. **Given** a first tap, **When** the user taps again within two seconds, **Then** the tempo
   becomes the tapped interval as whole BPM, and each further tap refines it using the average
   of the most recent intervals (at most the last eight).
3. **Given** a tap sequence, **When** more than two seconds pass without a tap, **Then** the
   sequence ends, the caption returns to "Sync finger rhythm" and the next tap starts a new one.
4. **Given** taps faster than 240 BPM or slower than 40 BPM, **When** the tempo is computed,
   **Then** it is clamped to the range.
5. **Given** the click is running, **When** the user taps a tempo, **Then** the click adopts
   each new tempo from its next tick, as with any other tempo control.

---

### User Story 4 - Choose a click sound (Priority: P2)

A musician opens the sound picker, sees the five built-in sounds by name, chooses one and hears
a single preview click of it if the metronome is stopped; if it is running, the next tick already
uses the new sound. Each sound has a clearly stronger accent, a normal click and a softer sub
tick so the bar shape stays audible whichever sound is chosen.

**Why this priority**: The user asked for a choice of sounds; it is independent of the tempo and
bar controls and of the custom import.

**Independent Test**: Select each sound while stopped and confirm the preview; select each while
running and confirm the swap happens on the next tick without a gap; record one bar per sound
and confirm the three levels are distinguishable.

**Acceptance Scenarios**:

1. **Given** the picker is closed, **When** it is opened, **Then** it lists Classic Wood, 808
   Rim, Digital Beep, Cowbell, Mechanical and Custom, and marks the current sound.
2. **Given** the click is stopped, **When** the user selects a sound, **Then** one click of it
   sounds at the Click level and the picker shows the new name.
3. **Given** the click is running, **When** the user selects a sound, **Then** the next tick uses
   the new sound and no tick is missed or doubled.
4. **Given** any built-in sound, **When** a bar with all three levels plays, **Then** the
   Accent is clearly louder than the Click and the Sub clearly softer.

---

### User Story 5 - Use my own sound (Priority: P2)

A musician chooses "Custom" in the picker, picks a WAV file from their device, and the app
prepares it so it clicks cleanly at any tempo: it starts exactly on the beat even if the file
has silence at its start, it is brought to the same loudness as the built-in sounds, and at fast
tempos it never piles up or smears because a sound still ringing when the next tick arrives is
faded out just before it. The file's name appears on the Custom entry, and the sound survives
app restarts and the deletion of the original file.

**Why this priority**: Explicitly requested, with the user's concern that the sound must work
at any speed; it builds on the picker of User Story 4.

**Independent Test**: Import a stereo 24-bit 96 kHz file with 100 ms of leading silence and a
long ringing tail; confirm the first transient lands on the beat, that at 240 BPM with 1/16 every
tick starts on time with the tail faded before it, and at 40 BPM the sound plays in full; delete
the original file, restart the app and confirm the sound still plays; import a non-WAV file and a
silent file and confirm the messages and that the previous sound is kept.

**Acceptance Scenarios**:

1. **Given** no custom sound has been imported, **When** the user chooses "Custom", **Then** the
   platform's file picker opens filtered to WAV files, and cancelling it leaves the current
   sound selected.
2. **Given** a valid WAV file was picked, **When** the import completes, **Then** within two
   seconds the Custom entry shows the file's name, the custom sound is selected, a preview
   click sounds if the metronome is stopped, and the running click switches on its next tick.
3. **Given** a file with leading silence, **When** it clicks, **Then** the sound's first
   transient, not the start of the file, lands on the beat.
4. **Given** a file whose sound is longer than the tick interval, **When** the click runs,
   **Then** each tick starts exactly on time and the previous sound is faded out in the few
   milliseconds before it, so no two clicks pile up; a sound shorter than the interval plays in
   full; the sound's pitch and speed never change with the tempo.
5. **Given** a file that is not a WAV, uses an unsupported encoding, or contains no sound,
   **When** the user picks it, **Then** a short message says why it was not imported and the
   previously selected sound is kept.
6. **Given** a custom sound was imported, **When** the user chooses "Custom" again, **Then** the
   custom sound is selected, and a separate "Replace" action lets the user pick another file,
   which replaces the previous custom sound.
7. **Given** a custom sound is selected, **When** the app is closed and reopened, **Then** the
   custom sound is still selected and plays; if its prepared copy is missing or damaged, the app
   falls back to Classic Wood and says so once.

---

### User Story 6 - Keep clicking with the screen off; handle interruptions (Priority: P2)

A musician starts the click, puts the phone face-down and practises; the screen locks and the
click keeps going until they pick the phone up and pause it. If a call comes in or another app
takes over the audio, the click stops and stays stopped until they press play again. Moving to
another destination in the app stops the click too.

**Why this priority**: A practice metronome that stops when the screen times out is unusable;
this is handling around the core rather than the core.

**Independent Test**: Start the click, lock the screen for ten minutes and confirm it keeps
going without a gap; return and confirm the button reads pause. Receive a call while running and
confirm it stops and shows play afterwards; connect headphones and confirm the click continues on
them, then disconnect them and confirm it stops.

**Acceptance Scenarios**:

1. **Given** the click is running on Metronome, **When** the screen locks or the user switches
   to another app, **Then** the click keeps running at the same tempo with no gap, and on return
   the screen shows it running.
2. **Given** the click is running, **When** the user navigates to another destination in the
   app, **Then** the click stops; returning to Metronome shows it stopped.
3. **Given** the click is running, **When** a call or another app takes the audio, **Then** the
   click stops and does not restart by itself when the interruption ends; the button reads play.
4. **Given** the click is running, **When** headphones are connected, **Then** the click
   continues on the new route at the same tempo; **When** headphones are disconnected, **Then**
   the click stops and the button reads play.
5. **Given** the click is running, **When** the window is rotated or resized, **Then** the click
   continues without a hiccup and every setting is preserved.

---

### User Story 7 - Fits every window, pointer, keyboard and screen reader (Priority: P3)

The metronome lays itself out for a phone, a phone held sideways, a tablet and a desktop-mode
window inside the frame from feature 002; a mouse user sees hover states and can turn the tempo
with the scroll wheel over the dial, a keyboard user can reach and operate every control, and a
screen-reader user hears the tempo and its name, each beat's level and the play state.

**Why this priority**: Required by the constitution for every screen; it does not change what
the metronome does.

**Independent Test**: Render the screen at compact and expanded widths and in phone landscape on
both platforms and compare with the design; tab through every control; read the dial, the cells
and the picker with the platform screen reader.

**Acceptance Scenarios**:

1. **Given** a window narrower than 600 dp, **When** Metronome is shown, **Then** one compact
   card holds the bar-shape controls (time signature menu, subdivision tiles, accent cells in one
   row, style pills), the sound picker and the dial with its slider follow, the dial, slider, Tap
   Tempo and play controls are visible without scrolling at the default font scale, the column
   scrolls only when the height cannot hold the dial at its minimum, and the Tap Tempo and play
   controls stay visible at the bottom; nothing is clipped or overlapping.
2. **Given** a window 840 dp or wider and at least 480 dp tall, **When** Metronome is shown,
   **Then** the settings cards stand in a left column with the sound picker as a tile grid, the
   dial and slider fill the area beside them, the Tap Tempo and play controls sit beneath the
   dial, and no right-hand panel is shown.
3. **Given** a window at least 600 dp wide and shorter than 480 dp (a phone held sideways),
   **When** Metronome is shown, **Then** the settings stand in a column beside the dial as the
   same compact bar-shape card as portrait with the accent cells visible, the dial with its
   slider and the Tap Tempo and play controls underneath form one block at the right edge, they
   fit without scrolling, and the settings column scrolls only if the height requires it.
4. **Given** a pointer, **When** it hovers over any chip, cell, pill, picker entry, stepper,
   slider, dial or button, **Then** the control shows a hover state, the icon-only controls show
   a tooltip, and the scroll wheel over the dial changes the tempo by one per notch; **When**
   focus is moved with a keyboard, **Then** every control receives a visible focus indicator in
   reading order, the dial and slider respond to the arrow keys by one and to Page Up and Page
   Down by ten, and play, tap and every chip can be activated.
5. **Given** a screen reader, **When** it reaches the dial, **Then** it announces the tempo in
   beats per minute and the tempo name and offers it as an adjustable value; **When** it reaches
   a cell, **Then** it announces the beat number and its level; **When** it reaches the play
   button, **Then** it announces "Start metronome" or "Stop metronome".

---

### Edge Cases

- The tempo is at 40 or 240: the stepper that would leave the range does nothing and is shown as
  unavailable; the slider and dial stop at the ends; tap tempo clamps.
- The user taps the cell of the only accent in the bar until no cell is an accent: allowed; the
  bar then has no accent and no style pill is selected.
- A single tap on Tap Tempo, or two taps more than two seconds apart: the tempo is unchanged.
- Taps with uneven spacing: the tempo follows the average of the recent intervals and settles as
  the taps become even; it never jumps outside 40 to 240.
- The time signature changes while cells were edited by hand: cells for beats that still exist
  keep their levels, new beats get Click, and no style pill is selected.
- 7/8 with 1/32 at 240 BPM (the fastest grid, sixteen ticks a second) and 2/4 with 1/4 at 40
  BPM (the slowest): both play evenly, and every tick starts on time.
- A custom sound longer than the longest tick interval (1.5 seconds at 40 BPM): only its first
  two seconds are kept at import, and it is faded before the next tick whenever it would
  overlap.
- A custom file that is large (up to 64 MB), very long, 8-bit, 32-bit float, or at 8 kHz or 192 kHz: it
  is accepted if it is a PCM WAV, and only the first two seconds of sound are kept.
- A custom file whose sound is a single sharp transient a few milliseconds long: it is accepted
  and clicks cleanly.
- The original custom file is moved or deleted after import: the sound keeps working from the
  app's copy.
- The sound picker is changed rapidly several times while running: each change applies on the
  next tick; no gap, no double click, no crash.
- The app is force-quit or crashes while running: on the next launch the metronome is stopped
  and every setting, including the custom sound, is as last set.
- The user enables the largest system font size: the tempo number, name, chip labels, cell
  labels and picker names stay readable and unclipped; the cells and chips wrap to a second row
  when needed, keeping their minimum size.
- The platform's reduce-motion setting is on: the beat cells still light on the beat (it is
  information), but the glow, pulsing and any decorative animation of the dial, the tap button
  and the play button are disabled.
- The user selects the already selected chip, pill or sound: nothing changes and no preview
  sounds.
- Wireless headphones with a long output delay: the beat cells still light in time with the
  audible click, not early.

## Requirements *(mandatory)*

### Functional Requirements

**Destination and defaults**

- **FR-001**: The Metronome destination MUST replace the feature 002 placeholder with the
  metronome screen. The top bar, navigation control and back behaviour from feature 002 MUST
  remain unchanged, and Metronome MUST NOT show a transport bar or a right-hand panel at any
  width. On the first launch the defaults are 120 BPM, 4/4, subdivision 1/4, the Standard style
  and the Classic Wood sound, stopped.

**Tempo**

- **FR-002**: The tempo MUST be a whole number of beats per minute between 40 and 240
  inclusive, where a beat is the note named by the lower number of the time signature (a quarter
  note in x/4, an eighth note in x/8).
- **FR-003**: The dial MUST show the tempo as a large number with the unit "BPM", the tempo name
  with its range under it (FR-005), and an arc filled from the start of the ring to the position
  of the tempo within 40 to 240. Dragging along the arc MUST set the tempo in whole numbers, the
  scroll wheel over the dial MUST change it by one per notch, and with keyboard focus the arrow
  keys MUST change it by one and Page Up and Page Down by ten.
- **FR-004**: A "−" and a "+" button inside the ring MUST change the tempo by one; holding one
  MUST repeat the step until it is released or the range ends; at the ends of the range the
  button that would leave it MUST do nothing and show as unavailable. Both MUST show the
  tooltips "Decrease tempo" and "Increase tempo". A slider under the dial labelled 40 and 240 at
  its ends MUST set the tempo in whole numbers across the range, follow every other tempo
  control, and respond to the keyboard as the dial does.
- **FR-005**: The tempo name MUST be taken from this table, shown as the name followed by its
  range in brackets exactly as "Moderato (108-120)". A tempo equal to a boundary shared by two
  names takes the slower name (120 is Moderato, 121 is Allegro).

  | Name | Range |
  |---|---|
  | Largo | 40-60 |
  | Larghetto | 60-66 |
  | Adagio | 66-76 |
  | Andante | 76-108 |
  | Moderato | 108-120 |
  | Allegro | 120-156 |
  | Vivace | 156-176 |
  | Presto | 176-200 |
  | Prestissimo | 200-240 |

**Bar shape**

- **FR-006**: The time signature MUST be chosen from exactly these six, in this order, with 4/4
  the default: 4/4, 3/4, 6/8, 2/4, 5/4, 7/8. The upper number is the number of beats in a bar
  and the number of accent cells; the lower number names the beat (FR-002). In x/8 signatures
  every eighth note is a beat with its own cell and the tempo counts eighth notes (clarified
  2026-09-29); there is no beat-grouping control.
- **FR-007**: The subdivision MUST be one of four choices meaning one, two, four or three ticks
  per beat, in this order, with one tick the default. The chips MUST be labelled as note values
  for the current beat: "1/4", "1/8", "1/16", "3let" for x/4 signatures and "1/8", "1/16",
  "1/32", "3let" for x/8 signatures, each with the design's note glyph. The ticks between beats
  MUST be evenly spaced, MUST sound at the Sub level, and MUST NOT appear as cells.
- **FR-008**: The accent block MUST show one cell per beat of the bar, in order, each drawn at
  its level as the design shows (Accent tallest and in the accent colour, Click, Sub shortest
  and muted) and labelled with the level name. Tapping a cell MUST cycle its level Accent →
  Click → Sub → Accent. Every level combination is allowed, including a bar with no accent.
- **FR-009**: There are exactly three levels: Accent (clearly the strongest), Click (the normal
  click) and Sub (clearly the softest). Subdivision ticks always sound at Sub; a beat whose cell
  is Sub sounds exactly like a subdivision tick.
- **FR-010**: The style row MUST offer exactly "Standard", "Downbeat" and "Syncopated".
  Selecting one MUST rewrite every cell from the table below. The pill of the style that the
  current cells match MUST be shown selected; when the cells match none, no pill is selected.
  Changing the time signature MUST re-apply the selected style at the new length; when no style
  is selected the cells of the beats that remain keep their levels and new beats become Click.

  | Style | Cells |
  |---|---|
  | Standard | Beat 1 Accent, every other beat Click; in 6/8 beat 4 is also Accent |
  | Downbeat | Beat 1 Accent, every other beat Sub |
  | Syncopated | Every even-numbered beat Accent, every odd-numbered beat (including beat 1) Click |

  The Syncopated row is the shipped definition (clarified 2026-09-29); FR-037 gives debug builds
  two alternative readings for auditioning. The cells always stay one per beat.

**Playback and timing**

- **FR-011**: One button MUST start and stop the click: it shows play when stopped and pause
  when running, with the tooltips "Start metronome" and "Stop metronome". Starting MUST sound
  beat 1 within a tenth of a second and MUST always begin at beat 1; stopping MUST take effect at
  once, fading any sounding tick within a few milliseconds so nothing pops.
- **FR-012**: Clicks MUST be evenly spaced at the set tempo and subdivision: over any run of any
  length every tick MUST land within one millisecond of its nominal time counted from the first
  tick, with no cumulative drift, at every tempo and subdivision in range and on both platforms.
- **FR-013**: A change of tempo, subdivision, cell level, style or sound while running MUST take
  effect from the next tick (a change of time signature from the next beat, which becomes beat
  1) without stopping, skipping, doubling or moving a tick and without any gap or artefact. A
  tempo change MUST change only the spacing of the ticks; the sound's pitch and length never
  change with the tempo.
- **FR-014**: While running, the cell of the beat being sounded MUST light as its click is heard
  and dim before the next beat (confirmed 2026-09-29). The lighting MUST be timed to the audible click, allowing for
  the output delay of the current route (including wireless headphones), and MUST NOT lag the
  sound perceptibly. The rest of the screen MUST stay steady and responsive.

**Tap tempo**

- **FR-015**: The "Tap Tempo" button MUST carry the caption "Sync finger rhythm" at rest. Each
  tap MUST be acknowledged visibly at once. A tap more than two seconds after the previous tap
  starts a new sequence; a single tap never changes the tempo.
- **FR-016**: From the second tap of a sequence the tempo MUST be set to the average of the
  intervals between the most recent taps of the sequence (at most the last eight), converted to
  beats per minute, rounded to the nearest whole number and clamped to 40 to 240; while a
  sequence is active the caption MUST show that taps are being counted, returning to the rest
  caption when the sequence ends.
- **FR-017**: A tapped tempo MUST behave like any other tempo change (FR-013); it MUST NOT
  restart the click or move the beat to the taps.

**Sounds**

- **FR-018**: Five built-in sounds MUST ship, named exactly "Classic Wood", "808 Rim", "Digital
  Beep", "Cowbell" and "Mechanical", each recognisably different from the others, each short
  enough to sound clean at the fastest grid, and each with an Accent, a Click and a Sub version
  where the Accent is clearly stronger than the Click and the Sub clearly softer (at least 6 dB
  apart in loudness). Every built-in sound MUST be produced by the app or bundled under a
  permissive licence recorded in the licence inventory.
- **FR-019**: Clicks MUST start and stop without pops, MUST play at a comfortable level (every sound's Click
  level-matched to the built-in set) that follows the device's media volume on the current output route, and every tick MUST start
  exactly on its time even when the previous sound is still sounding: a sound that is still
  sounding when the next tick arrives MUST be faded out over a few milliseconds before that tick
  so clicks never pile up or smear; a sound shorter than the interval plays in full, and at a
  slow tempo there is silence between ticks. The sound itself is never sped up, slowed down or
  pitch-shifted by the tempo (clarified 2026-09-29, the behaviour of the Soundbrenner app).
- **FR-020**: Selecting a sound while stopped MUST play one preview click of it at the Click
  level; selecting one while running MUST switch on the next tick (FR-013). Selecting the
  already selected sound does nothing.
- **FR-021**: The sound picker MUST list, in this order, Classic Wood, 808 Rim, Digital Beep,
  Cowbell, Mechanical and Custom, marking the current one and showing the current sound's name
  when closed. In the expanded layout of FR-028 it is the design's grid of tiles with glyphs; in
  every other layout (compact, medium and phone landscape) it is a dropdown opened from a pill.

**Custom sound**

- **FR-022**: The "Custom" entry MUST, while no custom sound exists, show a "+" and open the
  platform's file picker limited to WAV files; cancelling keeps the current sound. Once a custom
  sound exists the entry MUST show the file's name (without its extension), selecting it MUST
  select the custom sound, and a separate "Replace" action MUST open the picker again; a new
  file replaces the previous custom sound. Exactly one custom sound exists at a time.
- **FR-023**: The app MUST accept WAV files containing PCM audio in 8, 16, 24 or 32-bit integer
  or 32-bit floating-point form, mono or stereo, at any sample rate from 8 kHz to 192 kHz, and
  MUST prepare each imported file so it clicks well at any tempo: it MUST find the sound's onset
  and drop the silence before it so the first transient lands on the beat; keep at most the first
  two seconds of sound after the onset; end the kept sound with a short fade; bring its loudness
  to the level of the built-in Click; and derive the Accent and Sub versions from it (the Accent
  clearly stronger, the Sub clearly softer, as in FR-018). Preparation MUST finish within two
  seconds for a file of up to thirty seconds and MUST NOT interrupt a running click.
- **FR-024**: The prepared sound MUST be stored as the app's own copy so it works after the
  original file is moved or deleted and across app launches. If the copy is missing or damaged
  when it is needed, the app MUST fall back to Classic Wood and tell the user once.
- **FR-025**: A file that is not a WAV, that uses an unsupported encoding, whose content has no
  sound, that is larger than 64 MB, or that cannot be read from the device MUST be refused with one short message naming the reason, and the previously selected
  sound MUST remain selected.

**Remembered settings and lifecycle**

- **FR-026**: The tempo, time signature, subdivision, every cell level, the selected style, the
  selected sound and the custom sound MUST be remembered across app launches. The metronome
  MUST always launch stopped.
- **FR-027**: A running click MUST keep running, without a gap, while the screen is locked or
  another app is in front, until the user stops it (clarified 2026-09-29). It MUST stop when the user navigates to
  another destination in the app, when a call or another app takes the audio, or when the app is
  closed, and it MUST NOT restart by itself afterwards; the button then reads play. When
  headphones or another output are connected the click MUST continue on the new route; when
  headphones are disconnected the click MUST stop and show play, as both platforms' official
  guidance requires so nothing is played aloud unexpectedly (revised 2026-09-29 during
  planning, replacing "continue on the new route").

**Layout, input and accessibility**

- **FR-028**: The screen MUST adapt to the window: in windows narrower than 600 dp the
  bar-shape controls share one compact card (the time signature as a menu, the subdivisions as
  note-figure tiles, the accent cells in one row, the style pills) above the sound picker and the
  dial, the dial card takes the remaining height so that the dial, its slider and the Tap Tempo
  and play controls are visible without scrolling on a phone in portrait at the default font
  scale, and the column scrolls only when the dial would fall under its minimum size, with the
  Tap Tempo and play controls always visible at the bottom (revised 2026-10-01: the separate
  cards pushed the dial off screen); in windows 840 dp or wider that are at least 480 dp tall the
  settings cards (with the picker as a tile grid) form a left column, the dial and slider fill
  the area beside them and the Tap Tempo and play controls sit beneath the dial; between 600 and
  839 dp the compact rules apply; in windows at least 600 dp wide and shorter than 480 dp (a
  phone held sideways) the settings stand in a column beside the dial using the same compact
  bar-shape card as portrait (time signature menu, subdivision tiles, accent cells in one row,
  style pills) so that the accent cells are visible without scrolling (revised 2026-10-01 after
  the Pixel landscape check: the chip cards pushed the cells below the fold), the dial with its
  slider and, underneath it, the Tap Tempo and play controls fill the right three fifths of the
  width while the settings take the left two fifths as a landscape card (signature and style
  menus on one row, the subdivision tiles, the accent cells without labels in one row or, for
  6/8 and 7/8, two rows of 44 dp cells, the sound picker) that fits the height without
  scrolling (revised 2026-10-01, second to sixth user checks), and the dial,
  slider, Tap Tempo and play controls fit without scrolling while the settings column may scroll
  only at large font sizes. The accent cells MUST stay in one row for every time signature whenever each
  cell can keep its 44 dp minimum, hiding the level label when a cell is narrower than its widest
  label; only when 44 dp cannot be kept do they, like the chips, wrap to a second row rather than
  shrink below their minimum size. Nothing MUST clip, overlap or scroll horizontally at any
  width, including at the largest system font size.
- **FR-029**: Every chip, cell, pill, picker entry, stepper, the slider, the dial, the Tap Tempo
  button and the play button MUST show a hover state under a pointer and a visible focus
  indicator, MUST be operable from the keyboard, and MUST measure at least 44 dp in both
  dimensions. Icon-only controls MUST show a tooltip on hover.
- **FR-030**: Every control MUST expose its label, selected or checked state and a stable test
  identifier to accessibility services. The dial MUST be exposed as one adjustable element
  announcing the tempo in beats per minute and the tempo name; the slider likewise; each cell
  MUST announce its beat number and level; the play button its "Start metronome" or "Stop
  metronome" label; the Tap Tempo button MUST announce the tempo it set; the picker entries
  their names and the Custom entry its file name or that it is empty.
- **FR-031**: All colours, type styles, spacing, radii and icons MUST come from the shared design
  system; the new icons (the note glyph, the touch glyph, pause, the stepper "−" and "+", the
  waveform, rim, bell, cowbell, mechanical and "+" glyphs of the picker, and the chevron) MUST
  be added to the shared icon set through its existing mechanism. Every live number MUST use
  tabular figures. The dial MUST match the design's gradient arc on visual review.
- **FR-032**: When the platform's reduce-motion setting is on, the beat cells MUST still light on
  the beat, but the glow, pulsing and any decorative animation of the dial, the tap button and
  the play button MUST be disabled.

**Quality**

- **FR-033**: Automated tests MUST cover: the tempo-name table at every tempo from 40 to 240
  including every boundary; the tap-tempo arithmetic (single tap, averaging window, rounding,
  two-second cut-off, clamping); the cell pattern produced by every style at every signature
  and the re-application rules of FR-010; the three Syncopated readings of FR-037 rendered over
  one bar; the subdivision labels for every signature; the
  sequence of levels rendered for one bar of every worked example above; the tick timing of
  FR-012 and the change rules of FR-013 measured on audio rendered offline without a device
  (at 40, 120 and 240 BPM with every subdivision, over at least ten minutes at one tempo); the
  tail fade of FR-019 with a long sound at the fastest grid; the preparation of FR-023 with test
  files (leading silence, a long tail, mono and stereo, 8, 16, 24 and 32-bit and float, 8 kHz
  and 192 kHz) and the refusals of FR-025; the fallback of FR-024; the remembered settings; the
  screen with a fake audio source in every state; and the accessibility semantics of FR-030.
  Behaviour that needs real hardware (screen lock, interruptions, route changes, the timing of
  the beat light against the audible click) MUST be verified on a device and recorded in the
  feature's verification notes.
- **FR-034**: Screenshots at compact (360 dp) and expanded (1024 dp or wider) widths and in
  phone landscape on Android and on the iOS simulator MUST be stored under the feature directory
  and reviewed against the Stitch references before acceptance. The set MUST include a state matrix: stopped and running (with beat 1 lit),
  each style in 4/4, 7/8 with its seven cells, 6/8 showing the x/8 subdivision labels, the picker
  open, the Custom entry filled, an import refusal message, and the tempo at 40 and 240.
- **FR-035**: Any dependency added for this feature MUST carry a permissive licence and MUST be
  recorded in the licence inventory with its version and source.
- **FR-036**: The feature MUST NOT include: locking the click to a project timeline or
  transport, count-in, tempo automation or trainer modes (gradual tempo change, random muting),
  a silent beat level, accents on individual subdivision ticks, polyrhythms or beat grouping
  within a bar, tempo entry by typing, a click volume control, aligning the beat to the taps,
  more than one custom sound, separate custom files per level, haptic or flash-on-beat
  feedback, saving or naming presets, a transport bar, top-bar icons, or changes to the
  navigation and its glyphs.
- **FR-037**: Debug builds MUST offer, through a developer-only launch argument (the app's
  developer tooling, never a control on the screen), a setting that selects
  how the Syncopated style is rendered, so the user can audition the readings by ear: "Even
  beats" (the FR-010 definition, the default), "Off-beat ticks" (every subdivision tick between
  beats sounds at Accent and every beat at Sub; with one tick per beat it behaves as "Even
  beats") and "All but one" (every beat except beat 1 at Accent, beat 1 at Sub). The setting
  changes only how the Syncopated style sounds; the cells keep one entry per beat and show the
  "Even beats" pattern. Release builds MUST ignore the argument and always use "Even beats".

### Key Entities

- **Tempo**: a whole number of beats per minute from 40 to 240 and the tempo name it maps to.
- **Time Signature**: one of six; its upper number is the number of beats per bar, its lower
  number names the beat the tempo counts.
- **Subdivision**: the number of ticks per beat (one, two, four or three) with labels that follow
  the beat.
- **Beat Level**: Accent, Click or Sub; the strength of one beat.
- **Accent Pattern**: the ordered list of beat levels for the current bar, one per beat.
- **Style**: one of the three presets that fills the accent pattern; "selected" only while the
  pattern matches it.
- **Click Sound**: a built-in sound (five, by name) or the custom sound; each provides an
  Accent, a Click and a Sub version.
- **Custom Sound**: the user's imported file: its display name and the app's prepared copy.
- **Tap Sequence**: the timestamps of the recent taps and the tempo derived from them.
- **Playback State**: stopped or running, and while running the beat currently sounding.
- **Metronome Preferences**: the remembered tempo, signature, subdivision, pattern, style, sound
  and custom sound.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a ten-minute run at 40, 120 and 240 BPM with each subdivision, every click
  measured from the audio output lands within 1 ms of its nominal time and the final click
  within 1 ms of the ideal, on both platforms.
- **SC-002**: A tempo, subdivision, accent, style or sound change while running is heard on the
  very next tick, and a recording around the change shows no skipped, doubled or displaced tick
  and no gap or artefact.
- **SC-003**: Eight taps at a steady tempo anywhere from 40 to 240 BPM, with up to 20 ms of
  jitter per tap, set the dial within 1 BPM of the tapped tempo, and the first value appears on
  the second tap.
- **SC-004**: A stereo 24-bit 96 kHz WAV with 100 ms of leading silence and a 1.2-second tail
  imports within 2 s; its first transient lands within 1 ms of the beat; at 240 BPM with four
  ticks per beat every tick starts on time with the previous sound faded before it; at 40 BPM
  the sound plays in full.
- **SC-005**: For every built-in sound and for the custom sound, the Accent measures at least
  6 dB louder than the Click and the Sub at least 6 dB softer, and a listener can tell the five
  built-in sounds apart.
- **SC-006**: On a wired output, the lit cell and the audible click coincide within 30 ms on a
  combined screen and audio recording.
- **SC-007**: With the screen locked, the click runs for 10 minutes without a gap on both
  platforms; the first click sounds within 100 ms of pressing play.
- **SC-008**: After a phone call, a headphone change and a rotation, the settings are unchanged
  and the play state is as specified in every case.
- **SC-009**: Screenshots at compact and expanded widths and in phone landscape on both
  platforms show zero clipped, overlapping or off-screen elements and match the design on
  visual review; all test lanes are green with zero compiler and lint warnings.
- **SC-010**: 100% of controls are reachable and operable by keyboard, announce their label and
  state to the screen reader, and measure at least 44 dp.
- **SC-011**: A musician who has never used the app can set 3/4 at 92 BPM with a triplet
  subdivision, choose the Cowbell sound and start the click within 30 seconds without
  instructions.

## Assumptions

- **Build order and the constitution.** The user asked for the metronome directly after the
  tuner, and the constitution was amended to v1.2.0 on 2026-09-29 to fix that order (scaffold,
  navigation shell, tuner, metronome, audio spine, transport and timeline, mixer, effects and
  automation, recording and loops and speed, collaboration). The research report (§6) valued
  the metronome as the feature that proves sample-accurate scheduling and swappable click
  sounds, and both proofs are as useful before the audio spine as after it. The tuner already
  produces sound on both platforms, so the output path the metronome needs exists.
- **A global, standalone metronome.** The constitution (v1.2.0, 2026-09-29) defines the
  Metronome destination as a global metronome for playing along: it runs on its own tempo and
  settings, kept with the app's preferences, and is not part of any recording session, project
  or timeline session. The same amendment names the parts the transport and timeline feature
  reuses rather than rebuilds: the click sound set and its configuration (the built-in sounds,
  the custom sound import and preparation, the Accent, Click and Sub levels), the tempo and
  tempo-name model, the bar-shape model (time signature, subdivision, accent pattern) and the
  click scheduler. Locking the click to the timeline, following the project tempo and time
  signature, and the transport bar's metronome toggle belong to that feature, which takes over
  the tempo and time signature from the project when it lands.
- **How the rhythm controls relate** is decided in "How the rhythm controls fit together": the
  four controls are layers, style is a preset for the cells, subdivision ticks always sound at
  Sub, and the design's cells (Accent, Click, Sub, Click) illustrate the three levels rather than
  define Standard. Standard is the plain metronome pattern every musician expects (accent on 1),
  with 6/8 also accenting beat 4 as compound time requires; Downbeat softens every other beat;
  Syncopated moves the accent to the even beats (confirmed 2026-09-29 as the default). Because
  the user wants to hear the alternatives before fixing one, debug builds also render Syncopated
  as accented off-beat ticks or as every beat but the first (FR-037); the shipped definition can
  be changed later through `/speckit-clarify` without touching the cells.
- **Beats, not groupings.** The tempo counts the beat named by the signature's lower number, so
  6/8 has six cells and 7/8 seven (confirmed 2026-09-29); grouping (6/8 felt in two, 7/8 as
  2+2+3) is expressed with the cells by hand, not by a grouping control.
- **Subdivision labels follow the beat.** The design shows "1/4, 1/8, 1/16, 3let" with 4/4
  selected; for x/8 signatures those labels would be wrong, so the chips read "1/8, 1/16, 1/32,
  3let" there while meaning the same one, two, four and three ticks per beat.
- **Sound list.** The phone and tablet screens list different sounds; the tablet's five plus
  "Custom" are taken because they include the import the user asked for, and "808 Click" and
  "808 Rim" are read as one sound. The sounds themselves do not exist yet; the plan decides
  whether each is generated by the app or bundled as a permissively licensed recording, and how
  the Accent and Sub versions of each are produced.
- **Custom sound handling.** The user asked that a custom file "work nicely" at any tempo. The
  chosen behaviour: the file's onset is found and aligned to the beat, up to two seconds of sound
  are kept, its loudness is matched to the built-in click, its pitch and length never change with
  the tempo, and a sound still ringing when the next tick arrives is faded out just before that
  tick (confirmed 2026-09-29 against the Soundbrenner app's behaviour: the sound plays
  unchanged, only the spacing follows the tempo). One custom slot exists because the design
  shows one "Custom" tile; the app keeps its own prepared copy so the original file is not
  needed again. WAV only, as requested.
- **Levels for the custom sound** are derived from the one file (stronger for Accent, softer for
  Sub); separate files per level are excluded.
- **Runs with the screen off.** A practice metronome must survive the screen timing out, so the
  click keeps running while the screen is locked or another app is in front. It stops when the
  user leaves Metronome inside the app (the tuner needs the audio, and the transport will own
  playback later), when a call or another app takes the audio, and it never restarts by itself
  after an interruption, because an unexpected click is worse than a paused one.
- **Play always starts at beat 1** and pause is immediate; there is no count-in.
- **Tap tempo arithmetic.** Two seconds ends a sequence (a gap longer than any interval in range),
  the average of at most the last eight intervals gives a stable value without lagging a change
  of mind, and the result is a whole BPM ("the exact or nearest tempo"). The beat is not moved to
  the taps.
- **Tempo names** use the common Italian markings with the ranges shown; the boundary rule keeps
  the design's "Moderato (108-120)" true at 120.
- **Sound preview on selection** while stopped is added so the user hears a choice without
  starting the click; it is not in the design but costs nothing.
- **Beat light and reduce motion.** Lighting the current cell is information and stays on under
  reduce motion; glows and pulses are decoration and go off.
- **Design copy.** The captions "Common & Complex" and "Sync finger rhythm" are kept as the
  design's labels; casing follows the design system's label style, which the plan verifies.
- **Navigation glyph.** Feature 002 chose the Metronome glyph; the two metronome screens show
  two different ones, so neither replaces it.
- **Layout.** No right-hand panel and no transport bar, as for the tuner. The compact column
  scrolls with the Tap Tempo and play row pinned so the click can always be started and stopped;
  the medium band (600 to 839 dp) follows the compact rules; the phone-landscape layout follows
  the uploaded landscape screen.
- **Remembered settings** live with the app's other preferences; they are not part of any project
  file.
- **Dark theme only, English only, left-to-right**, as in the scaffold and features 002 and 003.
- **Branch.** `004-metronome` was created from `main` by the spec-kit git extension on
  2026-09-29.
