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
- The time-signature, subdivision, style and sound sections are not there yet (User Stories 2 and 4), so the
  column has empty space above the pinned tap and play row.
- The play button's glow is clipped by the bottom navigation bar at its lower edge (as in the design screenshot).

### Fix round 1 (2026-09-30)

- **Ring gradient.** The first captures drew `tempoArc` from the dial box's top-left to its bottom-right, unrotated.
  The design's SVG carries the same top-left → bottom-right gradient but is drawn turned −90°, so on screen it runs
  from bottom-left (`primary`) to top-right (`secondary`): the fill starts warm, pink-lavender, at 12 o'clock and
  cools to violet towards its end, as in `design/stitch/screens/metronome/phone/screenshot.jpg`. The earlier note
  blamed the sweep length; that was wrong (110° at 120 BPM is the spec's formula and the design's dash is only the
  mock's value). The dial now turns the brush with the SVG; `android-compact-stopped.png` and
  `android-compact-running-beat1.png` were retaken and match the design's colouring.
- **Glyphs.** The play and pause glyphs are now the filled Material Symbols (`PlayArrowFilled`, `PauseFilled`,
  fetched with `tools/fetch-material-symbols.sh --fill`), as the design draws them; checked on the Pixel captures.
- **iOS retake.** The iOS captures could not be retaken: the Mac's screen locked during the run, so the Simulator
  window could not be driven (no window in the accessibility tree) to reach the Metronome destination.
  `ios-compact-stopped.png` and `ios-compact-running-beat1.png` still show the first build (outlined glyph, unrotated
  gradient). The rotated gradient and the filled glyph are covered on the simulator by `TempoDialTest.
  theRingGradientRunsFromBottomLeftToTopRight` and `MetronomeControlsTest.thePlayButtonUsesTheFilledGlyphs`; the two
  iOS captures are to be retaken when the Mac is unlocked.

## User Story 2 (batch B9, T106), 2026-09-30

**Devices**: Pixel 6 Pro (Android 17, serial 19021FDEE001AH, 411 dp wide, debug APK from `:androidApp:assembleDebug`);
iPhone 17 simulator (iOS 26.5, 402 pt wide, Debug build from `xcodebuild -scheme iosApp`). Both runs were launched with
the debug argument (`--es tembra.metronome.syncopation <id>` / `--tembra-metronome-syncopation=<id>`), which also opens
the app on the Metronome destination. The simulator was driven through System Events `click at` on the Compose
accessibility elements (the Mac was unlocked this time).

| File | What it shows |
|---|---|
| `<platform>-compact-standard.png` | Launch state: 4/4 and 1/4 selected, "Common & Complex" caption, A C C C, "Standard" selected |
| `<platform>-compact-downbeat.png` | After "Downbeat": A S S S and the Downbeat pill selected |
| `<platform>-compact-syncopated.png` | Syncopated + 1/8, running (pause glyph), relaunched with `off-beat-ticks` (Android) / `all-but-one` (iOS): the cells keep the even-beat pattern C A C A |
| `<platform>-compact-7-8.png` | 7/8 + Standard: the subdivision chips read 1/8, 1/16, 1/32, 3let with 1/8 selected; seven cells A C C C C C C in two rows (4 + 3) |
| `<platform>-compact-6-8-labels.png` | 6/8 + Standard: A C C A C C, x/8 subdivision labels; two rows of three on the Pixel, one row on the iPhone 17 |

### Quickstart §4, steps 1–4 and 6, by observation

- **Step 1.** 7/8 gives seven cells, Accent then six Clicks, and the x/8 labels with 1/8 selected (both platforms).
  Running across a signature change was not listened to; the grid is sent at once and the engine maps the position
  (research R1, `metronome_renderer_test`).
- **Step 2.** 1/8 on 4/4 leaves the cells unchanged (the grid is `ASCSCSCS`, `MetronomeViewModelTest`); the soft tick
  between beats is an ear check left for the user.
- **Step 3.** Pixel: from Downbeat (A S S S), tapping cell 2 gave A A S S and every style pill deselected; Syncopated
  then rewrote the cells to C A C A and selected its pill. Simulator: "Downbeat" gave A S S S with the pill selected.
- **Step 4.** 6/8 + Standard shows A C C A C C; 4/4 + Syncopated shows C A C A. What they sound like is left for the
  listening check.
- **Step 6 (debug readings).** `off-beat-ticks` (Pixel) and `all-but-one` (both) were each launched with Syncopated and
  1/8 stored and played: the app opened on the Metronome, the settings were restored, playback started and the cells
  kept the even-beat pattern C A C A. The grids themselves (`SASASASA` for off-beat ticks with 1/8, `SAAA` for all but
  one with 1/4) are asserted by `MetronomeViewModelTest`; the audible difference is an ear check for the user. A
  release build never reads the argument (`BuildConfig.DEBUG` in `MainActivity`, `#if DEBUG` in `ContentView.swift`).
- Once, the Pixel opened on Home rather than the Metronome: the start was sent right after `adb install -r`, while
  the freshly installed process was still coming up. Three later force-stop + start runs with the extra all opened on
  the Metronome.

### Found and fixed on the Pixel: truncated "ACCENT" labels

The first 6/8 and 7/8 captures on the Pixel kept every cell in one row (seven 46 dp cells fit the 363 dp row at the
44 dp target), so the "ACCENT" label was clipped to "ACCE". `AccentCells` now also floors a cell at its widest level
label ("ACCENT" in bold, measured with the theme's `microLabel`) plus its padding, so 6/8 and 7/8 wrap on the Pixel and
large fonts wrap earlier, while the iPhone 17's wider row still fits 6/8 in one row. The test is
`AccentCellsTest.cellsWrapBeforeTheirLabelsWouldClip` (RED on the simulator before the fix).

### Against the Stitch phone screen (`design/stitch/screens/metronome/phone/screenshot.jpg`)

Matches: the section order and cards (`surfaceContainerLow`, 12 dp corners, 8 dp padding); "TIME SIGNATURE" with the
"Common & Complex" caption on the right; six equal chips in one row with 4/4 in `secondaryContainer`; four
subdivision chips with the note glyph and 1/4 in `primaryContainer`; the cells card with the "STYLE" row under a seam
and the three pills, Standard in `secondaryContainer`.

Differences, to settle later:
- The style row is taller than the design's (the pills keep a 44 dp target, the design's buttons are about 28 dp), so
  the gap between the cells and the row reads larger.
- The design draws beat 3 as SUB in its 4/4 illustration; Standard is A C C C by the spec (FR-010, research table).
- The sound pill ("Classic Wood") between the cells card and the dial is not there yet (User Story 4).
- The caption uses the tabular-parameter face at the section label's 10 sp size (the design's `text-[10px]`).
- The two iOS User Story 1 captures noted as stale above (`ios-compact-stopped.png`, `ios-compact-running-beat1.png`)
  were not retaken in this batch.

## User Stories 3 and 4 (batch B10, T111 and T117), 2026-09-30

**Devices**: Pixel 6 Pro (Android 17, serial 19021FDEE001AH, debug APK from `:androidApp:assembleDebug`); iPhone 17
simulator (iOS 26.5, Debug build from `xcodebuild -scheme iosApp`). Both were launched with the debug argument, which
opens the app on the Metronome. The Pixel was driven with `adb shell input tap` (tap loops run inside the device shell
so the intervals are steady) and read with `uiautomator dump`; the simulator with System Events `click at` on the
Compose accessibility elements (the Mac was unlocked).

| File | What it shows |
|---|---|
| `<platform>-compact-picker-open.png` | 4/4 stopped, the sound pill "Classic Wood" under the cells card and its menu open: Classic Wood (primary, check), 808 Rim, Digital Beep, Cowbell, Mechanical, Custom with a "+" |

### Quickstart §6 (tap tempo), by observation

- **One tap**: tempo unchanged, caption "Tap again" (Pixel).
- **A steady run**: ten taps every ~550 ms on the Pixel (0.5 s sleep plus the `input tap` cost) settled on 109 BPM,
  six taps every ~710 ms on the simulator (AppleScript clicks) gave 84 BPM with the caption "6 taps"; both match the
  intervals the drivers produce. A steady 100 BPM by finger is the user's check.
- **Found and fixed on the Pixel**: ten taps read "9 taps" because the sequence keeps the last nine taps for the
  average. The caption now counts every tap of the run (`MetronomeViewModelTest.theCaptionCountsEveryTapOfALongRun`,
  RED before the fix); twelve taps read "12 taps" on the Pixel afterwards.
- **Fast and slow**: taps as fast as `input tap` allows gave 240; three taps 1.9 s apart gave 40.
- **Rest**: three seconds after the last tap the caption reads "Sync finger rhythm" again and the next tap starts over
  ("Tap again") on both platforms.
- **Running**: taps while running changed the tempo without a restart (the play button kept "Stop metronome"; the
  grid is re-sent, `MetronomeViewModelTest.aSecondTapSetsTheTempoThroughTheSettingsPath`). Whether the click adopts the
  new tempo on the next tick is an ear check for the user.
- **Flash**: the 120 ms `primary` flash is verified by `MetronomeScreenTest` with the frozen clock (and absent under
  a zero `MotionDurationScale`); it is too short to catch in a device screenshot.

### Quickstart §4 step 5 (sounds), by observation

- The menu lists the six entries in the spec's order with the current one checked (both platforms, captures above).
- Stopped: choosing 808 Rim closed the menu and the pill read "808 Rim" (both); the engine calls
  `selectSound(Rim808)` then `previewClick(Click)` (`MetronomeViewModelTest`).
- Running: choosing Cowbell kept the click running ("Stop metronome") and the pill read "Cowbell" (both).
- Not listened to (driven over adb and AppleScript): the preview click, the swap on the next tick, and the three levels
  of each sound (Accent clearly louder, Sub clearly softer) are left for the user's ear check.
- Custom with nothing imported opens the platform WAV picker; preparing and selecting the picked file is User Story 5.

### Against the Stitch phone screen

Matches: the pill between the cells card and the dial card, centred, `surfaceContainerHigh`, fully rounded, with the
`graphic_eq` glyph in `primary`, the name in `labelMedium` and the `expand_more` chevron in `outline`; the 160 dp menu
on `surfaceContainerHighest` with 12 dp corners, the current entry in `primary` with a check and the others in
`onSurfaceVariant`. Differences: the menu's rows are Material's 48 dp (the design's `py-1.5` rows are about 32 dp), so
the menu is taller; it opens above the pill on both phones (Material positions it);
the design's list ("808 Click", "Studio Rim", no Custom) is replaced by the spec's (FR-021).
