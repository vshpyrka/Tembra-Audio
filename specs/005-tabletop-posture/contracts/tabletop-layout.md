# Contract: Tabletop layouts

UI tests drive the stateless content composables (`TunerScreenContent`, `MetronomeScreenContent`) with
`layout = Tabletop` and a `fold` (null splits the screen in half). Tags below are the test surface.

## Shared

| Tag | Element |
|---|---|
| `<screen>.tabletop.upper` | The region above the fold (`tuner` or `metronome` prefix) |
| `<screen>.tabletop.lower` | The region below the fold |

Invariant (spec SC-001): for every element listed under *Upper*, its bounds lie inside `<screen>.tabletop.upper`; for
every element under *Lower*, inside `<screen>.tabletop.lower`; the two regions do not overlap and are at least
`2 × spacing.md` apart. Keyboard traversal reads the upper region first (FR-010). Each region scrolls on its own when
its content is taller than it (FR-007, SC-005).

## Tuner (`screen.tuner` root unchanged)

| Mode | Upper | Lower |
|---|---|---|
| Auto Listen | `tuner.autoListen` with `tuner.meter` (or the denied panel's button) | `tuner.settings` with the instrument control, the audio-state pill, the mode control |
| By Ear | `tuner.byEar` with every string card | `tuner.settings` with the instrument and mode controls, the string count (guitar), the preset picker, the loop-tone toggle |

## Metronome (`screen.metronome` root unchanged)

| Upper | Lower |
|---|---|
| `metronome.dialCard` with the tempo dial (readout, marking, − / +); `metronome.cells` with one cell per beat | `metronome.settings` with `metronome.bar` (signature menu, subdivision tiles, style row) and the sound pill; the tempo slider; tap tempo; play/pause |

The slider is NOT in `metronome.dialCard` in this layout. The cells keep tap-to-cycle and lighting.

## Unchanged

`Stacked`, `Wide` and `Landscape` keep every existing contract (`specs/003-instrument-tuner/contracts/tuner-screen.md`,
`specs/004-metronome/contracts/metronome-screen.md`); their existing tests must pass unmodified.
