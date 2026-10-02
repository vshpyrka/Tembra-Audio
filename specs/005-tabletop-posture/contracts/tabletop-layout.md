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
| Auto Listen | `tuner.autoListen` with `tuner.meter` (or the denied panel's button) | `tuner.settings`: centred "TUNER CONTROLS" heading with the audio-state pill on its line, then `tuner.instrument` over `tuner.mode`, centred |
| By Ear | `tuner.byEar` with every string card | `tuner.settings`: the same heading, then `tuner.instrument`, `tuner.mode`, `tuner.strings` (guitar), `tuner.preset.picker`, the loop-tone row, stacked in that order |

## Metronome (`screen.metronome` root unchanged)

| Upper | Lower |
|---|---|
| `metronome.dialCard` with the tempo dial (readout, marking, − / +); `metronome.cells` with one cell per beat | `metronome.settings`, one card: centred "METRONOME CONTROLS" heading, then `metronome.signature`, `metronome.subdivision` and `metronome.sound` on one line, `metronome.style`, `metronome.slider`, `metronome.tap` and `metronome.play` |

The slider is NOT in `metronome.dialCard` in this layout. The cells keep tap-to-cycle and lighting. From 600 dp of
width (a book-style foldable) the dial card and the accent card sit side by side above the fold; narrower (a
flip-style foldable, ~412 dp) they stack, the dial card over the accent card (T029, found in the T027 review). The
controls card below the fold is the same at every width; its rows wrap.

## Book posture (design iteration D1)

With `verticalFold` passed to `MetronomeScreenContent` in the `Wide` layout: `metronome.settings` ends at least
`spacing.md` before the fold's left edge and `metronome.dialCard`, `metronome.tap` and `metronome.play` start at least
`spacing.md` after its right edge (`MetronomeHingeLayoutTest`). Without it the column is the usual third (≥ 320 dp).

## Unchanged

`Stacked`, `Wide` and `Landscape` keep every existing contract (`specs/003-instrument-tuner/contracts/tuner-screen.md`,
`specs/004-metronome/contracts/metronome-screen.md`); their existing tests must pass unmodified.
