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
| Auto Listen meter: "IN TUNE" badge, large note name with octave ("E2"), arc gauge with a needle, end marks, quarter marks and a highlighted centre segment, live "82.4 Hz" readout, "TARGET 82.4 HZ" chip, "Flat" and "Sharp" labels with arrows at the ends | Both Auto Listen screens | **Kept**; the values are examples, the readout is the live frequency heard and the chip is the nearest note's reference frequency |
| "Tuning Preset" picker showing the preset name and its string notes, with a disclosure arrow | Both By Ear screens | **Kept** on By Ear only (clarified 2026-09-26: Auto Listen is chromatic and does not use the preset) |
| "Loop Tone" toggle labelled "Active" | Both By Ear screens | **Kept** |
| One card per string: Roman numeral, target frequency, note name with octave, "Audition" button; the sounding card outlined in the accent with a "Playing" button | Both By Ear screens | **Kept** |
| Card order from the lowest string to the highest, numerals counting down to "I" for the highest string; two columns on the phone, three on the tablet | Both By Ear screens | **Kept** |
| Decorative guitar illustration on the right | 7-string tablet screen only | **Excluded** (decorative, absent from every other screen) |
| Top-bar icons, project name in the top bar, "Remote" rail item, transport bar | Vary between screens; none of the tuner screens has a transport bar | **Excluded**, as decided in feature 002 |

Export artefacts to be aware of: the phone Auto Listen screenshot rendered with a fallback serif
font (its HTML is correct), and the tablet Auto Listen `index.html` in the export contains the
timeline arranger's markup while its screenshot shows the tuner. Use the HTML for the phone
screen and the screenshot for the tablet screen.

## Clarifications

### Session 2026-09-26

- Q: When Auto Listen hears a note, what should the meter measure it against? → A: The nearest
  semitone, as a standard chromatic tuner does: the large text names the note actually heard with
  its accidental, the frequency readout is the live value heard, the needle shows the distance to
  that nearest note, the "Flat" or "Sharp" label is highlighted by direction, and the "In tune"
  pill lights within tolerance. The tuning preset affects By Ear only, so the preset picker stays
  on By Ear as the design shows.
- Q: Which non-standard tunings should the tuning preset picker offer? → A: The fixed list in
  FR-003 (6-string: Standard, Drop D, Half-Step Down, D Standard, DADGAD, Open G, Open D;
  7-string: Standard 7-String, Drop A; cello: Standard, Bach Suite No. 5). No custom or
  user-editable tunings.
- Q: What should a By Ear reference tone sound like? → A: A clean, steady synthesized tone, but
  replaceable: if a recording for that instrument and note is placed in one documented location,
  the app plays the recording instead of the synthesized tone (FR-022). No recordings ship with
  this feature.
- Q: Should the constitution's build order be amended so the tuner comes right after the
  navigation shell? → A: Yes. Amend the constitution to place the tuner immediately after the
  navigation shell; the audio spine, transport and timeline follow later. Simpler features first.

## User Scenarios & Testing *(mandatory)*

The users of this feature are guitarists and cellists who open Tembra Audio to get their
instrument in tune before recording or practising: on a phone held in one hand with the
instrument in the other, on a tablet on a music stand, or on a desktop-mode window with a
mouse. The feature turns the Tuner placeholder from feature 002 into a working tuner with two
modes: Auto Listen, which hears the instrument through the microphone and shows the note heard
and how far it is from being in tune, and By Ear, which sounds the target pitch of any string of
the chosen tuning so the user can match it by ear.

### User Story 1 - Tune a string by listening (Priority: P1)

A guitarist opens Tuner, plucks the low E string and watches the meter. The app shows, as large
text, the note it hears (with its accidental, "E2" or "D♯2"), the frequency it hears, the
reference frequency of that nearest note, and a needle that leans left when the string is flat of
that note and right when it is sharp, with the "Flat" or "Sharp" label at that end highlighted. As
the user turns the peg the needle moves smoothly; when the string is close enough the "In tune"
pill lights up. Between plucks, and when nothing is playing, the meter rests.

**Why this priority**: Hearing the instrument and telling the user which way to turn the peg is
the tuner. Nothing else in the feature has value without it.

**Independent Test**: Play a steady tone at each open-string pitch of a standard guitar and cello,
at pitches between them, and at known offsets into the microphone (a reference tone from another
device is enough) and confirm the displayed note, direction and amount match; then confirm the
meter rests in silence.

**Acceptance Scenarios**:

1. **Given** Tuner is open in Auto Listen with microphone access granted, **When** a steady
   tone at the pitch of any note between A1 and E5 sounds, **Then** within a quarter of a second
   the meter shows that note's name with accidental and octave, its reference frequency, the
   heard frequency to one decimal, the needle centred and the "In tune" pill lit.
2. **Given** the same setup, **When** a tone 20 cents below a note sounds, **Then** the needle
   leans left by 20 cents of the scale, the "Flat" label is highlighted, the "In tune" pill is
   unlit, and the note shown is still that note.
3. **Given** the same setup, **When** a tone 20 cents above a note sounds, **Then** the needle
   leans right by 20 cents of the scale and the "Sharp" label is highlighted.
4. **Given** a tone is being heard, **When** its pitch changes, **Then** the needle follows
   within a quarter of a second and moves without visible jumps or jitter, and when the pitch
   crosses the halfway point to the next semitone the note name changes to that note.
5. **Given** the room is quiet or only noise is present, **When** no clear note is heard,
   **Then** the note area shows the resting state, the needle rests at the centre, the "In tune"
   pill is unlit and neither "Flat" nor "Sharp" is highlighted.
6. **Given** a low string with weak fundamental and strong overtones (a plucked low E or a bowed
   cello C), **When** it sounds, **Then** the meter shows the correct octave, not an octave above
   or below.
7. **Given** Auto Listen is active, **When** the user leaves the Tuner destination, switches to
   By Ear or the app goes to the background, **Then** listening stops, the "Mic active" pill
   disappears and the microphone is released.

---

### User Story 2 - Choose the instrument, string count and tuning (Priority: P1)

A user picks Guitar or Cello, for a guitar picks 6-String or 7-String, and in By Ear picks a
tuning preset for that instrument: standard by default, or an alternate tuning. By Ear shows one
card per string of that tuning; Auto Listen is chromatic and does not depend on the preset. The
app remembers the choices for the next time.

**Why this priority**: Without the right set of strings the reference tones are wrong; this story
is what makes By Ear a guitar and cello tuner rather than a generic tone generator.

**Independent Test**: Switch between Guitar 6-String, Guitar 7-String and Cello and between
presets, and confirm in each case that the By Ear cards list exactly the strings of the preset
with the right notes and frequencies.

**Acceptance Scenarios**:

1. **Given** Tuner is opened for the first time, **When** it appears, **Then** Guitar, 6-String,
   the "Standard" preset and Auto Listen are selected and the "Loop Tone" toggle is on.
2. **Given** Guitar is selected, **When** the user selects 7-String, **Then** the preset list
   changes to the 7-string presets, "Standard 7-String" is selected, and By Ear shows seven cards
   from B1 to E4.
3. **Given** Guitar is selected, **When** the user selects Cello, **Then** the string-count
   selector disappears, the "Standard" cello preset is selected, and By Ear shows four cards from
   C2 to A3.
4. **Given** By Ear is shown, **When** the user opens the preset picker, **Then** it lists every
   preset for the current instrument and string count by name with its string notes, marks the
   current one, and selecting another replaces the cards immediately.
5. **Given** the "Drop D" preset is selected, **When** By Ear is shown, **Then** the lowest card
   reads "VI", "D2" and "73.4 Hz", and auditioning it sounds D2.
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
   note and the offset in cents with its direction, or the resting state;
   **When** it reaches a card, **Then** it announces the string numeral, note and whether it is
   playing.

---

### Edge Cases

- The heard pitch is exactly halfway between two semitones (50 cents): the lower note is shown,
  with the needle at the sharp end.
- The heard pitch drifts across the halfway point while the user tunes: the note name switches to
  the neighbouring note and the needle jumps from one end of the scale to the other; this is the
  expected chromatic behaviour and must not be smoothed away.
- The heard pitch is outside the hearing range (below A1 or well above E5, for example a
  harmonic or a whistle): the meter shows the resting state rather than a wrong note.
- The instrument is a 7-string in Drop A: its lowest string (A1, 55.0 Hz) is the lowest pitch the
  tuner has to hear and to sound; it is detected as reliably as the other strings.
- The input is very loud and distorted, or very quiet: the meter either shows a correct reading
  or the resting state; it never shows a wrong note with the "In tune" pill lit.
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
  Listen" (microphone icon) and "By Ear" (ear icon); and an audio-state pill. The tuning preset
  picker belongs to By Ear (FR-017) and MUST NOT appear in Auto Listen. Exactly one segment of
  each selector MUST be selected at any time.
- **FR-003**: The tuning presets MUST be the fixed lists below, in this order, with the first
  entry as the default for its instrument and string count. Each preset is shown by name with
  its string notes from lowest to highest. Frequencies use the A4 = 440 Hz reference.

  | Instrument | Preset | Strings, lowest to highest |
  |---|---|---|
  | Guitar, 6-String | Standard | E2 A2 D3 G3 B3 E4 |
  | Guitar, 6-String | Drop D | D2 A2 D3 G3 B3 E4 |
  | Guitar, 6-String | Half-Step Down | D#2 G#2 C#3 F#3 A#3 D#4 |
  | Guitar, 6-String | D Standard | D2 G2 C3 F3 A3 D4 |
  | Guitar, 6-String | DADGAD | D2 A2 D3 G3 A3 D4 |
  | Guitar, 6-String | Open G | D2 G2 D3 G3 B3 D4 |
  | Guitar, 6-String | Open D | D2 A2 D3 F#3 A3 D4 |
  | Guitar, 7-String | Standard 7-String | B1 E2 A2 D3 G3 B3 E4 |
  | Guitar, 7-String | Drop A | A1 E2 A2 D3 G3 B3 E4 |
  | Cello | Standard | C2 G2 D3 A3 |
  | Cello | Bach Suite No. 5 | C2 G2 D3 G3 |

- **FR-004**: Changing the instrument or string count MUST select that instrument's default
  preset; changing any of instrument, string count or preset MUST replace the By Ear cards
  immediately and MUST stop any sounding reference tone. Auto Listen MUST NOT depend on the
  preset.
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
- **FR-008**: While a clear note is heard the meter MUST show: the heard note, named as the
  nearest semitone in equal temperament with A4 = 440 Hz, with its accidental and octave (large);
  the heard frequency in Hz to one decimal (tabular figures); a chip showing that note's
  reference frequency to one decimal; a needle on an arc scale from 50 cents flat (left) to 50
  cents sharp (right) with marks at the ends, at the quarter points and at the centre; "Flat" and
  "Sharp" labels with arrows at the ends of the scale, the one on the needle's side highlighted
  whenever the offset exceeds the in-tune tolerance; and an "In tune" pill that is lit when the
  heard pitch is within 3 cents of the nearest note and unlit otherwise. The in-tune state MUST
  be visibly distinct on the pill and the needle.
- **FR-009**: The note shown MUST be the semitone nearest to the heard pitch; when the pitch is
  exactly halfway between two semitones, the lower note is shown. The offset shown is the
  distance from the heard pitch to that note in cents, so it always lies within the scale. The
  black-key notes MUST be spelled with sharps (C#, D#, F#, G#, A#) everywhere the app names a
  note, including the preset lists and the By Ear cards.
- **FR-010**: When no clear note is heard (silence, noise, no single dominant note, or a pitch
  outside the hearing range) the meter MUST show a resting state: the note area shows the word
  "Listening" in place of a note, no frequency and no reference chip, the needle at the centre
  without the in-tune styling, the "In tune" pill unlit and neither end label highlighted. The
  meter MUST return to the resting state within half a second of the note ending.
- **FR-011**: The tuner MUST hear and correctly name every semitone from A1 (55.0 Hz), the
  lowest string of any preset in FR-003, to at least E5 (659.3 Hz), one octave above the highest
  string, and MUST show the correct octave on plucked and bowed strings whose overtones are
  stronger than the fundamental.
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
- **FR-018**: Tapping "Audition" MUST sound the reference sound for that string (the built-in
  clean, steady synthesized tone with a soft attack and release, or a bundled recording that
  replaces it under FR-022) at the string's target pitch, accurate to within 1 cent, starting
  within a tenth of a second, at a comfortable level that follows the device's media volume, on
  the device's current output route. While it sounds the
  card MUST be outlined in the accent colour and its button MUST read "Playing"; tapping
  "Playing" MUST stop the tone. Only one tone MUST sound at a time: auditioning another card
  stops the first.
- **FR-019**: With "Loop Tone" on, the tone MUST repeat seamlessly (no gap, no click) until the
  user stops it, auditions another string, or a stop condition in FR-020 occurs. With "Loop
  Tone" off, the built-in tone MUST sound once for about two seconds (a recording plays once in
  full) and stop by itself, returning the card to "Audition". Switching the toggle off during a loop ends the tone after the current
  repetition; switching it on during a single play keeps the tone repeating.
- **FR-020**: A reference tone MUST stop, with the card returning to "Audition", when the user
  changes instrument, string count or preset, switches to Auto Listen, leaves the Tuner
  destination, or the app leaves the foreground. A tone MUST NOT restart by itself when the app
  returns.
- **FR-021**: Tones MUST start and stop without clicks or pops, and switching between strings
  MUST leave no audible gap longer than a tenth of a second.
- **FR-022**: Reference sounds MUST be replaceable without code changes. The app MUST look in
  one documented location among its bundled resources for a recording named by instrument and
  note (one file per instrument and note, for example the guitar E2 or the cello C2; the exact
  location, file format and naming rule are fixed in the plan and written into the feature's
  documentation). When a recording exists for the auditioned string's instrument and note it
  MUST be played instead of the synthesized tone, once in full when "Loop Tone" is off and
  repeated seamlessly when it is on; when none exists the synthesized tone MUST play. An
  automated test MUST measure every bundled recording with the same pitch hearing Auto Listen
  uses and fail if any recording is more than 1 cent from its note. This feature ships no
  recordings.

**Layout, input and accessibility**

- **FR-023**: Both modes MUST adapt to the window: in windows narrower than 600 dp the selectors
  occupy two rows (instrument and pill; string count and mode), the meter spans the content
  width and the cards form two columns; in windows 840 dp or wider the selectors share one row,
  the meter is centred in its card and the cards form three columns; between the two widths the
  layout follows the narrower rules. Nothing MUST clip, overlap or scroll horizontally at any
  width, including at the largest system font size.
- **FR-024**: Every selector segment, the preset picker, the loop toggle, the settings control of
  the denied state and every card button MUST show a hover state under a pointer, a visible
  focus indicator, MUST be operable from the keyboard, and MUST measure at least 44 dp in both
  dimensions. Icon-only controls, if any, MUST show a tooltip on hover.
- **FR-025**: Every control MUST expose its label, selected or checked state and a stable test
  identifier to accessibility services. The meter MUST expose, as one element, the heard note
  and the offset in cents with its direction, or the resting state; each card MUST expose its
  numeral, note and playing state; the pill MUST expose its text.
- **FR-026**: All colours, type styles, spacing, radii and icons MUST come from the shared design
  system; the new icons (microphone, ear, play, pause, speaker, chevron, left and right arrows,
  and the guitar and cello glyphs) MUST be added to the shared icon set through its existing
  mechanism. Every live number MUST use tabular figures. The meter MUST match the design's arc
  gauge on visual review.
- **FR-027**: When the platform's reduce-motion setting is on, the needle MUST still move, but
  decorative animation (the "Playing" level indicator, any pulsing of the audio-state pill or
  the "In tune" pill)
  MUST be disabled.

**Quality**

- **FR-028**: Automated tests MUST cover: the mapping from a heard frequency to note name,
  octave and cents, including the halfway rule and sharp spelling, at every semitone of the
  hearing range and at every string pitch of every preset; the preset lists; the remembered choices; the pitch-hearing accuracy, response time and octave
  correctness of FR-011 and FR-012 using synthetic signals played offline without a microphone
  (steady tones, tones with strong overtones and a weak fundamental, silence and noise); the reference-tone pitch accuracy of FR-018 from offline output; the recording override and
  fallback of FR-022 with a test recording; both modes' screens with a fake
  audio source, including the denied state, the resting state and the card states; and the
  accessibility semantics of FR-025. Behaviour that needs real hardware (the permission prompt,
  interruptions, route changes) MUST be verified on a device and recorded in the feature's
  verification notes.
- **FR-029**: Screenshots of both modes at compact (360 dp) and expanded (1024 dp or wider)
  widths on Android and on the iOS simulator MUST be stored under the feature directory and
  reviewed against the Stitch references before acceptance, with the phone Auto Listen screen
  compared to its HTML and the tablet Auto Listen screen to its screenshot (see the export
  artefacts above).
- **FR-030**: Any dependency added for this feature MUST carry a permissive licence and MUST be
  recorded in the licence inventory with its version and source.
- **FR-031**: The feature MUST NOT include: a string-locked mode that measures against the
  selected tuning's strings instead of the nearest semitone, custom or user-editable tunings, an adjustable reference pitch (A4 stays 440 Hz), polyphonic or strum
  tuning, instruments other than guitar and cello, capo or transposition settings, haptic
  feedback, an input-device picker, a transport bar, top-bar icons, or the decorative
  illustration.

### Key Entities

- **Instrument**: Guitar or Cello. Guitar has a string count of 6 or 7; Cello always has 4.
- **Tuning Preset**: a named, ordered list of target strings for one instrument and string
  count (FR-003). One preset is active at a time; the first of each list is the default.
- **Target String**: one string of the active preset: numeral (Roman, "I" for the highest), note
  name with octave, target frequency. It is what By Ear sounds; Auto Listen does not use it.
- **Tuner Mode**: Auto Listen or By Ear; exactly one is active. Auto Listen owns the microphone,
  By Ear owns the reference tone; they are never active together.
- **Pitch Reading**: what the tuner currently hears: either resting (no clear note) or a heard
  frequency with the nearest semitone's name, accidental and octave, that note's reference
  frequency, the offset in cents with its direction and whether it is in tune.
- **Reference Sound**: what a card plays for its instrument and note: the built-in synthesized
  tone, or a bundled recording found in the documented location, which takes precedence.
- **Audition State**: which target string, if any, is sounding, and whether it loops.
- **Microphone Access State**: not yet asked, granted, or denied; drives whether Auto Listen
  shows the meter or the denied state.
- **Tuner Preferences**: the remembered instrument, string count, preset, mode and loop toggle.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every semitone from A1 to E5, which includes every open string of every
  preset, a steady tone at that pitch is shown with the correct note, accidental and octave, an
  offset within 1 cent, and the "In tune" pill lit, within 250 ms of the tone starting, on both
  platforms.
- **SC-002**: Tones offset by 10, 20 and 40 cents in either direction are shown within 1 cent of
  the true offset on the correct side with the matching end label highlighted, and the "In tune"
  pill lights only for offsets of 3 cents or less.
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

- **Build order and the constitution.** The constitution (v1.0.2) fixed the build order as
  scaffold, navigation shell, audio spine, transport and timeline, mixer, metronome, effects and
  automation, recording and loops and speed, tuner, collaboration, and says a plan must not pull
  later work forward without amending that order. On 2026-09-26 the user decided to amend the
  order with `/speckit-constitution` so the tuner comes immediately after the navigation shell,
  with the audio spine, transport and timeline following later. The research report (§6) notes
  the tuner is largely independent of the rest of the engine; it is the first feature that makes
  the app capture audio and produce sound, and the plan must respect the engine-isolation
  principle for both.
- **Two pages are two modes.** The request's "two pages" are the Auto Listen and By Ear modes of
  the single Tuner destination from feature 002, switched with the mode selector shown on every
  tuner design screen. No new navigation destination is added.
- **Preset picker on By Ear only.** Auto Listen is chromatic (clarified 2026-09-26), so the
  tuning preset only affects By Ear and the picker stays where the design places it.
- **Cello has no string-count selector.** The cello design screen shows a "4-String / Cello Std"
  selector whose second segment has no defined meaning. A cello always has four strings, so the
  selector is hidden for Cello rather than shown with one option.
- **Which alternate tunings.** The user asked for standard and non-standard tunings without
  naming them and confirmed the lists in FR-003 on 2026-09-26; they are the common alternate
  tunings for each instrument, and custom tunings are out of scope. The 7-string "Drop A" preset sets the lowest pitch the tuner must
  hear (A1, 55.0 Hz), a little below the cello C2 the research brief designed for; the brief's
  search range already covers it.
- **Chromatic Auto Listen.** The user chose the standard chromatic tuner behaviour on
  2026-09-26: the meter names the nearest semitone to what it hears and measures the offset from
  that note; the design's "82.4 Hz" and "TARGET 82.4 HZ" are example values, the readout is live
  and the chip is the nearest note's reference frequency. A string-locked mode that measures
  against the selected tuning is excluded (FR-031). A consequence to accept: a string more than
  50 cents from its intended pitch reads as the neighbouring note until it gets closer, as in
  every chromatic tuner.
- **Sharp spelling.** Black-key notes are spelled with sharps (D#2, not Eb2) everywhere, the
  usual chromatic-tuner convention; the "Half-Step Down" preset is listed that way.
- **In-tune tolerance and accuracy.** The pill lights within 3 cents of the nearest note, inside the
  3 to 5 cent window the research brief recommends; the 1 cent accuracy, quarter-second response
  and 20 updates per second follow from the brief's analysis of what a well-built detector
  achieves on low strings. If the plan finds the low strings need more time, the response time
  may be relaxed to 300 ms for strings below E2 through `/speckit-clarify`.
- **Reference pitch.** A4 = 440 Hz, fixed; no design screen shows a reference-pitch control.
- **Reference tone character and loop semantics.** The design gives no sound. The user chose a
  clean, steady synthesized tone on 2026-09-26 and asked that real recordings can replace it
  later by dropping files into a documented place (FR-022); no recordings ship now. A single
  audition of the built-in tone lasts about two seconds; looping repeats it seamlessly, which
  sounds like a sustained tone. "Loop Tone" defaults to on because both By Ear design screens
  show it active.
- **Audio-state pill.** The phone screens show "MIC ACTIVE" in Auto Listen and "TONE OUT" in By
  Ear; they are treated as two states of one pill that reports what the audio is doing and hides
  when nothing is. Sentence case ("Mic active", "Tone out") follows the design system's label
  style; the plan verifies the exact casing against the design tokens.
- **Pill and end labels.** The "In tune" pill is lit only within tolerance and unlit otherwise
  (it stays in place so the layout does not jump); direction is shown by highlighting the
  design's "Flat" or "Sharp" end label; the resting state puts the word "Listening" in the note
  area.
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
