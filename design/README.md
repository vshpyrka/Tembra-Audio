# Design

## `stitch/`

The app UI design, exported from Google Stitch (project 12040171611712589599). `stitch/DESIGN.md` and `stitch/theme.json` are the design-system source of truth; `stitch/screens/` holds HTML and full-size screenshots for all 8 screens in phone and tablet variants. See `stitch/README.md` for the screen catalogue, navigation model and build notes.

## `brand/`

- `logo/` — current logo candidates (`logo-v1.jpeg`, `logo-v2.jpeg`).
- `logo-concepts/generate-all.sh` — prompt script that generates further logo concepts with the Antigravity image CLI. Outputs land next to the script. Run it from a terminal inside the Antigravity IDE (see the header comment).

## `mockups/`

Reference UI mockups used in the research and referenced by feature specs:

| File | Screen |
|------|--------|
| `01-tablet-portrait-timeline-loop-pads.png` | Tablet portrait: multitrack timeline with the loop-pad matrix docked at the bottom |
| `02-phone-portrait-timeline.png` | Phone portrait: timeline with bottom navigation (Timeline, Users, Comments, Remote) |
| `03-tablet-landscape-timeline-loop-library.png` | Tablet landscape: timeline with the categorised loop library in the right sidebar |
| `04-tablet-landscape-automation-effects.png` | Tablet landscape: automation curve editor with effect list and metronome/speed controls |
