# Feature Specification: Timeline Arranger

**Feature Branch**: `009-timeline-arranger`

**Created**: 2026-10-13

**Status**: Draft

**Input**: User description: "The next I would like you to focus on the timeline functionality in the app. This is one of the largest pieces in the app because it allows us to create the project with multiple tracks to add tracks to mix them so them to split the track into multiple pieces like stems. It also shows the length of the track and allow to change the volume of the track like a general volume. It also displays the vertical line that goes with the track when I click on play button, and it should also show me the track length and while it's playing it change the seconds and milliseconds while it's playing, and it should show the total track length as well. The timeline should also allow me to use the metronome and set the configuration for the metronome. The timeline should also allow user to zoom in or Zoom out the tracks to see it more granularly. You can show some sort of a top section above the tracks that allows user to click on it then slice the selected track or user can use certain gesture to zoom in or zoom out on this panel and this panel should also display the bars and seconds or milliseconds or minute, depending on the zoom level. The tracks on the timeline should display or visualize the wave form of the recorded audio. Well, I add a track. I want to be able to name it or add a specific label for the instrument so I want to be able to select the label from the pre-fined list of the instruments. I would like to be able to modify the track settings, which includes applying a different types of effects. These piece will be implemented later, but I want to have the ability to open a different menu or open this track settings. The mobile design can display a different screen with effects and track settings, and the largest screen size is like a tablets or foldable. Revise can't expand the track and display those effects with additional parameters of each effect. So you can simply put a placeholder here as this functionality will be implemented and clarified and specified later. The timeline should also have the ability to open the mixer section. It can be like a bottom sheet or any other visual elements that will allow me to see the tracks like whether it's arranged or session view so in this mix of you, I want to be able to see the sliders to have the ability to enable or disable the track to see the sound spikes whether it's too loud or too quiet I want to be able to pan the track with it left or right I want to be able to solo or mute a track. I don't know what is VU and what it stands for, but it looks nicely as the widget in the mixer settings. You can check the existing Google stitch platform, and the designs and I will attach more screenshots of how these can look like. I would like you to make it mobile friendly app as this is the that targets, the iOS and android platforms both mobile phones, foldables and tablets. Make sure that you follow the same design principles and schema that is already used in the app. If you want to add a new library, whether is cross platform, C++ functionality or any other share components make sure that you do not break any license."

## What the design shows *(evidence for every decision below)*

Ten Stitch screens were reviewed element by element. From the Tembra Audio project
(`12040171611712589599`, re-checked against Stitch on 2026-10-13: the two arranger screens are
unchanged since the export): `timeline-arranger/phone` (portrait, 390 wide) and
`timeline-arranger/tablet` (1280 wide) for the timeline; `track-effects/phone` and
`track-effects/tablet` for where a track's settings and effects live; `projects-library/phone`
and `projects-library/tablet` for the project list and the transport bar they repeat. From the
user's second Stitch project (`13820816821466270236`, "Studio Sound" design system, pulled on
2026-10-13 as image-only screens into `design/stitch/screens/mixer-timeline/`): `phone-drawer`
(a phone timeline with a "Mixer" drawer at the bottom), `tablet-split-v1`, `tablet-split-v2` and
`tablet-split-console` (a mixer console beside a timeline, the last with studio console faders);
the user supplied them as mixer examples, so their mixer parts are evidence for feature 010 and
only their timeline parts are decided here. The metronome screens were decided in feature 004
and are only referenced. The DAW internals brief (`docs/research/daw-internals.md` §2 and §4,
summarised in `docs/DAW-App-Research-Report.md` §2 and §6) supplies the transport, tempo and
mixing behaviour used in the requirements and success criteria.

| Element | Where it appears | Verdict |
|---|---|---|
| Toolbar above the tracks: "+ Track" primary button; a scissors button titled "Split / Scissors"; a "Grid 1/16" pill; a zoom pill with a magnifier and "1x"; an "Arrange" pill | Both timeline screens | **Kept**: add track (FR-019), split (FR-032), grid picker (FR-018), zoom readout (FR-015; D1 replaced the readout with an always-visible zoom control, since the owner's verdict of 2026-10-27 the overview strip). The "Arrange" pill is **excluded**: it would switch to a second view (session or clip-launch) that does not exist yet |
| Headphones button and a muted-speaker button in the toolbar | Tablet timeline screen | **Excluded**: input monitoring belongs to the recording feature (011) and the second glyph has no stated meaning on either screen |
| Ruler: "BPM 124.0" and "4/4" at the left, bar numbers 1 to 10 with small beat ticks, a playhead marker (a rounded tab) above the vertical playhead line | Both timeline screens (the phone shows bars 1 to 8) | **Kept**: the ruler shows bars and beats and, as the user asked, a time scale whose labels follow the zoom (FR-013, FR-014); the tempo and time signature were shown and edited from the ruler's left end until D1 (2026-10-26) moved the tempo into the toolbar and the signature into the metronome sheet, since on a phone the two pills covered the first bars (FR-036, FR-037); the tempo is a whole number as in the metronome, the ".0" is drift |
| Track header: a colour dot, the name ("Lead Vocal", "Bass 808", "Perc / Loops", "Pad Filter"), "M" and "S" toggles titled "Mute" and "Solo Listen", a gain readout ("−0.5dB", "−2.4dB", "+0.8dB", "−3.2dB"), a "…" menu; the phone shows the name as "Track 1" followed by a chip "Lead Vocal" | Both timeline screens | **Kept** as the phone shows it: a free name and an instrument label chip from a fixed list (FR-020, FR-021), colour (FR-022), mute and solo (FR-024), gain (FR-023) and the track menu (FR-026) |
| Clips: rounded blocks tinted with the track colour, a file-name caption ("TAKE_01_VOX.WAV", "808_Sub_A.wav"), a waveform drawn inside; a track with four short identical clips | Both timeline screens | **Kept** (FR-027 to FR-030). The caption is the clip's name |
| Vertical playhead line through the ruler and every track | Both timeline screens | **Kept** (FR-009) |
| Expanded fourth track ("Pad Filter" with a chevron) showing an automation curve with "LPF Cutoff", "Resonance", "Drive" and a Speed/Ease/Blend tooltip | Both timeline screens | Automation is **excluded** (effects and automation feature). The **inline expansion itself is kept** for expanded windows as the place where a track's settings and the effects placeholder open (FR-042) |
| Right-hand "Categories" / "Bass loops" panel with a bank of loop tiles | Tablet timeline screen (a strip of the same tiles under the tracks on the phone) | **Excluded**: the loop library is the Pads feature |
| Transport bar at the bottom: a speaker glyph with a volume slider ("78%"); skip-to-start, rewind, play/pause and fast-forward buttons; the readout "00:00.00 / 00:42.20"; a metronome toggle reading "Off"; a speed pill "1x"; on the tablet projects screen also a loop button, a "preview bus" slider, "124 BPM", "DSP 4%" and "Offline cache OK" | Tablet timeline screen in full; the phone timeline and both projects screens show parts of it | **Kept** on Timeline and the track settings screen: master volume (FR-007; D1: a speaker button with a popup slider on phones), skip to start, step back and forward by one bar, play/pause (FR-004 to FR-006), the position and length readout (FR-010), the metronome toggle (FR-038). Playback speed, loop, the preview bus, the load and cache readouts are **excluded** (later features or drift). The transport bar is **not** shown on Projects in this feature (Assumptions) |
| Top bar with menu, logo, the project name ("Music project - 001") with a cloud glyph, help, sync, share and avatar | Both timeline screens | **Kept** only as feature 002 already decided it: the top bar shows the project name as the Timeline title (FR-002). Cloud, sync, share, help and the avatar are collaboration and are **excluded** |
| Bottom navigation (phone) and rail (tablet) with Timeline, Tuner, Effects, Pads, Remote, Projects | Both timeline screens | **Excluded**: the navigation was decided in feature 002 |
| Track effects screen: a header "Track 1: Lead Guitar" with an A/B switch and a preset menu, then a rack of effect cards (Distortion, Parametric EQ, Compressor, Studio Reverb) each with a power button and controls, and an "Insert FX plugin" button | Both track-effects screens | **Deferred**: this feature opens the place where this screen will live, with the track's settings at the top and a placeholder for the effects rack (FR-041 to FR-043); the effects themselves are the effects and automation feature |
| Projects: a title with a project count, a search field, filter chips ("All Projects (18)", "Recent", "Cloud Synced"), a "Sort by: Recently Modified" menu, a "New Project" button, a settings glyph | Tablet projects screen (the phone shows "Active & Recent", a count and a sort control) | **Kept** locally: the count, search by name and description, the "All" and "Recent" filters, sorting by last edited or by name, "New project" (FR-050, FR-053). "Cloud Synced" and the settings glyph are **excluded** |
| Project cards: badges ("Active session", "Synced", "Cloud synced", "8 stems", "Local draft / queued"), a relative time ("2m ago"), the name, a one-line description, a waveform or bar-chart overview (the active card with bar markers "1.1.00 … 17.1.00" and a playhead), a stats row (BPM, key such as "Dmin" or "F#maj", "Engine 48kHz / 24-bit" or "Audio spec 96kHz / 32-bit float", tracks or "2 studio guests"), a footer "4 Tracks · 142 MB", buttons "Resume", "Open Project", "Load Project", "Export Stems", "Sync Now" and a duplicate glyph; the phone card shows BPM, key and length "00:42.20" | Both projects screens | **Kept** locally: the "Active" and "Demo" badges, the relative time, the name, the description, the overview drawn from the project's audio, the stats row with tempo, key, length and track count, the footer with tracks and size on the device, "Resume" on the open project and "Open" on the others, and Duplicate in the card menu (FR-051, FR-052, FR-054). Cloud and sync badges and buttons, "Export stems", the engine or audio-spec stat (the same for every project) and guests are **excluded** |
| Project overview drawn as a 12-bar chart on the phone cards and as a waveform line on the tablet cards | Both projects screens | **Kept** as one overview of the mix drawn in the design's form for each width (FR-054) |
| Ruler with two rows: a bar row with a rounded lilac chip over the playhead showing the position, and a seconds row ("0, 1, 2, 3") with fine ticks; a vertical lilac playhead line with a rounded handle at the top | `mixer-timeline/phone-drawer`, `tablet-split-v2` | **Kept**: it is the two-row ruler and the ruler marker of FR-009, FR-013 and FR-014; the chip shows the playhead's bar and beat (FR-009) |
| Track headers with large square "S" and "M" buttons with rounded corners, tinted with the track colour, and the track name as the clip's caption | `phone-drawer` | **Kept** as the compact header's mute and solo (FR-024, FR-055); the tablet split screens number the headers "1…6" instead of naming them, the original project's names win |
| Horizontal "Pan L/R" slider in each compact header | `phone-drawer` | **Deferred to feature 010**: pan arrives with the mixer; the header's place for it is noted there |
| Clips drawn full-height in the track lane with rounded corners, the name as a caption on a darker band and a filled peak waveform in the track's tint; tints across the four screens: lilac, salmon, dusty rose, peach, mint and violet | All four mixer-timeline screens | **Kept** as the clip style and the track palette: six tints in creation order (FR-022, FR-028) |
| Top bar with a back chevron and "Project Alpha - Timeline" | `phone-drawer` | **Kept** as already decided: the title is the project name (FR-002); the back chevron is feature 002's navigation |
| Transport at the top (play, record, skip to start, rewind, fast-forward) with a "120 BPM / 4/4" pill; a bottom navigation with Mixer, Timeline, Library, Effects, Settings (or Home, Mixer, Play, Levels, Settings) | The three tablet split screens | **Excluded**: the transport bar stays at the bottom as the Tembra Audio project and the constitution place it, record is feature 011, and feature 002 decided the navigation; the tempo and signature pill confirms the ruler's readouts (FR-036, FR-037) |
| "Mixer" drawer handle at the bottom of the phone timeline with a master fader, a dual-colour LED meter and an "FX" dial; mixer console strips (number, dual meters, VU dial, pan knob, Solo and Mute, long-throw fader with a dB scale from +10 to −70 and a unity mark, speaker button, name) | `phone-drawer` and the three tablet split screens | **Deferred to feature 010** as its design evidence: a bottom sheet on phones and a console beside the timeline on tablets |

Export artefacts to be aware of: the tablet timeline `index.html` carries a bottom bar that
contradicts the rail feature 002 built; the track-effects screens title the destination
"Effects", which is the global Effects destination, not a track's settings; the tablet projects
screen titles itself "Tembra Audio Cloud Library" with a "Pro tier" badge, which is
collaboration; the second project's screens carry iOS status bars, a top transport and their own
bottom navigation and use the "Studio Sound" tokens, not Tembra Audio's. All are drift; the
Tembra Audio project's `DESIGN.md` and `theme.json` remain the source of truth for every
colour, and the plan maps the six tints onto its tokens.

## Where this feature sits in the build order

The constitution's build order lists, after the metronome: **audio spine** (the engine playing
one clip on both platforms), **transport and timeline**, **mixer**, **effects and automation**,
**recording and loops and speed**, **collaboration**. The user's request spans the spine, the
timeline, the mixer and (from the clarification session) recording, and asked for the cut that
works best. The cut taken:

- **This feature (009)**: the audio spine and the transport and timeline as one feature. The
  spine has no user-facing form of its own; its proof is a project that plays. It includes the
  tracks with gain, mute and solo in their headers, clip editing, the ruler, the metronome locked
  to the project, the track settings with the effects placeholder, audio import, the demo project
  and the full local Projects library.
- **Feature 010, Mixer**: the mixer sheet and panel with faders, pan, peak and averaged (VU) meters
  with clip indicators and the master strip. It reuses this feature's tracks and transport and
  adds pan to the track settings and headers. Its design evidence is already pulled: the four
  `mixer-timeline` screens listed above.
- **Feature 011, Recording**: the microphone as a source for a track (arming, recording during
  playback, input metering, monitoring, latency alignment). It comes after the mixer because it
  needs the mixer's meters, and before effects because the user asked for it now.

The build order therefore needs one amendment through `/speckit-constitution` before
`/speckit-plan`: merge "audio spine" and "transport and timeline" into one step delivered by
this feature, and move "recording" before "effects and automation" (loops and speed stay where
they are). The amendment names this specification.

## Clarifications

### Session 2026-10-13

- Q: Should this one feature deliver the audio spine, the transport and timeline, and the mixer
  sheet, with the constitution amended, or should the mixer be cut into the next feature? → A:
  Any order that works best; splitting is allowed. Cut taken: spine plus transport and timeline
  here, the mixer as feature 010 (the headers keep gain, mute and solo; pan and meters move to
  010), recording as feature 011 ("Where this feature sits in the build order").
- Q: Where does audio come from, since recording is a later feature? → A: A demo project; import
  of WAV, FLAC and MP3 files into the selected track or into a new track; and the microphone as
  a recording source for a track. Recording is specified as feature 011; this feature ships the
  demo project and the import (FR-044, FR-045).
- Q: How much of the Projects destination belongs here? → A: The full design, local only
  (option C): the list with search, filters, sorting, cards with description, overview, key,
  length, track count and size, and the actions; the project document must be built with good
  abstractions and data structures so that it can later be saved to a remote service unchanged
  (FR-049 to FR-054).
- Q: When the user navigates from Timeline to another destination inside the app while a
  project is playing, what should happen to playback? → A: It stops (pauses, position kept)
  whenever the user leaves Timeline or its track settings screen for any other destination,
  after a confirmation dialog that asks whether to stop playback and leave; cancelling stays on
  Timeline with playback running. Locking the screen or switching apps keeps playback running
  without a dialog (FR-008, FR-040).
- Q: What should a project keep for an imported audio file: the file exactly as it was, or a
  larger playback-ready copy? → A: The original file, unchanged in format and size, and the
  project session plays from it; anything the app needs for fast playback or waveforms is a
  rebuildable cache that is never part of the project (FR-044, FR-048, FR-049).
- Q: On a touch screen, what should a one-finger drag that starts on a clip do? → A: A plain
  drag on a clip scrolls the timeline; moving a clip requires a long press on it first, then
  dragging without lifting the finger (a lifted long press just selects). With a pointer, a
  drag on a clip moves it and the wheel or trackpad scrolls (FR-016, FR-031, FR-033).
- Q: While the user drags the playhead marker in the ruler during playback, should the audio be
  heard following the finger, or stay silent until the finger lifts? → A: Silent during the
  drag: the readout and playhead follow the finger, playback resumes from the release point (or
  stays paused if it was paused); no audible scrubbing in this feature (FR-011).
- Q: When the user picks several audio files at once in the file picker, what should happen? →
  A: "Add audio as new track" allows several files: each becomes its own new track named after
  the file, in picker order, with its clip at the playhead, as one undo step; "Add audio…" to
  an existing track still takes one file (FR-044).
- Q (analysis, 2026-10-13): FR-003 placed the grid and the zoom level inside the project while
  FR-049 keeps view conveniences outside the document — which wins? → A: FR-049. Zoom and grid
  are remembered per project on the device, outside the document (FR-003, FR-015, FR-018,
  FR-047). Also settled by the same pass: the readout and the ruler truncate to hundredths,
  never round (FR-010); a file larger than 500 MB is refused before decoding with its own reason
  (FR-044); the library-open section measures the Projects list as it is, with no seeded
  project count.

### Session 2026-10-26 (design iteration D1: the owner's review of the release build on every target)

- Q: On a phone the tempo and signature pills at the ruler's left end cover the first bars. →
  A: The ruler shows bars and time only. The tempo pill moves into the toolbar at every width
  (on phones between redo and the overflow menu; "Add audio…" goes into the overflow menu, as
  FR-055 already says); the time signature leaves the ruler and the toolbar and is chosen in
  the metronome sheet, which also shows the tempo (FR-013, FR-036, FR-037, FR-039, FR-055).
- Q: The master volume slider takes much of a phone's transport bar. → A: Below 600 dp the bar
  shows a speaker button whose state is the percentage; tapping it opens a popup above the bar
  with the horizontal slider and the percentage, closed by a tap outside, Escape or Back; from
  600 dp the slider stays inline (FR-007).
- Q: "Grid: Bar" changes nothing visible. → A: The picker reads "Snap" with its value; the snap
  lines are drawn in the track area at the picked resolution in a stroke distinct from the
  faint density grid, so a change is visible at once; "Off" draws none (FR-018).
- Q: Zoom in and out sit in the overflow menu on phones and the pinch is easy to miss. → A: Two
  always-visible zoom controls are built for a comparison on different devices, chosen per
  device from the overflow menu's "Zoom control" entry: "−" and "+" buttons at the ruler's right
  end with the level between them (the default; a tap on the level fits the project), or an
  overview strip above the transport bar (the whole project with its clips as blocks, the
  playhead and a draggable window for the visible span whose edges zoom). The owner keeps one;
  the other is removed afterwards (FR-015, FR-055); settled on 2026-10-27, in the session below.
- Q: The playhead marker's text is barely visible. → A: Text in a theme colour that contrasts
  with the chip at 4.5:1 or better, and a 1 dp outline on the chip (FR-009).
- Q: There is no way to record into a track. → A: Out of scope here (FR-064). The owner chose
  recording as the next feature, before the mixer, as its own spec with its own input level
  meter on the track header; the constitution's order is amended for it.

### Session 2026-10-27 (design iteration D1, the zoom verdict)

- Q: Which of the two zoom controls stays? → A: The owner: "I prefer the strip more, however I
  would like you to add a little padding from left and right sides as there can be a back
  navigation gesture happening on the screen and it will be hard to tap on the strip edge to
  change it. Make it also configurable from settings, so it can be displayed or hidden." The
  overview strip stays and the zoom buttons at the ruler's right end are removed, with the
  per-device "Zoom control" choice. The strip is inset from the window's sides by at least
  16 dp, and by the system's back-gesture zone where that is wider, so either end of the
  window can be grabbed without starting a back swipe. The app has no settings screen; the
  Timeline's per-device choices live in the toolbar's overflow menu, so its "Overview strip"
  entry shows or hides the strip, shown by default and remembered on the device. With the strip
  hidden the pinch, the wheel with the modifier, the trackpad pinch and the "+" and "−" keys
  still zoom (FR-015, FR-055).

## User Scenarios & Testing *(mandatory)*

The users of this feature are musicians arranging a song from imported audio on a phone held in
one hand, on a tablet or foldable on a desk, or in a desktop-mode window with a mouse and
keyboard. The feature turns the Timeline placeholder from feature 002 into a working arranger: a
project with tracks and audio clips laid out against bars and time, a transport that plays them
together with a moving playhead and a live time readout, a ruler that zooms from the whole song
to a single beat, clips that can be split and moved, tracks with names, instrument labels, gain,
mute and solo, the metronome clicking to the project's tempo, the place where a track's effects
will later live, and a Projects library where every project is kept on the device and reopens as
it was left.

### User Story 1 - Play a project and watch the playhead (Priority: P1)

A musician opens Timeline. The demo project is there: a few tracks with clips. They press play
in the transport bar; all clips sound together, in time, through the speaker or headphones. A
vertical line sweeps across the ruler and the tracks at the position being heard, and the
readout counts up "00:00.00 / 00:42.20" while it plays: position on the left, the length of the
whole project on the right. Pressing pause stops the sound where it is; pressing skip-to-start
returns to the beginning; tapping the ruler puts the playhead where they tapped and playback
continues from there. The master volume slider in the transport bar turns everything up or
down. Locking the phone does not stop the music.

**Why this priority**: Playing several clips together with a playhead that tells the truth is
the audio engine's first real job and the foundation of every other story. Nothing else in the
feature has value without it.

**Independent Test**: Open the demo project on each platform, press play, record the output and
the screen for the full length of the project; confirm every clip starts at its position, the
clips stay in sync to the end, the playhead and the readout follow the audio, and pause, skip
to start and seek behave as specified. Delivers a working multitrack player.

**Acceptance Scenarios**:

1. **Given** Timeline is opened for the first time, **When** it appears, **Then** the demo
   project is shown with its tracks, clips and waveforms, the transport bar reads
   "00:00.00 / <project length>", the play button shows play, the metronome toggle is off and
   the master volume is at its default.
2. **Given** a project with clips on several tracks, **When** the user presses play, **Then**
   sound begins within a tenth of a second, every clip starts at its own position, the playhead
   line moves smoothly across the ruler and every track, and the position readout counts up in
   minutes, seconds and hundredths while the length readout stays fixed.
3. **Given** playback is running, **When** the user presses pause, **Then** the sound stops at
   once with no click, the playhead stays where it was, the button shows play, and pressing play
   again continues from that position.
4. **Given** the playhead is anywhere, **When** the user presses skip-to-start, **Then** the
   playhead moves to the beginning; if playback was running it continues from there without a
   gap.
5. **Given** playback is running or stopped, **When** the user taps the ruler at bar 5,
   **Then** the playhead jumps to bar 5 (snapped to the grid when the grid is on), the readout
   shows the matching time, and running playback continues from bar 5 within a tenth of a
   second with no artefact.
6. **Given** playback is running, **When** the user drags the master volume slider,
   **Then** the loudness follows the slider smoothly with no steps or crackle, and the
   percentage readout updates while dragging.
7. **Given** playback is running, **When** the screen locks or another app comes to the front,
   **Then** playback continues until the user stops it or a call or another app takes the
   audio; **When** the user chooses another destination inside the app, **Then** a dialog asks
   whether to stop playback and leave: "Stop and leave" pauses at the current position and
   navigates, "Keep playing" stays on Timeline with the music running.
8. **Given** the playhead reaches the end of the last clip, **When** playback continues,
   **Then** it keeps running in silence (so the metronome can keep clicking) until the user
   stops it, and the position readout keeps counting past the project length.

---

### User Story 2 - Build the project from tracks (Priority: P1)

A musician starts a new project, taps "+ Track", and a new track appears with the name
"Track 1" and the next colour. They open its settings, rename it "Verse guitar" and choose
"Guitar" from the instrument list; the header now shows the name and a "Guitar" chip. They add
audio to the track from a file on the device; the clip appears at the playhead with its
waveform. For the bass they choose "Add audio as new track" and a track named after the file
appears with the clip already on it. They turn the first track's gain down a little from the
header, mute the second to hear the first alone, then solo the second to hear only it. A track
they no longer need goes through its menu: move up, move down or delete.

**Why this priority**: Tracks are what a project is made of; naming and labelling them, giving
them audio and balancing them from the header is the user's first stated need.

**Independent Test**: Create a project, add four tracks, name and label each, import a clip on
each (two through the track, two as new tracks), set a gain per track, mute and solo
combinations, reorder and delete one; confirm the headers show every change, playback reflects
gain, mute and solo, and the project reopens identically after the app is relaunched.

**Acceptance Scenarios**:

1. **Given** an empty project, **When** the user taps "+ Track", **Then** a track named
   "Track 1" with no instrument label, the first palette colour, 0 dB gain, not muted and not
   soloed appears at the bottom of the track list and is selected; a second tap adds "Track 2"
   in the second colour.
2. **Given** a track, **When** the user renames it to "Verse guitar" and selects "Guitar" from
   the instrument list, **Then** the header shows "Verse guitar" with a "Guitar" chip, the
   project is saved, and the list offered contains exactly the labels of FR-021.
3. **Given** a selected track, **When** the user chooses "Add audio…" and picks a supported
   file, **Then** a clip named after the file appears on that track starting at the playhead,
   its waveform draws (progressively for long files), the project length readout grows to the
   end of the clip if it is now the last, and the file plays back at its original pitch and
   speed whatever its sample rate or channel count.
4. **Given** any project, **When** the user chooses "Add audio as new track" and picks a file,
   **Then** a new track named after the file (without its extension) is added at the end with
   the clip on it at the playhead and is selected; **When** the user picks three files at once,
   **Then** three new tracks are added in the picker's order, each with its clip at the
   playhead, the last one selected, and one undo removes all three; **When** no track is
   selected and the user chooses the toolbar's "Add audio…", **Then** it behaves as "Add audio
   as new track".
5. **Given** several tracks, **When** the user changes a track's gain from its header,
   **Then** the readout shows the value in dB with one decimal (for example "−2.4dB"), the
   loudness of that track follows during playback without steps, and the settings show the same
   value.
6. **Given** several tracks playing, **When** the user mutes one, **Then** it goes silent at
   once and its "M" shows active; **When** the user solos another, **Then** only soloed tracks
   are heard and the "S" shows active; **When** several tracks are soloed, **Then** all of them
   are heard; **When** a muted track is also soloed, **Then** it stays silent.
7. **Given** three tracks, **When** the user chooses "Move up" on the third, **Then** it becomes
   the second and the clips move with it.
8. **Given** a track with clips, **When** the user chooses "Delete track" and confirms,
   **Then** the track and its clips disappear, playback continues with the remaining tracks, and
   undo brings the track back with its clips, settings and position.

---

### User Story 3 - Find the way around time: ruler, zoom and grid (Priority: P1)

The ruler above the tracks shows bar numbers with beat ticks and, under them, the time in
minutes and seconds. The musician pinches on the ruler or the tracks to zoom in; the beat ticks
become labelled beats, then subdivisions, and the time labels change to seconds, then tenths and
hundredths. Pinching out shows the whole song at once with only bars and minutes labelled. The
overview strip above the transport bar shows the whole song with a window over the part in
view; a scroll wheel with the modifier key or a trackpad pinch does the same with a mouse. Scrolling sideways moves along the song and, during playback, the view
follows the playhead. The grid picker sets the snap for taps on the ruler, playhead drags and
clip moves to a bar, a quarter, an eighth, a sixteenth or off.

**Why this priority**: The user asked for the ruler, the zoom and the adaptive labels
explicitly; without them the timeline cannot be read or edited at any precision.

**Independent Test**: At every zoom level from widest to closest, capture the ruler and confirm
the labels match the rules of FR-014; zoom with pinch, wheel and trackpad; scroll during
playback and confirm the view follows; set each grid value and confirm taps and drags snap to
it. Delivers a navigable timeline.

**Acceptance Scenarios**:

1. **Given** a project opened at the default zoom, **When** the ruler is read, **Then** it
   shows bar numbers from 1 with a tick per beat and a second row of time labels in
   minutes:seconds, the bar labels and the time labels never overlap one another, and the
   tracks' clips are drawn to the same scale.
2. **Given** any zoom level, **When** the user pinches on the ruler or on the track area,
   **Then** the timeline zooms around the pinch centre, the clips and waveforms rescale with it,
   the labels change density by the rules of FR-014, and the overview strip's window resizes; the pinch
   stops at the widest level (the whole project, at least 32 bars, fits the window) and at the
   closest (one beat spans at least the window's width).
3. **Given** a pointer device, **When** the user turns the scroll wheel with the platform's
   modifier key held or pinches on a trackpad over the timeline, **Then** the timeline zooms
   around the pointer; without the modifier the wheel scrolls the timeline sideways; vertical
   scrolling moves through the tracks.
4. **Given** playback running with the playhead reaching the right edge, **When** it continues,
   **Then** the view pages so the playhead stays visible; **When** the user scrolls the view
   away by hand, **Then** it stops following until the user presses skip-to-start, taps the
   ruler or restarts playback.
5. **Given** the grid is "1/8", **When** the user taps the ruler between two eighths, **Then**
   the playhead lands on the nearer eighth; **When** the grid is "Off", **Then** it lands exactly
   where the user tapped.
6. **Given** any zoom, **When** the user drags the playhead marker in the ruler, **Then** the
   playhead follows the finger (snapped when the grid is on), the readout updates while
   dragging, nothing is heard during the drag, and releasing resumes playback from the released
   position if it was running before the drag (or leaves it paused there if it was paused).

---

### User Story 4 - Split, move and delete clips, and undo it all (Priority: P2)

A musician taps a clip to select it; it shows a selection outline. They tap the ruler to put
the playhead inside the clip and press the scissors; the clip becomes two clips that play
exactly as before. They drag the second part later on the same track (it snaps to the grid and
stops against its neighbour), or up onto another track where there is room. A part they do not
want goes with "Delete clip". Every edit can be undone and redone from the toolbar.

**Why this priority**: Splitting a track into pieces is the editing the user named; moving and
deleting are what one does with the pieces, and undo is what makes editing safe.

**Independent Test**: Split a clip at several positions and compare the rendered output before
and after (identical to the sample); move clips with every grid setting and across tracks;
delete; undo and redo each step and confirm the arrangement and the output are restored.
Delivers a clip editor.

**Acceptance Scenarios**:

1. **Given** a clip on a track, **When** the user taps it, **Then** it is selected (an outline
   in the design's selection style) and the scissors becomes available; tapping empty track area
   or another clip changes the selection; **When** the user drags a finger across a clip without
   a long press, **Then** the timeline scrolls and the clip stays put; **When** the user long
   presses a clip and keeps the finger down, **Then** the clip lifts and follows the finger.
2. **Given** a selected clip crossed by the playhead, **When** the user presses the scissors,
   **Then** the clip becomes two clips meeting at the playhead (snapped when the grid is on),
   each with its own waveform and the original name, and playback across the cut is identical to
   the uncut clip; **When** the playhead is at a clip edge or outside the selected clip,
   **Then** the scissors does nothing and is shown as unavailable.
3. **Given** a clip move in progress (long press and drag on touch, press and drag with a
   pointer), **When** the user moves it sideways, **Then** it follows with its waveform, snaps
   its start to the grid, cannot pass over a neighbouring clip on the same track (it stops
   against it), cannot start before the beginning, and the project length readout updates on
   release.
4. **Given** a clip move in progress, **When** the user moves it onto another track, **Then** it
   moves to that track at the same position if the span is free, otherwise it returns to where
   it was; the clip takes the colour of its new track.
5. **Given** a selected clip, **When** the user chooses "Delete clip" (menu, delete key or
   backspace), **Then** it disappears and the project length is recalculated.
6. **Given** a sequence of edits (add track, import, split, move, delete, gain change, rename,
   tempo change), **When** the user presses undo repeatedly, **Then** each edit is reverted in
   reverse order, including during playback, and redo reapplies them; at least 50 steps are
   kept; undo and redo are unavailable when there is nothing to undo or redo.

---

### User Story 5 - Play along with the metronome at the project tempo (Priority: P2)

The project runs at 124 BPM in 4/4, shown at the left end of the ruler. The musician turns the
transport bar's metronome toggle on and presses play; the click from the Metronome destination
(its sound, subdivision, accents and style) sounds in time with the clips at the project's
tempo, the bar numbers in the ruler and the beats of the click agree, and the accent falls on
beat 1 of each bar. Tapping the tempo readout opens the tempo control; the clips keep their bar
positions as the tempo changes. Long-pressing the metronome toggle (or opening it from the
toolbar's menu) shows the metronome's own settings for the click without leaving the timeline.

**Why this priority**: The user asked for the metronome from the timeline with its
configuration; the constitution already decided it is the same click driven by the project's
tempo map.

**Independent Test**: Turn the metronome on, play a project whose clip starts on bar 1, record
the output and confirm every click lands on its beat relative to the clip for ten minutes;
change the tempo and time signature and confirm the ruler, the click and the clip positions all
follow; change the click's sound and subdivision from the timeline and confirm the Metronome
destination shows the same settings. Delivers tempo-locked play-along.

**Acceptance Scenarios**:

1. **Given** a project, **When** the ruler is read, **Then** its left end shows the project
   tempo as a whole number with "BPM" and the time signature (for example "124 BPM", "4/4"),
   and both are tappable.
2. **Given** the metronome toggle is off and playback runs, **When** the user turns the toggle
   on, **Then** the click starts on the next beat at the project tempo with the accent on beat 1
   of the ruler's bars, using the sound, subdivision, accent pattern and style currently set in
   the Metronome destination; turning it off stops the click on the next beat; the toggle's
   state is remembered per project.
3. **Given** the metronome toggle is on and playback is stopped, **When** the user presses play,
   **Then** the first click and the first audio start together, and every later click is within
   a millisecond of its beat relative to the audio.
4. **Given** the tempo readout is tapped, **When** the user sets a new tempo with the tempo
   control (the metronome's dial, steppers, slider and tap tempo) between 40 and 240,
   **Then** the ruler redraws with bars at the new spacing, every clip keeps its bar position
   (so its time position changes) while its audio plays at its original speed, the project
   length readout updates, the running click adopts the tempo on its next tick, and the change
   is one undoable step.
5. **Given** the time signature is tapped, **When** the user chooses one of the metronome's six
   signatures, **Then** the ruler's bars and beat ticks follow, the click's accent pattern
   follows the metronome's rules for that signature, and clip positions in beats are unchanged.
6. **Given** the metronome toggle is long-pressed (or the toolbar menu's "Metronome settings"
   chosen), **When** the sheet opens, **Then** it offers the Metronome destination's sound
   picker, subdivision, accent cells and style with the tempo and time signature shown
   read-only as the project's; a change made here is visible in the Metronome destination and
   the other way round.
7. **Given** the user chooses the Metronome destination while playing and confirms "Stop and
   leave", **When** the destination opens, **Then** project playback and its click have
   stopped and the standalone metronome shows its own tempo, unchanged by the project.

---

### User Story 6 - Open a track's settings and the place for its effects (Priority: P3)

From a track's menu (or by tapping its header's chevron on a tablet), the musician opens the
track's settings. On a phone a new screen opens over the timeline with the transport bar still
at the bottom: the name field, the instrument list, the gain control, mute and solo, delete,
and below them an "Effects" section that says effects are coming and offers nothing yet. On a
tablet or an open foldable the same content opens inline beneath the track header, pushing the
tracks below it down, with a chevron to collapse it. Back returns to the timeline exactly as it
was.

**Why this priority**: The user asked for the entry point now and the effects later; the entry
point costs little and fixes where the next features' work goes on each window size (pan in
feature 010, effects later).

**Independent Test**: Open the settings for a track at compact width (screen) and at expanded
width (inline), change every setting and confirm the header follows; confirm the effects
section is a clearly labelled placeholder with no controls; collapse, navigate back and rotate
and confirm the state survives.

**Acceptance Scenarios**:

1. **Given** a window narrower than 840 dp, **When** the user chooses "Track settings" from a
   track's menu, **Then** a screen titled with the track name opens with the name field, the
   instrument list, the gain control with its readout, the mute and solo toggles, a "Delete
   track" action and an "Effects" section containing one sentence saying that effects arrive in
   a later version; the transport bar remains at the bottom and keeps working; back returns to
   the timeline at the same scroll and zoom.
2. **Given** a window 840 dp or wider, **When** the user taps a track header's chevron or
   chooses "Track settings", **Then** the same content expands inline under that header, the
   tracks below move down, only one track is expanded at a time, and the chevron collapses it;
   the expansion state survives rotation and is not saved in the project.
3. **Given** the settings are open anywhere, **When** the user edits the name, label, gain,
   mute or solo, **Then** the header and playback reflect the change at once, and each change
   is an undoable step (name and label edits are one step per field).
4. **Given** the window is resized across 840 dp while settings are open (a foldable opening or
   a desktop window), **When** the layout changes, **Then** the settings move between the screen
   and the inline form with the edited values intact.

---

### User Story 7 - Keep projects: the library (Priority: P3)

The Projects destination is the library of the projects on the device. Each card shows the
project's name, its one-line description, a small overview of its audio, its tempo, key,
length and track count, when it was last edited and how much space it takes; the open project
carries an "Active" badge and a "Resume" button, the others "Open". A search field finds
projects by name or description, chips filter all or recent projects, and a sort menu orders
them by last edited or by name. "New project" creates an empty project and opens it in
Timeline; a card's menu renames it, edits its description and key, duplicates it or deletes
it. Everything the musician does in Timeline is saved as they go; relaunching the app opens the
project they were last working on, stopped, at the start. The project itself is one portable
document, so that a later feature can send it to a server and get it back unchanged.

**Why this priority**: The user said "create the project" and chose the full library; without a
way to make, keep and return to projects the timeline's work is lost at the next launch. It is
last because every other story can be exercised on the demo project.

**Independent Test**: Create three projects with different content, describe and key them,
duplicate one, rename one, delete one, search and sort, relaunch the app and confirm the
remaining projects reopen identically and the last opened one is shown; force-quit during an
edit and confirm nothing older than two seconds is lost; export a project document as text and
confirm it contains no device paths and reads back into an identical project. Delivers
persistence and the library.

**Acceptance Scenarios**:

1. **Given** the app's first launch, **When** Projects is opened, **Then** it shows the count
   "1 project", the demo project's card with a "Demo" badge, its name, description, overview,
   "124 BPM", its key, its length, "3 tracks" (or more), its size and its last-edited time, and
   a "New project" action.
2. **Given** Projects, **When** the user chooses "New project", **Then** a project named
   "Project N" (the next free number) at 120 BPM, 4/4, with no description, no key and no
   tracks, is created, listed and opened in Timeline with an empty track area that invites
   adding a track or audio.
3. **Given** a card, **When** the user taps it or its "Open" ("Resume" on the active project),
   **Then** Timeline opens it stopped at the start with its tracks, clips, gains, mutes, solos,
   zoom, grid and metronome toggle as last saved; the top bar shows its name; the card now
   carries the "Active" badge.
4. **Given** a project is open, **When** any edit is made, **Then** it is saved within two
   seconds without any action by the user, and a crash or force-quit loses at most the edits of
   the last two seconds.
5. **Given** a card's menu, **When** the user renames the project, edits its description or
   picks a key, **Then** the card and (for the name) the top bar show the change; **When** the
   user duplicates it, **Then** a copy named "<name> copy" with the same content appears and
   plays identically; **When** the user deletes it and confirms, **Then** it and its audio
   copies are removed from the device, and deleting the open project opens the most recently
   edited remaining project or a new empty one if none remains.
6. **Given** twenty projects, **When** the user types in the search field, **Then** the list
   narrows as they type to projects whose name or description contains the text; **When** the
   "Recent" chip is selected, **Then** only projects edited in the last seven days are shown;
   **When** the sort is changed to "Name", **Then** the cards reorder alphabetically.
7. **Given** a project whose audio file copy is missing or unreadable, **When** it opens,
   **Then** the affected clips are shown as unavailable (greyed, named, no waveform) and
   silent, everything else works, and the project is not altered by opening it.
8. **Given** any saved project, **When** its document is read as text, **Then** it contains a
   format version, a stable identifier for the project, every track and every clip, all
   positions in beats, and references to audio by a stable content identifier with the audio's
   format facts, and contains no device path, cache state or anything else specific to the
   device; **When** the same document is read back on the other platform with the audio copies
   present, **Then** it opens as an identical project.

---

### User Story 8 - Fits every window, pointer, keyboard and screen reader (Priority: P3)

The same timeline and library work on a phone in portrait and landscape, on a closed and an
open foldable, on a tablet in both orientations and in a resizable desktop-mode window, with
fingers, a mouse, a trackpad, a keyboard and a screen reader.

**Why this priority**: Required by the constitution for every screen; it does not change what
the timeline does, only that everyone can use it.

**Independent Test**: Render Timeline, the track settings and Projects at 360, 600, 840 and
1200 dp widths and in phone landscape on Android and iOS; operate every control with a pointer
(hover and tooltips), a keyboard (focus order, shortcuts) and the screen reader; confirm nothing
clips, overlaps or hides behind the transport bar.

**Acceptance Scenarios**:

1. **Given** a window narrower than 600 dp in portrait, **When** Timeline is shown, **Then**
   the toolbar, the ruler, the track list and the transport bar fit without horizontal
   scrolling of the frame (only the timeline content scrolls), track headers are compact
   (colour, name, label chip, M, S, menu) with gain in the settings, at least three tracks are
   visible above the transport bar, and the transport bar never covers a track.
2. **Given** a window 840 dp or wider, **When** Timeline is shown, **Then** the track headers
   form a fixed column at least 200 dp wide with the gain readout visible, the ruler and clips
   scroll together beside them, and settings expand inline; **When** Projects is shown,
   **Then** the cards form two or more columns.
3. **Given** a window at least 600 dp wide and shorter than 480 dp (a phone held sideways),
   **When** Timeline is shown, **Then** the ruler, at least two tracks and the transport bar
   are visible.
4. **Given** a pointer, **When** it hovers a clip, a track control, a toolbar button, a
   transport button or a project card, **Then** the control shows a hover state and icon-only
   controls show a tooltip; the ruler shows the time under the pointer.
5. **Given** keyboard focus, **When** the user presses space, **Then** playback toggles; home
   returns to the start; left and right arrows move the playhead by one grid step (a bar with
   the grid off); S splits the selected clip; delete or backspace deletes it; the platform's undo
   and redo shortcuts undo and redo; "+" and "−" zoom; tab moves through the toolbar, the
   headers, the clips and the transport bar in order with a visible focus ring; in Projects, tab
   moves through the search, chips, sort, the cards and their menus.
6. **Given** a screen reader, **When** it reaches each element, **Then** tracks announce their
   name, label, gain, mute and solo state; clips announce their name, track, start bar and
   length; the playhead announces its position; the transport buttons their action; the readout
   the position and length; the master volume is an adjustable element; project cards announce
   their name, description, tempo, key, length, track count and last-edited time.

---

### Edge Cases

- The project has no tracks: the track area shows a short invitation with "Add track" and
  "Add audio", play runs the position and the metronome, the readout shows
  "00:00.00 / 00:00.00", and split and delete are unavailable.
- A track has no clips: it plays silence; solo on it silences everything else.
- Every track is muted, or every soloed track is also muted: the output is silence; playback
  still runs.
- The soloed track is deleted: no track is soloed afterwards and all remaining tracks sound.
- A clip is shorter than a touch target at the current zoom: it still selects with a tap on it,
  and its move starts from a long press; zooming in restores a usable width.
- The finger drifts slightly during a long press on a clip: the press still counts as a long
  press within the platform's usual slop, and the move begins from where the clip was.
- The playhead is exactly at a clip's start or end when the scissors is pressed: nothing is
  split and the control is unavailable.
- Two clips meet exactly (after a split): playback across the join is identical to the
  original; the later clip cannot be dragged left even by one grid step.
- A tempo change makes clips on one track overlap in time (clip starts are in beats, audio
  lengths are fixed): the later clip is heard where they overlap and the earlier one is cut
  short audibly only; nothing is deleted and undoing the tempo change restores the original
  sound.
- A tempo of 40 or 240, or a time-signature change in a project with clips: ruler, click and
  positions follow; no clip is moved in beats.
- The imported file is not WAV, FLAC or MP3, has more than two channels, is longer than 30
  minutes, is larger than 500 MB, or is unreadable: it is refused with a message naming the
  reason, and nothing is added to the project.
- The imported file is 8-bit, 32-bit float, 8 kHz or 192 kHz, mono or stereo, or silent: it is
  accepted, plays at the right pitch and speed, and its waveform reflects its content (a flat
  line when silent).
- The original file is moved or deleted after import: the clip keeps working from the app's
  copy.
- The same file is imported twice into one project: one audio copy is kept and two clips refer
  to it; deleting one clip keeps the copy for the other.
- A multiple selection mixes accepted and refused files (a 4-channel file among stereo WAVs):
  the accepted files become tracks, one message names each refused file and its reason, and
  the import is still one undo step.
- A multiple selection of twelve files: twelve tracks appear, the waveforms fill in
  progressively, and the timeline stays responsive while they prepare.
- The app's copy of a file cannot be read when a project opens: the clip is shown as
  unavailable and silent (User Story 7, scenario 7).
- The user zooms to the widest level on a 60-minute project: labels stay legible (bars only,
  minutes only) and the whole project fits; at the closest level one beat spans the window and
  the waveform shows individual peaks.
- Thirty-two tracks: the track list scrolls vertically, the ruler stays pinned at the top,
  headers stay pinned at the left in expanded windows, and playback of eight simultaneous clips
  is uninterrupted on the reference devices.
- The window rotates or resizes during playback: playback continues, the playhead and readout
  stay correct, the zoom level is kept and the view re-centres on the playhead.
- The app is force-quit or crashes during playback: on relaunch the project opens stopped at
  the start with every edit older than two seconds intact.
- Headphones are unplugged while playing: playback pauses (the platform conventions), the
  playhead stays; plugging in a new output does not restart it. A phone call or another app
  taking the audio pauses playback; it does not resume by itself.
- The user chooses another destination while playing: the stop-and-leave dialog appears;
  confirming pauses with the position kept and navigates, and returning to Timeline shows it
  paused at that position; cancelling leaves everything as it was. Choosing the destination
  while stopped navigates at once with no dialog.
- The largest system font size: readouts, names and chips stay readable and unclipped; names
  truncate with an ellipsis, chips wrap to a second line in the header, the transport readout
  never truncates; project cards grow in height, never clip.
- Reduce motion is on: the playhead still moves (it is information); glows, pulses and
  decorative transitions of sheets, buttons and selection are disabled.
- A project name is edited to empty or whitespace: the edit is rejected and the previous name
  kept; a track name edited to empty reverts to "Track N"; a duplicate's name that already
  exists gets a number ("<name> copy 2").
- A search with no match: the list shows a short "No projects match" line and the count "0 of
  N"; clearing the search restores the list.
- Two hundred projects: the list scrolls smoothly, the overviews appear as cards come into
  view, and search narrows within a frame of each keystroke.
- Undo after switching projects: the history belongs to the project it was made in and is
  cleared when another project is opened; it does not survive an app restart.

## Requirements *(mandatory)*

### Functional Requirements

**Destination and project**

- **FR-001**: The Timeline destination MUST replace the feature 002 placeholder with the
  arranger: a toolbar, a ruler, a scrollable track area and the transport bar, at every window
  size class. The navigation, top bar frame and back behaviour of feature 002 MUST remain
  unchanged.
- **FR-002**: The top bar title on Timeline MUST be the open project's name. On the first
  launch the open project MUST be the demo project.
- **FR-003**: A project MUST have a name (1 to 60 characters), an optional one-line description
  (up to 120 characters), an optional key (one of the 24 major and minor keys), one tempo (a
  whole number from 40 to 240 BPM; the metronome's tempo model), one time signature (one of the
  metronome's six), an ordered list of tracks, a master volume and the metronome toggle state;
  the grid and the last zoom level are remembered per project on the device, outside the
  document (FR-049). The demo project MUST be created at 124 BPM in 4/4 with a
  description, a key, at least three tracks and five clips of permissively licensed or generated
  audio whose total length is between 30 and 60 seconds; a new project MUST start at 120 BPM in
  4/4 with no description, no key and no tracks.

**Transport and playback**

- **FR-004**: The transport bar MUST be shown at the bottom of Timeline and of the track
  settings screen, above the navigation bar in compact windows and spanning the content width
  in expanded windows, and MUST contain, left to right: the master volume slider with its
  percentage, skip-to-start, step-back-one-bar, play/pause, step-forward-one-bar, the position
  and length readout, and the metronome toggle. It MUST NOT contain a playback speed or loop
  control.
- **FR-005**: Play MUST start sound within 100 ms of the press from the playhead position, with
  every clip whose span contains that position starting mid-clip at the right point, and the
  button MUST show pause while playing. Pause MUST stop within 10 ms with a short fade so there
  is no click, keep the playhead where it stopped, and show play. Play again MUST continue from
  the paused position. Playback past the end of the last clip MUST continue in silence until
  stopped.
- **FR-006**: Skip-to-start MUST move the playhead to the beginning; step-back and step-forward
  MUST move it by one bar (to the previous or next bar line); all three MUST work while
  playing, continuing from the new position within 100 ms with no artefact.
- **FR-007**: The master volume MUST range from silence to full with a default of 80 %, shown
  as a percentage, applied to the sum of all tracks, smoothed so that dragging it produces no
  steps or crackle, and remembered per project. In windows narrower than 600 dp the transport
  bar MUST show it as a speaker button whose state is the percentage; tapping the button MUST
  open a popup above the bar holding the same slider and the percentage, closed by a tap
  outside, Escape or Back; from 600 dp the slider MUST be inline in the bar (D1, 2026-10-26).
- **FR-008**: Playback MUST continue while the screen is locked or another app is in front until
  the user pauses it, a call or another app takes the audio, or the output route is lost
  (headphones unplugged); after an interruption it MUST NOT resume by itself. Connecting a new
  output while playing MUST continue playback on it.
- **FR-008a**: While a project is playing, choosing any other destination inside the app
  (including Tuner, Metronome, Projects and the system back action from Timeline), or leaving
  the track settings screen for a destination other than Timeline, MUST first show a
  confirmation dialog asking whether to stop playback and leave, with "Stop and leave" and
  "Keep playing" actions; confirming MUST pause playback (position kept) and then navigate,
  cancelling MUST stay where the user was with playback running. The dialog MUST NOT appear
  when playback is stopped, when moving between Timeline and its track settings screen, when
  the screen locks or when another app comes to the front (added 2026-10-13, clarification).

**Playhead and readout**

- **FR-009**: A vertical playhead line MUST be drawn through the ruler and every track at the
  position being heard, moving smoothly every frame during playback, with a draggable rounded
  marker in the ruler's bar row that shows the playhead's bar and beat (for example "5.1") in a
  theme colour that contrasts with the chip at 4.5:1 or better, with a 1 dp outline, legible at
  the smallest size (D1). The
  displayed position MUST be within 30 ms of the audible position on a wired output.
- **FR-010**: The readout MUST show the playhead position and the project length as
  minutes:seconds.hundredths in the form "00:00.00 / 00:42.20" in tabular figures (hundredths
  truncated, never rounded, as the ruler's time row does), the position
  updating every frame during playback and on every seek, the length being the end of the
  last clip (00:00.00 when the project has no clips) and updating on every edit that changes it.
- **FR-011**: Tapping the ruler MUST move the playhead to the tapped time, snapped to the grid
  when it is on, and running playback MUST continue from there. Dragging the ruler's marker
  MUST move the playhead and the readout with the finger or pointer (snapped when the grid is
  on) with no sound during the drag: if playback was running it MUST pause silently when the
  drag begins and resume from the release point within 100 ms of the lift; if it was paused it
  MUST stay paused at the release point. Audible scrubbing is not part of this feature
  (confirmed 2026-10-13).
- **FR-012**: During playback the view MUST keep the playhead visible by paging when it reaches
  the right edge; a manual scroll MUST suspend following until the next skip-to-start, ruler tap
  or play press.

**Ruler, zoom and grid**

- **FR-013**: The ruler MUST show, above the tracks and pinned while the tracks scroll
  vertically, a bar row (bar numbers from 1 and beat ticks) and a time row (minutes and
  seconds) on one shared horizontal scale with the clips; it MUST NOT carry the tempo or the time
  signature, which live in the toolbar and the metronome sheet (D1; FR-036, FR-037, FR-039).
- **FR-014**: The labels MUST follow the zoom so that no two labels in a row overlap and the
  densest legible level is shown: at the widest zooms only every Nth bar is numbered (N a power
  of two) and the time row shows whole minutes; as the zoom closes, every bar, then every beat
  (as "bar.beat"), then subdivisions down to sixteenths are labelled, and the time row shows
  seconds, then tenths, then hundredths. Ticks MUST be drawn for one level below the labelled
  level.
- **FR-015**: Zoom MUST be continuous between a widest level at which the whole project, and at
  least 32 bars, fits the window and a closest level at which one beat spans at least the
  window width; it MUST be driven by a pinch on the ruler or the track area (zooming around the
  pinch centre), by the scroll wheel with the platform's modifier key and by a trackpad pinch
  (zooming around the pointer), by the "+" and "−" keys, and by the overview strip (D1; the
  owner's verdict of 2026-10-27): a strip above the transport bar that draws the whole project
  with its clips as blocks, the playhead and a window for the visible span, where dragging the
  window scrolls, dragging its edges zooms and a tap outside the window centres it there. The
  strip MUST be shown by default and MUST be shown or hidden from the overflow menu's "Overview
  strip" entry, checked while shown and remembered on the device, outside every project; with
  the strip hidden the pinch, wheel, trackpad and key paths remain. The strip MUST be inset from
  the window's left and right sides by at least 16 dp, plus whatever part of the system's
  back-gesture zone still reaches the strip beside a rail, the end column or the header column,
  so that both ends of its window can be grabbed without starting a back swipe. The overflow
  menu's zoom readout is replaced by the strip. The level MUST be remembered per project.
- **FR-016**: The timeline MUST scroll horizontally by drag, by the scroll wheel without the
  modifier and by a two-finger trackpad swipe; the track list MUST scroll vertically when the
  tracks exceed the height, with the ruler pinned at the top and, in expanded windows, the
  headers pinned at the left.
- **FR-017**: The playhead, the clips and the waveforms MUST stay aligned with the ruler at
  every zoom level and scroll position, with no visible drift between the ruler's time row and
  the readout.
- **FR-018**: The toolbar's grid picker, labelled "Snap" with its value (for example
  "Snap: Bar"; D1), MUST offer Bar, 1/4, 1/8, 1/16 and Off, default 1/16, remembered per
  project; when on, the grid MUST snap ruler taps, playhead drags, clip moves and splits to the
  nearest grid line; the faint density grid of FR-014 MUST be drawn in the track area and, over
  it, the snap lines at the picked resolution in a distinct stronger stroke, so that a change of
  the picker is visible at once; "Off" draws no snap lines (D1).

**Tracks**

- **FR-019**: "+ Track" MUST add a track at the end of the list, named "Track N" where N is one
  more than the highest existing "Track N" name (starting at 1), with no instrument label, the
  next palette colour, 0 dB gain, not muted, not soloed, and select it. There MUST be no fixed
  limit below 32 tracks.
- **FR-020**: A track MUST have a name of 1 to 40 characters, editable in its settings; an empty
  or whitespace name MUST revert to "Track N".
- **FR-021**: A track MUST have an optional instrument label chosen from exactly this list:
  Vocals, Guitar, Bass, Drums, Percussion, Keys, Synth, Strings, Brass, Woodwind, Loops, Other;
  "None" clears it. The label MUST be shown as a chip after the name in the header and MUST
  carry the label's glyph where the design system has one.
- **FR-022**: Each track MUST have a colour assigned automatically from the design system's
  track palette of six tints (lilac, salmon, dusty rose, peach, mint, violet, as the design
  shows) in creation order, cycling when the palette is exhausted; the colour MUST tint the
  header dot, the header's mute and solo buttons in compact windows and the track's clips. No
  colour picker is included.
- **FR-023**: Each track MUST have a gain from silence to +6 dB, default 0 dB, shown in dB with
  one decimal and a sign ("+0.8dB", "−2.4dB", "−∞" at silence), editable from the header in
  expanded windows (a drag on the readout or its slider) and from the settings; changes MUST
  apply smoothly during playback; a double tap MUST return it to 0 dB.
- **FR-024**: Each track MUST have mute and solo toggles ("M", "S") in its header: a muted track
  is silent; when any track is soloed only soloed tracks sound; a track both muted and soloed is
  silent; several tracks may be soloed; mute and solo MUST apply within 20 ms with a short fade
  and MUST be saved with the project.
- **FR-025**: A tap on a track header MUST select the track (highlighted); the selected track is
  where imported audio lands and whose settings the toolbar menu opens; exactly one track or
  none is selected.
- **FR-026**: Each header MUST have a "…" menu with: Track settings, Add audio…, Move up, Move
  down, Delete track (confirmed; undoable). Move up and Move down MUST be unavailable at the
  ends of the list.

**Clips and waveforms**

- **FR-027**: A clip MUST represent a contiguous piece of one audio copy on one track with a
  start position in beats, a start offset into the audio and a length that is the audio's own
  duration (the project tempo does not change how fast a clip plays). Clips on one track MUST
  NOT overlap by editing; when a tempo change makes them overlap in time the later clip is heard
  in the overlap and nothing is altered in the project.
- **FR-028**: A clip MUST be drawn as a rounded block tinted with its track colour, spanning its
  time extent at the current zoom and the lane's height, with its name as a caption on a darker
  band at the top and its filled peak waveform below it; a selected clip MUST show the design's
  selection outline; an unavailable clip (its audio copy cannot be read) MUST be drawn greyed
  with its name and no waveform.
- **FR-029**: The waveform MUST show the peak envelope of the clip's audio (both channels
  combined or stacked) at every zoom level, drawn from a precomputed overview so that zooming
  and scrolling never wait on the audio file; the overview MUST be computed on import and on
  first open of a project that lacks it, progressively, with the clip showing a placeholder
  until its overview is ready, and MUST be cached with the project on the device.
- **FR-030**: A clip MUST be named after its source file without the extension; names longer
  than the clip's width MUST be clipped visually without changing the name.

**Editing and undo**

- **FR-031**: Tapping a clip MUST select it; one clip is selected at a time; tapping empty track
  area clears the selection. On touch, a plain one-finger drag that starts on a clip MUST scroll
  the timeline exactly as a drag on empty track area does; a long press on a clip MUST select it
  and, if the finger then moves without lifting, start the clip move of FR-033 (a lifted long
  press only selects), with a brief visual lift of the clip to show that the move has begun.
  With a pointer, a press-and-drag on a clip MUST move it and scrolling is the wheel's or the
  trackpad's (confirmed 2026-10-13).
- **FR-032**: The scissors (toolbar; "S" key) MUST split the selected clip at the playhead
  (snapped when the grid is on) into two clips that together play exactly as the original,
  each keeping the name; it MUST be unavailable when no clip is selected, when the playhead
  lies outside the selected clip or when it lies within one hundredth of a second of a clip
  edge.
- **FR-033**: A clip move (begun as FR-031 defines) MUST drag the clip along its track (start
  snapped to the grid when on, never before the beginning, stopping against neighbouring clips)
  and onto another track at the same position when the span is free (otherwise it returns); the
  move MUST be applied on release and MUST be reflected in playback at once, including during
  playback. While a move is in progress the timeline MUST NOT scroll under the finger except to
  follow the clip when it is dragged past an edge.
- **FR-034**: "Delete clip" (clip menu, delete key, backspace) MUST remove the selected clip
  without touching the audio copy while other clips still use it; an audio copy no clip uses
  MUST be removed from the device once the deletion has left the undo history.
- **FR-035**: Undo and redo MUST be available from the toolbar and the platform's shortcuts for
  every project edit (track add, rename, label, order, gain, mute, solo, delete; clip import,
  split, move, delete; tempo; time signature; master volume; project rename, description and
  key), keeping at least 50 steps per open project, working during playback, unavailable when
  empty, and cleared when another project is opened. Zoom, scroll, selection and the metronome
  toggle are not undo steps.

**Tempo, time signature and metronome**

- **FR-036**: The toolbar MUST show the project tempo as "<N> BPM" at every width (D1: on
  phones between redo and the overflow menu); tapping it MUST open the tempo
  control reusing the Metronome destination's tempo entry (dial, steppers, slider, tap tempo,
  tempo name) over the range 40 to 240; a change MUST redraw the ruler and reposition clips by
  their beat positions, keep each clip's audio speed, update the project length, apply to the
  running click on its next tick, and be one undo step on release.
- **FR-037**: The metronome sheet (FR-039) MUST show the time signature and offer the
  metronome's six signatures (D1: it is no longer on the ruler or in the toolbar); a change MUST redraw the bars and beat ticks, follow the metronome's accent rules
  for the signature, and leave clip positions in beats unchanged.
- **FR-038**: The transport bar's metronome toggle MUST switch the click on and off, remembered
  per project, default off; the click MUST be the Metronome destination's click (the same sound
  set, custom sound, Accent/Click/Sub levels, subdivision, accent pattern and style) driven by
  the project's tempo and time signature, with beat 1 of each ruler bar an accent; it MUST
  start on the next beat when switched on during playback and together with the first audio
  when playback starts with it on; it MUST be silent while playback is stopped.
- **FR-039**: A long press on the toggle, or "Metronome settings" in the toolbar menu, MUST open
  a sheet with the Metronome destination's sound picker, subdivision, accent cells and style
  controls, with the tempo shown read-only as the project's and the time signature chosen there
  (FR-037, D1); changes made
  there MUST be the same settings the Metronome destination shows and changes.
- **FR-040**: The standalone Metronome destination MUST keep its own tempo and settings as
  feature 004 defined; it is reached only after project playback has stopped (FR-008a); the
  project tempo MUST never change it and it MUST never change the project.

**Track settings and the effects placeholder**

- **FR-041**: "Track settings" MUST contain: the name field, the instrument label list, the gain
  control with its readout, the mute and solo toggles, "Delete track", and an "Effects" section
  headed as such whose only content is one sentence stating that effects for this track arrive
  in a later version; no effect, preset, A/B or insert control MUST be shown. Pan is added by
  feature 010.
- **FR-042**: In windows narrower than 840 dp the settings MUST open as a screen over Timeline
  titled with the track name, with the transport bar at the bottom still working and back
  returning to Timeline at the same scroll and zoom; in windows 840 dp or wider they MUST open
  inline beneath the track header (a chevron in the header toggles it), one track at a time,
  pushing the following tracks down; a resize across 840 dp MUST move the open settings between
  the two forms without losing edits.
- **FR-043**: Edits in the settings MUST apply at once to the header and playback, each field
  being one undo step.

**Audio import and the demo project**

- **FR-044**: "Add audio…" (a track's menu, or the toolbar for the selected track) MUST open the
  platform's file picker for one file; "Add audio as new track" (the toolbar's menu and the
  empty-project invitation) MUST open it allowing several files, and MUST create one new track
  per accepted file, in the picker's order, each named after its file and carrying its clip at
  the playhead, the whole import being one undo step (confirmed 2026-10-13); files refused in a
  multiple selection MUST be named in one message while the accepted ones are still imported.
  WAV, FLAC and MP3 files with up to two channels and up to 30 minutes
  MUST be accepted; anything else MUST be refused with a message naming the reason (unsupported
  format, more than two channels, longer than 30 minutes, larger than 500 MB, unreadable). Each accepted
  file MUST be copied byte for byte, in its own format, into the project's storage (once per
  project, even when imported again), so the original is never needed again; the project MUST
  play it from that copy at its original pitch and speed whatever its format or sample rate
  (confirmed 2026-10-13: originals are kept and used, never converted into the project); any
  playback or waveform preparation the app needs MUST be a cache that can be rebuilt from the
  copy and is not part of the project. The clip is placed at the playhead (or at the first free
  position after it when the playhead is inside a clip) on the chosen track or on a new track
  named after the file; its overview MUST be computed; the import MUST be one undo step. When no
  track is selected the toolbar's "Add audio…" MUST behave as "Add audio as new track".
- **FR-045**: The demo project of FR-003 MUST be installed on first launch, MUST be re-creatable
  from Projects ("Restore demo project") when it was deleted or edited, and its audio MUST ship
  with the app under a permissive licence recorded in the licence inventory.

**Projects and persistence**

- **FR-046**: Every project edit MUST be saved to the device automatically within 2 s of the
  last change and on leaving Timeline, backgrounding and termination; a crash MUST lose at most
  the last 2 s of edits. The project document format MUST be versioned and read
  forward-compatibly (unknown fields ignored, explicit migrations between versions).
- **FR-047**: On launch the app MUST reopen the most recently opened project in Timeline,
  stopped at the start, with its zoom, grid, master volume and metronome toggle as saved.
- **FR-048**: Audio copies (the original files, FR-044) MUST live with the project on the
  device, MUST be shared by every clip that uses the same audio, and MUST be removed with the
  project; waveform overviews and any playback cache MUST be kept apart from the copies, MUST be
  rebuildable from them, MUST NOT count toward the project's size shown on its card, and MUST be
  removed with the project.
- **FR-049**: The project document MUST be portable and self-describing: one text document
  carrying its format version, a stable identifier for the project and for every track, clip
  and audio copy, every position in beats, every setting of FR-003 and of the tracks and clips,
  and references to audio by a stable content identifier with the audio's format facts
  (channels, sample rate, length) rather than by any device path; anything specific to the
  device (file locations, overview caches, last-opened state, window and zoom conveniences
  that are not project content) MUST live outside the document. The same document MUST open as
  an identical project on either platform when its audio copies are present, so that a later
  feature can send it to a remote service and receive it back unchanged.
- **FR-050**: The Projects destination MUST replace its placeholder with the library: a title
  with the project count ("N projects"), a search field, the filter chips "All" and "Recent"
  (edited in the last seven days), a sort menu ("Last edited", the default, and "Name"), a
  "New project" action, and the cards; in windows 840 dp or wider the cards MUST form two or
  more columns. It MUST NOT show a transport bar.
- **FR-051**: Each card MUST show: an "Active" badge on the open project and a "Demo" badge on
  the demo project; the last-edited time as a relative time ("2 m ago", "Yesterday 22:40",
  "3 days ago", the date beyond a week); the name; the description (or nothing); the overview
  of FR-054; a stats row with the tempo ("124 BPM"), the key (or "—"), the length ("00:42.20")
  and the track count ("4 tracks"); a footer with the size on the device; a "Resume" button on
  the open project and "Open" on the others; and a card menu.
- **FR-052**: The card menu MUST offer: Rename (1 to 60 characters; empty rejected),
  Description (up to 120 characters), Key (the 24 keys and "None"), Duplicate (a complete copy
  named "<name> copy", numbered when taken, with its own audio copies), Delete (confirmed;
  removes the project and its files), and on the demo card "Restore demo project". "New
  project" MUST create "Project N" (the next free number) at 120 BPM, 4/4, empty, and open it;
  tapping a card, "Open" or "Resume" MUST open it in Timeline. Deleting the open project MUST
  open the most recently edited remaining project or a new empty one.
- **FR-053**: Search MUST narrow the list as the user types to projects whose name or
  description contains the text (case-insensitive), with the count showing "M of N"; the
  filters and the sort MUST combine with it; a search with no match MUST show a one-line empty
  state; the search text, filter and sort MUST be kept while the app runs and reset on launch.
- **FR-054**: Each project MUST carry an overview of its mix for the card: the project length
  divided into equal spans (at least 48), each with the peak level of the combined clips in that
  span after track gain and mute, and the colour of the loudest track there, derived from the
  clips' waveform overviews whenever the project is saved; it MUST be drawn in the design's
  form for the width (bars on compact cards, a line on expanded cards) and MUST be empty for a
  project without clips.

**Layout, input and accessibility**

- **FR-055**: In windows narrower than 600 dp the toolbar MUST show "+ Track", the scissors,
  undo, redo, the tempo pill (D1) and an overflow menu holding "Add audio…", "Add audio as new
  track", the "Snap" picker, the "Overview strip" entry and "Metronome settings" (D1); headers MUST be compact (colour dot, name,
  label chip, M, S, menu) at least 56 dp tall with the gain in the settings; at least three
  tracks and the ruler MUST be visible above the transport bar on a phone in portrait at the
  default font scale. In windows 840 dp or wider every toolbar control MUST be visible, headers
  MUST form a column of at least 200 dp with the gain readout and a chevron, and the ruler and
  clips MUST scroll beside them. Between 600 and 839 dp the compact rules apply with the full
  toolbar when it fits. In windows at least 600 dp wide and shorter than 480 dp the ruler, at
  least two tracks and the transport bar MUST be visible. Nothing MUST clip, overlap, hide
  behind the transport bar or scroll the frame horizontally at any width, including at the
  largest system font size.
- **FR-056**: Every toolbar button, transport button, header toggle, menu entry, settings
  control, project card, card button and the ruler's tempo and signature MUST show a hover
  state under a pointer and a visible focus indicator, MUST measure at least 44 dp, and
  icon-only controls MUST show a tooltip. Clips MUST show a hover state and the ruler MUST show
  the time under a hovering pointer.
- **FR-057**: Keyboard: space toggles play, home skips to start, left and right arrows move the
  playhead by one grid step (one bar with the grid off), shift with the arrows by one bar, "S"
  splits, delete and backspace delete the selected clip, the platform's undo and redo shortcuts
  undo and redo, "+" and "−" zoom, "M" and "O" toggle mute and solo of the selected track; tab
  traverses the toolbar, the headers, the clips and the transport bar in order; the master
  volume and the gain controls respond to the arrow keys in 1 % and 0.5 dB steps and to Page Up
  and Page Down in 10 % and 6 dB steps; in Projects, tab traverses the search, chips, sort, cards
  and card menus, and enter opens the focused card.
- **FR-058**: Every control MUST expose its label, value or state and a stable test identifier
  to accessibility services; a track announces its name, label, gain, mute and solo; a clip its
  name, track, start bar and beat and its length; the playhead its position; the readout the
  position and length; the master volume and the gain controls are adjustable elements
  announcing their value; a project card announces its name, description, tempo, key, length,
  track count and last-edited time.
- **FR-059**: All colours, type styles, spacing, radii and icons MUST come from the shared design
  system; the new icons (scissors, undo, redo, skip-to-start, step back, step forward, grid,
  zoom, search, sort, duplicate, the instrument glyphs and the track menu glyph) MUST be added
  through its existing mechanism; every live number MUST use tabular figures; the track palette
  MUST be the design's clip tints.
- **FR-060**: Under the platform's reduce-motion setting the playhead MUST still move (it is
  information) while glows, pulses and decorative transitions MUST be disabled.

**Quality**

- **FR-061**: Automated tests MUST cover, without a device: the beat-to-time and time-to-beat
  conversions at every tempo and signature; the ruler label rules of FR-014 at a sweep of zoom
  levels; the grid snapping; the split arithmetic (both halves, exact join, edge refusal); the
  move constraints (neighbours, beginning, other track free or not); the solo and mute truth
  table; the gain law; the project length; the undo history (50 steps, during playback, cleared
  on switch); the project document round trip including an unknown field, a version migration,
  a missing audio copy and the portability rules of FR-049 (no device path, identical on both
  platforms); the import acceptance and refusal matrix (formats, channels, length, bit depths,
  sample rates, a repeated file, a multiple selection with refusals, one undo step for a
  multiple import); the overview computation and the card overview of FR-054;
  the library's search, filter and sort; duplicate naming; the stop-and-leave dialog of
  FR-008a (shown only while playing, both actions, not shown between Timeline and its track
  settings); the multi-clip rendering (clip
  starts within one hundredth of a millisecond of their position, mid-clip start, the join
  after a split, overlap precedence after a tempo change, the click aligned with the audio) on
  audio rendered offline; the screens in every state with a fake audio source; and the
  accessibility semantics of FR-058. Behaviour that needs real hardware (route changes,
  interruptions, screen lock, the playhead against the audible position, eight simultaneous
  clips without dropouts) MUST be verified on a device and recorded in the feature's
  verification notes.
- **FR-062**: Screenshots at compact (360 dp) and expanded (1024 dp or wider) widths and in
  phone landscape on Android and the iOS simulator MUST be stored under the feature directory
  and reviewed against the Stitch references before acceptance. The set MUST include: the demo
  project stopped and playing with the playhead mid-song, the widest and closest zoom, a
  selected clip and the result of a split, the grid picker open, the track settings as a screen
  and inline, the tempo control open from the ruler, the metronome settings sheet, an empty
  project, an unavailable clip, an import refusal, the stop-and-leave dialog, the Projects
  library with at least six cards
  (the demo, the active project, a project with a description and key, one without), a card
  menu open, a search with results and without, and the largest font size.
- **FR-063**: Any dependency added for this feature (audio file decoding, resampling or
  otherwise) MUST carry a permissive licence, MUST be recorded in the licence inventory with its
  version and source, and MUST NOT be one of the libraries the constitution forbids.
- **FR-064**: The feature MUST NOT include: the mixer sheet or panel with faders, pan, meters
  and a master strip (feature 010); pan on tracks (feature 010); recording or input monitoring
  (feature 011); a loop or cycle region; playback speed; time-stretch or pitch change of clips;
  clip trim, copy, paste, duplicate, fades, crossfades or gain per clip; overlapping clips by
  editing; multi-selection; automation; effects, sends or buses; a session or clip-launch view
  (the design's "Arrange" pill); the loop library and pads; markers; audible scrubbing while
  dragging the playhead; tempo changes within a project; count-in; a track colour picker; track
  freeze or bounce; stem or mix export; cloud
  sync, sharing, collaboration, guests or sync badges; MIDI; a transport bar on Projects,
  Tuner, Metronome, Home, Effects, Pads or Profile; or changes to the navigation and its glyphs.

### Performance instrumentation declared by this feature

- Journeys added: one new journey, `timeline`, on both platforms: open Projects, open the demo
  project from its card, wait for every waveform to be ready, press play and let eight bars
  pass, tap the ruler to seek, zoom in and out with a pinch (marked as the animation variant),
  split the first clip, open and close the track settings, toggle the metronome on and off,
  pause, and return to the start. The `stress` journey MUST be extended to play the demo project
  with the metronome on while scrolling the timeline, so the audio-thread health counters of
  feature 007 measure a real engine load for the first time.
- Sections added (the catalogue's timeline and engine families): project open (from the tap on
  the card to the first frame of the tracks), waveform preparation (per clip, import and first
  open), play start (press to the first audible output), seek (tap to the first output from the
  new position), zoom (per gesture, animation variant), clip split, playhead frame (the
  per-frame cost of the playhead and readout), project save, and library open (Projects to the
  first frame of the cards).
- Design iteration D1 (2026-10-26) adds its two interactions to the `timeline` journey: on a
  phone, open the master volume pop-up, move its slider a little and close it with a tap
  outside (the change is undone, so the project is the same after every pass), and, unless the
  device hid the overview strip, drag the strip's window once and back (measured by the existing
  scroll section). It adds one section, the pop-up's opening (master popup), and
  anchors on the speaker button, the volume slider, the strip and its window.
- Counters added: playback underruns during the journey, clip reads that were not ready when
  the engine needed them (starvation), peak and average callback load during playback, and the
  number of simultaneous clips rendering.
- Anchors: every toolbar and transport control, the ruler, the first clip, the track settings,
  the metronome toggle, the Projects search field and the first card carry stable identifiers
  for the journey drivers.
- Baseline profile: Timeline and Projects are primary destinations and this feature adds a
  journey and main interactions, so the Android baseline profile MUST be regenerated and
  committed in this feature (feature 008's rule).
- After the merge the feature MUST be measured with a labelled session on every available device
  and simulator; the report is pull-request evidence. The startup family MUST not regress by
  more than the gate, and the new audio counters MUST read zero underruns on the reference
  devices.

### Key Entities

- **Project**: a named arrangement: description, key, tempo, time signature, ordered tracks,
  master volume, metronome toggle, last edited; owns its audio copies and overviews. Its grid
  and zoom are a **Project View State** kept on the device beside it, outside the document.
- **Project Document**: the portable text form of a project (FR-049): version, stable
  identifiers, beats, settings, audio references; nothing device-specific.
- **Tempo Map**: the project's one tempo and time signature and the conversions between beats
  and time they define; the only way a beat position becomes a time.
- **Key**: one of the 24 major and minor keys, written as the design shows ("Dmin", "F#maj").
- **Track**: name, optional instrument label, colour, gain, mute, solo, order; owns its clips.
- **Instrument Label**: one of the twelve fixed labels.
- **Clip**: a reference to an audio copy, a start in beats, an offset into the audio and the
  audio length it covers; drawn with a name and a waveform overview.
- **Audio Copy**: the project's own byte-for-byte copy of an imported or demo file in its
  original format, identified by its content, with its format facts (format, channels, rate,
  length); shared by the clips that use it. Its waveform overview and any playback cache are
  derived, rebuildable and outside the project.
- **Transport State**: stopped or playing, the playhead position, whether the view follows it.
- **Edit History**: the ordered undo and redo snapshots of the open project.
- **Metronome Settings**: the Metronome destination's sound, subdivision, accent pattern and
  style, shared with the timeline's click; tempo and time signature come from the project.
- **Library Entry**: what a card shows for one project: name, description, badges, tempo, key,
  length, track count, size on the device, last edited, and the mix overview of FR-054.
- **Library View State**: the search text, filter and sort of the Projects destination.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The demo project and a project with eight tracks each playing a clip play on
  both reference devices for ten minutes with zero audible dropouts and zero underrun counts,
  with every clip starting within 1 ms of its position and no drift between clips at the end.
- **SC-002**: Play starts sound within 100 ms of the press, a seek during playback resumes
  within 100 ms, pause is silent within 10 ms, and a recording around each shows no click or
  gap.
- **SC-003**: On a wired output, the playhead line and the readout coincide with the audible
  position within 30 ms on a combined screen and audio recording, and the playhead moves at
  the display's frame rate with no stutter during playback while the user zooms and scrolls.
- **SC-004**: With the metronome on, every click over ten minutes lands within 1 ms of its beat
  relative to a clip that starts on bar 1, at 40, 124 and 240 BPM in 4/4 and 7/8.
- **SC-005**: A clip split at any position and played back is identical to the unsplit clip; a
  tempo change followed by undo restores an identical output.
- **SC-006**: Gain set to any value is reproduced in the output within 0.1 dB; mute and solo
  take effect within 20 ms.
- **SC-007**: A 5-minute stereo WAV imports and shows its complete waveform within 3 s, a
  60-minute project opens with its waveforms within 2 s from the cache, and zooming or
  scrolling never shows a blank clip for longer than one frame once the overview exists.
- **SC-008**: Every edit is on the device within 2 s; after a force-quit at a random moment the
  project reopens with at most the last 2 s of edits missing, on both platforms, in 20 of 20
  trials.
- **SC-009**: A project document saved on one platform and opened on the other with its audio
  copies present plays identically and shows the identical arrangement; the document contains
  no device path.
- **SC-010**: The Projects library with 200 projects opens within 1 s, scrolls without dropped
  frames, and narrows a search within one frame of each keystroke.
- **SC-011**: Screenshots at compact and expanded widths and in phone landscape on both
  platforms show zero clipped, overlapping or hidden elements and match the design on visual
  review; all test lanes are green with zero compiler and lint warnings.
- **SC-012**: 100 % of controls are reachable and operable by keyboard, announce their label
  and state to the screen reader, and measure at least 44 dp.
- **SC-013**: A musician who has never used the app can create a project, add two tracks with
  labels, import a file onto each, split one clip, balance the tracks with their gain and play
  the result with the metronome on within three minutes without instructions.
- **SC-014**: The labelled performance session after the merge shows the `timeline` journey on
  every available device, zero underruns and zero starvation on the reference devices, and no
  family above its gate limit in the worse direction.

## Assumptions

- **Three requests, three features.** The user allowed any cut. The audio spine's only
  user-facing proof is a clip that plays, so it merges with the transport and timeline here;
  the mixer sheet is feature 010 and recording feature 011, in that order because recording
  needs the mixer's input meters. The constitution's build order is amended through
  `/speckit-constitution` before `/speckit-plan`, as it was for the tuner and the metronome.
- **Where the audio comes from.** Recording is feature 011, so in this feature audio enters a
  project by import from a file on the device and from the bundled demo project. WAV, FLAC and
  MP3 are the formats (confirmed 2026-10-13) because they can be decoded identically on both
  platforms with permissively licensed code the engine already uses for WAV; the platform's own
  decoders are not used so that a file behaves the same everywhere. Up to two channels and 30
  minutes keeps overviews and copies bounded. "Add audio as new track" is the user's "into a new
  track" and accepts several files at once, one track per file (confirmed 2026-10-13), because a
  set of stems is the common case; adding to an existing track takes one file so the drop
  position stays unambiguous. The project keeps the original
  file bytes and plays from them (confirmed 2026-10-13): the size on a card is the real size of
  what the user imported, nothing is lost or re-encoded, and the project document's content
  identifiers name real files a server can store; the plan decides what rebuildable cache the
  engine wants beside them.
- **"Split the track into multiple pieces like stems"** is read as splitting a clip at the
  playhead (the design's scissors), not as exporting stems; export is the export and mastering
  screen, later. "Slice the selected track" from the ruler is read as: tap the ruler to place the
  playhead, then split the selected clip there.
- **The mixer, pan, meters and "VU".** The user's mixer sheet (sliders, enable or disable a
  track, level spikes, pan, solo, mute, the VU widget) is feature 010 by the cut above. For the
  record: a VU meter ("volume unit", a 1939 broadcast standard) averages the signal over about
  300 ms, so it shows how loud a sound feels rather than its spikes; a peak meter shows the
  spikes; feature 010 gives each strip both. "Enable or disable the track" is read as the mute
  toggle, which this feature already has in the headers.
- **Positions in beats, audio at its own speed.** Clip starts are stored in beats as the
  constitution requires, so a tempo change moves clips to keep their bar positions while their
  audio plays unchanged (there is no time-stretch yet). An overlap this creates is only an
  audible precedence of the later clip; nothing is destroyed. Editing never creates overlaps.
- **One tempo and one time signature per project.** Tempo changes within a song are excluded;
  the tempo map therefore has one entry, which is still the only way beats become time.
- **The metronome is the metronome.** As the constitution decided on 2026-09-29, the timeline's
  click is the Metronome destination's click (sound set, custom sound, levels, subdivision,
  accent pattern, style and scheduler), driven by the project's tempo and time signature; its
  sound and bar-shape settings are the one set of preferences the Metronome destination also
  edits, so changing them from the timeline's sheet changes them everywhere. Only tempo and time
  signature differ: the project's in the timeline, the metronome's own on its destination.
- **The readout shows hundredths.** The user said "seconds and milliseconds"; the design shows
  "00:00.00" and whole milliseconds cannot be read at the display's refresh rate, so the readout
  uses hundredths and the ruler's closest level labels hundredths as well.
- **Transport buttons.** The design's rewind and fast-forward are read as one bar back and one
  bar forward (a predictable step on a bar-based ruler) rather than continuous shuttling. The
  projects screens' transport bar is not built: opening a project goes to Timeline where the
  transport lives, which avoids the question of which project "play" would play from the list.
- **The library follows the design, locally.** Option C (confirmed 2026-10-13): search, filters,
  sort, cards with description, key, overview, stats, size and actions. "Cloud synced", "Sync",
  "Export stems", guests and the engine or audio-spec stat (identical for every project) wait
  for their features. "Recent" means edited in the last seven days. Keys are the 24 major and
  minor keys written with sharps as the design does ("F#maj"); the key is a label the user sets,
  not detected.
- **A portable project document.** The user asked for abstractions and data structures that let
  a project be saved to a remote service later. The specification states the user-visible
  contract (FR-049: one versioned text document, stable identifiers, beats, audio by content
  identifier, nothing device-specific, identical on both platforms); the plan chooses the
  structures that satisfy it and keeps device-side state (paths, caches, last-opened) outside
  the document.
- **The card overview** is derived from data the project already has (the clips' waveform
  overviews, gains and mutes) at save time rather than by rendering audio, so the library never
  plays or decodes anything; the phone's bar chart and the tablet's line are two drawings of the
  same overview.
- **Default master volume 80 %** is close to the design's 78 % and leaves headroom; per-track
  gain reaches +6 dB like most mixers.
- **Instrument labels** are a short, fixed list of instrument families so a chip fits a phone
  header; "Other" covers the rest; tracks are not forced to carry one.
- **Track colours** come from the design's clip tints in creation order; a picker is excluded for
  simplicity.
- **Playback continues in the background** like the metronome's click, because a musician may
  lock the phone while playing along, and never restarts itself after an interruption. Inside
  the app it stops when the user leaves Timeline for any other destination (confirmed
  2026-10-13), because no other destination shows a transport bar yet and Tuner and Metronome
  own the audio; the user asked for a confirmation dialog before the stop so that a stray tap
  on the navigation never silences a performance (FR-008a). When Effects and Pads land with
  their own transport bars, the rule can narrow to Tuner and Metronome.
- **Undo** is included although the user did not ask for it: a DAW without undo loses work, and
  the constitution's document model is built for it. Fifty steps per project is enough for a
  session and bounded in memory.
- **The demo project** exists so the first launch shows a working timeline, the CI smoke test
  has something to play, and every story can be tested without an import; its audio is bundled
  and permissively licensed or generated.
- **Design drift** (the tablet's bottom bar, the track-effects title "Effects", the ".0" on the
  tempo, the "Arrange" pill with no second view, the library's "Cloud Library" title and "Pro
  tier" badge, the second project's top transport, bottom navigation, iOS status bars and
  "Studio Sound" tokens) is noted above and not followed.
- **The second Stitch project** (`13820816821466270236`) was pulled on 2026-10-13 at the user's
  request as mixer examples; its screens are image-only (Stitch holds no HTML for them), so they
  live under `design/stitch/screens/mixer-timeline/` as screenshots and are listed in
  `design/stitch/screens.json` with their project. The two arranger screens of the Tembra Audio
  project were re-checked the same day and are unchanged since the 2026-10-06 export.
- **Dark theme only, English only, left-to-right**, as in the earlier features.
- **Branch.** `009-timeline-arranger` was created from `main` by the spec-kit git extension on
  2026-10-13.
