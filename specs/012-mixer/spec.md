# Feature Specification: Mixer

**Feature Branch**: `012-mixer`

**Created**: 2026-11-03

**Status**: Draft

**Input**: User description: "I would like you to work on the mixer functionality in the app.
Please check the existing screenshots that I provided to you way earlier with a couple of samples
of how the mixer can look like and how we can adjust the volumes and mix multiple tracks at the
same time. I believe the original project in the Google Stitch designs doesn't contain a screen
for the mixer tool, so maybe you can check the provided screenshots and I will post or attach a
couple of screenshots that I used for the inspiration. [Image #1] the screenshot for inspiration.
I like how the faders look as on a real console, the knobs, however I don't know what is VU, but
it looks cool too. Make sure that you make the design adaptive for different screen sizes. It
should be mobile device oriented and adaptive for various screen sizes."

## What the design shows *(evidence for every decision below)*

The inspiration image attached to the request is the composite of the four screens the owner
had already pulled on 2026-10-13 from the second Stitch project (`13820816821466270236`,
"Studio Sound" design system, image-only screens under `design/stitch/screens/mixer-timeline/`):
`phone-drawer`, `tablet-split-v1`, `tablet-split-v2` and `tablet-split-console`. The Tembra Audio
project (`12040171611712589599`) has no mixer screen, as the owner remembers; its
`timeline-arranger` screens carry the track headers and the transport bar this feature builds on.
Feature 009 reviewed the four screens, decided every timeline element on them and set the mixer
elements aside for this feature ("a bottom sheet on phones and a console beside the timeline on
tablets", 009 spec, design table; "Pan is added by [the mixer]", 009 FR-041; the mixer is excluded
from 009 by FR-064 and from 010 by FR-034). Feature 010 fixed the input level meter's scale,
colours, hold and clip latch on the armed track's header and ruled that "the mixer reuses this
meter rather than building a second one" (010 FR-005). The DAW internals brief
(`docs/research/daw-internals.md` §1.7, §3 and §8.3: smoothed gain and pan, the −3 dB
constant-power pan law, peak and averaged metering over the telemetry path, the master bus as the
summing point) and the research report (`docs/DAW-App-Research-Report.md` §6, step "Mixer":
"per-track gain/pan (−3 dB law) + summing buses + master; metering back over the telemetry
FIFO") supply the behaviour used in the requirements.

| Element | Where it appears | Verdict |
|---|---|---|
| Six console strips side by side, each with the track number at the top and the track name at the bottom | The three tablet split screens | **Kept** as the strip's frame: the number is the track's position in the project, the name is the track's name (FR-002); the strip is tinted with the track's colour as `tablet-split-v2` tints its base and the headers already do |
| Dual vertical LED meter per strip, lit from the bottom in the track's tint, a brighter top segment | `tablet-split-console`, `tablet-split-v1` | **Kept** as the strip's level meter: two bars (left and right of the track's output after its fader and pan) with the scale, colours, peak-hold mark and clip latch feature 010 fixed for the input meter (FR-012); the armed track's strip shows the input meter instead (FR-014) |
| Round "VU" dial with a needle per strip | All three tablet split screens | **Kept** as the averaged level (a VU reading, FR-013); shown when the window is tall enough, dropped before the fader loses its throw (FR-025) |
| Round pan knob per strip with a pointer at the top | All three tablet split screens | **Kept** as the pan control (FR-006, FR-007); the knob's value is also readable as text (FR-008) |
| "Solo" and "Mute" buttons per strip, Solo lit in lavender or amber, Mute dim | All three tablet split screens | **Kept** as the strip's solo and mute toggles with the meanings and colours feature 009 fixed for the headers' S and M (FR-010) |
| Long-throw fader per strip with a dB scale "10, 5, 0, 5, 10, 20, 30, 40, 50, 60, 70" and a unity arrow at 0 | `tablet-split-console`, `tablet-split-v1`, `tablet-split-v2` (no scale) | **Kept** as the gain fader with feature 009's range: +6 dB at the top, silence at the bottom, a unity mark at 0 dB and a console-like throw where the upper part is fine (FR-003, FR-004); the "+10" top and the "−70" bottom are drift against 009 FR-023 |
| Speaker button under each fader | `tablet-split-console`, `tablet-split-v1` | **Excluded**: it has no stated meaning on either screen, as feature 009 excluded the transport's muted-speaker button; the master volume keeps its place in the transport bar (009 D1) |
| "MIXER" drawer handle over the phone timeline with a master fader, a dual-colour LED meter (green body, yellow warning, pink clip) and an "FX" dial | `phone-drawer` | **Kept** as the mixer sheet on phones with its handle (FR-018) and as the master strip (FR-011); the LED colours are already the design tokens `meter_body`, `meter_warning` and `meter_clip` that feature 010 sampled from this screen; the "FX" dial is **excluded** here: inserts and sends are the effects feature (013) |
| Horizontal "Pan L/R" slider in each compact track header | `phone-drawer` | **Kept** as the pan slider in the track settings and as a readout in the expanded header (FR-007); the compact header has no room for it below 600 dp (010 US6 scenario 1) |
| Mixer console filling the left part of a tablet window beside the timeline's lanes | All three tablet split screens | **Kept** as the mixer panel of expanded windows (FR-021); the two panes share the toolbar and the transport bar |
| "Sliders" glyph at the right of a thin toolbar row above the console, a "list" glyph at its left | `tablet-split-console`, `tablet-split-v1` | The sliders glyph is **kept** as the toolbar's Mixer button (FR-017); the list glyph is **excluded**, it has no stated meaning |
| Transport at the top (play, record, skip, rewind, fast-forward, "120 BPM / 4/4"), a bottom navigation with "Mixer, Timeline, Library, Effects, Settings" or "Home, Mixer, Play, Levels, Settings", iOS status bars | The three tablet split screens | **Excluded**, as feature 009 decided: the transport bar stays at the bottom, feature 002 decided the navigation, the mixer is not a destination (FR-016) |
| A sixth strip numbered "6" after "4" in `tablet-split-v2`, i.e. a missing "5" | `tablet-split-v2` | Export drift; strips are never skipped (FR-002) |
| Clip waveforms, ruler, playhead chip, track lanes | All four screens | Already decided by feature 009; unchanged here |

Export artefacts to be aware of: the second project's screens carry their own "Studio Sound"
tokens (amber solo, rose faders, a lighter chrome) which feature 009 recorded as drift; this
feature draws the mixer with the Tembra Audio tokens (`design/stitch/DESIGN.md`, `theme.json`:
lavender for active toggles, the six track tints, the three meter colours). Stitch holds no HTML
for these screens, so the plan designs the mixer's states from this table and syncs them to the
Tembra Audio project as the earlier features did.

## Where this feature sits in the build order

The constitution's build order (v1.3.5, amended 2026-11-03) is: project scaffold, navigation
shell, tuner, metronome, audio spine with transport and timeline (009), recording (010), large
screens and desktop (011), **mixer (012)**, effects and automation, loops and speed,
collaboration. The owner chose the mixer on 2026-11-03 as the next specification while feature
011 is being implemented and feature 010 awaits its merge. Feature 009 calls the mixer "feature
010" and feature 010 calls it "feature 011"; both are read as 012, as the constitution says.

This feature builds on what the two earlier features left for it: the per-track gain, mute, solo
and master volume with their ranges, defaults, fades and keys (009 FR-007, FR-019, FR-023,
FR-024, FR-057), the undo history in which a drag is one step (009 FR-035), the input meter's
scale and behaviour (010 FR-005) and the rule that pointer and keyboard are first-class on every
control (constitution Principle IV; 011 FR-012, FR-014, FR-019, FR-022). It adds **pan**, the
**playback level meters** (per track and master) and the **mixer surface** (sheet, console and
panel) where all of them are controlled together. Effects (inserts, sends, EQ) and automation are
the next feature and MUST NOT be pulled into this one.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Balance the tracks with faders (Priority: P1)

A musician who has recorded or imported several tracks opens the mixer from the timeline and sets
each track's level with a long-throw fader, the way a console works: the fader moves under the
finger, the sound follows at once without clicks, the exact value is readable in dB, a double tap
returns a fader to unity, and two faders can be moved at the same time with two fingers. Closing
the mixer brings the timeline back exactly as it was, and every change is in the project and in
the undo history.

**Why this priority**: it is the feature's reason to exist; without it the mixer is a display.
Feature 009's gain readout in the track settings is precise but one track at a time.

**Independent Test**: open the demo project, open the mixer, drag two faders at once while the
project plays, read both values, double-tap one, undo twice, close the mixer, reopen the project
from the library and see the remaining change.

**Acceptance Scenarios**:

1. **Given** the demo project open on a phone, **When** the user taps the Mixer button in the
   toolbar, **Then** the mixer sheet rises over the lanes with one strip per track in the
   project's order and the master strip at its right end, the transport bar stays in place and
   playback is not interrupted.
2. **Given** the mixer open and the project playing, **When** the user drags a track's fader
   down by a third of its throw, **Then** the track's gain readout falls accordingly, the track
   is quieter within 20 ms of each finger movement, and no click, step or zipper is heard.
3. **Given** a fader at −7.3 dB, **When** the user double-taps its handle, **Then** the fader
   returns to 0 dB with a short animated move and the readout shows "+0.0dB".
4. **Given** two tracks, **When** the user drags both faders at once with two fingers,
   **Then** both follow their own finger independently and both gains change together.
5. **Given** a fader drag that raised a track from 0 dB to +4.1 dB in one gesture, **When** the
   user taps Undo, **Then** the track returns to 0 dB in one step and the fader moves with it.
6. **Given** the mixer closed after a change, **When** the project is reopened later,
   **Then** every fader is where it was left and the track settings' gain readout agrees.

---

### User Story 2 - See the levels while the project plays (Priority: P1)

While the project plays, every strip shows its track's output level on a two-bar meter that
lights from the bottom with a peak-hold mark and a clip indicator, plus a VU reading that shows
the loudness the ear hears rather than the instant peak; the master strip shows the summed
output the same way. The musician sees which track is too hot, which is buried, and whether the
mix clips, and fixes it with the faders without stopping.

**Why this priority**: faders without meters are guesswork; the owner asked for the meters
and the VU dials explicitly.

**Independent Test**: play the demo project with the mixer open; watch each strip's meter move
with its track, the hold mark stay for a moment at every peak, the master meter sum the tracks;
raise a track to +6 dB until the master clips and see the clip indicator latch; tap it to clear.

**Acceptance Scenarios**:

1. **Given** the mixer open and the project playing, **Then** each strip's meter moves with its
   own track's sound after that track's fader, pan and mute, refreshes at the display's rate and
   lags the sound by no more than 50 ms.
2. **Given** a track's peak, **Then** a hold mark stays at the highest level for 1.5 s before
   it falls; **Given** a muted track, **Then** its meter rests at silence.
3. **Given** the master's sum exceeds full scale, **Then** the master strip's clip indicator
   lights and stays lit until the user taps it or playback starts again from a stop; the two
   master bars show the summed output in the same scale as the strips.
4. **Given** the VU reading of a strip, **Then** it moves slowly and steadily with the track's
   average loudness, reaches "0 VU" when the track's average is at the reference level, and
   never twitches with single peaks the way the bar meter does.
5. **Given** an armed track (feature 010), **Then** its strip's meter shows the input level
   meter of the armed header with the same scale, hold and clip latch, and returns to the
   playback meter when the track is disarmed.
6. **Given** the transport stopped, **Then** every meter falls to silence within one second and
   the clip indicators keep their latched state.

---

### User Story 3 - Place the tracks in the stereo field with pan (Priority: P2)

The musician turns a track's pan knob to move it left or right; the track moves across the
stereo field without a jump in loudness, the knob snaps to the centre, the value is readable as
"L 35", "C" or "R 20", and the same pan is reachable as a slider in the track's settings. Pan is
part of the project and of the undo history.

**Why this priority**: pan is the second control of any mixer and the one feature 009 deferred
here; it is less urgent than level because a mono mix is usable and an unbalanced one is not.

**Independent Test**: with the demo project playing on headphones, turn the first track hard
left and the second hard right, hear each on one side at the same loudness as in the centre,
double-tap a knob to return to the centre, undo, and open the track's settings to see the pan
slider at the same position as the knob.

**Acceptance Scenarios**:

1. **Given** a strip's pan knob, **When** the user drags up or down on it (or turns it along
   its arc), **Then** the track's pan follows between full left and full right, the readout shows
   the position, and the move is heard within 20 ms without clicks.
2. **Given** a track panned fully left, **Then** it is heard only on the left at the same
   loudness as when centred (a −3 dB constant-power law); **Given** the track centred, **Then**
   it is equally loud on both sides.
3. **Given** a knob near the centre, **When** the user releases it within 5 % of the centre,
   **Then** it settles on the centre; **When** the user double-taps the knob, **Then** it returns
   to the centre.
4. **Given** a pan change in one gesture, **When** the user taps Undo, **Then** the track
   returns to its previous pan in one step.
5. **Given** the track's settings open, **Then** a "Pan" slider shows the same value as the
   knob and sets it; **Given** an expanded window, **Then** the track header's second row shows
   the pan beside the gain readout.
6. **Given** a project saved before this feature, **When** it is opened, **Then** every track
   is centred and the project plays as before.

---

### User Story 4 - Mute, solo and the master from one surface (Priority: P2)

From the mixer the musician solos a track to hear it alone, mutes another, and sets the master
volume on the master strip; the headers in the timeline show the same states, and the rules of
feature 009 hold: several tracks may be soloed, a track both muted and soloed is silent, every
toggle applies within 20 ms with a short fade.

**Why this priority**: mute and solo already exist in the headers; the mixer must agree with
them perfectly or it is a second truth. The master strip completes the console.

**Independent Test**: solo a track in the mixer, see the header's S light in the timeline,
unsolo it from the header and see the strip follow; mute two tracks from the mixer; drag the
master fader and see the transport bar's speaker state agree.

**Acceptance Scenarios**:

1. **Given** the mixer open, **When** the user taps Solo on one strip, **Then** only that track
   sounds, the strip's Solo lights, and the same track's S in the timeline header lights.
2. **Given** two soloed tracks, **When** the user taps Mute on one of them, **Then** that track
   is silent and the other soloed track still sounds.
3. **Given** the master strip, **When** the user drags its fader, **Then** the master volume
   changes as a percentage, the transport bar's speaker button and popup show the same value, and
   the change is one undo step.
4. **Given** a strip, **When** the user taps the track's name or number, **Then** the track
   becomes the selected track and the timeline scrolls so its lane is visible; **Given** a
   selected track in the timeline, **When** the mixer opens, **Then** its strip is visible.
5. **Given** the keyboard focus on a strip, **When** the user presses "M" or "O", **Then** the
   strip's track is muted or soloed as feature 009's keys do for the selected track.

---

### User Story 5 - Fits every window, pointer, keyboard and screen reader (Priority: P2)

The mixer works on a phone in one hand as a sheet over the timeline, on a phone held sideways as
a console under the toolbar, on a tablet, a foldable or a desktop window as a panel beside the
timeline that the user can show and hide, with a mouse (hover, tooltips, the scroll wheel on
faders and knobs), a keyboard (arrow keys on the focused control, the shortcut sheet) and a
screen reader (each fader and knob is an adjustable control that announces its value).

**Why this priority**: constitution Principle IV; the owner asked for a mobile-first mixer that
adapts to every screen size. It is P2 because the sheet and the panel are both needed for the
first release, but each of the three layouts is verifiable on its own.

**Independent Test**: take the screenshot set of FR-034 on both platforms; drive the mixer with
the keyboard alone; read three strips and the master with the screen reader.

**Acceptance Scenarios**:

1. **Given** a 360 dp phone window, **When** the mixer opens, **Then** the sheet shows at least
   three track strips and the master strip without clipping, each fader's throw is at least
   120 dp, the strips scroll sideways to reach the rest, and the toolbar and transport bar stay
   usable.
2. **Given** a phone held sideways (at least 600 dp wide, shorter than 480 dp), **When** the
   mixer opens, **Then** the console replaces the lanes between the toolbar and the transport
   bar, the VU dials are dropped so each fader keeps a throw of at least 120 dp, and the Mixer
   button closes it.
3. **Given** an expanded window (840 dp or wider and at least 480 dp tall), **When** the mixer
   opens, **Then** it is a panel beside the lanes with the full strips (meter, VU, pan, Solo,
   Mute, fader, name), the lanes keep at least 320 dp of width, both panes follow the same
   playhead, and the panel's shown or hidden state is remembered for the next project.
4. **Given** a pointer, **Then** every fader, knob and button shows a hover state and a
   tooltip, the wheel over a fader changes it in 0.5 dB steps and over a knob in 1 % steps, and a
   secondary click on a strip opens the track's menu.
5. **Given** a keyboard, **Then** "X" toggles the mixer, Tab reaches every strip's controls in
   order (Solo, Mute, pan, fader) and then the master, the arrow keys move the focused fader by
   0.5 dB and the focused knob by 1 %, Page Up and Page Down by 6 dB and 10 %, Home returns a
   fader to unity and a knob to the centre, and the shortcut sheet lists these under "Mixer".
6. **Given** a screen reader, **Then** each fader announces its track and value ("Lead Guitar,
   gain, −3.5 dB"), each knob its pan ("pan, L 35"), each meter its level on request and
   "clipped" when the indicator lights, and the master strip "Master volume, 80 percent".

---

### User Story 6 - Many tracks stay smooth (Priority: P3)

A project with many tracks keeps the mixer fluid: the strips scroll sideways as one row, the
master strip stays in view, the meters keep their rate, and the mixer opens and closes without a
visible stall while the project plays.

**Why this priority**: a mixer that stutters at twelve tracks is unusable at the moment it is
needed most; the targets are checked by the performance journey, after the behaviour above.

**Independent Test**: build a 24-track project from the demo and the fixtures, open the mixer
while it plays, scroll the strips end to end and watch the frame counters and the audio
counters of the labelled session.

**Acceptance Scenarios**:

1. **Given** a 24-track project playing, **When** the mixer is open and the strips scroll,
   **Then** the frame rate stays at the display's rate within the performance gate and the audio
   counters report no underrun, starvation or dropped meter frame.
2. **Given** the mixer open, **When** the user opens or closes it ten times while playing,
   **Then** each transition completes within 300 ms and playback is never interrupted.
3. **Given** the strips scrolled to the far end, **Then** the master strip is still pinned at
   the right edge and the first strips are one swipe away.

---

### Edge Cases

- A project with one track: one strip and the master; the strips do not stretch to fill the row.
- A project whose tracks exceed the visible strips on a tablet panel: the panel scrolls sideways
  inside its width; it never grows beyond 60 % of the window.
- A fader dragged while its track is muted: the gain changes, the meter stays at silence, the
  readout updates; unmuting reveals the new level.
- A fader dragged to the very bottom: the readout shows "−∞", the track is silent, the meter
  rests; dragging up again restores sound without a jump.
- Two fingers on the same fader: the first finger keeps the fader; the second is ignored.
- A finger that leaves the fader sideways while dragging: the drag continues (the throw is what
  counts), until the finger lifts.
- A knob turned past full left or full right: it stops at the end; no wrap-around.
- A track deleted while the mixer is open: its strip leaves with the track; a track added
  appears at the end with a centred knob and a fader at 0 dB.
- Tracks reordered in the timeline (if a later feature adds it): the strips follow the order.
- The transport playing while the mixer opens: no gap in the sound; the meters show live levels
  on the first frame they are visible.
- Playback stopped: the meters fall to silence within one second; the clip latches remain.
- The window resized across a breakpoint with the mixer open (desktop, foldable): the sheet
  becomes the panel or the console and back without losing the open state, the scroll position
  or a drag in progress.
- Tabletop or book posture (feature 005) with the mixer open: the sheet's half height aligns to
  the hinge where feature 005's split applies; nothing sits under the hinge.
- The largest font size: strip texts (number, readouts, name) scale and the strip widens;
  nothing is clipped and the fader keeps its throw.
- A screen reader user adjusting a fader: each step announces the new value; the hold and clip
  states are announced once, not every frame.
- An armed track whose input clips: the strip's clip indicator follows feature 010's rule (lit
  until disarmed or tapped) and is distinct from the master's.
- The master volume at 0 %: the master meter rests at silence and the strips' meters still show
  their tracks (the meters are before the master fader).
- A document written by a later version with an unknown mixer value: feature 009's unknown-field
  rule applies; the document opens and the value is kept.

## Requirements *(mandatory)*

### Functional Requirements

**The mixer surface**

- **FR-001**: The mixer MUST show one strip per track in the project's order, followed by the
  master strip, and MUST update the set of strips when tracks are added or removed; the strips
  are one row that scrolls sideways while the master strip stays pinned at the row's right edge.
- **FR-002**: Each strip MUST show, top to bottom: the track's number (its position in the
  project, never skipped), its level meter, its VU reading (where room allows, FR-025), its pan
  knob, its Solo and Mute toggles, its fader with the gain readout and dB scale, and its name;
  the strip MUST be tinted with the track's colour in the places the design shows (the base
  band and the lit meter).
- **FR-003**: The fader MUST set the track's gain over feature 009's range (silence to +6 dB,
  default 0 dB) with a unity mark at 0 dB, and MUST show the value as feature 009's readout
  does ("+0.8dB", "−2.4dB", "−∞"). The throw MUST be console-like: 0 dB at about 70 % of
  the way up, −12 dB near the middle, and the lowest fifth covering −40 dB to silence, so the
  range where mixing happens is the finest.
- **FR-004**: A drag that begins on a fader's handle MUST move the fader with the finger or
  pointer along the throw, MUST keep following until release even when the finger strays
  sideways, and MUST apply every intermediate value to the sound within 20 ms, smoothly, without
  audible steps. Two or more faders MUST be draggable at the same time by different fingers on a
  touch screen. A double tap on a handle MUST return the fader to 0 dB.
- **FR-005**: A completed fader or knob gesture MUST be one undo step, as feature 009 treats a
  gain drag (FR-035); every mixer change MUST be saved with the project under feature 009's
  save rules.

**Pan**

- **FR-006**: Every track MUST have a pan position from full left through centre to full right,
  default centre, stored in the project document; a document from before this feature MUST open
  with every track centred.
- **FR-007**: Pan MUST be controllable by the strip's knob (a vertical drag or a drag along the
  arc), by a "Pan" slider in the track's settings (feature 009's settings form), and MUST be
  shown as a readout in the expanded track header's second row beside the gain readout; the
  compact header MUST NOT change.
- **FR-008**: The pan readout MUST show "C" at the centre and otherwise a side and a percentage
  ("L 35", "R 100"); a release within 5 % of the centre MUST settle on the centre; a double tap
  on the knob MUST return it to the centre.
- **FR-009 (pan law)**: The pan MUST be applied with a constant-power (−3 dB) law so that a
  track keeps the same perceived loudness as it moves across the field and is heard on one side
  only at its extremes; pan changes MUST be smoothed so that no click or zipper is heard.

**Mute, solo and the master**

- **FR-010**: The strip's Solo and Mute toggles MUST be the same state as the header's S and M
  (feature 009 FR-024: several tracks may be soloed, a track both muted and soloed is silent,
  each toggle applies within 20 ms with a short fade), lit with the design's active colour, and
  the two surfaces MUST never disagree.
- **FR-011**: The master strip MUST show the master volume as a fader over feature 009's range
  (0 to 100 %, default 80 %) with a percentage readout, and the summed output's level meter
  (FR-012) with its own clip indicator; it MUST have no pan, Solo or Mute. The transport bar's
  speaker button and popup (009 D1) remain and show the same value.

**Meters**

- **FR-012**: Each strip MUST show its track's output level after the fader, pan and mute on a
  two-bar meter (left and right) with feature 010's scale and behaviour: silence (−60 dB) to
  full scale (0 dB), the body colour up to −6 dB and the warning colour above, a peak-hold mark
  that stays 1.5 s, and a clip indicator that lights when the level reaches full scale. The
  master strip's meter MUST show the summed output on the same scale before the device's
  limit. The master's clip indicator MUST stay lit until tapped or until playback next starts
  from a stop; a strip's playback clip indicator MUST do the same.
- **FR-013**: Each strip MUST show a VU reading: the track's average loudness over about 300 ms,
  on a scale from −20 to +3 VU where 0 VU corresponds to the reference level of −18 dB below
  full scale, drawn as the design's dial with a needle. It MUST move smoothly and MUST NOT
  twitch on single peaks.
- **FR-014**: While a track is armed (feature 010), its strip's meter MUST be the armed header's
  input meter (010 FR-005), with the same scale, hold and clip latch; when the track is
  disarmed the strip MUST return to its playback meter. The armed state and the arm control
  stay in the header; the strip does not add a second arm control.
- **FR-015**: All meters MUST refresh at the display's rate, lag the sound by no more than
  50 ms, fall to silence within one second of a stop, and MUST be read along the same per-frame
  path as the playhead (constitution Principle II); a meter MUST NOT make the strip recompose on
  every frame.

**Opening, closing and placement**

- **FR-016**: The mixer is part of the Timeline destination, not a destination of its own: the
  navigation, the toolbar and the transport bar stay as features 002 and 009 built them, and
  the transport bar stays visible and usable while the mixer is open.
- **FR-017**: The toolbar MUST carry a Mixer button (the design's sliders glyph; icon-only, with
  a tooltip) that opens and closes the mixer; it is a toggle whose state is visible. On
  keyboards "X" MUST do the same (FR-029).
- **FR-018 (compact, narrower than 600 dp)**: The mixer MUST be a bottom sheet over the lanes
  with a handle labelled "Mixer", opened by the button or by dragging the handle up from the
  transport bar's edge, closed by the button, a swipe down or Back. The sheet MUST have a half
  height (at least 240 dp, strips in a compact form: meter, Solo, Mute, fader, name) and a full
  height that leaves the toolbar visible (full strips); each fader's throw MUST be at least
  120 dp at either height. At 360 dp of width at least three track strips and the master MUST be
  visible at once.
- **FR-019 (600 to 839 dp)**: The compact rules apply with more strips visible.
- **FR-020 (landscape, at least 600 dp wide and shorter than 480 dp)**: The mixer MUST replace
  the lanes between the toolbar and the transport bar as a full-width console; the VU dials are
  dropped (FR-025) so the fader keeps a throw of at least 120 dp; the ruler and the lanes return
  when the mixer closes.
- **FR-021 (expanded, 840 dp or wider and at least 480 dp tall)**: The mixer MUST be a panel at
  the left of the lanes, as the tablet split screens show, with the full strips; its width MUST
  be what its strips need up to 60 % of the window, scrolling sideways beyond that; the lanes
  MUST keep at least 320 dp of width. The panel's shown or hidden state MUST be remembered on
  the device across projects and launches.
- **FR-022**: When the window crosses a breakpoint while the mixer is open (a resized desktop
  window, a fold, a rotation), the mixer MUST change form (sheet, console, panel) without losing
  its open state, its scroll position or a drag in progress.
- **FR-023**: Tapping a strip's number or name MUST select its track and scroll the timeline so
  the track's lane is visible; opening the mixer with a selected track MUST scroll the strips so
  that track's strip is visible.
- **FR-024**: Opening or closing the mixer MUST NOT interrupt playback or recording, and the
  meters MUST show live levels on the first visible frame.
- **FR-025**: Each strip MUST be at least 72 dp wide on touch screens and its fader handle at
  least 44 dp wide; when the window is too short for the full strip (the console in landscape,
  the sheet's half height) the strip MUST drop first the VU dial and then the pan knob (reachable
  in the track settings) before shortening the fader below 120 dp.
- **FR-026**: Nothing MUST overlap, clip or sit under system bars at any supported width,
  including the largest font size (Principle IV); the strip widens with its texts.

**Pointer, keyboard and screen readers**

- **FR-027**: Every fader, knob and toggle MUST show a hover state and a tooltip; the wheel or
  trackpad scroll over a fader MUST change it by 0.5 dB and over a knob by 1 % (011 FR-014);
  a secondary click on a strip MUST open the track's menu at the pointer (011 FR-012); a
  horizontal scroll gesture over the strips MUST scroll the row.
- **FR-028**: Keyboard focus MUST reach, in order, each strip's Solo, Mute, pan and fader and
  then the master strip's fader; the arrow keys MUST move the focused fader by 0.5 dB and the
  focused knob by 1 %, Page Up and Page Down by 6 dB and 10 %, Home MUST return a fader to
  0 dB and a knob to the centre (009 FR-057 steps); "M" and "O" MUST act on the focused strip's
  track as on the selected track.
- **FR-029**: "X" MUST toggle the mixer; the shortcut sheet (011 FR-022) MUST list "X", the
  fader and knob keys and "M"/"O" under a "Mixer" group.
- **FR-030**: Every fader and knob MUST be an adjustable control for screen readers with the
  track's name, the control's name and its value, each step announced; every meter MUST expose
  its level on request and announce "clipped" once when its indicator lights; the master strip
  MUST announce "Master volume" and its percentage; every control MUST carry a stable
  identifier for tests and the journey drivers.

**Engine behaviour the user hears**

- **FR-031**: Gain, pan and master changes MUST reach the sound within 20 ms, smoothed over a
  few milliseconds so that no click, step or zipper noise is heard at any speed of movement;
  mute and solo keep feature 009's 20 ms fade.
- **FR-032**: The output level the meters report MUST be the level that reaches the output: a
  track at 0 dB, centred and alone MUST meter at the level of its own audio within 0.5 dB; the
  master meter MUST read the sum.

**Scope**

- **FR-033**: This feature MUST NOT include: inserts, sends, EQ or any effect (feature 013, the
  "FX" dial of the design); automation of any mixer value; a limiter or any processing on the
  master beyond its volume; input gain or trim; a per-strip speaker or listen control; track
  reordering, grouping, buses or stereo width; a mixer destination in the navigation; pan on the
  compact header.
- **FR-034**: The feature MUST deliver screenshots of the sheet at half and full height on a
  360 dp phone, the console in phone landscape, the panel on a tablet, a foldable and a desktop
  window, with meters live, on Android and on the iOS simulator, as the earlier features did;
  the plan syncs the designed states to the Tembra Audio Stitch project.

### Performance instrumentation declared by this feature

- Journeys added: one new journey, `mixer`, on both platforms: open Projects, open the demo
  project from its card, play, open the mixer, drag the first strip's fader down and back up over
  two seconds, turn its pan knob to the left and back, solo the second track and unsolo it, mute
  the third and unmute it, scroll the strips to the master and back, drag the master fader down
  and back, close the mixer, undo until the project is as it was, pause and skip to the start.
  The `stress` journey MUST be extended to keep the mixer open with its meters live while the
  demo project plays with the metronome on and the timeline scrolls, so the per-frame cost of
  the meters is measured beside the playhead's.
- Sections added (the catalogue's timeline, transport and engine families): mixer open (the
  press to the first frame with live meters), mixer close, mixer frame (the per-frame cost of
  the visible strips' meters and VU readings), fader drag (the per-frame cost of a drag with
  the readout), pan turn, master drag, and pan apply (the engine's application of a pan change,
  for the C++ test target's timing rather than the device sessions).
- Counters added: master clips (blocks in which the master's sum exceeded full scale), meter
  frames dropped (meter readings the UI missed because a frame was late), and the peak callback
  load while the mixer is open (read from the existing callback-duration counters during the
  journey's mixer phase, so this one is not a new counter), beside feature 009's underruns and
  starvation.
- Anchors: the Mixer button, the sheet handle, each strip's fader, pan knob, Solo, Mute, meter
  and clip indicator, the master strip's fader, meter and clip indicator, the strips' scroll
  row and the track settings' pan slider carry stable identifiers for the journey drivers.
- Baseline profile: Timeline is a primary destination and this feature adds main interactions,
  so the Android baseline profile MUST be regenerated and committed in this feature (feature
  008's rule).
- After the merge the feature MUST be measured with a labelled session on every available
  device and simulator; the report is pull-request evidence. The startup family MUST NOT regress
  by more than the gate, the mixer frame section MUST stay within one frame at 24 tracks on the
  reference devices, and the counters MUST read zero underruns and zero dropped meter frames
  there.

### Key Entities

- **Track** (feature 009): gains one attribute, **Pan**, from full left through centre to full
  right, default centre; its gain, mute and solo are unchanged.
- **Pan Law**: the rule that turns a pan position into the two sides' levels at constant
  perceived loudness; a property of the engine, never edited by the user.
- **Track Level**: the live reading of a track's output after its fader, pan and mute: the two
  sides' current peak, held peak and clip latch, and the averaged (VU) level; telemetry, never
  saved.
- **Master Level**: the same reading for the summed output before the device's limit.
- **Mixer View State**: whether the mixer is open, its form (sheet at half or full height,
  console, panel), the strips' scroll position and a drag in progress; held while the project is
  open, never part of the project document. The panel's shown or hidden preference on expanded
  windows is a device setting.
- **Edit History** (feature 009): a fader drag, a knob turn, a toggle and a master drag are each
  one snapshot.
- **Project Document** (feature 009): carries the pan of every track; documents without it read
  as centred.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user who has never seen the app opens the mixer and sets the levels of three
  tracks and the pan of one within 60 seconds on a phone, without instructions (observed with
  five users; four of five succeed).
- **SC-002**: A fader, knob, master or toggle change is audible within 20 ms of the gesture and
  no click, step or zipper is audible at any speed of movement, measured on a loopback
  recording of a sweep from silence to +6 dB and from full left to full right.
- **SC-003**: Meters lag the sound by no more than 50 ms and refresh at the display's rate; a
  track at 0 dB, centred and alone meters within 0.5 dB of its own audio; the master meter reads
  the sum within 0.5 dB; a VU reading settles within 300 ms of a steady tone.
- **SC-004**: A track panned fully to one side is within 0.5 dB as loud as the same track
  centred, on a loopback recording summed to mono after the −3 dB law.
- **SC-005**: With 24 tracks playing and the mixer open, the frame rate stays within the
  performance gate on the reference devices and the session reports zero underruns, zero
  starvation and zero dropped meter frames; the mixer opens and closes within 300 ms.
- **SC-006**: Two faders moved at once with two fingers each follow their own finger; the gains
  recorded in the project equal the fader positions within 0.1 dB.
- **SC-007**: The screenshot set of FR-034 shows no clipped text, no overlap and no control
  under a bar at 360 dp, in phone landscape, on the tablet, the foldable and the desktop
  window, at the default and the largest font size.
- **SC-008**: Every mixer control is reachable and operable with the keyboard alone and with a
  pointer alone, and every fader, knob and toggle is announced with its track and value by
  TalkBack and VoiceOver.
- **SC-009**: Every mixer change survives undo, redo, save and reopen: a project changed in the
  mixer, saved and reopened plays identically (the same loopback recording within 0.1 dB).
- **SC-010**: The strips' Solo and Mute and the headers' S and M never disagree in 200
  randomised toggles across both surfaces (an automated test).

## Assumptions

- **What "VU" is.** A VU meter ("volume unit", a 1939 broadcast standard) averages the signal
  over about 300 ms and shows loudness as the ear hears it; the bar meter shows instant peaks so
  the user sees clipping. The owner likes the dial's look and asked what it is; the dial shows
  the averaged reading (FR-013) and the bars the peaks (FR-012), as feature 009 already noted
  ("[the mixer] gives each strip both"). 0 VU is placed at −18 dB below full scale, the common
  digital reference and the gain-staging target of the DAW internals brief.
- **Meters are after the fader, pan and mute** ("post-fader") so that what the user sees is
  what reaches the master; the armed track's input meter (feature 010) is the one exception and
  is pre-fader by nature.
- **Placement follows feature 009's reading of the design**: a sheet on phones, a console in
  phone landscape, a panel beside the lanes on expanded windows, and no mixer destination. The
  panel sits at the left as all three tablet screens show. The panel's shown or hidden state is
  a device preference like feature 009 D1's overview strip.
- **The fader's console-like throw** (0 dB at about 70 %, −12 dB near the middle) follows
  hardware consoles and most DAWs; feature 009's track-settings gain slider keeps its own law.
- **Pan is a percentage per side** ("L 35") rather than degrees or a 0–127 value, matching the
  master's percentage and the design's knob with a centre pointer.
- **The design's dB scale (+10 to −70) is drift**: feature 009 capped gain at +6 dB and showed
  "−∞" at silence; the scale marks follow that range.
- **Keys**: "X" for the mixer is free (009, 010 and 011 use space, Home, arrows, S, Delete,
  undo and redo keys, plus and minus, M, O, A, R, I, the modifier with 1 to 5 and the modifier
  with "/"); the fader and knob key steps are feature 009's (FR-057).
- **Multi-touch** is limited by the device; every supported phone and tablet reports at least
  two touch points.
- **The master stays a percentage** as feature 009 defined it; a dB master is not introduced.
- **The arm control stays in the header** (feature 010); the strip shows the armed state through
  its meter only, so the two features' controls are not duplicated.
- **The stress and mixer journeys use the demo project** plus the fixtures of feature 009; the
  24-track project of SC-005 is built from them by the journey driver.
- **Dark theme only, English only, left-to-right**, as in the earlier features.
- **Branch.** `012-mixer` was created from the 010 tip by the spec-kit git extension on
  2026-11-03 in the `next-spec` worktree, so the constitution v1.3.5 and feature 010's
  specification are in its history; it merges after 010 and 011.
