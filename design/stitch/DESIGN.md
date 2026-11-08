---
name: Tembra Audio
colors:
  surface: '#131316'
  surface-dim: '#131316'
  surface-bright: '#39393c'
  surface-container-lowest: '#0e0e11'
  surface-container-low: '#1b1b1e'
  surface-container: '#1f1f22'
  surface-container-high: '#2a2a2d'
  surface-container-highest: '#353438'
  on-surface: '#e4e1e6'
  on-surface-variant: '#cac4d4'
  inverse-surface: '#e4e1e6'
  inverse-on-surface: '#303033'
  outline: '#948e9d'
  outline-variant: '#494552'
  surface-tint: '#cebdff'
  primary: '#cebdff'
  on-primary: '#381385'
  primary-container: '#a78bfa'
  on-primary-container: '#3c1989'
  inverse-primary: '#674bb5'
  secondary: '#ffafd3'
  on-secondary: '#620040'
  secondary-container: '#85145a'
  on-secondary-container: '#ff93c8'
  tertiary: '#d0bcff'
  on-tertiary: '#3c0091'
  tertiary-container: '#ab88ff'
  on-tertiary-container: '#40009b'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e8ddff'
  primary-fixed-dim: '#cebdff'
  on-primary-fixed: '#21005e'
  on-primary-fixed-variant: '#4f319c'
  secondary-fixed: '#ffd8e7'
  secondary-fixed-dim: '#ffafd3'
  on-secondary-fixed: '#3d0026'
  on-secondary-fixed-variant: '#85145a'
  tertiary-fixed: '#e9ddff'
  tertiary-fixed-dim: '#d0bcff'
  on-tertiary-fixed: '#23005c'
  on-tertiary-fixed-variant: '#5516be'
  background: '#131316'
  on-background: '#e4e1e6'
  surface-variant: '#353438'
typography:
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Space Grotesk
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Space Grotesk
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Space Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Space Grotesk
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  tabular-timecode:
    fontFamily: Space Grotesk
    fontSize: 15px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.05em
  tabular-param:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.02em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 0.75rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1.25rem
  space-xl: 1.75rem
---

<!--
Source of truth: Google Stitch project "Tembra Audio DAW Design"
https://stitch.withgoogle.com/projects/12040171611712589599
Design system asset: assets/fb1657e091d24ba8a3e2aa5dee703ce3 (version 1)
Pulled verbatim via the Stitch MCP server on 2026-09-17. Edit in Stitch, then re-pull; do not hand-edit here.
-->

## Brand & Style

This design system embodies a modern, tactile, and precision-engineered digital audio workstation aesthetic. It serves audio engineers, music producers, sound designers, and mobile creators who demand professional-grade sequencing and arrangement tools without cognitive friction or visual fatigue during extended studio sessions.

The design movement combines **Sleek Studio Dark Minimalism** with **Tactile Surface Engineering**. Visual depth is achieved through subtly layered charcoal slate surfaces, high-fidelity neon-lavender playheads, dusty rose waveforms, and muted wine trigger pads. The atmosphere is nocturnal, focused, and calibrated for both precision mouse operations and fluid fingertip gestures on multi-touch glass.

## Colors

The palette is tuned specifically for low-light studio environments, minimizing retina strain while elevating the hierarchy of active audio elements:

- **Base & Canvas:** `#0F0F12` anchors the viewport shell, surrounded by `#18171C` for master sidebars and track backplates, and `#212026` for individual arrangement track lanes.
- **Surface Elevation Containers:** `#292832` provides soft containment for floating toolbars, parameter chips, and pad grids, while `#32303C` serves as the raised interactive state for hovering or pressed controls.
- **Primary Lavender (`#A78BFA`, `#C4B5FD`):** Designates active playheads, automation curve lines, frequency nodes, and primary timeline navigation.
- **Secondary Rose / Mauve (`#F472B6`, `#FDA4AF`, `#E879F9`):** Reserved for recorded vocal/instrument waveform clips, alert markers, and loop regions.
- **Muted Wine / Plum (`#5B2A3D`, `#78354E`, `#9D4B68`):** Form the background matrices for drum/sampler matrix pads, providing tactile warmth against the deep slate canvas.
- **Grid & Measurement Overlays:** Semi-transparent violet tints (`#A855F71F`, `#C084FC33`) draw bar divisions, beat subdivisions, and automation decibel horizontal rules.
- **Recording (feature 010):** `record` (`#E36F7E`, a softened coral red) fills the arm toggle of an armed track and every state of the Record control, and never appears anywhere else. The input meter uses `meter_body` (`#94D39A`, green) up to −6 dB, `meter_warning` (`#F5E786`, pale yellow) above it, and `meter_clip` (`#F472B6`, the palette's rose) for the clip latch. These four sit beside the palette rather than inside it, so a recording state reads at a glance against the lavender and rose of playback.

## Typography

Typography prioritizes functional clarity, instant scanability, and precision telemetry:

- **Headings & Metric Displays (`Space Grotesk`):** Deliver modern, geometric technical character across track labels, window titles, and HUD overlays.
- **Interface & Track Metadata (`Inter`):** Renders tooltips, system menus, automation parameters, and category names with clarity.
- **Tabular Figures & Metrics:** All numeric values—including timecodes (`00:00.00 / 00:42.20`), parametric EQ readouts (`257Hz`, `dB`), pad indices (`033`, `044`), and tempo (BPM)—must enforce `font-feature-settings: "tnum" 1` to prevent visual jittering as values update in real time.

## Layout & Spacing

The arrangement layout uses an adaptable docked-panel architecture:

- **Layout Grid Model:**
  - **Desktop / Widescreen:** 3-column composite layout. A slim 96px vertical navigation rail of icon-only items on the far left (the collapsed Material 3 Expressive rail since 2026-10-17; it was 64px with labels before), a central fluid sequencer canvas spanning track rows and automation curves, and a persistent right-hand 320px sound matrix / loop library inspector.
  - **Tablet (Portrait / Landscape):** The sound matrix switches to a collapsible drawer or docks beneath the timeline, preserving horizontal real estate for 4–8 visible audio tracks.
  - **Mobile (~390px):** Single-column workspace. The bottom transport bar docks permanently above native system indicators, switching to an expandable thumb-accessible sheet for pad triggers, track controls, and category filters.
- **Density Rhythm:** Spacing adopts a tight base module (4px/8px) to maximize track density and multi-track visibility while preserving standard 44px minimum touch targets across all transport buttons and sampler trigger pads.

## Elevation & Depth

Visual hierarchy avoids heavy drop shadows, relying on tonal surface stepping, refined border seams, and luminescent accents:

- **Canvas Level 0 (`#0F0F12`):** Ground viewport substrate; deepest black backing the entire DAW window.
- **Surface Level 1 (`#18171C`):** Track list container background, global top application bar, and bottom transport bar base.
- **Surface Level 2 (`#212026`):** Audio track rows and empty arrangement lanes, delineated by 1px subtle borders (`#2B2A34`).
- **Surface Level 3 (`#292832`):** Floating inspector panels, utility buttons, sound category pills, and inactive sampler pads.
- **Surface Level 4 (`#32303C`):** Active popover parameter cards (e.g., node automation HUD showing Speed, Time, Ease, Blend) and elevated dialogue sheets.
- **Luminescent Depth:** Waveform clips and active nodes employ a delicate inset ambient glow (`box-shadow: 0 0 12px rgba(167, 139, 250, 0.25)`) to communicate signal presence without producing visual glare.

## Shapes

The geometric architecture balances soft tactile radii with clean, rectilinear workspace borders:

- **Track Lanes & Window Panes:** Soft structural perimeter (`rounded-lg` or 12px–16px) with clean flush internal dividers.
- **Audio Clips & Waveform Blocks:** Smooth rounded rectangles (`rounded-md` or 8px) with internal padding that cradles rendered audio amplitude spikes.
- **Trigger Matrix & Sound Pads:** Squircle pads (`rounded-md` to `rounded-lg` or 8px–12px) featuring balanced, tactile touch wells.
- **Transport Controls & Status Chips:** Full pill radii (`rounded-full` or 9999px) for playhead speed chips (`1x`, `1.5x`), metronome toggles, play/pause circular rings, and category badges.

## Components

### Buttons & Transport Controls
- **Transport Bar:** Anchored dock housing playback triggers (Play, Pause, Return to Zero, Fast Forward, Loop, Metronome). Primary play/record buttons utilize high-contrast white-on-dark or illuminated lavender icons.
- **Utility Buttons:** Pill or soft-square geometries with `#292832` fill, transitioning to `#32303C` on hover. Active toggles (e.g., Metronome On, Solo, Mute) illuminate with full lavender (`#A78BFA`) or muted rose fills.

### Audio Clips & Track Lanes
- **Audio Track Row:** High-contrast horizontal band featuring track title, mute/solo icons, and track options (`...`).
- **Waveform Blocks:** Encapsulated audio events with rose (`#F472B6`) or lavender (`#C4B5FD`) fills and dark contrasting interior waveform vectors. Clips support drag handles on edges for fades and trimming.

### Automation Lanes & Control Curves
- **Curve Canvas:** Integrated sub-track grid displaying frequency and level steps with continuous Bezier curves.
- **Control Points:** Draggable circular nodes (`#A78BFA` with `#FFFFFF` center) displaying a floating parameter readout HUD (`#18171C` background, 1px border) with tabular readouts for speed, easing, and blend values.

### Trigger Matrix / Drum Pads
- **Sound Matrix (4x3 / 4x4 Grid):** Uniform grid of tactile buttons rendered in deep wine (`#5B2A3D`). Each pad displays a monospaced identifier (`033`–`044`) in the upper-left corner and a minimalist centered iconography glyph. Active or triggering pads pulse into vibrant magenta/mauve (`#9D4B68`).

### Category Chips & Filters
- **Filter Tags:** Horizontal scrolling row of pill-shaped tags (`Piano`, `Bass`, `Drums`, `Synth`). Inactive states adopt neutral dark slate fills; active tags switch to filled plum/mauve accents with high-contrast text.

### Scrubber & Playhead
- **Global Playhead:** Full-height vertical line (`#C084FC`) anchored by a circular indicator node, cutting cleanly through all track lanes and automation zones to indicate live time position.

### Recording: arm, Record and the input meter
- **Arm toggle:** a 44px target in every track header after M and S: a hollow circle in `outline` at rest, a filled `record` disc when the track is armed. Only one track is armed at a time.
- **Record control:** a 44px target drawing the record circle in `record`. Idle is the hollow circle; count-in is the filled disc with a ring that pulses out once per beat (still under reduce motion); recording is the filled disc with a soft glow. It sits in the transport bar from 432px wide, between Forward and the readout; below that it moves into the armed track's compact header, in the place of the instrument chip. While a take runs, the readout counts the take, and during the count-in it reads `Count-in N`.
- **Input meter:** a 4px bar along the armed header's bottom edge, from −60 dB to 0 dB: `meter_body` up to −6 dB, `meter_warning` above, a 2px peak-hold mark, and a small `meter_clip` square at the right end that lights while the input has clipped and clears on a tap. Nothing is drawn while no track is armed.
- **Growing take:** the take is drawn as an ordinary clip in the track's tint, named `Take N`, growing from its start to the playhead, with its waveform filling in as it records.
- **Monitoring:** a headphones toggle in the toolbar on tablets ("Monitor input" in the overflow menu on phones). It is refused on the built-in speaker, with the snackbar "Connect headphones to monitor the input".
- **Messages:** a denied microphone shows the snackbar "Microphone access is needed to record" with "Open settings"; leaving during a take asks "Stop recording?" with "Stop recording and leave" and "Keep recording"; the count-in length (Off, 1 bar, 2 bars) lives in the metronome settings sheet.

### Mixer: strips, fader, knob, meters and VU
- **The sheet:** the mixer rises over the timeline's lanes from a 32px grabber row (the pill and the label `Mixer`; `surface-level-2` with the 16px top corners, a 1px `surface-level-2-border` seam). Two heights on phones and tablets: half (the larger of 256px and 45% of the lanes box; on expanded windows the height that leaves two lanes visible) and full; phone landscape has the full height only. The Mixer button sits in the toolbar after Redo (in the overflow menu, `Mixer`, when the compact bar is too narrow at a large font); `X` toggles it. Where the platform has free-form windows an `open_in_new` icon button at the grabber's end opens the console in a window of its own.
- **Strip:** 72px wide, top to bottom the track number (`tabular-param`), in the full form a VU dial and the pan knob, the Solo and Mute toggles (36px, `S` and `M`; Solo lit in `primary`, Mute dimly in `primary` at 35% with the letter in `on-surface`), the fader with the level meter beside it, the ellipsised track name (`label-md`); a 4px band in the track's tint along the base, dimmed with the strip when the track is not heard. The selected track's number and name read as selected; the strips scroll horizontally with a scrollbar under a mouse; the master strip is pinned at the right in `surface-level-3`.
- **Fader:** a 4px rail in `surface-container-highest` with the lit part below the handle in `primary`, a 44×28px handle (`surface-container-high`, `surface-container-highest` on hover, an `on-surface` centre line) and `micro-label` scale marks (+6, 0, −6, −12, −24, −40, −∞ in `on-surface-variant`); at least 120px of throw. A console taper: 0 dB at 70% of the travel, the top +6 dB, the bottom −∞. Keyboard focus draws the 2px `primary` ring; the hover layer tints the whole rail area. The master's fader is the same part in percent (0–100, home 80).
- **Pan knob:** a 40px dial (`surface-container-high`) with a 270° arc from lower left over the top to lower right in `outline-variant`, the turned part from the centre to the pointer in `primary`, an `on-surface` pointer; readout `C`, `L 35`, `R 20` in the track header under the gain. A vertical drag of 2px per percent, a release within 5% snaps to the centre, a double tap returns to it.
- **Level meter:** two 6px bars (left and right) under a clip square, `meter_body` to −6 dB and `meter_warning` above, a 2px peak-hold mark; the square lights `meter_clip` when the strip clipped and clears on a tap. The master's meter is the same part on the summed output.
- **VU dial:** a 48×28px half-dial in the full form, −20 to +3 VU with 0 VU at −18 dBFS, the needle's region above 0 VU in `meter_warning`; it reads the level's RMS, the bars read peaks.
- **Tooltips and hover:** every control names itself on hover and keyboard focus (`Drums, gain`, `Drums, pan`, `Master volume`); the hover layer is `on-surface` at 8% over the control's whole hit area.

### Sliders
- **Shared anatomy (tempo slider, noise gate slider):** a 6px track with full radius in `surface-container-highest` (`#353438`), a 16px `primary` (`#CEBDFF`) disc as the handle, and small end labels in `tabular-param` with the `outline` tone (`#948E9D`): `40` / `240` on the tempo slider, `Off` / `High` on the noise gate. A press anywhere on the track moves the handle to the finger; the arrow keys move it one step. The handle's touch box is at least 44px tall; the noise gate's is 44×44px, so the track starts and ends 22px inside the row (Material adds 10px of its own).
- **States:** hover, press, drag and keyboard focus draw a state layer around the handle, `primary` at 16% and twice the disc's diameter; focus adds a 2px `primary` ring. No value bubble, no tick marks.
- **Noise gate (tuner, Auto Listen):** the row sits under a `label-sm` caption `NOISE GATE` in `on-surface-variant`, uppercase. The handle is the gate level and the track is the live input level: from the start of the track to the input's level, in `primary` while the sound is read and in `outline` while the gate holds it back, empty while not listening. The scale is never printed; the start of the track means off. It goes at the foot of the Auto Listen card under the gauge (upright phone, tablet), and after the mode selector in the settings column (phone sideways) and in the Tuner controls card (foldable tabletop and book).

