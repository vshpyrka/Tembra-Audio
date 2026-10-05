# Feature Specification: Tuner Noise Gate

**Feature Branch**: `006-tuner-noise-gate`

**Created**: 2026-10-05

**Status**: Draft

**Input**: User description: "I would like you to start working on the tuner noise gate functionality. You did all
the research and technical analysis about how to implement it, so please start doing that. Please include testing on
all connected iOS simulators and the connected Pixel device."

## What the tuner does today and what the design shows *(evidence for every decision below)*

The research brief `docs/research/tuner-noise-gate.md` (2026-09-29) describes the built tuner of feature 003. Its
findings were checked again on 2026-10-05; the tuner's hearing has not changed since the brief was written.

**What Auto Listen ignores today.** Three fixed checks, none of them adjustable:

- silence (a muted or dead input);
- sound with no clear pitch (hiss, a fan, room noise);
- pitches outside the hearing range of feature 003 (below A1 or well above E5).

**What gets through.** Any sound with a clear pitch, however quiet: a voice, a television, music in the next room,
the hum of the mains. When the instrument is silent the meter names whichever of those is clearest. Feature 003
promised the resting state for "silence, noise, no single dominant note" (its FR-010); it never promised rest on a
quiet background that has a pitch, and no test covers that case.

**What a noise gate is.** The standard answer to exactly that case: ignore the input until it is louder than a level
the user sets above the background and below the instrument. It helps only when the instrument is louder than the
background; nothing short of separating the sounds helps otherwise.

**What is already there.** The tuner measures how loud the input is many times a second, and nothing on the screen
shows it.

**What the design shows.** One Stitch screen draws the control,
`design/stitch/screens/tuner-landscape-auto-listen-d`; no other tuner screen has it. Feature 003 rejected that screen
because it added controls the tuner's specification did not have. This feature takes the noise gate from it and
nothing else.

| Element on that screen | Verdict |
|---|---|
| Card title "NOISE GATE / SENSITIVITY" | **Kept** as the label "Noise gate". "Sensitivity" is dropped: it runs the opposite way (high sensitivity is a low gate), and one control must not carry two names that point in opposite directions |
| Slider: a round handle on a rounded track | **Kept** (clarified 2026-10-05). The handle sets the gate level; with the handle at the start of the track the gate is off |
| Gradient fill of the track up to the handle | **Changed**: the fill shows the live input level, not the handle's position, so the sound and the gate are seen on one scale |
| Tick labels "−60 dB (High)", "−40 dB (Optimal)", "−10 dB (Low)" | **Replaced** by two end labels, "Off" at the start and "High" at the end. Decibel figures mean little to a musician, and the design's "High" meant high sensitivity at the quiet end, the opposite of a high gate |
| Live readout "SIGNAL LOCK (−42 dB)" | **Replaced** by the live fill of the track. No decibel figure is shown |
| "Detected string / Auto-locked" row, "A4 calibration", the "Perfect pitch" caption, the algorithm and buffer line | **Excluded**, as in feature 003 (its FR-031) |

## Clarifications

### Session 2026-10-05

- Q: Which targets must the verification cover? → A: Every target attached to the workstation: the Pixel 6 Pro, the
  Pixel 9 Pro Fold emulator (the owner attached it after the first draft of this specification, so the tabletop and
  book forms are verified on it and not by layout tests alone), and the iPhone 17, iPad Pro 13-inch (M5) and iPhone
  Duo simulators. The owner's iPhone 14 is skipped for now; the iPhone 17 simulator stands in for an iPhone.
- Q: What should the noise gate control look like: a few named levels to tap, or a handle you drag along the live
  level bar? → A: A slider on the level bar, as the Stitch screen draws it. The track shows the live input level, the
  handle sets the gate level, and the start of the track is "Off". The first draft's four named levels (Off, Low,
  Mid, High) are gone.
- Q: On a phone held upright and on a tablet, where should the noise gate slider sit: above the tuning gauge with the
  other selectors, or below it at the foot of the gauge's card? → A: Below the gauge, at the foot of its card, under
  the "Flat" and "Sharp" labels, where it reads as a level strip belonging to the meter and is within reach of the
  thumb. (The first draft had it above the gauge, under the mode selector.)
- Q: The slider is new to the design system, so should this feature also record it in the design: add it to
  `DESIGN.md` and push the updated tuner screens to the Stitch project once the build is accepted? → A: Yes, after
  acceptance. Build and verify first; once the owner accepts the result, the slider is added to the design document
  and the Auto Listen screens in Stitch are updated from the app's captures, as for features 004 and 005.
  (Correction from planning: the app already has a slider, the metronome's tempo slider of feature 004. What is new
  is its entry in the design document and a slider on the tuner's screens. The answer stands.)
- Q: (the owner's request when planning) What else must the check on every target show? → A: A change of screen
  orientation on every target, and that the control is visible on the screen, not outside it, not cut and not
  overlapping any other element, in every orientation and posture (FR-023, FR-024, SC-008).
- Q: (after `/speckit-analyze`) The plan's handle was a 16 dp disc in a box 16 dp wide and 44 dp tall, like the tempo
  slider's, while FR-018 asks for 44 dp in both directions. Reword the requirement, or widen the box? → A: Widen the
  handle's touch box to 44 dp in both directions, so it is easy to hit. The disc drawn in it keeps its size; the
  tempo slider is not changed by this feature.

## User Scenarios & Testing *(mandatory)*

The users are the guitarists and cellists of feature 003, tuning somewhere that is not silent: a living room with a
television on next door, a rehearsal room with other players warming up, a classroom. The feature adds one control to
Auto Listen, "Noise gate": a slider whose track shows how loud the input is and whose handle sets the gate. By Ear
does not use the microphone and is not changed.

Terms used below:

- **Input level**: how loud the sound reaching the microphone is after the tuner's own filtering to the range of
  pitches it hears, in decibels below the loudest sound the device can capture (so every level is a negative number and a
  larger number is louder: −40 dB is louder than −60 dB).
- **Gate level**: the input level a sound must reach before the meter reads it, set with the slider's handle. With
  the handle at the start of the track the gate is **off**: there is no gate.
- **The gate is open** when sound is being read and **closed** when it is being ignored.
- **Check levels**: −60, −50 and −40 dB, used wherever a scenario or a test needs a particular gate level. The gate
  behaves the same at every position of the handle.
- **Meter**: the Auto Listen display of feature 003 (the note, the needle on its arc, the readouts). The
  clarification answers and the plan also call it the gauge; it is one thing.

### User Story 1 - Keep a quiet background off the meter (Priority: P1)

A guitarist tunes in a living room while a television plays in the next room. Between plucks the meter names notes
out of the television's speech and music. They drag the "Noise gate" handle to just past where the television's sound
reaches on the track. Now the meter rests between plucks and reads only the guitar.

**Why this priority**: it is the whole purpose of the feature. Without it the meter shows wrong notes whenever the
instrument is silent in a room that is not.

**Independent Test**: play a quiet steady tone as the background and a louder one as the instrument (two reference
tones are enough). With the gate off the meter names the quiet tone; with the gate level between the two it rests on
the quiet tone and reads the loud one.

**Acceptance Scenarios**:

1. **Given** Auto Listen is listening with the gate off, **When** a quiet, clear, steady tone sounds, **Then** the
   meter names it exactly as it did before this feature.
2. **Given** a gate level is set, **When** a clear steady tone sounds at 6 dB or more below that level, **Then** the
   meter stays in the resting state, with the "In tune" pill unlit, for as long as the tone sounds.
3. **Given** a gate level is set, **When** a tone sounds at or above that level, **Then** within a quarter of a
   second the meter shows its note with the same accuracy as with the gate off.
4. **Given** a background tone is sounding 6 dB or more below the gate level, **When** a note at least 12 dB louder
   than the background and above the gate level sounds over it, **Then** the meter shows the note; **When** the note
   stops, **Then** the meter rests within half a second and does not name the background.
5. **Given** a steady tone is sounding, **When** the user moves the handle past the tone's level, **Then** the meter
   rests within half a second; **When** the user moves it back below the tone's level or to "Off", **Then** the note
   shows within a quarter of a second. Listening is not interrupted by either move: the "Mic active" pill stays and
   the platform's microphone indicator does not blink.
6. **Given** By Ear is selected, **When** the screen shows, **Then** there is no noise gate control and the reference
   tones behave as before.

---

### User Story 2 - A ringing note stays on the meter (Priority: P1)

A plucked string is loudest at the pluck and fades over a few seconds while the player turns the peg and watches the
needle. The gate must not cut the reading off the moment the note dips, and must not make the meter flicker when a
note hovers around the gate level.

**Why this priority**: a gate that drops a note while the player is still tuning to it is worse than no gate. This is
the main risk the research found, and the reason the gate is off by default.

**Independent Test**: feed a tone that starts well above the gate level and fades steadily; the reading is unbroken
until the tone is clearly below the gate level, then the meter rests once and stays at rest.

**Acceptance Scenarios**:

1. **Given** a gate level is set, **When** a note starts 15 dB above it and fades steadily, **Then** the meter reads
   it without a gap from its start until its level has fallen 3 dB below the gate level, and rests within half a
   second after that.
2. **Given** a gate level is set, **When** a steady tone's level wavers by up to 1 dB either way around the gate
   level, **Then** the meter either reads it continuously or rests continuously; it does not alternate.
3. **Given** a gate level is set, **When** a sustained note (a bowed cello string) sounds above it, **Then** the
   reading is the same as with the gate off for as long as the note sounds.
4. **Given** a note has faded and the meter rests, **When** the string is plucked again above the gate level, **Then**
   the meter reads it within a quarter of a second.

---

### User Story 3 - Set the gate by eye (Priority: P2)

A level is hard to choose blind. The slider's track is a live picture of the input: it fills from the quiet end as far
as the sound is loud. The user sees the background flicker low on the track, sees their instrument reach far along
it, and puts the handle between the two. When the meter rests although they are playing, the track shows why: the
fill is not reaching the handle.

**Why this priority**: the gate works without the live fill, but the user would be setting it by trial and error, and
a gate set too high would look like a broken tuner.

**Independent Test**: with a tone at a known level, the fill ends at that level on the track; dragging the handle
across the end of the fill stops the reading on the way up and restores it on the way back.

**Acceptance Scenarios**:

1. **Given** Auto Listen is listening, **When** the sound gets louder or quieter, **Then** the fill follows it
   smoothly within a fifth of a second, while the rest of the screen stays steady.
2. **Given** a gate level is set, **When** the gate is closed, **Then** the fill is drawn in its muted look and ends
   short of the handle; **When** the gate is open, **Then** it is drawn in its active look. With the gate off the
   fill is always drawn in its active look.
3. **Given** a steady tone is sounding, **When** the user drags the handle slowly along the track, **Then** the meter
   stops reading the tone as the handle passes the end of the fill and reads it again as the handle comes back: the
   handle and the fill share one scale.
4. **Given** Auto Listen is selected but not listening (the audio is interrupted, or the app has just returned),
   **When** the control shows, **Then** the fill is empty and the handle stays where it was set and can be moved.

---

### User Story 4 - Off by default and remembered (Priority: P2)

Someone who never touches the control gets the tuner exactly as it was. Someone who sets the gate finds it where they
left it the next time they open the app.

**Why this priority**: the default protects every existing user from the risk in User Story 2; remembering saves the
user from setting the same level before every session in the same room.

**Independent Test**: on a fresh install confirm the handle at "Off" and unchanged readings; set a gate level, close
and reopen the app, and confirm the handle is where it was left and the gate is in force before the first note.

**Acceptance Scenarios**:

1. **Given** the app is opened for the first time, or for the first time after an update from a version without the
   gate, **When** Auto Listen shows, **Then** the handle is at the start of the track ("Off") and the tuner behaves
   exactly as before.
2. **Given** the user set a gate level, **When** the app is closed and reopened, **Then** the handle is at the same
   place and the gate level is in force from the first moment of listening.
3. **Given** the user set a gate level, **When** they switch to By Ear and back, leave the Tuner and return, or send
   the app to the background and return, **Then** the gate level is unchanged and in force.

---

### User Story 5 - Fits every window, pointer, keyboard and screen reader (Priority: P3)

The control takes its place in every form the tuner has: a phone upright, a phone held sideways, a tablet, a foldable
standing in tabletop posture or half-opened like a book. A mouse user sees a hover state and can click the track, a
keyboard user can reach the handle and move it, and a screen-reader user hears where the gate is set and can change
it.

**Why this priority**: required by the constitution for every control; it does not change what the gate does.

**Independent Test**: render Auto Listen with the control in each form and compare the captures, standing the
foldable emulator in tabletop and in book posture for those two forms; tab to the control and move the handle from
the keyboard; read it and adjust it with the platform screen reader.

**Acceptance Scenarios**:

1. **Given** a window narrower than 600 dp, **When** Auto Listen shows, **Then** the control sits at the foot of the
   meter's card, under the "Flat" and "Sharp" labels, across the card's width; the meter above it is whole and
   nothing is clipped or overlapping.
2. **Given** a window 840 dp or wider and at least 480 dp tall, **When** Auto Listen shows, **Then** the control sits
   at the foot of the meter's card, under the meter, centred and no wider than the meter.
3. **Given** a phone held sideways (the settings column of feature 003), **When** Auto Listen shows, **Then** the
   control is in the settings column under the mode selector, and at the default font size the column shows it
   without scrolling.
4. **Given** a foldable in tabletop posture or in book posture (feature 005), **When** Auto Listen shows, **Then**
   the control is in the "Tuner controls" card under the mode control; in tabletop the upper half still shows only
   the meter, and nothing crosses the fold.
5. **Given** a pointer, **When** it hovers over the handle, **Then** the handle shows a hover state; **When** a place
   on the track is clicked, **Then** the handle moves there. **Given** a keyboard, **When** focus moves through the
   screen, **Then** the control receives a visible focus indicator in the order it is seen (after the meter where
   it sits under the meter, after the mode control where it sits in the settings), and each press of an arrow key
   moves the handle one step.
6. **Given** a screen reader, **When** it reaches the control, **Then** it announces "Noise gate", where the handle
   is ("Off" or its position along the track) and, while listening behind a gate, whether the sound is above or
   below it; the handle can be moved with the screen reader's adjust gestures.

---

### Edge Cases

- **The background is as loud as the instrument, or louder.** No gate level separates them: any level that holds the
  background back holds the instrument back too. The track shows both reaching the same place. The app does not
  promise silence in a noisy room; By Ear remains the way to tune there.
- **The background sits just under the gate level** (within 3 dB of it). Once a note has opened the gate, the
  background can hold it open until it dips, and the meter may name it. The track shows the background touching the
  handle; moving the handle a little further is the remedy.
- **The gate is set higher than the instrument reaches.** The meter rests while the user plays. The track shows the
  fill ending short of the handle, which is the cue to bring the handle back.
- **A loud sound outside the hearing range** (a deep rumble, a high hiss) counts for much less towards the input
  level than a sound inside it (FR-004), so it seldom opens the gate. When it does, the meter still rests: a pitch
  outside the hearing range is never shown (feature 003, FR-010).
- **Loud noise with no pitch** (applause, a fan close to the microphone) may open the gate; the meter still rests,
  because the existing check for a clear pitch applies whatever the gate does.
- **A very loud, distorted note** opens the gate like any other; the reading follows feature 003's rules.
- **The handle is moved while a note is sounding**: the new gate level applies to that note at once, also while the
  drag is still going on (User Story 1, scenario 5).
- **The handle is dragged past an end of the track**: it stops at the end.
- **A drag on the control inside a column or card that scrolls**: a sideways drag moves the handle and does not
  scroll; an up-and-down drag that starts on the control scrolls and does not move the handle.
- **A call or another app interrupts listening**: when listening resumes, the gate level is in force.
- **The window is rotated, resized or folded while listening**: listening continues, the gate level stays set and in
  force, and the control moves to its place in the new form.
- **Microphone access is denied**: the denied state of feature 003 replaces the meter and the noise gate control is
  not shown; the remembered gate level is kept and is in force once access is granted.
- **The remembered gate level cannot be read** (damaged, or outside this version's scale): the gate is off. A value
  that falls between two steps is moved to the nearer one.
- **A short window** (a small phone upright, the iPhone Duo's outer display): the control keeps its full touch height; the meter takes its compact form (the one a phone held sideways shows)
  and shrinks to the height that is left; nothing is clipped, and if the two still cannot both fit the content
  scrolls.
- **The largest system font size**: the label and the two end labels stay readable and unclipped; a settings column
  or controls card that becomes too tall scrolls, as it does today.
- **Reduce motion is on**: the fill still moves. It is information, like the needle.
- **A developer test signal is in use** (feature 003, FR-032): it passes through the gate exactly as microphone sound
  does, so a quiet test signal rests behind a gate.

## Requirements *(mandatory)*

### Functional Requirements

**The control**

- **FR-001**: Auto Listen MUST offer a control labelled "Noise gate": a slider with one handle on a horizontal track.
  The track MUST be a scale of input level that runs from quiet at its start to loud at its end, from −80 dB to
  −20 dB (see Assumptions, "The scale"), with the label "Off" at its start and "High" at its end. The control MUST be
  shown whenever Auto Listen shows the meter, and MUST NOT be shown in By Ear or in the microphone-denied state.
- **FR-002**: The handle's place on the track MUST set the gate level: the level of the scale at that place, in steps
  of 2 dB. With the handle at the start of the track the gate MUST be off: every reading MUST be what it was before
  this feature. The handle MUST be movable by dragging it, by touching or clicking a place on the track, and from
  the keyboard (FR-018), and MUST stop at the ends of the track.
- **FR-003**: A move of the handle MUST take effect within a quarter of a second, during a drag as well as at its
  end, and MUST NOT interrupt listening: the "Mic active" pill stays shown and the platform's microphone indicator
  does not turn off and on. Each move MUST be judged afresh: after it the gate is open only if the input level is at
  or above the new gate level (always, when the gate is off), whatever the gate's state was before.

**What the gate does**

- **FR-004**: The input level MUST be measured after the tuner's own filtering to the range of pitches it hears
  (feature 003, FR-011), so that sound outside that range counts for less the further out it lies: two octaves
  beyond either end it MUST count at least 20 dB less than its true loudness. The gate and the fill of the track MUST
  use the same measurement, so that what the user sees is what the gate acts on.
- **FR-005**: With a gate level set, the gate MUST open when the input level reaches the gate level, and once open
  MUST stay open until the input level has fallen 3 dB below the gate level, where it MUST close. While the gate is
  closed the meter MUST show the resting state of feature 003 (FR-010) however clear the sound is.
- **FR-006**: A note that starts at or above the gate level MUST be shown within a quarter of a second of starting,
  as feature 003 requires (FR-012): the gate MUST NOT take a note past that limit. When the gate closes the meter
  MUST reach the resting state within half a second.
- **FR-007**: While the gate is open the reading (the note, its octave, the offset in cents, how smoothly the needle
  moves) MUST be the same as with the gate off. The gate decides whether a sound is read; it MUST NOT change how.
- **FR-008**: A sound whose level wavers by up to 1 dB either way around the gate level MUST NOT make the meter
  alternate between a note and the resting state.
- **FR-009**: The fixed checks of feature 003 MUST remain in force at every gate level: silence, sound with no clear
  pitch and pitches outside the hearing range rest the meter even when the gate is open.
- **FR-010**: The gate MUST apply to a developer test signal (feature 003, FR-032) exactly as it applies to the
  microphone.

**The live level on the track**

- **FR-011**: The track MUST show the current input level as a fill that runs from the start of the track to that
  level, on the same scale the handle uses. While Auto Listen is listening the fill MUST update at least 20 times
  per second; when Auto Listen is not listening the fill MUST be empty, and the handle MUST stay where it was set
  and stay movable.
- **FR-012**: The fill MUST have an active look while sound is being read (the gate open, or off) and a muted look
  while the gate is closed. The difference MUST NOT rely on colour alone: the fill reaching the handle or ending
  short of it carries it too.
- **FR-013**: The fill MUST move smoothly while the rest of the screen stays steady and responsive, as the needle
  does (feature 003, FR-013). The control MUST NOT show a decibel figure.

**Remembering the gate level**

- **FR-014**: The gate level MUST be remembered across app launches together with the tuner's other remembered
  choices (feature 003, FR-005). The first-launch default, and the value after updating from a version without the
  gate, MUST be off. A remembered value that cannot be read or lies outside the scale MUST be treated as off; one
  that falls between two steps MUST be moved to the nearer step, the quieter one when it is exactly halfway.
- **FR-015**: The gate level MUST stay as set through switching to By Ear and back, leaving and returning to the
  Tuner, the app going to the background and returning, an interruption, and a change of window size, rotation or
  fold posture; whenever listening starts or resumes the gate level MUST be in force from its first moment.

**Layout, input and accessibility**

- **FR-016**: The control MUST appear in every form of the Auto Listen screen:
  - windows narrower than 600 dp, and the medium band of feature 003: at the foot of the meter's card, under the
    "Flat" and "Sharp" labels, across the card's width;
  - windows 840 dp or wider and at least 480 dp tall: at the foot of the meter's card, under the meter, centred, no
    wider than the meter;
  - a phone held sideways: in the settings column, under the mode selector, visible without scrolling at the default
    font size;
  - tabletop posture and book posture (feature 005): in the "Tuner controls" card, under the mode control.

  In every form the meter MUST remain whole (in a window too short for the meter and the control together the
  meter first takes its compact form, and only if that is still too tall does the content scroll instead of
  clipping), and nothing MUST clip, overlap, scroll horizontally or, on a foldable, cross
  the fold.
- **FR-017**: Outside the control, the Auto Listen layouts MUST change only by the room the control takes; By Ear,
  the selectors, the audio-state pill and the denied state MUST be unchanged.
- **FR-018**: The handle MUST show a hover state under a pointer and a visible focus indicator. With focus on the
  control, each press of an arrow key MUST move the handle one step towards the matching end. The handle's touch
  area MUST measure at least 44 dp in both dimensions (its own box, whatever the size of the disc drawn in it), and
  the track MUST accept touches over a height of at least 44 dp. A sideways drag on the control MUST move the handle and MUST NOT scroll the screen; an up-and-down drag
  that starts on it MUST scroll the content (where it scrolls) and MUST NOT move the handle. In keyboard and
  screen-reader order the control MUST come where it is seen: after the meter in the forms that put it under the
  meter, after the mode control in the settings column and in the "Tuner controls" card.
- **FR-019**: The control MUST be exposed to accessibility services as one adjustable element with the label "Noise
  gate", a value ("Off" at the start of the track, otherwise the handle's place as a percentage of the track), a
  stable test identifier and, while listening behind a gate, a state that says "sound above the gate" or "sound below
  the gate". A screen-reader user MUST be able to move the handle with the platform's adjust gestures. The control
  MUST NOT announce every change of input level.
- **FR-020**: The slider MUST look and respond like the app's existing slider, the metronome's tempo slider (the
  same track, the same round handle, the same hover, press and focus feedback); every colour, type style, spacing
  and radius of it MUST come from the shared design tokens, and its look MUST be reviewed against the tempo slider
  and the Stitch drawing it comes from. With the platform's reduce-motion setting on, the fill MUST still move.

**Quality and verification**

- **FR-021**: Automated tests MUST cover, with synthetic sound played offline without a microphone: for each check
  level, steady tones below it (rest), at it and above it (read, with the accuracy of feature 003); a quiet pitched
  background alone (rest) and with a louder note over it (the note, then rest); a fading plucked note (User Story 2,
  scenario 1); a tone wavering around the gate level (FR-008); a sound two octaves outside the hearing range, which
  must measure at least 20 dB under its true loudness and must never be shown as a note (FR-004); noise with no pitch above the gate level (rest); a change of gate level while sound is
  playing (FR-003); and the gate off reproducing the readings of feature 003 unchanged. They MUST also cover: the
  remembered gate level, its default and the rules for a value that is unreadable, outside the scale or between two
  steps (FR-014); the screen's behaviour with a stand-in for the audio, including the control's presence and absence
  by mode and state, the handle's place, and the gate level in force when listening starts (FR-015); moving the
  handle by dragging, by a touch on the track and from the keyboard, in steps, stopping at the ends (FR-002,
  FR-018); the control in each form of FR-016; and the accessibility semantics of FR-019.
- **FR-022**: The developer test signal of feature 003 (FR-032) MUST also accept a level, so that the gate can be
  verified end to end without a microphone or an instrument. The catalogue MUST gain at least four signals, each an
  in-tune E2, at −70, −55, −45 and −30 dB. An end-to-end test on each platform MUST feed each of the four through
  the real audio path with the gate off and at each check level, and assert a reading of E2 within 1 cent when the
  signal's level is at or above the gate level (always, when the gate is off) and the resting state otherwise. The
  fourteen existing signals MUST keep reading as they do today with the gate off. Debug builds MUST also accept a
  gate level as a launch argument, so that a capture or a check can start behind a gate without touching the
  control; it lasts for that launch, is not remembered, and release builds MUST ignore it.
- **FR-023**: Screenshots MUST be stored under the feature directory and reviewed before acceptance: Auto Listen with
  the control at a compact width, at an expanded width and in phone landscape, on Android and on iOS; on the
  foldable emulator, Auto Listen with the control in tabletop posture, in book posture and opened flat; and, on each
  target of FR-024, one pair that shows the gate at −50 dB with the test signal below it (meter resting, fill muted
  and short of the handle) and above it (note shown, fill active and past the handle). Every target MUST be captured
  both upright and sideways.
- **FR-024**: Verification MUST be carried out on every target attached to the workstation, and each target's result
  MUST be written into the feature's verification notes. On 2026-10-05 those are five: the Pixel 6 Pro (connected by
  USB), the Pixel 9 Pro Fold emulator, and the three running iOS simulators, iPhone 17, iPad Pro 13-inch (M5) and
  iPhone Duo. On each target: every automated test suite that can run there MUST pass; the end-to-end test of FR-022
  MUST pass; and the app MUST be launched with the control exercised and captured (FR-023). On the foldable emulator
  the control MUST additionally be exercised in tabletop posture, in book posture and opened flat, and the device
  MUST be folded and unfolded while listening to confirm that listening continues and the gate level stays in force
  (FR-015). On every target the screen MUST also be turned between upright and sideways while listening behind a
  gate: listening continues, the gate level stays in force, and in each orientation the control is wholly on the
  screen, uncut and clear of every other element. A test that fails in the same way without this feature MUST be
  recorded as such with the evidence; it is not a failure of this feature. A target, or a step on a target, that cannot be driven MUST be named in the notes
  with the reason and the command the owner can run instead; it MUST NOT be dropped silently.
- **FR-025**: The gate MUST also be checked with live sound through the microphone on the Pixel 6 Pro and on each
  simulator, and the result recorded in the verification notes: a quiet pitched background played from a loudspeaker
  is named by the meter with the gate off and rests the meter at a gate level above it, while a louder tone at an
  instrument's level is still read. The same check MUST be attempted on the foldable emulator; if its microphone
  cannot be fed with sound from the workstation, the notes MUST say so and the test signal of FR-022 stands in
  there.
- **FR-026**: The feature MUST NOT include: a decibel readout or a typed-in level; named preset levels; automatic
  measurement of the room or automatic setting of the gate; any cleaning, filtering or suppression of the sound the
  tuner hears (the gate only decides whether to read it); a gate in By Ear; a separate gate level per instrument; or
  any other control of the Stitch screen it came from (reference-pitch calibration, detected string, the algorithm
  line).
- **FR-027**: After the owner accepts the build, and not before, the design MUST be brought in line with it: the
  slider MUST be described in the shared design document (`design/stitch/DESIGN.md`) as a component of the design
  system, and every Auto Listen screen the Stitch project keeps for the tuner (upright phone, tablet, phone
  landscape, foldable tabletop) MUST be updated from the app's captures to show the control where FR-016 puts it,
  with the local export of the Stitch project refreshed afterwards.

### Key Entities

- **Gate Level**: off, or a level on the scale in steps of 2 dB (FR-001, FR-002), set by the slider's handle. It is
  one of the remembered tuner preferences, beside the instrument, string count, preset, mode and loop toggle of
  feature 003.
- **Input Level**: how loud the sound is at this moment after the tuner's filtering to its hearing range (FR-004).
  Shown by the fill of the track; never stored.
- **Gate State**: open or closed, derived from the input level and the gate level with the 3 dB margin of FR-005;
  always open when the gate is off; never stored.
- **Pitch Reading** (feature 003): unchanged, except that it is also "resting" whenever the gate is closed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the gate at each of the three check levels, a clear steady tone 6 dB below the gate level, at
  every open-string pitch of feature 003's presets, produces no reading during 10 seconds; the same tone at the gate
  level or above it is shown within 250 ms with its offset within 1 cent.
- **SC-002**: With the gate off, every assertion the existing automated tests make about the tuner's readings and
  screens holds unchanged, and all fourteen existing test signals give the same readings. Only tests that pin
  something this feature changes on purpose are edited: the revision number of the engine's interface, the number of
  test signals, and the shape of the remembered choices.
- **SC-003**: A note that starts 15 dB above the gate level and fades at 10 dB per second is read without a gap until
  it is 3 dB below the gate level (within 2 dB), and the meter rests within 500 ms after that.
- **SC-004**: A tone wavering 1 dB either way around the gate level for 30 seconds causes at most one change between
  a reading and the resting state.
- **SC-005**: In 100% of trials a move of the handle while listening takes effect within 250 ms, and the platform's
  microphone indicator stays on throughout.
- **SC-006**: The fill updates at least 20 times per second and ends within 2 dB of a test tone's true level on the
  scale; dragging the handle across a steady test tone stops the reading and restores it within one step of the
  tone's level.
- **SC-007**: The gate level survives closing and reopening the app in 100% of trials, and a fresh install shows the
  handle at "Off".
- **SC-008**: Captures of Auto Listen in every form of FR-016, and on every target in both orientations, show zero
  clipped, overlapping or off-screen elements;
  the handle's touch area measures at least 44 dp, the handle is reachable and movable from the keyboard, and the
  screen reader announces the control's label, value and state and can adjust it.
- **SC-009**: The verification notes carry a result for 100% of the attached targets (Pixel 6 Pro, Pixel 9 Pro Fold
  emulator, iPhone 17, iPad Pro 13-inch (M5), iPhone Duo): automated tests passing with zero compiler and lint
  warnings, the end-to-end test passing for all sixteen combinations of gate level and signal level, and the
  captures of FR-023.
- **SC-010**: In the live check on the Pixel 6 Pro and on each simulator (and on the foldable emulator if its
  microphone can be fed), a quiet pitched background that the meter names with the gate off leaves the meter at rest
  for at least 95% of a 30-second observation once the gate is set above it, and a louder tone played over it is
  still read within 250 ms.
- **SC-011**: A guitarist in a room with a television playing can, without instructions and within 30 seconds, find a
  place for the handle at which the meter rests between plucks and still reads every open string.
- **SC-012**: On the foldable emulator, through ten consecutive changes between flat, tabletop and book posture while
  listening behind a gate, the gate level stays as set and in force, listening is never interrupted, and in each
  posture the control is whole and clear of the fold.
- **SC-013**: After acceptance, the design document describes the slider, and 100% of the Stitch project's current
  Auto Listen screens show the control as the app's captures do.

## Assumptions

- **An enhancement to the tuner, not a new step in the build order.** The tuner is already built (feature 003); like
  feature 005 this refines it and pulls no later work forward, so the constitution's build order needs no amendment.
  The owner chose to do it now by asking for it on 2026-10-05.
- **The research brief is the starting point for the plan.** `docs/research/tuner-noise-gate.md` records where the
  gate fits, what it touches and its risks. This specification settles the points the brief left to the spec; the
  brief's technical recommendations are for the plan to confirm.
- **Shape of the control.** The brief offered two shapes: a few named levels in the tuner's existing selector style,
  or the slider of the Stitch screen. The owner chose the slider on 2026-10-05 (Clarifications): it can be set finely,
  it is set by eye against the live level and so does not depend on how one phone's microphone compares with
  another's. Its cost is a second slider beside the metronome's tempo slider, with a live fill the tempo slider
  does not have (FR-018 to FR-020).
- **Names.** The label is "Noise gate" (the owner's own words and the design's first title). The end labels are
  "Off" and "High", named for the gate, not for sensitivity: towards "High" the gate holds back more.
- **The scale.** −80 dB to −20 dB in steps of 2 dB are starting values. The quiet end is where the tuner already
  treats sound as silence, so "Off" at the start of the track gives nothing up; the loud end is above where a phone
  microphone that applies no automatic gain is expected to capture an instrument at arm's length. That expectation
  is unmeasured, and it cannot be measured before the gate's own measurement of the input exists (plan, research
  R8). A quiet room, a pitched background and an instrument-level tone are therefore measured on the Pixel 6 Pro and
  through the iPhone 17 simulator at a checkpoint in the build, before the scale is accepted; if the scale proves
  wrong, either end is moved through `/speckit-clarify`, keeping it at least 50 dB long. The instrument-level tone is
  a loudspeaker standing in for an instrument; before the scale is changed on its account alone, the owner is asked
  for one reading from a real guitar. The check levels and the test signals of FR-022 are test points inside the scale and move
  with it; every scenario and criterion above is written relative to the gate level so that it survives such a move.
- **The 3 dB margin** between opening and closing (FR-005) is a starting value from the brief's "a few dB". A wider
  margin lets a fading note ring longer but lets a background just under the gate hold it open. It stays 3 dB unless
  the live check of FR-025 shows the meter flickering on a steady sound near the gate, or a background holding the
  gate open after a note; then it is changed, within 2 to 6 dB, through `/speckit-clarify`.
- **Off by default.** A gate can cut a fading note short (User Story 2) and the right level differs from room to
  room, so nobody gets a gate they did not set.
- **One gate level for everything.** It is not kept per instrument or per room. A cello is louder than a guitar, but
  the gate is set against the background, not the instrument.
- **Placement.** Upright and on a tablet the control sits under the meter, in the meter's card (the owner's choice
  of 2026-10-05, Clarifications; the brief had proposed it under the selector bar). Sideways it is in the settings
  column, as the brief proposed, and the tabletop and book forms follow feature 005's rule that every setting lives
  in the "Tuner controls" card, which also keeps the upper half of a tabletop screen for the meter alone. The control
  is hidden in the denied state because there is nothing to gate and that state should show one message and one
  action.
- **The resting state does not change its wording.** A closed gate shows "Listening" like any other rest; the track,
  not a new message, tells the user why.
- **Simulators and live sound.** An iOS simulator listens through the workstation's microphone, so the live check of
  FR-025 runs there too, with levels that are the workstation's rather than an iPhone's. The check with a real
  instrument in a real room (SC-011) is the owner's, as the instrument checks of feature 003 were.
- **Attached targets.** The five targets of FR-024 are what was attached on 2026-10-05 once the owner had started the
  Pixel 9 Pro Fold emulator. The owner's iPhone 14 is skipped by the owner's decision of the same day; the iPhone 17
  simulator stands in for an iPhone. No other Android emulator was running; a target attached later is included
  when the work is verified.
- **The foldable emulator's known limits.** Its audio clock has made the metronome's click-timing test fail there
  since feature 005 (recorded in that feature's verification notes); that failure is expected, is re-checked against
  `main`, and falls under the "fails in the same way without this feature" rule of FR-024. Its microphone hears the
  workstation only when the emulator is set to use the host's audio input, which may not be possible to switch from
  here; FR-025 says what happens then.
- **The iPhone Duo simulator** is covered in the poses that can be driven on it; it reports no fold posture to the
  app, so it shows the upright or wide form, never the tabletop one.
- **The design follows the build.** The owner chose on 2026-10-05 to sync the design after accepting the build
  (Clarifications, FR-027) and not to draw the screens first. Until then the Stitch drawing named in the evidence
  section is the only picture of the control, and the review of FR-020 is against it and the design tokens.
- **No new third-party dependency** is expected.
- **Dark theme only, English only, left-to-right**, as in every feature so far.
- **Branch.** `006-tuner-noise-gate` was created from `main` by the spec-kit git extension on 2026-10-05.
