# Feature Specification: Microphone Recording on the Timeline

**Feature Branch**: `010-timeline-recording`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "I would like to add the audio recording functionality using microphone to the Timeline."

## What the design shows *(evidence for every decision below)*

The screens reviewed for feature 009 were re-read for every element that concerns recording. From
the Tembra Audio project (`12040171611712589599`): `timeline-arranger/phone` and
`timeline-arranger/tablet`, and `track-effects/phone` and `track-effects/tablet` (which carry no
recording element). From the owner's second Stitch project (`13820816821466270236`, image-only
screens under `design/stitch/screens/mixer-timeline/`): `phone-drawer`, `tablet-split-v1`,
`tablet-split-v2` and `tablet-split-console`. Feature 009 decided every timeline element on these
screens and set two of them aside for recording: the headphones button in the toolbar ("input
monitoring belongs to the recording feature") and the record button of the second project's top
transport ("record is feature 011"). The owner's review of the release build on 2026-10-26
(009 spec, Clarifications, "There is no way to record into a track") asked for recording as the
next feature, as its own specification, "with its own input level meter on the track header".
The DAW internals brief (`docs/research/daw-internals.md` §1.7 and §8.3: the engine's input
boundary "also feeds live mic/record"), the research report (`docs/DAW-App-Research-Report.md`
§6, step 6: "recording + input monitoring", and §8: keep the microphone closed until the user
asks for it) and the tuner specification (feature 003: the microphone permission and interruption
pattern) supply the behaviour used in the requirements.

| Element | Where it appears | Verdict |
|---|---|---|
| Headphones button in the toolbar, after the scissors | Both timeline screens | **Kept** as the input monitoring toggle (FR-016): in the toolbar from 600 dp, in the toolbar's overflow menu below it, as feature 009 placed its rarely used toolbar entries on phones |
| Muted-speaker button beside the headphones | Tablet timeline screen | **Excluded**, as feature 009 decided: it has no stated meaning on either screen |
| Transport at the top with play, a red filled-circle record button, rewind and a "120 BPM / 4/4" pill | The three tablet split screens of the second project | **Kept** as the transport bar's Record button, a filled circle in the design's record colour, placed in the bottom transport bar right of play/pause (FR-007); the top placement stays excluded as feature 009 decided |
| Clip captions "TAKE_01_VOX.WAV" and "808_Sub_A.wav" | Both timeline screens | **Kept** as the take naming "Take N" on the track (FR-012); the upper-case file-name form is drift, clip names carry no extension (009 FR-030) |
| Track header with "M" and "S" toggles | Both timeline screens; large square "S" and "M" in `phone-drawer` | **Kept**: the record-arm toggle joins them with the same size and shape (FR-001) |
| Mixer strips with dual LED meters, VU dials and faders | `phone-drawer` and the three tablet split screens | **Deferred** to the mixer feature as its design evidence. The input level meter of this feature goes on the armed track's header (the owner's decision of 2026-10-26) and is the meter those strips later show for an armed track (FR-005) |
| Gain readout in the expanded header ("−0.5dB") | Tablet timeline screen | **Unchanged**: the input meter sits beneath it while the track is armed (FR-005) |
| No screen shows a permission state, a count-in, an interruption notice or a refused monitoring state | — | **Designed here** from feature 003's permission and interruption pattern and feature 004's click; the plan maps them onto the design tokens and the new states are synced to the Stitch project as the earlier features did |

Export artefacts to be aware of: the second project's screens carry their own top transport, iOS
status bars and "Studio Sound" tokens (drift, as feature 009 recorded); the record button's red
is read as "the design system's record colour", which the plan takes from `DESIGN.md` and
`theme.json`.

## Where this feature sits in the build order

The constitution's build order was amended on 2026-10-13 to v1.3.1 (commit 0acc17f: "audio spine
with transport and timeline (one feature), mixer, recording, effects and automation, loops and
speed, collaboration"). That sentence was lost when `main` was merged into the 009 branch on
2026-10-17 (merge 18460a5 kept `main`'s older wording), so the constitution on `main` today
carries the pre-1.3.1 order ("mixer, effects and automation, recording and loops and speed")
under the v1.3.3 header. On 2026-10-26 the owner then chose recording as the next feature,
before the mixer (009 spec, Clarifications, session 2026-10-26; 009 task T145). This feature is
therefore **010** and the mixer becomes **011**; the mentions of "feature 010" (mixer) and
"feature 011" (recording) in the 009 specification are read with that swap and are not rewritten.

Before `/speckit-plan` the constitution MUST be amended through `/speckit-constitution` to restore
the lost sentence with recording moved ahead of the mixer: project scaffold, navigation shell,
tuner, metronome, audio spine with transport and timeline (one feature, 009), recording (010),
mixer (011), effects and automation, loops and speed, collaboration. The 2026-10-13 reason for
placing recording after the mixer ("recording needs the mixer's meters") is superseded: the input
meter lives on the track header (FR-005) and the mixer reuses it. The amendment names this
specification and feature 009's.

## Clarifications

### Session 2026-10-27

- Q: When a new take is recorded over clips that already exist on the armed track, what
  should happen to those older clips? → A: The take replaces what it covers: the overlapped
  parts of older clips are trimmed away when the take ends, as one undo step (FR-013).
- Q: How should the app line up a recorded take with the project so that what the musician
  played on the beat lands on the beat? → A: Automatically only, from what the device reports
  and what the app measures for the routes in use; no manual offset and no calibration
  routine in this feature (FR-014).
- Q: When wireless headphones that have their own microphone are connected, which microphone
  should record the take? → A: The device's built-in microphone; playback stays at full
  quality through the wireless headphones and the headset's microphone is never used (FR-002).
- Q: How should the phone's transport bar make room for the new Record button? → A: Keep every
  control and stack the readout (position above length) below 600 dp; the owner will check
  the result by hand on a phone and may switch to Record in the armed track's header beside
  the arm toggle instead, as a design iteration after the first release build (FR-007).
- Q: Should the count-in clicks sound when the transport bar's metronome toggle is off? → A:
  Yes: whenever Count-in is 1 or 2 bars the count-in clicks sound; after it the click
  continues only if the metronome toggle is on (FR-015).
- Planning finding 2026-10-27 (`/speckit-plan` research R17, `/speckit-analyze` I1): eight 44 dp
  targets are 352 dp, so the chosen phone bar cannot hold Record and a readout below 432 dp of
  bar width. Record stays in the bar from 432 dp (stacked readout to 599 dp) and goes to the
  armed track's compact header below it, the owner's named fallback; the owner's manual check on
  the first release build stands and may replace the header placement by a floating button
  (FR-007, FR-008, FR-026).
- Planning finding 2026-10-27 (research R3, analysis I2): on iOS, enabling the microphone
  restarts the audio unit, so arming while a project plays may gap the output for a bounded
  time; FR-001 states the bound (30 ms) and the verification notes record the measurement.

## User Scenarios & Testing *(mandatory)*

Recording is how a musician's own playing gets into a project. Feature 009 gave the Timeline
tracks, clips, playback, the metronome locked to the project and import from files; this feature
adds the microphone as a source: arm a track, see the input level, press Record, play along with
the project (with a count-in if wanted), hear yourself through headphones if wanted, and get a
clip that behaves like any other clip.

### User Story 1 - Arm a track and record a take along with the project (Priority: P1)

A musician opens a project, taps the record-arm toggle on a track's header (it lights, and a live
input level meter appears in the header), places the playhead where the part should start and
presses Record in the transport bar. The other tracks play from the playhead and a new clip grows
on the armed track under the moving playhead, drawing its waveform as it is recorded. They press
Record again, or Pause, to end the take; the clip is finished with its waveform and its name
("Take 1"), the project is saved, and pressing play plays it back in time with the other tracks.
Recording a second take over the first replaces what it covers; undo brings the earlier state
back.

**Why this priority**: This is the feature. Without arming, Record and a take that lands in time
with the project, nothing else here has value.

**Independent Test**: Open the demo project, arm a track, press Record, play or sing four bars
along with the project, end the take; confirm a clip named "Take 1" appears on the track spanning
the recorded time, plays back in time with the other tracks (a sound made on a beat is heard on
that beat), survives a relaunch, and that undo removes it and redo brings it back.

**Acceptance Scenarios**:

1. **Given** a project with tracks and the microphone permission granted, **When** the user taps
   the arm toggle on a track header, **Then** that track shows the toggle lit and a live input
   level meter in its header within 300 ms, any other armed track is disarmed, and the platform's
   recording indicator shows that the app is listening.
2. **Given** an armed track and the transport stopped with the count-in set to Off, **When** the
   user presses Record, **Then** within 100 ms playback starts from the playhead, the Record
   button lights, a clip appears on the armed track at the playhead and grows with the playhead,
   its waveform drawn as it is recorded, and the project length readout grows with it once the
   take passes the end of the last clip.
3. **Given** a take running, **When** the user presses Record again, **Then** the take ends
   within 10 ms with no click, the clip is finalised within 1 s with its complete waveform and
   the name "Take 1", playback continues, the track stays armed and the project is saved.
4. **Given** a take running, **When** the user presses Pause, **Then** the take ends as in
   scenario 3 and playback pauses with the playhead where it stopped.
5. **Given** the project playing with the metronome on and a take recorded while the musician
   taps along to the click, **When** the take is played back on a wired output, **Then** each
   recorded tap is heard on its beat: within 10 ms of the beat on the reference devices.
6. **Given** a track with "Take 1" from bar 1 to bar 5, **When** the user records "Take 2" from
   bar 3 to bar 7, **Then** "Take 1" is trimmed to bars 1 to 3, "Take 2" spans bars 3 to 7, no
   clips overlap, one undo restores "Take 1" whole and removes "Take 2", and redo brings "Take 2"
   back with the trim.
7. **Given** a take recorded, **When** the app is relaunched, **Then** the project reopens with
   the take's clip, name and waveform, and the project's size on its card includes the take.
8. **Given** the transport playing with a track armed, **When** the user presses Record,
   **Then** the take starts at the position being heard within 100 ms without a count-in and
   playback continues uninterrupted.
9. **Given** no track armed and a track selected, **When** the user presses Record, **Then** the
   selected track is armed and the take starts as in scenario 2; **Given** an empty project,
   **When** the user presses Record, **Then** "Track 1" is created, selected, armed and the take
   starts on it.

---

### User Story 2 - See the input level before and while recording (Priority: P1)

Before recording, the musician checks the level: the meter on the armed track's header follows
the instrument at once, with a mark that holds the loudest recent peak and a clip indicator that
lights and stays lit when the input hits full scale. They move the microphone or turn the
instrument down until the meter stays out of the top of its range, tap the indicator to reset it,
and record. The meter keeps showing the input during the take.

**Why this priority**: The owner asked for the input level meter on the track header as part of
recording, and a take that clips is unusable; the meter is what makes the first take succeed.

**Independent Test**: Arm a track, play quietly, then loudly, then overload the microphone;
confirm the meter follows within 50 ms, the hold mark stays about 1.5 s, the clip indicator
lights at full scale and stays lit until tapped or the track is disarmed, and the meter is
present in compact and expanded headers without changing the header's height.

**Acceptance Scenarios**:

1. **Given** an armed track, **When** the input is silent, **Then** the meter shows nothing;
   **When** the instrument plays, **Then** the meter rises within 50 ms and the hold mark stays
   at the loudest recent peak for about 1.5 s before falling.
2. **Given** an armed track, **When** the input reaches full scale, **Then** the clip indicator
   lights in the same frame and stays lit; **When** the user taps it, **Then** it resets;
   **When** the track is disarmed, **Then** the meter and the indicator disappear.
3. **Given** an armed track in a compact window, **Then** the meter is a thin bar along the
   header's bottom edge and the header keeps its height; **Given** an expanded window, **Then**
   the meter sits beneath the gain readout in the header column.
4. **Given** a take running, **When** the input changes, **Then** the meter follows as before
   the take and the growing clip's waveform shows the same peaks.

---

### User Story 3 - Start on the beat: the count-in (Priority: P2)

The musician wants a bar of clicks before the take starts so the first note lands on beat 1.
With the count-in set to one bar (the default) in the metronome sheet, pressing Record from a
stop plays one bar of the metronome's click at the project tempo and signature while the readout
counts the beats down; the take and the playback start together on the downbeat after the last
count-in beat. A two-bar count-in is available, and Off for musicians who punch in while playing.

**Why this priority**: A count-in is what makes recording along with a project usable for a
musician holding an instrument; it comes second only because a take can be recorded without it.

**Independent Test**: Set the count-in to 1 bar at 124 BPM in 4/4, press Record from a stop;
confirm four clicks sound with the metronome's accent on the first, the readout counts "4, 3, 2,
1", the playhead stays until the downbeat, the take's first beat follows the last click by
exactly one beat, and that Record or Pause during the count-in cancels it with nothing recorded.

**Acceptance Scenarios**:

1. **Given** the count-in at 1 bar, the metronome toggle off and the transport stopped, **When**
   the user presses Record, **Then** one bar of the metronome's click sounds (accent on beat 1,
   the Metronome destination's sound and levels), the Record button pulses on each beat, the
   readout's position shows "Count-in 4" down to "Count-in 1", the playhead does not move, and
   the take and playback start together on the next beat; after it the click stays silent
   because the toggle is off.
2. **Given** the count-in at 2 bars in 7/8 and the metronome toggle on, **When** the user
   presses Record, **Then** fourteen count-in clicks sound with the signature's accents, the take
   starts on the following downbeat and the click continues with playback.
3. **Given** a count-in running, **When** the user presses Record, Pause, space or Escape,
   **Then** the count-in stops, nothing is recorded, the playhead is unchanged and the Record
   button returns to idle.
4. **Given** the count-in at 1 bar and the transport playing, **When** the user presses Record,
   **Then** the take starts at once without a count-in.
5. **Given** the metronome sheet open from the Timeline, **Then** it shows "Count-in" with Off,
   1 bar and 2 bars, the choice is saved with the project, and the standalone Metronome
   destination is unchanged.

---

### User Story 4 - Hear myself: input monitoring (Priority: P2)

With headphones on, the musician turns on the headphones toggle and hears the instrument through
the app, through the armed track's gain and the master volume, with as little delay as the device
allows, while the other tracks play. With the phone's own speaker as the output the toggle cannot
be turned on, and a tap on it explains that headphones are needed, so the microphone never feeds
back into the speaker.

**Why this priority**: Singers and electric instruments need to hear themselves in the mix; the
design shows the toggle. It is P2 because an acoustic instrument records fine without it.

**Independent Test**: With wired headphones, arm a track, turn on monitoring, play; confirm the
instrument is heard through the headphones within 30 ms on the reference devices, follows the
track's gain, mute and solo and the master volume, stays on during a take, turns off at once
when the headphones are unplugged, and cannot be turned on while the speaker is the output.

**Acceptance Scenarios**:

1. **Given** an armed track and headphones connected, **When** the user turns on "Monitor
   input", **Then** the input is heard through the headphones whether the transport is stopped,
   playing or recording, through the track's gain, mute and solo and the master volume, and the
   choice is remembered on the device.
2. **Given** the device's built-in speaker as the output, **When** the user taps the toggle,
   **Then** it stays off and a message says that headphones are needed to monitor the input.
3. **Given** monitoring on, **When** the headphones are unplugged, **Then** monitoring stops at
   once and the toggle shows off; a running take continues.
4. **Given** monitoring on, **When** the track is disarmed, **Then** monitoring stops (there is
   nothing to monitor) and resumes when a track is armed again while the toggle is on.
5. **Given** monitoring on, **When** the user records a take, **Then** the take contains the
   input only: nothing of the other tracks or the click beyond what the microphone hears in the
   room.

---

### User Story 5 - Microphone permission, interruptions and the screen off (Priority: P2)

The first arm action asks for microphone access with the platform's prompt; a refusal leaves the
track unarmed with a message and a way to the system settings. A take keeps recording when the
screen locks or another app comes to the front; a call or another app taking the audio ends the
take and keeps what was recorded; nothing restarts by itself. The microphone is open only while
a track is armed.

**Why this priority**: Without a clear permission and interruption story the P1 flow silently
fails for a first-time user, and a lost take after a call is lost work.

**Independent Test**: Reset the permission, arm a track and deny; confirm the message, the
settings action and that the track stays unarmed. Grant it, arm, record, lock the screen for two
minutes; confirm the take continues. Record and receive a call; confirm the take ends, is kept to
the moment of the call, and nothing resumes afterwards. Disarm; confirm the platform's recording
indicator goes off.

**Acceptance Scenarios**:

1. **Given** microphone access never requested, **When** the user arms a track, **Then** the
   platform's prompt appears; **When** the user allows, **Then** the track is armed and the meter
   shows; **When** the user denies, **Then** the track stays unarmed and a message explains that
   the microphone is needed to record, with an action that opens the app's system settings page.
2. **Given** access granted later in the system settings, **When** the user returns and arms a
   track, **Then** it arms without a further prompt.
3. **Given** a take running, **When** the screen locks or another app comes to the front,
   **Then** the take and playback continue with the platform's ongoing-recording indication until
   the user returns and ends it.
4. **Given** a take running, **When** a call arrives or another app takes the audio, **Then** the
   take ends and is kept up to the last captured sound, playback stops, a notice names the reason,
   and nothing resumes by itself; the track stays armed and the meter returns when the
   interruption ends with Timeline in front.
5. **Given** a take running, **When** the user chooses another destination or the system back
   action, **Then** the dialog of feature 009 reads "Stop recording and leave" and "Keep
   recording"; confirming ends the take (kept), pauses and navigates; cancelling keeps recording.
6. **Given** a track armed, **When** the user leaves Timeline for the Tuner, **Then** the track
   is disarmed and the microphone released before the Tuner listens; the Tuner works as before.
7. **Given** a take running, **When** the app is force-quit, **Then** the project reopens with
   the take's clip containing all but at most the last 2 s of what was recorded.

---

### User Story 6 - Fits every window, pointer, keyboard and screen reader (Priority: P3)

The arm toggle, the meter, the Record button and the monitoring toggle work on a phone in one
hand, on a tablet or a foldable, in phone landscape, with a mouse and keyboard in desktop mode,
at the largest font size and with a screen reader, as every earlier feature's controls do.

**Why this priority**: Constitution Principle IV; this story polishes the stories above and is
verified last.

**Independent Test**: Take the screenshot set of FR-031 on both platforms; drive arming, Record,
the count-in and monitoring with the keyboard alone; read the armed header, the Record button
and the growing clip with the screen reader.

**Acceptance Scenarios**:

1. **Given** a 360 dp phone window, **Then** the compact header holds the colour dot, name, chip,
   M, S, the arm toggle and the menu without clipping, and the transport bar holds the speaker
   button, skip-to-start, step-back, play/pause, Record, step-forward, the readout (stacked:
   position above length) and the metronome toggle without clipping.
2. **Given** a pointer, **Then** the arm toggle, Record and the monitoring toggle show a hover
   state and a tooltip, and the clip indicator a tooltip; **Given** a keyboard, **Then** "A"
   arms the selected track, "R" presses Record, "I" toggles monitoring, space during a take ends
   it and pauses, Escape during a count-in cancels, and tab reaches the arm toggle after "S" and
   Record after play/pause.
3. **Given** a screen reader, **Then** the arm toggle announces its track and state, Record its
   state ("Record", "Recording", "Count-in, 3 beats left"), the meter its level on request and
   "clipped" when the indicator lights, and the growing clip "Recording, Take 2" with its length.

---

### Edge Cases

- The user arms a second track: the first is disarmed; its meter disappears; monitoring follows
  the newly armed track.
- The armed track is deleted (from its menu or the settings): it is disarmed first; a running
  take prevents the deletion (FR-018).
- The user records with the playhead inside an existing clip on the armed track: the take starts
  there; the clip's part under the take is silent during the take and trimmed away at the end
  (FR-013).
- A take ends after less than one hundredth of a second (a double tap on Record): nothing is
  added, no undo step.
- The take reaches 30 minutes: it ends itself, is kept, and a notice says why.
- The device runs low on space: Record is refused below 200 MB free with a message; a running
  take ends itself and is kept when free space falls below 50 MB.
- The input device changes during a take (a wired headset with a microphone or an interface is
  plugged in or removed): the take ends and is kept, a notice says why; the next take uses the
  new input with fresh alignment.
- Headphones without a microphone, or wireless headphones, connect or disconnect during a take:
  only the output changes; the built-in microphone keeps recording and the take continues
  (feature 009 FR-008).
- The microphone permission is revoked while a track is armed: the track is disarmed and the
  denied message shown; a running take ends and is kept.
- The user rotates the device or resizes the window during a take: the take continues; the
  controls move with the layout.
- The system font is at its largest size: the header, the meter, the readout and the Record
  button still fit (FR-026).
- The platform's reduce-motion setting is on: the meter and the growing waveform still move
  (they are information); the Record button's pulse is replaced by a steady state change.
- The user opens the metronome sheet during a take: the count-in and the time signature are
  read-only during the take; the sound, subdivision, accents and style still change.
- The user changes the master volume, a track's gain, mute or solo, zooms or scrolls during a
  take: all work; the take is unaffected (mute does not mute the capture).
- The input is a stereo interface: the take is stereo; a built-in microphone gives a mono take;
  a clip's waveform shows the channels as feature 009 draws them.
- Monitoring is on and the user arms a track while the output is the speaker: the toggle shows
  suspended-off and the explanation; nothing is heard; no feedback.
- The Tuner's noise gate is set: it has no effect on recording or the meter.
- Two takes are recorded without stopping playback: each press of Record ends one take and the
  next press starts another at the position being heard; each is one undo step.
- The app is terminated during the count-in: nothing is recorded.
- The project is duplicated or sent to another platform after a take: the take travels as any
  audio copy (feature 009 FR-049, FR-052).

## Requirements *(mandatory)*

### Functional Requirements

**Arming and the input**

- **FR-001**: Every track header MUST carry a record-arm toggle (a filled-circle glyph, accessible
  name "Arm for recording") beside the "M" and "S" toggles at every width, with the same size and
  shape. At most one track is armed at a time: arming a track MUST disarm the armed one. Arming
  MUST open the microphone (after permission, FR-019) and show the input meter (FR-005) within
  300 ms; disarming MUST close the input at once and hide the meter. Arming MUST work while the
  transport is stopped or playing: on Android without interrupting playback; on iOS, where
  enabling the input restarts the audio unit, with an output gap of at most 30 ms and the
  position and the project unaffected (planning finding 2026-10-27, measured in the
  verification notes). The armed state is session
  state: it MUST NOT be saved with the project and MUST be cleared when the track is deleted,
  when another project is opened, when the app leaves Timeline and its track settings screen for
  any other destination (after the dialog of FR-022 when a take runs), and when the app is
  terminated.
- **FR-002**: The input MUST be the device's current default wired input: the built-in microphone,
  or the microphone of wired headphones or of an external interface when the system routes
  input there; no input picker is offered. The microphone of wireless headphones MUST never be
  used: with wireless headphones connected the built-in microphone records and playback and
  monitoring stay at full quality through the headphones (confirmed 2026-10-27,
  clarification). The capture MUST keep the input's own channel count
  up to two (one from a built-in microphone, two from a stereo interface) and MUST NOT apply
  voice-call processing (echo cancellation, noise suppression, automatic gain control) or the
  Tuner's noise gate: what the musician plays is what is recorded, and a take contains the input
  alone, never the playback of other tracks or the click except as the microphone hears them in
  the room.
- **FR-003**: The microphone MUST be open only while a track is armed and Timeline or its track
  settings screen is in front, or while a take runs with the screen locked or another app in
  front (FR-021); it MUST never be opened without the user's arm action (or a Record press that
  arms, FR-009), so that the platform's recording indicator shows exactly when the app listens.
- **FR-004**: Arming MUST hand the microphone over correctly with the other listeners: leaving
  Timeline for the Tuner disarms first (FR-001), and the Tuner and the standalone Metronome MUST
  behave exactly as before this feature.

**The input level meter**

- **FR-005**: While a track is armed its header MUST show a live input level meter: the input's
  peak level over the last frame on a scale from silence (−60 dB) to full scale (0 dB), updated
  every frame, with a peak-hold mark that stays 1.5 s before falling and a clip indicator that
  lights when the input reaches full scale and stays lit until the track is disarmed or the
  indicator is tapped. The meter MUST use the design's two meter colours (the body up to −6 dB,
  the warning colour above). In compact headers (windows narrower than 840 dp) it MUST be a thin
  horizontal bar along the header's bottom edge spanning the header's width; in expanded headers
  (840 dp or wider) a horizontal bar beneath the gain readout; it MUST NOT change the header's
  height and MUST be absent while the track is not armed. The meter keeps showing the input
  during a take. Its scale, hold and clip behaviour define the input meter that the mixer feature
  later shows in its strips: the mixer reuses this meter rather than building a second one.
- **FR-006**: The meter MUST reflect the input within 50 ms and MUST cost nothing measurable to
  scrolling, zooming or the playhead: it MUST follow the same per-frame path as the playhead
  (constitution Principle II), never screen state that would redraw the header.

**Record**

- **FR-007**: The transport bar MUST hold a Record button immediately right of play/pause whenever
  the bar is at least 432 dp wide (phone landscape, tablets, desktop windows): a filled circle in
  the design's record colour, accessible name "Record", with the states idle, count-in (pulsing
  once per count-in beat; a steady state change under reduce motion) and recording (lit). In
  bars from 432 dp to 599 dp the position and length readout MUST stack the position above the
  length in the same forms as feature 009's FR-010; from 600 dp it stays on one line. In bars
  narrower than 432 dp (every phone in portrait) the bar MUST stay exactly as feature 009 built
  it and Record MUST sit in the armed track's compact header beside the arm toggle, in the label
  chip's place while the track is armed (planning finding 2026-10-27: eight 44 dp targets leave
  no room for a readout at 360 dp, so the clarification's choice is kept where it fits and the
  owner's named fallback applies below). The owner still checks the phone result by hand on the
  first release build and may replace the header placement by a floating Record button as a
  design iteration of this feature, with the keyboard, semantics and journey anchors of FR-028,
  FR-029 and the instrumentation section moving with the button.
- **FR-008**: Record MUST be available whenever a project is open on Timeline or its track
  settings screen where the bar holds it (bars of 432 dp or wider); in narrower bars it MUST
  appear as soon as a track is armed, the arm toggle on every header being the entry, and the
  "R" key MUST record into the armed track at every width. It records into the
  armed track; when no track is armed it MUST first arm the selected track, and when no track is
  selected it MUST add a track exactly as "+ Track" does (009 FR-019), select and arm it.
- **FR-009**: Pressing Record while stopped MUST run the count-in if it is not Off (FR-015) and
  then start playback and the take together at the playhead within 100 ms of the press or of the
  count-in's end. Pressing Record while playing MUST start the take at the position being heard
  within 100 ms, with no count-in. Pressing Record while a take runs MUST end the take and keep
  playing. Pause while a take runs MUST end the take and pause with the playhead where it
  stopped (009 FR-005). During a count-in, Record, Pause, space or Escape MUST cancel it: nothing
  is recorded and the playhead is unchanged. While a take runs skip-to-start, step-back,
  step-forward, ruler taps and the playhead drag MUST be unavailable (no seeking mid-take).
- **FR-010**: While a take runs: a clip MUST appear on the armed track at the take's start within
  one frame of the start, grow with the playhead and draw its waveform as it is recorded, updated
  at least ten times per second, in the clip style of feature 009 (FR-028) and named per FR-012;
  the playhead, the readout, the follow-paging (009 FR-012) and the overview strip MUST behave as
  in playback; the readout's length MUST grow with the take once it passes the project's end;
  the other tracks MUST play as in playback; the armed track's own clips MUST play until the take
  reaches them, and the part of any clip the take covers MUST be silent while the take runs (it
  is being replaced, FR-013), the growing clip drawn above it.
- **FR-011**: Ending a take MUST stop capturing within 10 ms of the press with no click, keeping
  the last captured sound; MUST finalise the clip within 1 s with its complete waveform overview,
  identical to the overview computed from the stored audio; MUST apply the alignment of FR-014
  and the replacement of FR-013; MUST record the take and its replacement as one undo step (009
  FR-035; redo restores both); and MUST save the project (009 FR-046). A take shorter than one
  hundredth of a second MUST be discarded with no clip and no undo step. The track stays armed
  after a take.
- **FR-012**: A take's clip MUST be named "Take N", where N is one more than the highest "Take N"
  name already on that track (starting at 1); it is a normal clip name shown in the caption (009
  FR-030: no renaming, clipped visually when long).
- **FR-013**: When a take ends, every clip on the armed track whose span overlaps the take's span
  MUST be reduced to its parts outside the span: a clip straddling the start keeps its part
  before, a clip straddling the end keeps its part after, a clip inside the span is removed, a
  clip straddling both keeps both outside parts as two clips; so the track has no overlapping
  clips (009 FR-027). The trimmed clips' audio copies are untouched; the whole change is the one
  undo step of FR-011, and undo restores every trimmed clip exactly and removes the take's clip
  (confirmed 2026-10-27, clarification).
- **FR-014**: A take MUST be placed so that a sound the musician makes at the instant a beat is
  heard lands on that beat in the recorded clip: the app MUST compensate the delay from the
  engine to the output and from the input to the engine for the routes in use, automatically and
  with no user control, by a value measured or obtained per device and route rather than fixed,
  and freshly for each take when a route changed; no manual offset and no calibration routine
  are offered (confirmed 2026-10-27, clarification). Success criterion SC-001 states the
  tolerance.

**Count-in**

- **FR-015**: The metronome sheet opened from Timeline (009 FR-039) MUST offer "Count-in" with Off,
  1 bar and 2 bars, default 1 bar, saved with the project beside the metronome toggle (not an
  undo step) and absent from the standalone Metronome destination (009 FR-040). When Record is
  pressed while stopped and the count-in is not Off, the metronome's click MUST sound the count-in
  bars at the project's tempo and time signature with the metronome's sound, levels, subdivision
  and accent pattern (the Metronome destination's settings, 009 FR-038), whether or not the
  metronome toggle is on; the playhead MUST stay at its position; the readout's position MUST
  show "Count-in" with the beats left (for one bar of 4/4: "Count-in 4" to "Count-in 1"); the
  take and playback MUST start exactly one beat after the last count-in click, on the downbeat.
  After the count-in the click continues only when the metronome toggle is on (confirmed
  2026-10-27, clarification). The count-in MUST never apply to pressing play or to Record
  while playing.

**Input monitoring**

- **FR-016**: A headphones toggle, accessible name "Monitor input", MUST let the user hear the
  armed track's input through the output: in windows 600 dp or wider in the toolbar after the
  scissors (as the design shows); below 600 dp as a checked entry "Monitor input" in the
  toolbar's overflow menu. It is off by default and remembered on the device outside every
  project. While on and a track is armed, the input MUST be heard whether the transport is
  stopped, playing or recording, through the armed track's gain, mute and solo and the master
  volume like the track's clips, with the least delay the device allows (SC-004). It MUST be
  silent while no track is armed.
- **FR-017**: Monitoring MUST be impossible while the output is the device's built-in speaker:
  the toggle MUST show off (suspended when the remembered value is on) and a tap MUST show a
  message that headphones are needed to monitor the input. When the output becomes the speaker
  while monitoring sounds (headphones unplugged, a wireless output lost), monitoring MUST stop at
  once; a running take continues. When an output other than the speaker is present the remembered
  value MUST apply again.

**During a count-in or a take**

- **FR-018**: While a count-in or a take runs the following MUST be unavailable: undo, redo, the
  scissors, clip move and delete, "+ Track", "Add audio…", "Add audio as new track", a track's
  Move up, Move down and Delete track, the tempo control, the time signature, the count-in
  setting, "Restore demo project", opening another project and arming or disarming. The
  following MUST keep working: mute, solo, gain, the master volume, zoom, scroll, the metronome
  toggle, the monitoring toggle, the track settings fields that are not listed above, and the
  Projects library for browsing (opening a project is refused with the dialog of FR-022).

**Microphone permission**

- **FR-019**: The first arm action (or the first Record press that arms, FR-008) MUST request
  microphone access with the platform's standard prompt. Both platforms MUST declare why the
  microphone is used in the platform's usual place with one sentence that covers the tuner and
  recording: "Tembra Audio listens to your instrument to show its tuning and records it into your
  projects." This supersedes the wording of feature 003's FR-014; the plan records the amendment
  of that requirement.
- **FR-020**: When access is denied, the arm MUST NOT take effect and a message MUST explain that
  the microphone is needed to record, with an action that opens the app's system settings page;
  everything else on Timeline MUST stay usable. When access is granted in the settings and the
  user returns, the next arm action MUST succeed without a further prompt (no track is armed by
  itself). If access is revoked while a track is armed, the track MUST be disarmed and the
  message shown; a running take ends and is kept (FR-021).

**Interruptions, the screen off and leaving**

- **FR-021**: A running take MUST continue while the screen is locked or another app is in front,
  with the platform's ongoing-recording indication, until the user ends it or one of these ends
  it, keeping everything captured up to that moment and showing a notice that names the reason:
  a call or another app takes the audio, the input device changes, the permission is revoked, the
  take reaches 30 minutes, or free space falls below 50 MB (FR-025). When a call or another app
  takes the audio, playback MUST stop as feature 009's FR-008 says and nothing MUST resume by
  itself; the track stays armed and the microphone reopens when the interruption ends while
  Timeline is in front. A change of output alone (headphones connected or removed) MUST NOT end
  the take (009 FR-008; monitoring per FR-017).
- **FR-022**: While a take runs, the stop-and-leave dialog of feature 009 (FR-008a) MUST read
  "Stop recording and leave" and "Keep recording" for the same navigations it already covers;
  confirming MUST end the take (kept), pause and then navigate; cancelling MUST keep recording.
  The dialog MUST NOT appear between Timeline and its track settings screen. Leaving during a
  count-in MUST cancel it without a dialog.
- **FR-023**: If the app is terminated during a take (crash, force quit, system kill), the take
  MUST be kept up to at most 2 s before the termination: the audio is stored continuously as it is
  captured, and the next open of the project MUST show the take's clip with what was stored,
  named per FR-012, aligned per FR-014 and with the replacement of FR-013 applied, the undo
  history empty as after any relaunch (009 FR-035).

**Storage and the project document**

- **FR-024**: A take MUST be stored in the project's storage as its own audio copy (009 FR-048) in
  a lossless, uncompressed form at the rate and channel count captured, keeping everything the
  input delivered; it MUST count toward the project's size on its card, be shared and removed
  like an imported copy (009 FR-034, FR-048: an undone take whose deletion has left the undo
  history is removed from the device), and be referenced in the project document by its content
  identifier with its format facts (009 FR-049) so the document stays portable; a take recorded
  on one platform MUST open identically on the other.
- **FR-025**: A take MUST be limited to 30 minutes (it ends itself with a notice) and Record MUST
  be refused with a message when the device has less than 200 MB free; a running take MUST end
  itself, kept, when free space falls below 50 MB. The 500 MB limit of imports (009 FR-044) does
  not apply to takes.

**Layout, input and accessibility**

- **FR-026**: In windows narrower than 600 dp the compact header (009 FR-055) MUST hold the colour
  dot, the name, the label chip, "M", "S", the arm toggle and the menu at 360 dp at the default
  font size, and at the largest system font size, without clipping, at least 56 dp tall, with the
  meter along its bottom edge while armed; the transport bar MUST hold every control of 009
  FR-004 unchanged below 432 dp of bar width, and Record with the stacked readout from 432 dp
  (FR-007); while a compact header is armed below 432 dp it MUST hold the colour dot, the name
  (at least 72 dp), Record, "M", "S", the arm toggle and the menu, the label chip hidden until
  the track is disarmed. From 840 dp the arm toggle sits with
  "M" and "S" in the header column and the meter beneath the gain readout. In windows at least
  600 dp wide and shorter than 480 dp the ruler, at least two tracks and the transport bar with
  Record MUST be visible. Nothing MUST clip, overlap, hide behind the transport bar or scroll the
  frame horizontally at any width.
- **FR-027**: The arm toggle, Record, the monitoring toggle, the clip indicator and the count-in
  setting MUST show a hover state under a pointer and a visible focus indicator, MUST measure at
  least 44 dp, and the icon-only ones MUST show a tooltip ("Arm for recording", "Record",
  "Monitor input", "Clipped, tap to reset").
- **FR-028**: Keyboard: "A" arms or disarms the selected track, "R" acts as the Record button, "I"
  toggles monitoring, space during a take ends it and pauses (as 009 FR-057's play toggle),
  Escape during a count-in cancels it; tab MUST reach the arm toggle after "S" in each header and
  Record after play/pause in the transport bar; the keys of feature 009 keep their meanings, and
  the ones FR-018 makes unavailable do nothing during a count-in or take.
- **FR-029**: Every new control MUST expose its label, value or state and a stable test identifier
  to accessibility services: the arm toggle announces its track's name and "Arm for recording,
  on" or "off"; Record announces "Record", "Recording" or "Count-in, N beats left"; the meter is
  an element that announces its current level in dB on request and announces "clipped" once when
  the indicator lights; the growing clip announces "Recording, Take N" with its length; the
  monitoring toggle announces its state and, when suspended, the headphones reason; the notices
  of FR-021 and FR-025 are announced when shown.
- **FR-030**: All colours, type styles, spacing, radii and icons MUST come from the shared design
  system; the new icons (record, arm, headphones) MUST be added through its existing mechanism;
  the record colour and the meter's two colours MUST be design tokens; every live number
  (the countdown, the stacked readout) MUST use tabular figures. Under reduce motion the meter
  and the growing waveform still move; the Record button's pulse becomes a steady state change.

**Quality**

- **FR-031**: Screenshots at compact (360 dp) and expanded (1024 dp or wider) widths and in phone
  landscape on Android and the iOS simulator MUST be stored under the feature directory and
  reviewed against the design before acceptance. The set MUST include: an armed track with the
  meter at rest, at a loud level and clipped, in compact and expanded headers; the count-in in
  progress; a take in progress with the growing clip; the finished take; a take over existing
  clips before and after; the monitoring toggle on, and suspended with its message; the
  permission-denied message; the stop-recording-and-leave dialog; the metronome sheet with the
  count-in setting; the transport bar with Record at 360 dp; and the largest font size.
- **FR-032**: Automated tests MUST cover, without a device: the arming rules (one at a time,
  disarm on delete, project switch and leaving); Record from stopped, playing, count-in and
  recording, with no armed track, no selected track and an empty project; the count-in countdown
  and the downbeat start for every signature and count; the take placement and the replacement
  arithmetic of FR-013 (no clip, straddling the start, the end, both, inside, several) and its
  single undo step; take naming; the alignment arithmetic of FR-014 from given input and output
  delays; the 30-minute cap, the free-space refusals and the discard of a take under one
  hundredth of a second; the permission states; the monitoring guard of FR-017 for every output
  kind; the dialog texts of FR-022; the unavailability rules of FR-018; the project document round
  trip with a take; the meter's peak, hold and clip law from a known signal; the live overview
  against the stored audio; the engine's capture path on an offline render (captured blocks
  stored contiguously with no gap or repeat, the count-in click aligned with the beat, the take's
  first sample aligned by the given compensation, the covered clip silent, the monitored input
  summed through gain, mute, solo and master); and the accessibility semantics of FR-029.
  Behaviour that needs real hardware (the permission prompt, real capture, route changes, calls,
  the screen off, a force quit, the loopback alignment of SC-001 and the monitoring delay of
  SC-004) MUST be verified on a device and recorded in the feature's verification notes.
- **FR-033**: Any dependency added for this feature MUST carry a permissive licence, MUST be
  recorded in the licence inventory with its version and source, and MUST NOT be one of the
  libraries the constitution forbids.
- **FR-034**: The feature MUST NOT include: the mixer sheet or panel with faders, pan, meters and
  a master strip (feature 011); an input picker or recording from several inputs at once; an
  input gain or trim control; a manual latency offset; loop or cycle recording; punch-in and
  punch-out points, pre-roll or post-roll beyond the count-in; take lanes, layered takes or
  comping; overlapping clips; recording the app's own output, another app's audio or the system
  mix; compressed take formats; noise suppression, a noise gate or any processing on the way in;
  effects, sends or buses; MIDI; a transport bar or recording on any destination other than
  Timeline and its track settings screen; changes to the Tuner, the standalone Metronome, the
  Projects library or the navigation.

### Performance instrumentation declared by this feature

- Journeys added: one new journey, `recording`, on both platforms: open Projects, open the demo
  project from its card, tap "+ Track", arm the new track (the runner has granted the microphone;
  the input is the room), wait for the meter's first frame, tap the monitoring toggle and dismiss
  its message (or toggle it off again when headphones are present), press Record, wait through
  the one-bar count-in and four bars of the take, press Record again (the take ends, playback
  continues), pause, undo twice (the take, then the track, so the project is the same after every
  pass), and skip to the start. The `stress` journey MUST be extended to record a take on a new
  track while the demo project plays with the metronome on and the timeline scrolls, then undo
  it, so the audio-thread counters measure capture and playback together.
- Sections added (the catalogue's timeline and engine families): arm (the press to the meter's
  first frame), record start (the press, or the count-in's end, to the first captured sound
  stored), count-in (its whole span, animation variant), take frame (the per-frame cost of the
  growing clip and the meter), record stop (the press to the finalised clip with its complete
  waveform and the saved project), monitoring toggle (the press to the first monitored output or
  the refusal message), and take alignment (the determination of the compensation for a take).
- Counters added: input overruns (captured blocks the engine missed), take write backlog (the
  longest queue of captured audio waiting to be stored), take alignment applied (the
  compensation in milliseconds), peak and average callback load while a take runs (read from
  the existing callback-duration counters during the journey's recording phase, so these two
  are not new counters), and the monitoring delay as determined, beside feature 009's underruns
  and starvation.
- Anchors: the arm toggle in every header, the meter and its clip indicator, the Record button,
  the monitoring toggle and its overflow entry, the count-in setting in the metronome sheet, the
  growing clip, the permission message's settings action and the actions of the stop-recording
  dialog carry stable identifiers for the journey drivers.
- Baseline profile: Timeline is a primary destination and this feature adds main interactions, so
  the Android baseline profile MUST be regenerated and committed in this feature (feature 008's
  rule).
- After the merge the feature MUST be measured with a labelled session on every available device
  and simulator; the report is pull-request evidence. The startup family MUST NOT regress by more
  than the gate, and the new counters MUST read zero input overruns and zero underruns on the
  reference devices.

### Key Entities

- **Take**: a clip created by recording (feature 009's Clip in every other respect): its track,
  its start in beats after alignment, the audio copy it references and its take number.
- **Audio Copy** (feature 009): the stored take is one, in a lossless form with its format facts
  (channels, rate, length), identified by its content and referenced by the project document.
- **Recording Session State**: which track is armed, whether monitoring is on and whether it is
  possible on the current output, the count-in in progress (beats left), and whether a take
  runs and since when; never part of the project document.
- **Input Level**: the live reading the meter shows: the current peak, the held peak and the clip
  latch; telemetry, never saved.
- **Count-in Setting**: Off, 1 bar or 2 bars; a project setting beside the metronome toggle.
- **Capture Alignment**: the compensation applied to a take for the input and output routes in
  use; determined by the app, never edited by the user.
- **Input Route**: the device's current default input and its channel count; observed, never
  chosen in this feature.
- **Edit History** (feature 009): a take with its replacement is one snapshot.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the reference devices, a five-minute take recorded while the demo project plays
  with the metronome on, the click reaching the microphone from the device's speaker or by a
  loopback cable, shows every recorded click within 10 ms of its beat after alignment, from the
  first bar to the last; on every other device tested the measured offset is recorded in the
  verification notes.
- **SC-002**: Capture starts within 100 ms of the Record press (no count-in) and the clip appears
  within one frame; the take's first beat follows the last count-in click by one beat within
  1 ms; a take ends within 10 ms of the press with no click; the clip is finalised with its
  complete waveform within 1 s.
- **SC-003**: A 30-minute take while eight tracks play on both reference devices has zero missed
  input, zero underruns and no audible dropout, and a known test signal fed to the input by
  loopback is reproduced without a gap or a repeated sample and within 0.5 dB of its level.
- **SC-004**: With wired headphones, the delay from the input to the monitored output is below
  30 ms on the reference devices, measured by loopback; monitoring cannot be turned on while the
  speaker is the output, so no feedback occurs in any test.
- **SC-005**: Arming shows the meter within 300 ms; the meter reflects a level change within
  50 ms; full-scale input lights the clip indicator in the same frame.
- **SC-006**: In 10 of 10 trials each, a call during a take, a force quit during a take and a
  screen locked for 10 minutes during a take end with the take kept: up to the moment of the call,
  to within 2 s of the force quit, and whole after the lock.
- **SC-007**: A project with a take saved on one platform and opened on the other with its audio
  copies present plays identically and shows the identical arrangement; the document contains no
  device path.
- **SC-008**: Screenshots at compact and expanded widths and in phone landscape on both platforms
  show zero clipped, overlapping or hidden elements with the arm toggle, the meter, Record and
  the monitoring toggle present, and match the design on visual review; all test lanes are green
  with zero compiler and lint warnings.
- **SC-009**: 100 % of the new controls are reachable and operable by keyboard, announce their
  label and state to the screen reader, and measure at least 44 dp.
- **SC-010**: A musician who has never used the app can arm a track, record a part along with the
  demo project with the count-in, listen back, and record a second take over it within two
  minutes without instructions.
- **SC-011**: The labelled performance session after the merge shows the `recording` journey on
  every available device, zero input overruns and zero underruns on the reference devices, and
  no family above its gate limit in the worse direction.

## Assumptions

- **One input, one armed track.** The device delivers one input at a time, so arming is
  exclusive and a take goes to one track; recording several tracks at once from a multi-input
  interface waits for an input picker in a later feature. Wireless headset microphones are
  excluded (confirmed 2026-10-27) because using one drops the whole connection, playback
  included, to call quality; a musician who wants one waits for the input picker.
- **A new take replaces what it covers** (confirmed 2026-10-27). The covered parts of older clips on the armed track are
  trimmed away when the take ends and undo restores them (the default of the best-known mobile
  DAWs, and the only behaviour consistent with feature 009's rule that editing never creates
  overlapping clips). Take lanes, layered takes and comping are a later feature. A musician who
  wants to keep an earlier take records the next one on another track.
- **Count-in** defaults to one bar, lives in the metronome sheet beside the signature because it
  is a click matter, is a project setting like the metronome toggle, and sounds even when the
  toggle is off because a count-in that cannot be heard is no count-in (confirmed 2026-10-27). It reuses the Metronome
  destination's click as the constitution requires; the standalone Metronome keeps no count-in
  (feature 004).
- **Monitoring** is the app's own path from the input to the output (through the armed track's
  gain, mute and solo and the master volume, so the musician hears a balance), not a platform
  hardware loopback, and is impossible on the built-in speaker because the microphone would feed
  back. On phones it lives in the overflow menu, as feature 009 placed its rarely used toolbar
  entries, because it is set once per session.
- **Alignment is automatic** (confirmed 2026-10-27). The compensation comes from what the device reports and the app
  measures for the routes in use; a manual offset control is excluded. If a device cannot meet
  SC-001 with the automatic value, the verification notes record it and a per-device offset
  becomes a later feature rather than a control every musician sees.
- **Takes are stored lossless** at the captured rate and channel count because feature 009
  keeps originals and plays from them; the 30-minute cap matches the import limit, while the
  500 MB import limit does not apply because a long stereo take exceeds it legitimately.
- **Record never meets a disabled button.** Record arms the selected track or adds one so that a
  first-time user can press it and get a take; a track is never armed without a user action.
- **The readout stacks on phones** (position above length) to make room for Record without
  removing a transport control or a number feature 009 shows; the owner checks it by hand on
  the release build and may move Record into the armed header instead (confirmed 2026-10-27).
- **The permission wording** covers the tuner and recording in one sentence because each
  platform allows one declaration per app; feature 003's FR-014 wording is superseded by FR-019
  and the plan records the amendment.
- **Mute does not mute the capture.** Mute and solo decide what is heard; the take always
  contains the input, so a musician can record silently against the mix.
- **The header meter is the mixer's input meter.** The mixer feature (011) shows this meter in
  its strips for the armed track and adds the playback meters; its scale and behaviour are set
  here so the two never disagree.
- **Feature numbering.** Recording is 010 and the mixer 011 by the owner's decision of
  2026-10-26; feature 009's text keeps its "feature 010" (mixer) and "feature 011" (recording)
  mentions, read with the swap. The constitution amendment is made before `/speckit-plan` as the
  earlier features did ("Where this feature sits in the build order").
- **Performance measurement** relies on the runner granting the microphone (as the tuner's
  journey already does) and records the room; the `recording` journey undoes its edits so the
  library is the same after every pass.
- **Design drift** (the second project's top transport and the upper-case file-name captions) is
  noted above and not followed.
- **Dark theme only, English only, left-to-right**, as in the earlier features.
- **Branch.** `010-timeline-recording` was created from `main` by the spec-kit git extension on
  2026-10-27.
