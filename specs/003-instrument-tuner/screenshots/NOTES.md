# Verification notes: instrument tuner (feature 003)

Devices and simulators used (as in feature 002):

| Role | Device | OS | Notes |
|---|---|---|---|
| Android compact / expanded | Pixel 6 Pro (serial 19021FDEE001AH) | Android 17 | expanded band through `adb shell wm density 380` in landscape (606 dp window), `wm density reset` afterwards |
| iOS compact | iPhone 17 simulator (4CD6879C-8FBA-4CEF-B4EC-31968D0144B0) | iOS 26.5 | bundle id carries the local `TEAM_ID`: read `CFBundleIdentifier` from the built `Info.plist` before `simctl launch` |
| iOS expanded | iPad Pro 13-inch (M5) simulator (C9A67FF1-62FC-4BE6-89F9-685DC39FBD46) | iOS 26.5 | landscape through the Simulator menu (AppleScript); `simctl io screenshot` writes the portrait framebuffer, rotate with `sips -r -90` |
| physical iPhone | iPhone 14 (devicectl 48CD2B09-42B3-5156-9DD4-658E30BD0C1C) | iOS 26.5.2 | the real permission prompt and calls; no CLI screenshot |

Capture names: `<platform>-<compact|expanded>-<autoListen|byEar|denied>[-<signal id>|-largefont].png` (quickstart §8).
The state matrix `<platform>-compact-autoListen-<id>.png` comes from `app/tools/capture-tuner-states.sh`.

## Outcomes

Filled in per user story below as the checks are run.

### Automated lanes (T150 evidence)

| Lane | Result (2026-09-26) |
|---|---|
| `engineHostTest` (Catch2, macOS host) | 62 / 62 passed: ABI 2, seqlock, ring buffer, band-pass, MPM accuracy and octave (every semitone A1–E5 at 48 and 44.1 kHz within 1 cent), analyzer response/stability/resting/watchdog, tone generator (pitch, ramps, re-arm, switch, 60 s loop), sample player, recordings on pitch (vacuous: no bundled recording), engine state machine with the fake device and the synthetic test signal |
| `checkAndroidHost` (JVM) | core 27, engine 19, data 7, ui 101 (`TunerViewModelTest` 30 cases): 0 failed, zero warnings |
| `checkIos` (iPhone 17 simulator) | core 27, engine 27, data 12, ui 188: 0 failed, zero warnings. `TunerSignalEndToEndTest` reads all 14 catalogue signals within 1 cent on the real engine. `NativeAudioEngineTest` records that the bare simulator test process has no RemoteIO output unit (the tone round trip reports the device error there; the simulator app plays tones) |
| `checkAndroidDevice` (Pixel 6 Pro, Android 17) | core 27, engine 12, data 28, ui 186: 0 failed. `TunerSignalEndToEndTest` and the real tone round trip pass on the device |
| `:androidApp:connectedDebugAndroidTest` | `AndroidMicrophonePermissionTest` 3 / 3 |

### User Story 1 — Auto Listen (quickstart §3, §7, §8 state matrix)

- State matrix: `android-compact-autoListen-<id>.png` and `ios-compact-autoListen-<id>.png` for all 14 catalogue ids, produced by
  `app/tools/capture-tuner-states.sh --android` and `--ios "iPhone 17"` (iOS needs the 5 s settle the script now defaults to: the
  simulator's first tuner frame takes longer than a device's). Reviewed against data-model.md §9:

  | id | expected | seen (both platforms) |
  |---|---|---|
  | e2-in-tune | E2, needle centred, IN TUNE lit | E2, 82.4 Hz, REFERENCE 82.4 HZ, needle centred, pill lit |
  | e2-flat-20 | E2, 36° left, Flat lit | E2, 81.5 Hz, needle left of centre, Flat and its arrow in pink, pill unlit |
  | e2-sharp-20 | E2, 36° right, Sharp lit | E2, 83.4 Hz, needle right, Sharp lit |
  | e2-flat-45 | E2, 81° left | E2, 80.3 Hz, needle at the flat end mark |
  | a2-sharp-40 | A2, 72° right | A2, 112.6 Hz, needle near the sharp end |
  | d3-flat-5 | D3, Flat lit, pill unlit | D3, 146.4 Hz, small left lean, Flat lit, pill unlit |
  | g3 / b3 / e4 / c2 / b1 / a1 / e5 in tune | note named, pill lit | each note named with the right octave (C2, B1, A1 read as such, not an octave up), pill lit |
  | out-of-range-2khz | resting | "Listening", needle centred, pill unlit, no labels lit |

- `android-compact-autoListen.png` (real microphone, Pixel 6 Pro) and `ios-compact-autoListen.png` (real microphone, iPhone 17
  simulator using the Mac's input): pill MIC ACTIVE, meter resting on "Listening" in a quiet room; the iPad and the Pixel in the
  expanded captures happened to hear a steady room tone (B2 126 Hz on the Mac's input, C4 on the Pixel), which the chromatic meter
  names as a tuner should. No instrument was available in this session: SC-004 (a real guitar and cello brought to "In tune")
  remains for the user; the offline accuracy proof is `engineHostTest` and the on-device proof is the end-to-end test-signal lane.
- OS microphone indicator: the Android status bar shows the microphone icon while listening (`android-compact-autoListen.png`,
  top right) and it is gone in the By Ear and denied captures; on the simulator the orange dot is not rendered by `simctl`.
  Leaving the Tuner tab, backgrounding and switching to By Ear stop the stream (`stopListening` is asserted by
  `TunerViewModelTest`; the engine closes the Oboe/RemoteIO stream synchronously).

### User Story 2 — instrument, strings, presets (quickstart §4)

- `android-compact-byEar.png`, `ios-compact-byEar.png`: six cards VI…I with E2 82.4 Hz … E4 329.6 Hz, the string-count
  selector, the preset picker "Standard (E2 A2 D3 G3 B3 E4)" and the Loop Tone toggle "Active" (since 2026-09-28 the string count
  sits in the By Ear card beside the picker and Auto Listen shows none of it; see Phase 11 below). Relaunching after switching to By Ear reopened the Tuner in
  By Ear on both platforms (the DataStore round trip; `DataStoreTunerPreferencesTest` covers the file). The 7-string and cello
  card sets, Drop A and Drop D are asserted by `TunerScreenByEarTest` and `TunerViewModelTest`.

### User Story 5 — permission (quickstart §6)

- Android (`adb shell pm revoke`, then Tuner in Auto Listen): the system prompt "Allow Tembra Audio to record audio?" appeared;
  "Don't allow" → `android-compact-denied.png` ("Microphone needed", the FR-014 sentence, "Allow microphone"); "Allow microphone"
  showed the prompt again (rationale state); denying again then "Allow microphone" opened the app's settings page (App info,
  Permissions); granting and returning started listening (MIC ACTIVE) with no further action.
- iOS (`simctl privacy revoke`): `ios-compact-denied.png`; "Allow microphone" opened the Settings app (iOS never re-prompts);
  after `simctl privacy grant` and a relaunch listening started.
- Interruptions (a call, another app's audio, headphone route changes) need a physical device and a human: not performed in
  this session. The reactions are asserted against the fakes in `TunerViewModelTest` (Interrupted / InterruptionEnded /
  DeviceLost / OutputRouteLost) and the platform event sources are `AndroidAudioFocus` and `IosAudioSession`.

### User Story 6 — layouts, input, screen reader (quickstart §8, §9)

- Expanded: `android-expanded-autoListen.png`, `android-expanded-byEar.png`, `android-expanded-autoListen-e2-flat-20.png`
  (Pixel 6 Pro at `wm density 380`, landscape, 606 × 280 dp: one selector row, centred meter, three-column cards; the short
  landscape window scrolls the meter card), `ios-expanded-autoListen.png`, `ios-expanded-byEar.png`,
  `ios-expanded-autoListen-e2-flat-20.png` (iPad Pro 13-inch landscape, rotated upright with `sips -r -90`).
- Large fonts: `android-compact-autoListen-largefont.png`, `android-compact-byEar-largefont.png` (`font_scale 2.0`),
  `ios-compact-autoListen-largefont.png`, `ios-compact-byEar-largefont.png` (Larger Text XXXL). The first iOS pass showed the
  "Tuning Preset" overline and the "By Ear" label clipped when their rows no longer fit; both rows now wrap (FlowRow) and the
  captures were retaken.
- Keyboard: the iOS captures were driven entirely from the Mac keyboard through the simulator (Tab moves focus along the rail,
  the segments, the picker, the toggle and the card buttons in reading order; Return activates), which is how By Ear, the
  denied state and the settings link were reached. Hover and Android desktop-mode keyboard use, and TalkBack / VoiceOver
  announcements, were not exercised by a person in this session; the semantics they read are asserted by
  `TunerScreenLayoutTest`, `TunerMeterTest` and the component tests.

### User Stories 3 and 4 — tones and loop (quickstart §5)

- Tones are proven offline (`tone_generator_test`: pitch within 0.5 cent, 5 ms ramps, click-free stop, re-arm without a restart,
  10 ms switch, 60 s seamless loop) and on the Pixel 6 Pro by `NativeAudioEngineTest` (real Oboe output round trip). Listening
  to them, comparing against a second tuner app, and the headphone route checks need a person: not performed in this session.
- Drop-in recording: the decode, resample and loop path is exercised by `recordings_on_pitch_test` with a WAV it writes itself;
  no file was dropped into `composeResources/files/tuner/` (none ships).

### Release build (T150)

`./gradlew :androidApp:assembleRelease` succeeds with zero warnings; the stripped `arm64-v8a/libtembra_engine.so` still exports
the four `Java_audio_tembra_engine_NativeAudioEngine_nativeTuner*` symbols (release is not minified, so the JNI entry points
stay). The test-signal extra is read only inside `if (BuildConfig.DEBUG)`; the unsigned release APK was not installed.

### Phone landscape (Phase 10, 2026-09-28; spec FR-023 scenario 3, quickstart §8)

Before this phase a phone held sideways got the wide layout in a window too short for it: `android-landscape-autoListen-before.png`
(Pixel 6 Pro, 892 × 411 dp) shows the one-row selectors and the meter card cut through the gauge, scrolling.

**Design choice.** Twelve Stitch variants were pulled into `design/stitch/screens/tuner-landscape-*` (five Auto Listen, seven By
Ear) and compared on one question: do the settings, the preset, the string grid and the gauge all fit a 892 × ~330 dp content
area without clipping. The app follows `tuner-landscape-auto-listen-b` ("Mobile Landscape Auto Listen Tuner") and
`tuner-landscape-by-ear-g` / `-e` ("Mobile Landscape By Ear", six and seven strings), because:

1. the settings column is the same in both modes (instrument, strings, mode, then preset and loop toggle in By Ear), so switching
   modes swaps only the right-hand card;
2. the gauge keeps the note above the arc and the Flat / Sharp row under it, which is the phone meter's own geometry, so the one
   Canvas is reused in a compact size instead of a second meter;
3. the string grid keeps every card whole in two rows (three columns for six strings, four for seven) with no scrolling;
4. nothing in them lies outside the spec (the labelled rail is feature 002's rail).

Rejected: `tuner-landscape-auto-listen-d` and `tuner-landscape-by-ear-c` add a noise gate, A4 calibration, timbre and octave-shift
controls and a "Stop tone" bar that the spec does not have; `-a` and `by-ear-f` are crops of the same layout; the icon-only rail
family (`auto-listen-c`, `-e`; `by-ear-a`, `-b`, `-d`) titles each card ("Controls & mode", "Tuning gauge", "Reference strings"),
which costs the height a short window lacks, and its rail has no labels. The pane headers of the chosen screens ("Guitar: string 6
(low E)", "Select reference string / Active target") and the "Pitch reference A4 = 440 Hz" row are not reproduced for the same
reason (research R9, landscape table).

**Rule.** `TunerLayout.Landscape` = window at least 600 dp wide and under 480 dp tall (the rail's own short-window breakpoint):
settings column 36 % of the content width within 224–280 dp (× font scale, at most half the width, scrolling; the design's overlines
above each control are dropped because with 44 dp targets they would push the picker and toggle out of a 295 dp window), content
card beside it; the Auto Listen meter is `MeterSize.Compact` sized from the card height (`(height − 132 dp × scale) × 1.8`, 200–480 dp wide,
centred, scrolling only under 200 dp); By Ear cards fill two rows of ⌈n / 2⌉ columns (rows at least 128 dp). Proven by
`TunerLayoutTest`, `MeterLayoutTest`, `TembraSegmentedControlTest.equalWidthsShareTheControl` and the three `landscape*` cases plus
the landscape runs of `nothingOverlaps` (font scale 1 and 2), `allTargetsAtLeast44dp` and `keyboardOrderFollowsReading` in
`TunerScreenLayoutTest`.

**Captures (Pixel 6 Pro rotated with `user_rotation 1`, 892 × 411 dp, test signal `e2-in-tune`; iPhone 17 simulator rotated
Landscape Left, 874 × 402 pt, driven by the Mac keyboard):**

| Capture | Seen |
|---|---|
| `android-landscape-autoListen.png` | settings column (header + "Test signal" pill, Guitar / Cello, 6- / 7-String, Auto Listen / By Ear) beside the meter card; the compact meter (pill, E2, arc with the needle at 0, "82.4 Hz • Reference 82.4 Hz", Flat / Sharp) is centred and whole |
| `android-landscape-byEar.png` | column adds the preset picker and the loop toggle, all six controls visible without scrolling; six cards in two rows of three, every card whole |
| `android-landscape-byEar-guitar7.png` | seven cards in two rows of four; "Standard 7-String (B1 E2 A…" ellipsised in the picker (the full list is in the menu) |
| `android-landscape-byEar-cello.png` | no string-count control; four cards in two rows of two |
| `ios-landscape-autoListen.png`, `ios-landscape-byEar.png`, `ios-landscape-byEar-guitar7.png`, `ios-landscape-byEar-cello.png` | the same four states on the iPhone 17 simulator (no status bar in landscape, the rail inset by the Dynamic Island's 59 pt safe area): the column, meter and cards fit exactly as on the Pixel; the focus ring on the last keyboard-activated segment is the hardware-keyboard route used to drive the captures. `ios-landscape-byEar-before.png` is the previous build in the same orientation (one selector row, second card row cut off) |

The first Pixel pass kept the design's overline above each control; in By Ear the column then overflowed (the picker was cut
and the toggle below the fold), so the overlines were dropped and the column spacing tightened (research R9, landscape table).
The shell's 56 dp top bar is what makes a landscape phone short; the Stitch landscape screens keep only a status strip, which is a
feature-002 decision this feature does not change.

**Compose compiler report (constitution v1.1.0 gate; `./gradlew :shared:ui:compileAndroidMain --rerun -PcomposeReports=true`,
`app/shared/ui/build/compose_compiler/tembra_shared:ui-composables.txt`, 2026-09-28).** Every composable added or changed in this phase is
`restartable skippable`: `TunerScreenContent`, `ModePane`, `TunerSelectors`, `TunerSettingsColumn`, `InstrumentControl`, `StringCountControl`,
`ModeControl`, `AudioStatePillView`, `AutoListenPane`, `FittedMeter`, `ByEarPane`, `FilledStringCards`, `StringCardGrid`, `StringCard`,
`TunerMeter` (`size: MeterSize` stable) and `TembraSegmentedControl` / `Segment` (`widths: SegmentWidths` stable). The parameters the
report does not mark stable are the pre-existing ones: `TunerUiState` and `MeterReading` (data classes carrying `List<TuningPreset>` /
`TuningPreset`, which `:shared:core` does not annotate; strong skipping compares them by `equals`), `callbacks: TunerCallbacks` (a
data class of lambdas, stable in the classes report), `presets: List<TuningPreset>` and `preset: TuningPreset` on `TunerSettingsColumn`
and the picker. No immutable collection type is on the classpath (kotlinx-collections-immutable was not added, Constitution VI), so the
lists stay `List`; the column recomposes only on a selector, preset or toggle change, never on the 10 Hz reading, which is what the
split of `TunerUiState` into per-child fields was for.

### String count belongs to By Ear (Phase 11, 2026-09-28; spec clarification 2026-09-28, FR-002)

The 6-String / 7-String selector left the selector bar: Auto Listen is chromatic and its 50–1400 Hz range covers both guitars,
so the count only decides By Ear's preset list and cards. It now leads the picker row of the By Ear card (stacked and wide) and
follows the mode selector in the landscape settings column; the cello shows none, and Auto Listen shows none for either
instrument. Order everywhere is instrument → mode → strings → preset → loop → cards; `TunerScreenLayoutTest.stringCountBelongsToByEar`
proves it at all three layouts and `keyboardOrderFollowsReading` the focus order. Every capture the selector bar appears in was
retaken on the same devices as before (Pixel 6 Pro; iPhone 17 and iPad Pro 13-inch simulators):

| Capture | Seen |
|---|---|
| `android-compact-autoListen.png`, `ios-compact-autoListen.png` | instrument + pill row, mode alone on the second row (right-aligned), meter; no string count |
| `android-compact-byEar.png`, `ios-compact-byEar.png` | card: "6-String / 7-String" beside the picker on the first line, the loop toggle wrapped to the second, then the six cards |
| `android-compact-*-largefont.png`, `ios-compact-*-largefont.png` | at font scale 2 the By Ear card stacks strings, picker and toggle on three lines, all whole; Auto Listen unchanged |
| `android-expanded-*.png`, `ios-expanded-*.png` (the iPad set now also has `-byEar-guitar7` and `-byEar-cello`) | one selector row (instrument, pill, mode at the end); the By Ear card's first row holds strings, picker and toggle together, centred at 576 dp; the `-e2-flat-20` test-signal captures retaken too |
| `android-landscape-*.png`, `ios-landscape-*.png` | column: header + pill, instrument, mode, and in By Ear strings, picker, toggle; all fit without scrolling |
| `<platform>-compact-autoListen-<id>.png` (28) | the state matrix, retaken with the new bar; readings unchanged from the 2026-09-26 table above |

Pre-existing, not changed here: at font scale 2 the Auto Listen readout row ("82.4 Hz • Reference 82.4 Hz") is wider than the
340 dp meter and its ends are cut on the phone (`android-compact-autoListen-largefont.png`); the meter draws that row as one
line in the Canvas. Worth a follow-up (wrap or shrink the row at large font scales).

`app/tools/capture-tuner-states.sh` now captures through `adb exec-out screencap` so it honours `ANDROID_SERIAL` when several
Android devices are attached (the `android screen capture` tool refused with two devices online).

### Skill review of Phases 10 and 11 (constitution v1.1.0, Principle VIII; 2026-09-28)

The 2026-09-27 review (`docs/reports/chrisbanes-skills-review-2026-09-27.html`) covered the tuner before the landscape work; the
code added since was read again against the skills it was written under, with these contracts named for the gate:

| Contract | Where it applies | Outcome |
|---|---|---|
| `compose-component-design` | `TunerScreenContent(layout)`, `TunerSettingsColumn`, `TunerSelectorControls`, `AutoListenPane` / `ByEarPane` / `StringCardGrid(rowMinHeight)` / `StringCard(layout)`, `TunerMeter(size: MeterSize)`, `TembraSegmentedControl(widths: SegmentWidths)` | `modifier` is the first optional parameter and reaches the root; the two new variations are enums, not Boolean shape flags; `rowMinHeight` and the tokens stay primitives; no change |
| `compose-state-and-effects` | `TunerScreen` (wiring, effects keyed on the ViewModel) → `TunerScreenContent` (plain content the tests drive); the landscape panes' scroll states | one fix: `FittedMeter` created its scroll state inside the conditional modifier; it is now one `rememberScrollState()` applied conditionally |
| `compose-performance` | compiler report (`-PcomposeReports=true`): every composable of both phases is `restartable skippable`; the 10 Hz reading reaches only `TunerMeter`; `BoxWithConstraints` is confined to the two landscape panes whose content depends on the measured height | no measured regression, no change |
| `kotlin-control-flow` | `when (layout)` in the content, selectors, panes, grid and card; `TunerLayout.from(width, isShort)` | one fix: `MeterLayout`'s row helpers and `TunerMeter`'s styles branched on the closed `MeterSize` with `if`/`else`; now exhaustive `when (size)` |
| `kotlin-api-design` | `TunerLayout` and `MeterLayout.widthForHeight` as companion factories on their domain types, `SegmentWidths` / `MeterSize` as the smallest public shape | no change |
| `compose-ui-testing-patterns` | `TunerScreenLayoutTest` (state-driven content, sized root, bounds and semantics, unmerged surfaces), `TembraSegmentedControlTest`, `MeterLayoutTest`, `TunerLayoutTest` | no change |
| `gradle-run` | every lane of both phases ran through the wrapper | — |

### Left for a person (needs an instrument, ears, a call or a headset)

1. quickstart §3 items 1–5 with a real guitar and cello (SC-004); the meter's accuracy is proven by `engineHostTest` and the
   28 test-signal captures above.
2. quickstart §5 items 1–4: listen to the By Ear tones (60 s loop with no gap, switch between strings, one-shot ends after
   about two seconds, re-arm when the toggle flips), compare against a second tuner app.
3. quickstart §6 on the physical iPhone (the real prompt) and §7 item 5 (a call, another app's audio, unplugging headphones).
4. quickstart §9: TalkBack / VoiceOver announcements and a mouse hover pass on Android desktop mode.
