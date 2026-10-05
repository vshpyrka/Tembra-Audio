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
| Continuous slider from −60 dB to −10 dB with a gradient track | **Replaced** by four choices in the selector style the tuner already uses (see Assumptions, "Shape of the control") |
| Tick labels "−60 dB (High)", "−40 dB (Optimal)", "−10 dB (Low)" | **Excluded**: decibel figures mean little to a musician. The choices are named for how much the gate holds back: Off, Low, Mid, High |
| Live readout "SIGNAL LOCK (−42 dB)" | **Kept as an idea**: a level indication beside the control shows the input against the gate. No decibel figure is shown |
| "Detected string / Auto-locked" row, "A4 calibration", the "Perfect pitch" caption, the algorithm and buffer line | **Excluded**, as in feature 003 (its FR-031) |

## User Scenarios & Testing *(mandatory)*

The users are the guitarists and cellists of feature 003, tuning somewhere that is not silent: a living room with a
television on next door, a rehearsal room with other players warming up, a classroom. The feature adds one control to
Auto Listen, "Noise gate", and a level indication beside it. By Ear does not use the microphone and is not changed.

Terms used below:

- **Input level**: how loud the sound reaching the microphone is, counted only within the range of pitches the tuner
  hears, in decibels below the loudest sound the device can capture (so every level is a negative number and a
  larger number is louder: −40 dB is louder than −60 dB).
- **Gate level**: the input level a sound must reach before the meter reads it. "Off" means there is no gate.
- **The gate is open** when sound is being read and **closed** when it is being ignored.

### User Story 1 - Keep a quiet background off the meter (Priority: P1)

A guitarist tunes in a living room while a television plays in the next room. Between plucks the meter names notes
out of the television's speech and music. They set "Noise gate" to a level above the television. Now the meter rests
between plucks and reads only the guitar.

**Why this priority**: it is the whole purpose of the feature. Without it the meter shows wrong notes whenever the
instrument is silent in a room that is not.

**Independent Test**: play a quiet steady tone as the background and a louder one as the instrument (two reference
tones are enough). With the gate off the meter names the quiet tone; with a gate level between the two it rests on the
quiet tone and reads the loud one.

**Acceptance Scenarios**:

1. **Given** Auto Listen is listening with the gate "Off", **When** a quiet, clear, steady tone sounds, **Then** the
   meter names it exactly as it did before this feature.
2. **Given** a gate level is selected, **When** a clear steady tone sounds at 6 dB or more below that level, **Then**
   the meter stays in the resting state, with the "In tune" pill unlit, for as long as the tone sounds.
3. **Given** a gate level is selected, **When** a tone sounds at or above that level, **Then** within a quarter of a
   second the meter shows its note with the same accuracy as with the gate off.
4. **Given** a background tone is sounding 6 dB or more below the gate level, **When** a note at least 12 dB louder
   than the background and above the gate level sounds over it, **Then** the meter shows the note; **When** the note
   stops, **Then** the meter rests within half a second and does not name the background.
5. **Given** a steady tone is sounding at a level between two gate choices, **When** the user selects the higher one,
   **Then** the meter rests within half a second; **When** the user selects the lower one or "Off", **Then** the note
   shows within a quarter of a second. Listening is not interrupted by either change: the "Mic active" pill stays and
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

1. **Given** a gate level is selected, **When** a note starts 15 dB above it and fades steadily, **Then** the meter
   reads it without a gap from its start until its level has fallen 3 dB below the gate level, and rests within half
   a second after that.
2. **Given** a gate level is selected, **When** a steady tone's level wavers by up to 1 dB either way around the gate
   level, **Then** the meter either reads it continuously or rests continuously; it does not alternate.
3. **Given** a gate level is selected, **When** a sustained note (a bowed cello string) sounds above it, **Then** the
   reading is the same as with the gate off for as long as the note sounds.
4. **Given** a note has faded and the meter rests, **When** the string is plucked again above the gate level, **Then**
   the meter reads it within a quarter of a second.

---

### User Story 3 - See the input level against the gate (Priority: P2)

A level is hard to choose blind. Beside the control a level indication shows how loud the input is right now and
where the gate sits on the same scale. The user sees the background flicker low on the scale, sees their instrument
reach far higher, and picks the choice whose mark lies between the two. When the meter rests although they are
playing, the indication shows why: the sound is not reaching the mark.

**Why this priority**: the gate works without it, but the user would be choosing by trial and error, and a gate set
too high would look like a broken tuner.

**Independent Test**: with a tone at a known level and each gate choice in turn, the indication shows the level at the
same place on its scale, the mark moves to the selected gate level, and the indication's look changes as the tone
crosses the mark.

**Acceptance Scenarios**:

1. **Given** Auto Listen is listening, **When** the sound gets louder or quieter, **Then** the indication follows it
   smoothly within a tenth of a second, while the rest of the screen stays steady.
2. **Given** a gate level is selected, **When** the control shows, **Then** a mark stands on the indication's scale at
   that level; **Given** "Off", **Then** there is no mark.
3. **Given** a gate level is selected, **When** the gate is closed, **Then** the indication is drawn in its muted
   look and ends short of the mark; **When** the gate is open, **Then** it is drawn in its active look. With "Off" it
   is always drawn in its active look.
4. **Given** Auto Listen is selected but not listening (the audio is interrupted, or the app has just returned),
   **When** the control shows, **Then** the indication is empty.

---

### User Story 4 - Off by default and remembered (Priority: P2)

Someone who never touches the control gets the tuner exactly as it was. Someone who sets a level finds it again the
next time they open the app.

**Why this priority**: the default protects every existing user from the risk in User Story 2; remembering saves the
user from setting the same level before every session in the same room.

**Independent Test**: on a fresh install confirm "Off" and unchanged readings; select a level, close and reopen the
app, and confirm the level is selected and applied before the first note.

**Acceptance Scenarios**:

1. **Given** the app is opened for the first time, or for the first time after an update from a version without the
   gate, **When** Auto Listen shows, **Then** "Off" is selected and the tuner behaves exactly as before.
2. **Given** the user selected a gate level, **When** the app is closed and reopened, **Then** the same level is
   selected and is in force from the first moment of listening.
3. **Given** the user selected a gate level, **When** they switch to By Ear and back, leave the Tuner and return, or
   send the app to the background and return, **Then** the level is unchanged and in force.

---

### User Story 5 - Fits every window, pointer, keyboard and screen reader (Priority: P3)

The control and its level indication take their place in every form the tuner has: a phone upright, a phone held
sideways, a tablet, a foldable standing in tabletop posture or half-opened like a book. A mouse user sees hover
states, a keyboard user can reach and change the gate, and a screen-reader user hears the gate's level.

**Why this priority**: required by the constitution for every control; it does not change what the gate does.

**Independent Test**: render Auto Listen with the control in each form and compare the captures; tab to the control
and change it from the keyboard; read it with the platform screen reader.

**Acceptance Scenarios**:

1. **Given** a window narrower than 600 dp, **When** Auto Listen shows, **Then** the control and its indication sit
   directly under the mode selector, above the meter, across the content width; the meter is whole and nothing is
   clipped or overlapping.
2. **Given** a window 840 dp or wider and at least 480 dp tall, **When** Auto Listen shows, **Then** the control and
   its indication sit under the selector row, above the meter, centred and no wider than the meter.
3. **Given** a phone held sideways (the settings column of feature 003), **When** Auto Listen shows, **Then** the
   control and its indication are in the settings column under the mode selector, and at the default font size the
   column shows them without scrolling.
4. **Given** a foldable in tabletop posture or in book posture (feature 005), **When** Auto Listen shows, **Then**
   the control and its indication are in the "Tuner controls" card under the mode control; in tabletop the upper half
   still shows only the gauge, and nothing crosses the fold.
5. **Given** a pointer, **When** it hovers over a choice, **Then** the choice shows a hover state; **Given** a
   keyboard, **When** focus moves through the screen, **Then** the control receives a visible focus indicator after
   the mode selector and its choices can be selected.
6. **Given** a screen reader, **When** it reaches the control, **Then** it announces "Noise gate" and the selected
   choice; **When** it reaches the indication, **Then** it announces whether the sound is currently above or below
   the gate, or that the gate is off.

---

### Edge Cases

- **The background is as loud as the instrument, or louder.** No gate level separates them: any level that holds the
  background back holds the instrument back too. The level indication shows both reaching the same height. The app
  does not promise silence in a noisy room; By Ear remains the way to tune there.
- **The background sits just under the gate level** (within 3 dB of it). Once a note has opened the gate, the
  background can hold it open until it dips, and the meter may name it. The level indication shows the background
  touching the mark; the next choice up is the remedy.
- **The gate is set higher than the instrument reaches.** The meter rests while the user plays. The indication shows
  the sound ending short of the mark, which is the cue to choose a lower level or "Off".
- **A loud sound outside the hearing range** (a rumble below the lowest string, a whistle or hiss above the highest
  note) does not count towards the input level: it cannot open the gate or hold it open, and the indication does not
  rise for it.
- **Loud noise with no pitch** (applause, a fan close to the microphone) may open the gate; the meter still rests,
  because the existing check for a clear pitch applies whatever the gate does.
- **A very loud, distorted note** opens the gate like any other; the reading follows feature 003's rules.
- **The level is changed while a note is sounding**: the new level applies to that note at once (User Story 1,
  scenario 5).
- **A call or another app interrupts listening**: when listening resumes, the selected level is in force.
- **The window is rotated, resized or folded while listening**: listening continues, the level stays selected and in
  force, and the control moves to its place in the new form.
- **Microphone access is denied**: the denied state of feature 003 replaces the meter and the noise gate control is
  not shown; the stored level is kept and is in force once access is granted.
- **The remembered level cannot be read** (damaged, or written by a later version with a choice this version does not
  know): "Off" is used.
- **A short window** (a small phone upright, the iPhone Duo's outer display): the control keeps its full touch height
  and the meter shrinks to the height that is left; nothing is clipped, and if the two cannot both fit the content
  scrolls.
- **The largest system font size**: the label and the four choices stay readable and unclipped; a settings column or
  controls card that becomes too tall scrolls, as it does today.
- **Reduce motion is on**: the level indication still moves. It is information, like the needle.
- **A developer test signal is in use** (feature 003, FR-032): it passes through the gate exactly as microphone sound
  does, so a quiet test signal rests behind a gate.

## Requirements *(mandatory)*

### Functional Requirements

**The control**

- **FR-001**: Auto Listen MUST offer a control labelled "Noise gate" with exactly four choices in this order: "Off",
  "Low", "Mid", "High". Exactly one choice MUST be selected at any time. The control MUST be shown whenever Auto
  Listen shows the meter, and MUST NOT be shown in By Ear or in the microphone-denied state.
- **FR-002**: "Off" MUST mean there is no gate: every reading MUST be what it was before this feature. "Low", "Mid"
  and "High" MUST set the gate level to −60 dB, −50 dB and −40 dB of input level respectively (see Assumptions, "Gate
  levels", for how these values are confirmed).
- **FR-003**: Selecting a choice MUST take effect within a quarter of a second and MUST NOT interrupt listening: the
  "Mic active" pill stays shown and the platform's microphone indicator does not turn off and on. A change of choice
  MUST be judged afresh: after it the gate is open only if the input level is at or above the newly selected gate
  level (always, for "Off"), whatever the gate's state was before.

**What the gate does**

- **FR-004**: The input level MUST be measured only over the range of pitches the tuner hears (feature 003, FR-011),
  so that sound outside it cannot open the gate or hold it open. The gate and the level indication MUST use the same
  measurement, so that what the user sees is what the gate acts on.
- **FR-005**: With a gate level selected, the gate MUST open when the input level reaches the gate level, and once
  open MUST stay open until the input level has fallen 3 dB below the gate level, where it MUST close. While the gate
  is closed the meter MUST show the resting state of feature 003 (FR-010) however clear the sound is.
- **FR-006**: A note that starts at or above the gate level MUST be shown within a quarter of a second of starting,
  as feature 003 requires (FR-012): the gate MUST NOT add a delay. When the gate closes the meter MUST reach the
  resting state within half a second.
- **FR-007**: While the gate is open the reading (the note, its octave, the offset in cents, how smoothly the needle
  moves) MUST be the same as with the gate off. The gate decides whether a sound is read; it MUST NOT change how.
- **FR-008**: A sound whose level wavers by up to 1 dB either way around the gate level MUST NOT make the meter
  alternate between a note and the resting state.
- **FR-009**: The fixed checks of feature 003 MUST remain in force at every gate choice: silence, sound with no clear
  pitch and pitches outside the hearing range rest the meter even when the gate is open.
- **FR-010**: The gate MUST apply to a developer test signal (feature 003, FR-032) exactly as it applies to the
  microphone.

**The level indication**

- **FR-011**: Beside the control, as part of it, a level indication MUST show the current input level as a bar on a
  scale that runs from quiet to loud and reaches from at least 20 dB below the "Low" gate level to at least 20 dB
  above the "High" one. While Auto Listen is listening it MUST update at least 20 times per second; when Auto Listen
  is not listening it MUST be empty.
- **FR-012**: With a gate level selected the indication MUST show a mark on its scale at that level; with "Off" it
  MUST show no mark. The bar MUST have an active look while sound is being read (the gate open, or "Off") and a muted
  look while the gate is closed. The difference MUST NOT rely on colour alone: the bar ending before or past the mark
  carries it too.
- **FR-013**: The indication MUST move smoothly while the rest of the screen stays steady and responsive, as the
  needle does (feature 003, FR-013). It MUST NOT show a decibel figure.

**Remembering the choice**

- **FR-014**: The selected choice MUST be remembered across app launches together with the tuner's other remembered
  choices (feature 003, FR-005). The first-launch default, and the value after updating from a version without the
  gate, MUST be "Off". A remembered value that cannot be read or is not one of the four choices MUST be treated as
  "Off".
- **FR-015**: The selected choice MUST stay selected through switching to By Ear and back, leaving and returning to
  the Tuner, the app going to the background and returning, an interruption, and a change of window size, rotation or
  fold posture; whenever listening starts or resumes the selected level MUST be in force from its first moment.

**Layout, input and accessibility**

- **FR-016**: The control and its indication MUST appear in every form of the Auto Listen screen:
  - windows narrower than 600 dp, and the medium band of feature 003: directly under the mode selector and above the
    meter, across the content width;
  - windows 840 dp or wider and at least 480 dp tall: under the selector row and above the meter, centred, no wider
    than the meter;
  - a phone held sideways: in the settings column, under the mode selector, visible without scrolling at the default
    font size;
  - tabletop posture and book posture (feature 005): in the "Tuner controls" card, under the mode control.

  In every form the meter MUST remain whole (in a window too short for the meter and the control together, the
  content scrolls instead of clipping), and nothing MUST clip, overlap, scroll horizontally or, on a foldable, cross
  the fold.
- **FR-017**: Outside the control and its indication, the Auto Listen layouts MUST change only by the room the
  control takes; By Ear, the selectors, the audio-state pill and the denied state MUST be unchanged.
- **FR-018**: Every choice MUST show a hover state under a pointer and a visible focus indicator, MUST be operable
  from the keyboard, and MUST measure at least 44 dp in both dimensions. In keyboard and screen-reader order the
  control MUST follow the mode selector.
- **FR-019**: The control MUST expose its label, its choices, which one is selected and a stable test identifier to
  accessibility services; "Mid" MUST be announced as "Medium". The indication MUST be exposed as one element that
  states "above the gate", "below the gate" or "gate off", or "no input" when Auto Listen is not listening; it MUST
  NOT announce every change of level.
- **FR-020**: The control MUST use the selector style the tuner's other selectors use, and every colour, type style,
  spacing and radius of the control and the indication MUST come from the shared design system. With the platform's
  reduce-motion setting on, the indication MUST still move.

**Quality and verification**

- **FR-021**: Automated tests MUST cover, with synthetic sound played offline without a microphone: for each gate
  level, steady tones below it (rest), at it and above it (read, with the accuracy of feature 003); a quiet pitched
  background alone (rest) and with a louder note over it (the note, then rest); a fading plucked note (User Story 2,
  scenario 1); a tone wavering around the gate level (FR-008); a loud sound outside the hearing range with a quiet
  note inside it (FR-004); noise with no pitch above the gate level (rest); a change of gate level while sound is
  playing (FR-003); and "Off" reproducing the readings of feature 003 unchanged. They MUST also cover: the remembered
  choice, its default and the unreadable-value rule (FR-014); the screen's behaviour with a stand-in for the audio,
  including the control's presence and absence by mode and state, the selected choice, and the level in force when
  listening starts (FR-015); the control and the indication in each form of FR-016; and the accessibility semantics
  of FR-019.
- **FR-022**: The developer test signal of feature 003 (FR-032) MUST also accept a level, so that the gate can be
  verified end to end without a microphone or an instrument. The catalogue MUST gain at least four signals, each an
  in-tune E2: 10 dB below the "Low" level, midway between "Low" and "Mid", midway between "Mid" and "High", and 10 dB
  above "High". An end-to-end test on each platform MUST feed each of the four through the real audio path at each of
  the four gate choices and assert a reading of E2 within 1 cent when the signal's level is at or above the gate
  level (always, for "Off") and the resting state otherwise. The fourteen existing signals MUST keep reading as they
  do today with the gate "Off".
- **FR-023**: Screenshots MUST be stored under the feature directory and reviewed before acceptance: Auto Listen with
  the control at a compact width, at an expanded width and in phone landscape, on Android and on iOS; and, on each
  target of FR-024, one pair that shows the gate at "Mid" with the test signal below it (meter resting, bar muted and
  short of the mark) and above it (note shown, bar active and past the mark).
- **FR-024**: Verification MUST be carried out on every target attached to the workstation, and each target's result
  MUST be written into the feature's verification notes. On 2026-10-05 those are the Pixel 6 Pro (connected by USB)
  and the three running iOS simulators: iPhone 17, iPad Pro 13-inch (M5) and iPhone Duo. On each target: every
  automated test suite that can run there MUST pass; the end-to-end test of FR-022 MUST pass; and the app MUST be
  launched with the control exercised and captured (FR-023). A target that cannot be driven MUST be named in the
  notes with the reason and the command the owner can run instead; it MUST NOT be dropped silently.
- **FR-025**: The gate MUST also be checked with live sound through the microphone on the Pixel 6 Pro and on each
  simulator, and the result recorded in the verification notes: a quiet pitched background played from a loudspeaker
  is named by the meter with the gate "Off" and rests the meter at a gate level above it, while a louder tone at an
  instrument's level is still read.
- **FR-026**: The feature MUST NOT include: a continuous slider or any free-form level entry; a decibel readout;
  automatic measurement of the room or automatic choice of level; any cleaning, filtering or suppression of the
  sound the tuner hears (the gate only decides whether to read it); a gate in By Ear; a separate level per
  instrument; or any other control of the Stitch screen it came from (reference-pitch calibration, detected string,
  the algorithm line).

### Key Entities

- **Noise Gate Choice**: one of Off, Low, Mid, High; exactly one is selected. It is one of the remembered tuner
  preferences, beside the instrument, string count, preset, mode and loop toggle of feature 003.
- **Gate Level**: the input level that a choice other than "Off" stands for (FR-002).
- **Input Level**: how loud the sound within the tuner's hearing range is at this moment (FR-004). Shown by the level
  indication; never stored.
- **Gate State**: open or closed, derived from the input level and the gate level with the 3 dB margin of FR-005;
  always open when the choice is "Off"; never stored.
- **Pitch Reading** (feature 003): unchanged, except that it is also "resting" whenever the gate is closed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At each of the three gate levels, a clear steady tone 6 dB below the level, at every open-string pitch
  of feature 003's presets, produces no reading during 10 seconds; the same tone at the level or above it is shown
  within 250 ms with its offset within 1 cent.
- **SC-002**: With the gate "Off", every automated tuner test that existed before this feature passes unchanged and
  all fourteen existing test signals give the same readings.
- **SC-003**: A note that starts 15 dB above the gate level and fades at 10 dB per second is read without a gap until
  it is 3 dB below the gate level (within 2 dB), and the meter rests within 500 ms after that.
- **SC-004**: A tone wavering 1 dB either way around the gate level for 30 seconds causes at most one change between
  a reading and the resting state.
- **SC-005**: In 100% of trials a change of gate choice while listening takes effect within 250 ms, and the
  platform's microphone indicator stays on throughout.
- **SC-006**: The level indication updates at least 20 times per second and shows test tones within 2 dB of their
  true level; with a gate level selected its mark stands at that level within 2 dB.
- **SC-007**: The selected choice survives closing and reopening the app in 100% of trials, and a fresh install shows
  "Off".
- **SC-008**: Captures of Auto Listen in every form of FR-016 show zero clipped, overlapping or off-screen elements;
  every choice measures at least 44 dp, is reachable and operable from the keyboard, and is announced with its label
  and state by the screen reader.
- **SC-009**: The verification notes carry a result for 100% of the attached targets (Pixel 6 Pro, iPhone 17,
  iPad Pro 13-inch (M5), iPhone Duo): automated tests passing with zero compiler and lint warnings, the end-to-end
  test passing for all sixteen combinations of gate choice and signal level, and the captures of FR-023.
- **SC-010**: In the live check on the Pixel 6 Pro and on each simulator, a quiet pitched background that the meter
  names with the gate "Off" leaves the meter at rest for at least 95% of a 30-second observation once a gate level
  above it is selected, and a louder tone played over it is still read within 250 ms.
- **SC-011**: A guitarist in a room with a television playing can, without instructions and within 30 seconds, find a
  choice at which the meter rests between plucks and still reads every open string.

## Assumptions

- **An enhancement to the tuner, not a new step in the build order.** The tuner is already built (feature 003); like
  feature 005 this refines it and pulls no later work forward, so the constitution's build order needs no amendment.
  The owner chose to do it now by asking for it on 2026-10-05.
- **The research brief is the starting point for the plan.** `docs/research/tuner-noise-gate.md` records where the
  gate fits, what it touches and its risks. This specification settles the points the brief left to the spec; the
  brief's technical recommendations are for the plan to confirm.
- **Shape of the control.** The brief offered two shapes: a few named levels in the tuner's existing selector style,
  or the continuous slider of the Stitch screen. The named levels are chosen: they need no new kind of control,
  they work with a keyboard and a screen reader as the other selectors do, and a musician picks "Low" faster than a
  number. The cost is coarser steps; the level indication (User Story 3) is what makes a coarse choice easy to get
  right. If the owner prefers the slider, that is a change to FR-001, FR-002 and FR-026 through `/speckit-clarify`.
- **Names.** The label is "Noise gate" (the owner's own words and the design's first title). The choices are named
  for the gate, not for sensitivity: "High" holds back the most. "Mid" is the visible text because four choices must
  fit a narrow settings column; it is announced as "Medium".
- **Gate levels.** −60, −50 and −40 dB are starting values, 10 dB apart. The brief's example was −60, −45 and −30 dB;
  the upper two are lowered here because a phone microphone that applies no automatic gain is expected to capture
  an instrument at arm's length well below its loudest level, where a −30 dB gate would hold back most playing. That
  expectation is unmeasured. The plan MUST measure a quiet room, a pitched background and an instrument-level tone on
  the Pixel 6 Pro and through a simulator, and may move the three values together, keeping their order and at least
  10 dB between neighbours, through `/speckit-clarify`. Every scenario and criterion above is written relative to the
  gate level so that it survives such a move.
- **The 3 dB margin** between opening and closing (FR-005) is a starting value from the brief's "a few dB". A wider
  margin lets a fading note ring longer but lets a background just under the gate hold it open; the plan may settle
  it between 2 and 6 dB from the same measurements.
- **Off by default.** A gate can cut a fading note short (User Story 2) and one fixed level behaves differently from
  phone to phone, so nobody gets a gate they did not choose.
- **One level for everything.** The choice is not kept per instrument or per room. A cello is louder than a guitar,
  but the gate is set against the background, not the instrument.
- **Placement.** The brief proposed the control under the selector bar upright and in the settings column sideways;
  the tabletop and book forms follow feature 005's rule that every setting lives in the "Tuner controls" card. The
  control is hidden in the denied state because there is nothing to gate and that state should show one message and
  one action.
- **The resting state does not change its wording.** A closed gate shows "Listening" like any other rest; the level
  indication, not a new message, tells the user why.
- **Simulators and live sound.** An iOS simulator listens through the workstation's microphone, so the live check of
  FR-025 runs there too, with levels that are the workstation's rather than an iPhone's. The check with a real
  instrument in a real room (SC-011) is the owner's, as the instrument checks of feature 003 were.
- **Attached targets.** The list in FR-024 is what was attached on 2026-10-05. The owner's iPhone 14 was paired but
  not connected and no Android emulator was running, so they are outside the required set; if one is attached when
  the work is verified it is included. With no foldable attached, the tabletop and book forms are verified by the
  automated layout tests and, if the Pixel 9 Pro Fold emulator is started, on it.
- **The iPhone Duo simulator** is covered in the poses that can be driven on it; it reports no fold posture to the
  app, so it shows the upright or wide form, never the tabletop one.
- **The Stitch project is not updated by this feature.** If the owner wants the control in the design, the tuner
  screens are synced from the app's captures afterwards, as was done for features 004 and 005.
- **No new third-party dependency** is expected.
- **Dark theme only, English only, left-to-right**, as in every feature so far.
- **Branch.** `006-tuner-noise-gate` was created from `main` by the spec-kit git extension on 2026-10-05.
