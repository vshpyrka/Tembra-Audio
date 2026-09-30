# Feature 004 metronome: screenshot and device notes

## User Story 1 (batch B8, T098), 2026-09-30

**Devices**: Pixel 6 Pro (Android 17, serial 19021FDEE001AH, 1440 × 3120, debug build through
`:androidApp:installDebug`); iPhone 17 simulator (iOS 26.5, Debug build from `xcodebuild -scheme iosApp`).

| File | What it shows |
|---|---|
| `android-compact-stopped.png` | Metronome destination at launch: 120 BPM, "Moderato (108-120)", A C C C cells, play button |
| `android-compact-running-beat1.png` | Running at 120 BPM with beat 1 lit (secondary outline on the Accent bar box), pause glyph |
| `android-compact-tempo-240.png` | Running at 240 after holding "+": "Prestissimo (200-240)", full ring, "+" greyed, beat 2 lit |
| `android-compact-tempo-40.png` | 40 BPM after dragging the slider to its start: "Largo (40-60)", empty ring |
| `ios-compact-stopped.png` | The same stopped screen on the simulator |
| `ios-compact-running-beat1.png` | Running at 120 BPM with beat 1 lit, pause glyph |
| `ios-compact-tempo-240.png` | 240 BPM, "+" greyed |
| `ios-compact-tempo-40.png` | 40 BPM, "−" greyed |

### Quickstart §3, steps 1–3, by observation

- **Play and pause.** Pixel: play starts the engine stream (`dumpsys audio` lists the app's AAudio player as
  `started`) and the foreground service (`MetronomeForegroundService`, `isForeground=true`, type media playback);
  pause stops both at once (no started player, no service). The click itself was not listened to in this session:
  the run was driven over adb, so audibility and "no pop" are left for the user's listening check (quickstart §3.1).
- **Beat light.** A burst of screen captures at 120 BPM caught the outline moving cell 2 → cell 3 → cell 1 in turn
  on both platforms; only one cell is outlined at a time, and none while stopped.
- **Steppers.** "+" gives 121 "Allegro (120-156)", "−" back to 120 "Moderato (108-120)" (the slower name on the
  boundary). Holding "+" for 1.5 s on the Pixel went 120 → 134 (one step at 400 ms, then every 80 ms); holding
  longer stops at 240 with "+" greyed. The same at 40 with "−" greyed on the simulator.
- **Slider and ring.** Dragging the slider to its start gives 40 "Largo (40-60)". A drag on the ring from 12 o'clock
  to 3 o'clock gives 105 (a quarter of the 275° track), a shorter one 74 "Adagio (66-76)"; a drag that starts in the
  centre does nothing (UI test), so the column still scrolls.
- **Tempo change while running (step 3).** The new grid is sent on every change and applied by the engine from its
  next block (research R1, `metronome_renderer_test`); the cells kept lighting in turn at the new spacing. The
  "no skipped or doubled click" part is an ear check left for the user.
- The simulator run was driven through macOS Accessibility: System Events `click at` lands on the app's
  accessibility element under the point (the Simulator exposes the Compose semantics tree), so a slider click
  acts as an increment rather than a jump; the steppers were used for 40 and 240 there.

### Against the Stitch phone screen (`design/stitch/screens/metronome/phone/`)

Matches: the dial card's radial glow and vertical overlay, the 275° ring with round caps and the eight tick marks,
the steppers sitting on the ring at 9 and 3 o'clock, "120" with "BPM" in `secondary` and the marking in
`tertiary`, the slider's grey 6 dp track with the `primary` thumb and "40" / "240" end labels, the cells (Accent
bar in `secondary` with its pink glow, Click bars in `primary`, labels under them), the tap-tempo card and the
round `secondaryContainer` play button with its pink glow.

Differences, to settle later:
- The play and pause glyphs are the outlined Material Symbols (`FILL 0`, as every bundled glyph); the design uses
  the filled play arrow. A filled variant needs `tools/fetch-material-symbols.sh` to fetch `FILL 1` glyphs.
- The ring's gradient runs corner to corner across the dial box as in the SVG, so at 120 BPM the fill ends in the
  violet middle; the design screenshot's arc is drawn with a longer dash (`228` of 603 ≈ 136°) and reaches further
  into the pink. The fill here follows the spec's `(bpm − 40) / 200 × 275°` = 110°.
- The time-signature, subdivision, style and sound sections are not there yet (User Stories 2 and 4), so the
  column has empty space above the pinned tap and play row.
- The play button's glow is clipped by the bottom navigation bar at its lower edge (as in the design screenshot).
