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

## Typography

Typography prioritizes functional clarity, instant scanability, and precision telemetry:

- **Headings & Metric Displays (`Space Grotesk`):** Deliver modern, geometric technical character across track labels, window titles, and HUD overlays.
- **Interface & Track Metadata (`Inter`):** Renders tooltips, system menus, automation parameters, and category names with clarity.
- **Tabular Figures & Metrics:** All numeric values—including timecodes (`00:00.00 / 00:42.20`), parametric EQ readouts (`257Hz`, `dB`), pad indices (`033`, `044`), and tempo (BPM)—must enforce `font-feature-settings: "tnum" 1` to prevent visual jittering as values update in real time.

## Layout & Spacing

The arrangement layout uses an adaptable docked-panel architecture:

- **Layout Grid Model:**
  - **Desktop / Widescreen:** 3-column composite layout. A slim 64px vertical tool navigation rail on the far left, a central fluid sequencer canvas spanning track rows and automation curves, and a persistent right-hand 320px sound matrix / loop library inspector.
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
