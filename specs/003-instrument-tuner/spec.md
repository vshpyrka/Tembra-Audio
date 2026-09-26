# Feature Specification: Instrument Tuner

**Feature Branch**: `003-instrument-tuner`

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "The next thing is I would like to build the tuner in the app. You have access to the research of how exactly to build this, as you already used the Google NotebookLM tool, and you also have the HTML report with all the data and research. You can take a look into all this data and figure out how to build the tuner. Please focus on the designs that you pulled from the Google Stage about the instrument tuner. You can check how to build the guitar and cello tuners. There are two pages where you can select whether it's a guitar, instrument, or cello. For a guitar, you can select whether it's a 6 or 7 string. There is a way to auto-listen. The app uses the microphone on the mobile phone to detect the tuning of the instrument. You can also click on the button that says "by ear," where you can click on a specific string and you can hear the sound, and you can tune the instrument by this sound. You can also select the tuning preset for the guitar. You can select whether it's a standard or non-standard tuning. For the section "by ear," there is a button with a loop tone, so you can loop the sound and you can listen by ear to repeat that."

## What the design shows *(evidence for every decision below)*

The four Stitch tuner screens under `design/stitch/screens/` were reviewed element by element:
`instrument-tuner/phone` and `instrument-tuner/tablet` (the Auto Listen mode) and
`tuner-by-ear-cello/phone` and `tuner-by-ear-7-string-guitar/tablet` (the By Ear mode). The
"two pages" in the request are these two modes of the one Tuner destination that feature 002
already placed in the navigation; they are not two navigation destinations. The tuner research
brief (`docs/research/tuner-algorithms.md`, summarised in `docs/DAW-App-Research-Report.md` §4
and `docs/research/notebooklm-deep-report-en.md` §3) supplies the achievable accuracy, response
time and pitch range used in the success criteria.

| Element | Where it appears | Verdict |
|---|---|---|
| Instrument selector with two segments, "Guitar" and "Cello", each with an icon | All four screens | **Kept** |
| String-count selector "6-String" / "7-String" | Both guitar screens | **Kept**, shown for Guitar only |
| Second selector on the cello screen reading "4-String" / "Cello Std" | Cello phone screen only | **Excluded**: a cello always has four strings and "Cello Std" has no defined meaning; no selector is shown for Cello |
| Mode selector "Auto Listen" (microphone icon) / "By Ear" (ear icon) | All four screens | **Kept** |
| Audio-state pill: "MIC ACTIVE" in Auto Listen, "TONE OUT" in By Ear | Both phone screens (the tablet Auto Listen screen also shows "MIC ACTIVE") | **Kept** as one pill that reports what the audio is doing |
| Auto Listen meter: "IN TUNE" badge, large note name with octave ("E2"), arc gauge with a needle, end marks, quarter marks and a highlighted centre segment, live "82.4 Hz" readout, "TARGET 82.4 HZ" chip, "Flat" and "Sharp" labels with arrows at the ends | Both Auto Listen screens | **Kept** |
| "Tuning Preset" picker showing the preset name and its string notes, with a disclosure arrow | Both By Ear screens | **Kept**, and shown in Auto Listen too because the preset decides what Auto Listen targets (see Assumptions) |
| "Loop Tone" toggle labelled "Active" | Both By Ear screens | **Kept** |
| One card per string: Roman numeral, target frequency, note name with octave, "Audition" button; the sounding card outlined in the accent with a "Playing" button | Both By Ear screens | **Kept** |
| Card order from the lowest string to the highest, numerals counting down to "I" for the highest string; two columns on the phone, three on the tablet | Both By Ear screens | **Kept** |
| Decorative guitar illustration on the right | 7-string tablet screen only | **Excluded** (decorative, absent from every other screen) |
| Top-bar icons, project name in the top bar, "Remote" rail item, transport bar | Vary between screens; none of the tuner screens has a transport bar | **Excluded**, as decided in feature 002 |

Export artefacts to be aware of: the phone Auto Listen screenshot rendered with a fallback serif
font (its HTML is correct), and the tablet Auto Listen `index.html` in the export contains the
timeline arranger's markup while its screenshot shows the tuner. Use the HTML for the phone
screen and the screenshot for the tablet screen.

## User Scenarios & Testing *(mandatory)*

The users of this feature are guitarists and cellists who open Tembra Audio to get their
instrument in tune before recording or practising: on a phone held in one hand with the
instrument in the other, on a tablet on a music stand, or on a desktop-mode window with a
mouse. The feature turns the Tuner placeholder from feature 002 into a working tuner with two
modes: Auto Listen, which hears the instrument through the microphone and shows how far each
string is from its target, and By Ear, which sounds the target pitch of any string so the user
can match it by ear.

### User Story 1 - Tune a string by listening (Priority: P1)

A guitarist opens Tuner, plucks the low E string and watches the meter. The app shows the note
it hears, the frequency it hears, the string it thinks the user is tuning with that string's
target frequency, and a needle that leans left when the string is flat and right when it is
sharp. As the user turns the peg the needle moves smoothly; when the string is close enough the
"In tune" badge lights up. Between plucks, and when nothing is playing, the meter rests.

**Why this priority**: Hearing the instrument and telling the user which way to turn the peg is
the tuner. Nothing else in the feature has value without it.

**Independent Test**: With the default instrument (6-string guitar, standard tuning), play a
steady tone at each open-string pitch and at known offsets into the microphone (a reference
tone from another device is enough) and confirm the displayed note, target string, direction
and amount match; then confirm the meter rests in silence.

**Acceptance Scenarios**:

1. **Given** Tuner is open in Auto Listen with microphone access granted, **When** a steady
   tone at the pitch of any open string of the selected tuning sounds, **Then** within a quarter
   of a second the meter shows that string's note name and octave, its target frequency, the
   heard frequency to one decimal, the needle centred and the "In tune" badge lit.
2. **Given** the same setup, **When** a tone 20 cents below an open string sounds, **Then** the
   needle leans left by 20 cents of the scale, the badge reads "Flat", and the target chip still
   names that string.
3. **Given** the same setup, **When** a tone 20 cents above an open string sounds, **Then** the
   needle leans right by 20 cents of the scale and the badge reads "Sharp".
4. **Given** a tone is being heard, **When** its pitch changes, **Then** the needle follows
   within a quarter of a second and moves without visible jumps or jitter.
5. **Given** the room is quiet or only noise is present, **When** no clear note is heard,
   **Then** the note area shows the resting state, the needle rests at the centre, the badge
   reads "Listening" and no target is claimed.
6. **Given** a low string with weak fundamental and strong overtones (a plucked low E or a bowed
   cello C), **When** it sounds, **Then** the meter shows the correct octave, not an octave above
   or below.
7. **Given** Auto Listen is active, **When** the user leaves the Tuner destination, switches to
   By Ear or the app goes to the background, **Then** listening stops, the "Mic active" pill
   disappears and the microphone is released.

---

### User Story 2 - Choose the instrument, string count and tuning (Priority: P1)

A user picks Guitar or Cello, for a guitar picks 6-String or 7-String, and picks a tuning preset
for that instrument: standard by default, or an alternate tuning. The choice drives both modes:
Auto Listen targets the strings of the chosen tuning, and By Ear shows one card per string of
that tuning. The app remembers the choices for the next time.

**Why this priority**: Without the right set of target strings the meter targets the wrong
pitches and the reference tones are wrong; this story is what makes the tuner a guitar and cello
tuner rather than a generic pitch meter.

**Independent Test**: Switch between Guitar 6-String, Guitar 7-String and Cello and between
presets, and confirm in each case that the By Ear cards list exactly the strings of the preset
with the right notes and frequencies, and that Auto Listen targets one of those strings.

**Acceptance Scenarios**:

1. **Given** Tuner is opened for the first time, **When** it appears, **Then** Guitar, 6-String,
   the "Standard" preset and Auto Listen are selected and the "Loop Tone" toggle is on.
2. **Given** Guitar is selected, **When** the user selects 7-String, **Then** the preset list
   changes to the 7-string presets, "Standard 7-String" is selected, and By Ear shows seven cards
   from B1 to E4.
3. **Given** Guitar is selected, **When** the user selects Cello, **Then** the string-count
   selector disappears, the "Standard" cello preset is selected, and By Ear shows four cards from
   C2 to A3.
4. **Given** any instrument, **When** the user opens the preset picker, **Then** it lists every
   preset for that instrument and string count by name with its string notes, marks the current
   one, and selecting another applies it immediately to both modes.
5. **Given** a non-standard preset such as Drop D, **When** the low E string of a
   standard-tuned guitar sounds in Auto Listen, **Then** the target chip names the preset's
   lowest string (D2) and the needle shows the string as sharp.
6. **Given** the user changed instrument, string count, preset, mode or the loop toggle,
   **When** the app is closed and reopened, **Then** the Tuner shows the same choices.

---

### User Story 3 - Tune by ear with reference tones (Priority: P2)

A user switches to By Ear, taps "Audition" on the card of the string they want to tune, hears
that string's exact target pitch, and adjusts the string until it sounds the same. The sounding
card is highlighted and its button reads "Playing"; tapping it again, or tapping another card,
stops or switches the tone. The pill reads "Tone out" while a tone sounds.

**Why this priority**: It is the second of the two modes the user asked for and the only way to
tune when the room is too noisy for the microphone, but it depends on the instrument and preset
choices of User Story 2.

**Independent Test**: In By Ear, audition each card in turn and confirm by measuring the output
that each tone is at the string's target pitch, that exactly one tone sounds at a time, and that
the card and pill states follow the tone.

**Acceptance Scenarios**:

1. **Given** By Ear is shown, **When** the user taps "Audition" on a card, **Then** a clean,
   steady tone at that string's target pitch starts within a tenth of a second at a comfortable
   level, the card is outlined in the accent colour, its button reads "Playing" and the pill
   reads "Tone out".
2. **Given** a tone is playing, **When** the user taps "Playing", **Then** the tone stops
   without a click, the card returns to its normal look and the button reads "Audition".
3. **Given** a tone is playing, **When** the user taps "Audition" on another card, **Then** the
   first tone stops and the second starts with no overlap and no gap longer than a tenth of a
   second.
4. **Given** a tone is playing, **When** the user changes instrument, string count or preset,
   switches to Auto Listen, leaves the Tuner destination or the app goes to the background,
   **Then** the tone stops.
5. **Given** microphone access has been denied, **When** the user opens By Ear, **Then** every
   card auditions normally; By Ear never needs the microphone.

---

### User Story 4 - Loop the reference tone (Priority: P2)

With "Loop Tone" on, an auditioned tone repeats until the user stops it, so both hands stay on
the instrument. With it off, a tap plays the tone once for a couple of seconds and stops by
itself.

**Why this priority**: The user named the loop button explicitly; it is what makes By Ear usable
hands-free, but it is a refinement of User Story 3.

**Independent Test**: Audition a string with the toggle on and confirm the tone keeps sounding
for at least a minute without a gap; switch the toggle off and confirm a tap sounds for about two
seconds and the card returns to "Audition" by itself.

**Acceptance Scenarios**:

1. **Given** "Loop Tone" is on, **When** the user auditions a string, **Then** the tone repeats
   seamlessly until the user stops it, auditions another string, or one of the stop conditions of
   User Story 3 occurs.
2. **Given** "Loop Tone" is off, **When** the user auditions a string, **Then** the tone sounds
   once for about two seconds and stops, and the card returns to "Audition" when it does.
3. **Given** a tone is looping, **When** the user switches "Loop Tone" off, **Then** the current
   repetition finishes and the tone stops.
4. **Given** a tone is playing once (loop off), **When** the user switches "Loop Tone" on before
   it ends, **Then** the tone keeps repeating.

---

### User Story 5 - Microphone permission and interruptions (Priority: P2)

The first time a user enters Auto Listen the app asks for microphone access with the platform's
standard prompt. If the user declines, Auto Listen explains that the microphone is needed and
offers a way to the system settings; By Ear keeps working. If a call or another app takes the
audio, listening stops and resumes when the user returns.

**Why this priority**: Without a clear permission story the P1 mode silently does nothing for a
user who tapped "Don't allow" once; but it is handling around the core, not the core.

**Independent Test**: Reset the permission, enter Auto Listen and deny; confirm the message and
the settings action, that By Ear still works, and that granting the permission in settings and
returning starts listening. Then interrupt an active session with a phone call or another audio
app and confirm listening resumes on return.

**Acceptance Scenarios**:

1. **Given** microphone access has never been requested, **When** the user enters Auto Listen,
   **Then** the platform's permission prompt appears and, if the user allows, listening starts
   at once and the pill reads "Mic active".
2. **Given** microphone access was denied, **When** Auto Listen is shown, **Then** the meter is
   replaced by a short explanation and a control that opens the app's system settings, and no
   "Mic active" pill is shown.
3. **Given** access was denied, **When** the user grants it in the system settings and returns to
   the app, **Then** Auto Listen starts listening without further action.
4. **Given** listening is active, **When** a phone call or another app interrupts the audio,
   **Then** listening stops, the pill disappears, and it resumes when the interruption ends and
   the app is in the foreground on Tuner in Auto Listen.
5. **Given** listening is active, **When** the user revokes the permission in the system settings
   and returns, **Then** the app shows the denied state from scenario 2 instead of a frozen meter.

---

### User Story 6 - Fits every window, pointer, keyboard and screen reader (Priority: P3)

The tuner lays itself out for a phone, a tablet and a desktop-mode window inside the frame from
feature 002; a mouse user sees hover states, a keyboard user can reach and operate every
control, and a screen-reader user hears the note, the direction and the amount off pitch.

**Why this priority**: Required by the constitution for every screen; it does not change what
the tuner does.

**Independent Test**: Render both modes at compact and expanded widths on both platforms and
compare with the design; tab through every control; read the meter and the cards with the
platform screen reader.

**Acceptance Scenarios**:

1. **Given** a window narrower than 600 dp, **When** either mode is shown, **Then** the
   selectors stack in two rows above the content, the meter fills the width, By Ear cards form
   two columns, and nothing is clipped or overlapping.
2. **Given** a window 840 dp or wider, **When** either mode is shown, **Then** the selectors sit
   in one row, the meter is centred in its card, By Ear cards form three columns, and no
   right-hand panel is shown.
3. **Given** a pointer, **When** it hovers over any selector, card button or toggle, **Then** the
   control shows a hover state; **When** focus is moved with a keyboard, **Then** every control
   receives a visible focus indicator in reading order and can be activated.
4. **Given** a screen reader, **When** it reaches the meter, **Then** it announces the heard
   note, the target string and the offset in cents with its direction, or the resting state;
   **When** it reaches a card, **Then** it announces the string numeral, note and whether it is
   playing.

---

### Edge Cases

- The heard pitch is exactly halfway between two strings of the preset: the lower string is the
  target.
- The heard pitch is far from every string (a fretted note, a harmonic, or a string being tuned
  from far away): the note area shows the note actually heard, the target chip names the nearest
  string of the preset, and the needle rests against the end of the scale on the correct side.
- The heard pitch is above the highest string or below the lowest string of the preset: the
  nearest string is the target; nothing crashes and the badge reads "Sharp" or "Flat".
- The instrument is a 7-string in Drop A: its lowest string (A1, 55.0 Hz) is the lowest pitch the
  tuner has to hear; it is detected as reliably as the other strings.
- The input is very loud and distorted, or very quiet: the meter either shows a correct reading
  or the resting state; it never shows a wrong note with the "In tune" badge lit.
- Two plucks in quick succession, or a chord: the meter follows the clearest note and rests when
  no single note dominates.
- The user changes preset or instrument while a tone is looping: the tone stops.
- The window is rotated or resized while a tone plays or listening is active: the audio
  continues and every choice is preserved.
- The app goes to the background (home button, lock screen) while a tone loops or listening is
  active: the audio stops and the microphone is released; on return, Auto Listen resumes
  listening but a stopped tone is not restarted.
- Headphones are connected or disconnected while a tone plays: the tone continues on the new
  route, or stops and the card returns to "Audition" if it cannot continue.
- The user enables the largest system font size: the note name, frequencies, chips, card labels
  and selector labels stay readable and unclipped; cards grow in height as needed.
- The platform's reduce-motion setting is on: the needle still moves (it is information, not
  decoration), but the "Playing" level animation and any decorative pulsing are disabled.
- The user selects the already selected segment (Guitar again, Auto Listen again): nothing
  changes.
- Two microphones or an external audio interface are present: the platform's default input is
  used; no input picker is offered in this feature.

## Requirements *(mandatory)*

### Functional Requirements

**Destination, modes and selectors**

- **FR-001**: The Tuner destination MUST replace the feature 002 placeholder with the tuner
  screen. The top bar, navigation control and back behaviour from feature 002 MUST remain
  unchanged, and the Tuner MUST NOT show a transport bar or a right-hand panel at any width.
- **FR-002**: The screen MUST offer, above the mode content: an instrument selector with exactly
  two segments, "Guitar" and "Cello", each with its icon; for Guitar only, a string-count
  selector with the segments "6-String" and "7-String"; a mode selector with the segments "Auto
  Listen" (microphone icon) and "By Ear" (ear icon); an audio-state pill; and the tuning preset
  picker. Exactly one segment of each selector MUST be selected at any time.
- **FR-003**: The tuning presets MUST be the fixed lists below, in this order, with the first
  entry as the default for its instrument and string count. Each preset is shown by name with
  its string notes from lowest to highest. Frequencies use the A4 = 440 Hz reference.

  | Instrument | Preset | Strings, lowest to highest |
  |---|---|---|
  | Guitar, 6-String | Standard | E2 A2 D3 G3 B3 E4 |
  | Guitar, 6-String | Drop D | D2 A2 D3 G3 B3 E4 |
  | Guitar, 6-String | Half-Step Down | Eb2 Ab2 Db3 Gb3 Bb3 Eb4 |
  | Guitar, 6-String | D Standard | D2 G2 C3 F3 A3 D4 |
  | Guitar, 6-String | DADGAD | D2 A2 D3 G3 A3 D4 |
  | Guitar, 6-String | Open G | D2 G2 D3 G3 B3 D4 |
  | Guitar, 6-String | Open D | D2 A2 D3 F#3 A3 D4 |
  | Guitar, 7-String | Standard 7-String | B1 E2 A2 D3 G3 B3 E4 |
  | Guitar, 7-String | Drop A | A1 E2 A2 D3 G3 B3 E4 |
  | Cello | Standard | C2 G2 D3 A3 |
  | Cello | Bach Suite No. 5 | C2 G2 D3 G3 |

- **FR-004**: Changing the instrument or string count MUST select that instrument's default
  preset; changing any of instrument, string count or preset MUST apply immediately to both
  modes and MUST stop any sounding reference tone.
- **FR-005**: The instrument, string count, preset, mode and loop-toggle choices MUST be
  remembered across app launches. On the first launch the defaults are Guitar, 6-String,
  Standard, Auto Listen, and Loop Tone on.
- **FR-006**: The audio-state pill MUST read "Mic active" while the microphone is being
  listened to and "Tone out" while a reference tone sounds, and MUST be hidden otherwise. The two
  states are mutually exclusive: the microphone is never listened to while a tone sounds and no
  tone sounds while listening.

**Auto Listen**

- **FR-007**: Selecting Auto Listen MUST start listening automatically (after permission, FR-014)
  with no further tap, and listening MUST stop, releasing the microphone, when the user switches
  to By Ear, leaves the Tuner destination, or the app leaves the foreground. It MUST resume when
  the app returns to the foreground on Tuner in Auto Listen.
- **FR-008**: While a clear note is heard the meter MUST show: the heard note name with octave
  (large), the heard frequency in Hz to one decimal (tabular figures), a target chip naming the
  target string's note and target frequency to one decimal, a needle on an arc scale from 50
  cents flat (left) to 50 cents sharp (right) with marks at the ends, at the quarter points and
  at the centre, "Flat" and "Sharp" labels with arrows at the ends of the scale, and a badge
  that reads "In tune" when the heard pitch is within 3 cents of the target, otherwise "Flat" or
  "Sharp". The in-tune state MUST be visibly distinct on the badge and the needle. The needle
  MUST be clamped to the ends of the scale when the offset exceeds 50 cents.
- **FR-009**: The target string MUST be the string of the active preset whose pitch is nearest
  to the heard pitch; when two strings are equally near, the lower string is the target. The
  offset shown is the distance from the heard pitch to that target in cents.
- **FR-010**: When no clear note is heard (silence, noise, no single dominant note) the meter
  MUST show a resting state: the note area shows a placeholder instead of a note, no frequency,
  no target chip, the needle at the centre without the in-tune styling, and the badge reading
  "Listening". The meter MUST return to the resting state within half a second of the note
  ending.
- **FR-011**: The tuner MUST hear and correctly name every open string of every preset in
  FR-003, from A1 (55.0 Hz) to E4 (329.6 Hz), and notes up to at least one octave above E4, and
  MUST show the correct octave on plucked and bowed strings whose overtones are stronger than
  the fundamental.
- **FR-012**: On a steady tone the displayed offset MUST be within 1 cent of the true offset;
  the display MUST reflect a new or changed note within a quarter of a second; the needle MUST
  update at least 20 times per second and MUST NOT flicker by more than 1 cent on a steady tone.
- **FR-013**: The needle and the live readouts MUST move smoothly while the rest of the screen
  stays steady and responsive, and MUST NOT lag the sound by more than the response time in
  FR-012.

**Microphone permission**

- **FR-014**: Entering Auto Listen for the first time MUST request microphone access with the
  platform's standard prompt. Both platforms MUST declare why the microphone is used in the
  platform's usual place, with the wording "Tembra Audio listens to your instrument to show its
  tuning."
- **FR-015**: When access is denied, Auto Listen MUST show, in place of the meter, one short
  explanation that the microphone is needed to hear the instrument and a control that opens the
  app's system settings page; the selectors and By Ear MUST remain fully usable. When access is
  granted later and the app returns to the foreground, listening MUST start without further
  action. If access is revoked while listening, the denied state MUST replace the meter.
- **FR-016**: When another app or a call takes the audio, listening MUST stop and the pill hide;
  listening MUST resume when the interruption ends while the app is in the foreground on Tuner in
  Auto Listen.

**By Ear**

- **FR-017**: By Ear MUST show the tuning preset picker, a "Loop Tone" toggle labelled "Active"
  when on, and one card per string of the active preset ordered from the lowest string to the
  highest. Each card MUST show the string numeral (Roman, "I" for the highest string, counting
  up towards the lowest), the target frequency in Hz to one decimal, the note name with octave
  (large) and an "Audition" button.
- **FR-018**: Tapping "Audition" MUST sound a clean, steady tone at that string's target pitch,
  accurate to within 1 cent, starting within a tenth of a second, at a comfortable level that
  follows the device's media volume, on the device's current output route. While it sounds the
  card MUST be outlined in the accent colour and its button MUST read "Playing"; tapping
  "Playing" MUST stop the tone. Only one tone MUST sound at a time: auditioning another card
  stops the first.
- **FR-019**: With "Loop Tone" on, the tone MUST repeat seamlessly (no gap, no click) until the
  user stops it, auditions another string, or a stop condition in FR-020 occurs. With "Loop
  Tone" off, the tone MUST sound once for about two seconds and stop by itself, returning the
  card to "Audition". Switching the toggle off during a loop ends the tone after the current
  repetition; switching it on during a single play keeps the tone repeating.
- **FR-020**: A reference tone MUST stop, with the card returning to "Audition", when the user
  changes instrument, string count or preset, switches to Auto Listen, leaves the Tuner
  destination, or the app leaves the foreground. A tone MUST NOT restart by itself when the app
  returns.
- **FR-021**: Tones MUST start and stop without clicks or pops, and switching between strings
  MUST leave no audible gap longer than a tenth of a second.

**Layout, input and accessibility**

- **FR-022**: Both modes MUST adapt to the window: in windows narrower than 600 dp the selectors
  occupy two rows (instrument and pill; string count and mode), the meter spans the content
  width and the cards form two columns; in windows 840 dp or wider the selectors share one row,
  the meter is centred in its card and the cards form three columns; between the two widths the
  layout follows the narrower rules. Nothing MUST clip, overlap or scroll horizontally at any
  width, including at the largest system font size.
- **FR-023**: Every selector segment, the preset picker, the loop toggle, the settings control of
  the denied state and every card button MUST show a hover state under a pointer, a visible
  focus indicator, MUST be operable from the keyboard, and MUST measure at least 44 dp in both
  dimensions. Icon-only controls, if any, MUST show a tooltip on hover.
- **FR-024**: Every control MUST expose its label, selected or checked state and a stable test
  identifier to accessibility services. The meter MUST expose, as one element, the heard note,
  the target string and the offset in cents with its direction, or the resting state; each card
  MUST expose its numeral, note and playing state; the pill MUST expose its text.
- **FR-025**: All colours, type styles, spacing, radii and icons MUST come from the shared design
  system; the new icons (microphone, ear, play, pause, speaker, chevron, left and right arrows,
  and the guitar and cello glyphs) MUST be added to the shared icon set through its existing
  mechanism. Every live number MUST use tabular figures. The meter MUST match the design's arc
  gauge on visual review.
- **FR-026**: When the platform's reduce-motion setting is on, the needle MUST still move, but
  decorative animation (the "Playing" level indicator, any pulsing of the pill or the badge)
  MUST be disabled.

**Quality**

- **FR-027**: Automated tests MUST cover: the mapping from a heard frequency to note name,
  octave, target string and cents, including the tie rule, for every string of every preset;
  the preset lists; the remembered choices; the pitch-hearing accuracy, response time and octave
  correctness of FR-011 and FR-012 using synthetic signals played offline without a microphone
  (steady tones, tones with strong overtones and a weak fundamental, silence and noise); the
  reference-tone pitch accuracy of FR-018 from offline output; both modes' screens with a fake
  audio source, including the denied state, the resting state and the card states; and the
  accessibility semantics of FR-024. Behaviour that needs real hardware (the permission prompt,
  interruptions, route changes) MUST be verified on a device and recorded in the feature's
  verification notes.
- **FR-028**: Screenshots of both modes at compact (360 dp) and expanded (1024 dp or wider)
  widths on Android and on the iOS simulator MUST be stored under the feature directory and
  reviewed against the Stitch references before acceptance, with the phone Auto Listen screen
  compared to its HTML and the tablet Auto Listen screen to its screenshot (see the export
  artefacts above).
- **FR-029**: Any dependency added for this feature MUST carry a permissive licence and MUST be
  recorded in the licence inventory with its version and source.
- **FR-030**: The feature MUST NOT include: a chromatic mode that ignores the preset, custom or
  user-editable tunings, an adjustable reference pitch (A4 stays 440 Hz), polyphonic or strum
  tuning, instruments other than guitar and cello, capo or transposition settings, haptic
  feedback, an input-device picker, a transport bar, top-bar icons, or the decorative
  illustration.

### Key Entities

- **Instrument**: Guitar or Cello. Guitar has a string count of 6 or 7; Cello always has 4.
- **Tuning Preset**: a named, ordered list of target strings for one instrument and string
  count (FR-003). One preset is active at a time; the first of each list is the default.
- **Target String**: one string of the active preset: numeral (Roman, "I" for the highest), note
  name with octave, target frequency. It is what Auto Listen measures against and what By Ear
  sounds.
- **Tuner Mode**: Auto Listen or By Ear; exactly one is active. Auto Listen owns the microphone,
  By Ear owns the reference tone; they are never active together.
- **Pitch Reading**: what the tuner currently hears: either resting (no clear note) or a heard
  frequency with its nearest note name and octave, the target string, the offset in cents and
  whether it is in tune.
- **Audition State**: which target string, if any, is sounding, and whether it loops.
- **Microphone Access State**: not yet asked, granted, or denied; drives whether Auto Listen
  shows the meter or the denied state.
- **Tuner Preferences**: the remembered instrument, string count, preset, mode and loop toggle.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every open string of every preset, a steady tone at the target pitch is shown
  with the correct note, octave and target string, an offset within 1 cent, and the "In tune"
  badge lit, within 250 ms of the tone starting, on both platforms.
- **SC-002**: Tones offset by 10, 20 and 40 cents in either direction are shown within 1 cent of
  the true offset on the correct side, and the "In tune" badge lights only for offsets of 3
  cents or less.
- **SC-003**: On synthetic tones whose fundamental is 12 dB weaker than their second overtone,
  at every string pitch from A1 to E4, at least 99% of readings show the correct octave.
- **SC-004**: On a quiet-room recording or live test with a real guitar and a real cello, every
  open string can be brought to "In tune" and a musician confirms each string against the By
  Ear tone with no audible beating.
- **SC-005**: Every reference tone measures within 1 cent of its target pitch; a looped tone runs
  for 60 seconds with no audible gap or click; switching strings leaves no gap over 100 ms.
- **SC-006**: The needle updates at least 20 times per second, and on a steady tone its
  displayed offset varies by no more than 1 cent.
- **SC-007**: The meter returns to the resting state within 500 ms of a note ending, and the
  microphone is released within 1 second of leaving Auto Listen or backgrounding the app (the
  platform's microphone indicator turns off).
- **SC-008**: With microphone access denied, 100% of By Ear functions work, and the denied state
  offers a working path to the system settings.
- **SC-009**: Screenshots of both modes at compact and expanded widths on both platforms show
  zero clipped, overlapping or off-screen elements and match the design on visual review; all
  test lanes are green with zero compiler and lint warnings.
- **SC-010**: 100% of controls are reachable and operable by keyboard, announce their label and
  state to the screen reader, and measure at least 44 dp.
- **SC-011**: A guitarist who has never used the app can tune all six strings of a
  standard-tuned guitar in Auto Listen within 2 minutes without instructions.

## Assumptions

- **Build order and the constitution.** The constitution (v1.0.2) fixes the build order as
  scaffold, navigation shell, audio spine, transport and timeline, mixer, metronome, effects and
  automation, recording and loops and speed, tuner, collaboration, and says a plan must not pull
  later work forward without amending that order. This feature pulls the tuner ahead of the audio
  spine at the user's request. The research report (§6) notes the tuner is largely independent
  of the rest of the engine, and it is the first feature that makes the app capture audio and
  produce sound. Before `/speckit-plan`, the build order must be amended with
  `/speckit-constitution` to place the tuner after the navigation shell; this specification does
  not decide technology and is valid either way.
- **Two pages are two modes.** The request's "two pages" are the Auto Listen and By Ear modes of
  the single Tuner destination from feature 002, switched with the mode selector shown on every
  tuner design screen. No new navigation destination is added.
- **Preset picker in both modes.** The design places the tuning preset picker only on the By Ear
  screens. Because the preset decides which strings Auto Listen targets, the picker is shown in
  both modes; a user tuning to Drop D in Auto Listen must be able to see and change the preset
  there. This is the one deliberate addition to the Auto Listen design.
- **Cello has no string-count selector.** The cello design screen shows a "4-String / Cello Std"
  selector whose second segment has no defined meaning. A cello always has four strings, so the
  selector is hidden for Cello rather than shown with one option.
- **Which alternate tunings.** The user asked for standard and non-standard tunings without
  naming them. The lists in FR-003 are the common alternate tunings for each instrument; custom
  tunings are out of scope. The 7-string "Drop A" preset sets the lowest pitch the tuner must
  hear (A1, 55.0 Hz), a little below the cello C2 the research brief designed for; the brief's
  search range already covers it.
- **Targeting the nearest string, not a chromatic note.** Both Auto Listen design screens show a
  "TARGET" chip beside the heard frequency, so Auto Listen measures against the nearest string of
  the preset rather than the nearest chromatic note. The large note still names what is actually
  heard, so a string tuned far away (a low E at D2) reads "D2", targets "E2", and the needle
  rests against the flat end. A chromatic mode is excluded (FR-030). A consequence to accept:
  while a string is more than half the distance to a neighbouring string away from its target,
  the tuner targets the neighbour until the string gets closer.
- **In-tune tolerance and accuracy.** The badge lights within 3 cents of the target, inside the
  3 to 5 cent window the research brief recommends; the 1 cent accuracy, quarter-second response
  and 20 updates per second follow from the brief's analysis of what a well-built detector
  achieves on low strings. If the plan finds the low strings need more time, the response time
  may be relaxed to 300 ms for strings below E2 through `/speckit-clarify`.
- **Reference pitch.** A4 = 440 Hz, fixed; no design screen shows a reference-pitch control.
- **Reference tone character and loop semantics.** The design gives no sound. A single audition
  is a clean, steady tone of about two seconds; looping repeats it seamlessly, which sounds like a
  sustained tone. "Loop Tone" defaults to on because both By Ear design screens show it active.
- **Audio-state pill.** The phone screens show "MIC ACTIVE" in Auto Listen and "TONE OUT" in By
  Ear; they are treated as two states of one pill that reports what the audio is doing and hides
  when nothing is. Sentence case ("Mic active", "Tone out") follows the design system's label
  style; the plan verifies the exact casing against the design tokens.
- **Badge states.** The design shows only the "IN TUNE" badge. Its other states, "Flat",
  "Sharp" and "Listening", are added so the badge always tells the user what the meter is doing;
  they reuse the design's "Flat" and "Sharp" words.
- **Auto Listen starts by itself.** No design screen has a start button; entering Auto Listen
  with permission listens at once, and leaving it stops. The microphone is never used in the
  background.
- **Permission copy.** The microphone usage description is fixed here so both platforms say the
  same thing; the denied-state copy is written in the plan from the same sentence.
- **Remembered choices** live with the app's other preferences; they are not part of any
  project file.
- **Layout.** The Tuner has no right-hand panel (as decided for it in feature 002) and no
  transport bar (none of the tuner screens shows one). The tablet design's decorative guitar
  illustration is excluded. The expanded-width layout keeps the design's one-row selector bar,
  centred meter and three-column cards; the medium band (600 to 839 dp) follows the compact
  rules.
- **Dark theme only, English only, left-to-right**, as in the scaffold and feature 002.
- **Branch.** `003-instrument-tuner` was created from `main` by the spec-kit git extension on
  2026-09-26.
