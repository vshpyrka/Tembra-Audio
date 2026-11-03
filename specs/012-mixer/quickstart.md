# Quickstart: Mixer (feature 012)

Validation guide for the plan. Commands run from `app/` unless stated; every Gradle command
goes through the `gradle-run` wrapper with `--warning-mode all` (and, once `main` is merged
in, the CI gates `-Ptembra.warningsAsErrors=true --warning-mode fail`). Contracts: [engine ABI
v8](contracts/engine-c-abi-v8.md), [session](contracts/mixer-session.md),
[UI](contracts/mixer-ui.md), [perf](contracts/perf-instrumentation.md), [skills and
audit](contracts/skills-and-audit.md); entities in [data-model.md](data-model.md).

## 1. Prerequisites and the branch

- `010-timeline-recording` and `011-large-screens-desktop` merged to `main`, then `main`
  merged into `012-mixer` (the first task). After that merge the application id is
  `com.tembraaudio` (this branch still shows `audio.tembra.app` until then) and 011's desktop
  emulators, shortcut catalogue and overlay stack are available.
- The toolchain of feature 011 (`app/README.md`): Gradle 9.7.0 wrapper, Xcode 27.1 as the
  default `Xcode.app`, NDK 29, CMake 3.31.6; `cd perf && uv sync` for the pipeline; the SD card
  mounted for `tracepulse run` and `profile generate`.
- Headphones for §6 (pan is heard, not seen) and a 3.5 mm or USB-C loopback cable for §8's
  level measurements where the device has a jack or an interface; otherwise the acoustic
  variant at the lowest audible volume.

## 2. Every target, its id and what runs on it (the owner's rule: name any target skipped)

| Target | Id / name | Lanes and checks |
|---|---|---|
| Pixel 6 Pro (Android 17, reference, `pixel6`) | `19021FDEE001AH` | device lane, screenshots at 360/600/840 dp via `wm density`, release build, perf session, the main-thread trace query, TalkBack |
| Pixel 7 Pro (Android 16, reference) | `33241FDH3000NZ` | device lane, release build, perf session by serial |
| Pixel Tablet (Android 16, reference) | `3621105H803UFX` | device lane, screenshots at 1024/1200 dp, release build, perf session by serial |
| OPPO CPH2239 (ColorOS) | `O7PBYLZP4HMBW8S4` | device lane (brightness by hand; the two engine recording tests skip as in 010), release build; no perf (ColorOS refuses the setup) |
| Redmi Note 5 (Android 9) | `e9446ac2` | device lane (refuses do-not-disturb; Play Protect prompt "Don't send"), release build; no perf (API 28 has no Perfetto) |
| Pixel 9 Pro Fold emulator | AVD `Pixel_9_Pro_Fold`, `emulator-5554` (inner display `4619827259835644672`) | device lane (about 2.5 h; PixelCopy flakes are retried once), fold-posture screenshots (`adb emu fold`/`unfold`), release build |
| Desktop emulator (Android 17 desktop, 011) | AVD `Tembra_Desktop_API37`, `emulator-5582`, user 10 | device lane, free-form-window screenshots (sheet at both heights; the separate window where delivered), release build, perf session (`--device` entry from 011) |
| Desktop emulator (Android 14 desktop, 011) | AVD `Tembra_Desktop_API34`, `emulator-5580` | `:androidApp:connectedDebugAndroidTest`, the no-touchscreen pointer pass, Chromebook-style screenshots |
| iPhone 18 Pro simulator | `1374495F-788F-42B4-B036-5CFF2EA195EE` | `checkIos`, screenshot matrix, release build |
| iPad Pro 13-inch (M5) simulator | `035F4A11-FE89-4AA8-BCD0-405790F30B71` | `checkIos`, screenshot matrix (expanded and a trackpad pass), release build |
| iPhone Duo simulator (iOS 27.1) | `8AF0F092-16BB-4286-8F8F-E0AB208CBA8A` | `checkIos`, screenshots open and closed (Pocket form: the sheet in the end-rail layout), release build |
| iPhone 14 (iOS 27.0.1, reference, `iphone14`) | `00008110-001049660E3A401E` | release build, perf session, VoiceOver and the loopback measurements; not the Kotlin lane (a physical iPhone cannot run it) |

Run one desktop emulator at a time beside the other targets (memory); one Android target per
sub-agent, serialised by the device lock; never touch a device while a lane or a round runs.

## 3. Run conditions on devices (the owner's rules of 2026-10-25)

Before any lane, capture or perf round on a physical Android device:
`tools/apply-device-run-conditions.sh <serial>` (wake, dismiss the keyguard, brightness at
the floor, the music stream silent, do-not-disturb on; the OPPO refuses the brightness
settings, dim it by hand). The iPhone 14 is dimmed by `PerfJourneyTestCase`; set its ringer to
silent and the volume to zero by hand; the Mac stays muted. Device UI tests run behind the
black `Theme.Tembra.DeviceTest` window. The only steps that make a sound are §6 (pan on
headphones) and §8 (loopback), announced before they start.

## 4. Build, test lanes, gates

| Lane | Command | Must show |
|---|---|---|
| Engine | `./gradlew engineHostTest` | `pan_law_test`, `mix_params_test`, `mixer_abi_test`, `mix_meter_test`, the extended strip, bus, renderer and health tests, `trace_shim_test` (49), `trace_poison` with the new RT sources |
| JVM | `./gradlew checkAndroidHost` | `PanTest`, `FaderTaperTest`, `ProjectEditorSetPanTest`, the codec golden with pan, `ProjectSessionTest` (+6), `TimelineViewModelTest` (+7), `TimelineUiStateTest`, `PerfAnchorsCatalogueTest`, `PerfSectionCatalogueTest`, `ThemeJsonParityTest`, `IconAssetsTest`, `BaselineProfileCoverageTest` |
| Simulator | `./gradlew checkIos -Ptembra.iosSimulator="iPhone 18 Pro"` (then the iPad and the Duo by name or UDID) | the same plus every `uiTest` class of the UI contract, `NativeAudioEngineMixerTest`, `NativeAudioEngineMixerCostTest`, `NativeAudioEngineBlockingDispatchTest` (extended) |
| Device | `tools/gradlew-device.sh <serial> checkAndroidDevice` per Android target (§2) | the same `uiTest` classes, the two engine mixer tests, the dispatch test |
| App module | `tools/gradlew-device.sh <serial> :androidApp:connectedDebugAndroidTest` | the window capability binding test (R10) |
| Perf | `./gradlew -Ptembra.perf=true :androidApp:assemblePerf :perf-benchmarks:assemblePerf`; `cd perf && uv run pytest` | the perf build with the seven sections and two counters; the pipeline suite with the seventh journey |
| Compose report | `./gradlew :shared:ui:assembleRelease -PcomposeReports=true` | every new composable skippable; `MixerUi`, `StripUi`, `MasterUi` stable |
| Warnings | every command with `--warning-mode all` (CI: `-Ptembra.warningsAsErrors=true --warning-mode fail`) | zero |

Delegation: lane runs, screenshot captures and per-device perf rounds are handed to
Sonnet-class sub-agents, one target per agent, each with the serial, the commands above, §3
and the instruction to report the exact failing test names; the main agent keeps the engine,
the session, the sheet and every review.

## 5. US1 — faders (P1) and US4 — mute, solo, master (P2)

1. Open the demo project on the Pixel 6 Pro; tap the toolbar's Mixer button: the sheet rises
   to its half height over the lanes with one strip per track and the master pinned at the
   right; the transport bar is still tappable; playback is uninterrupted (press play first).
2. Drag the first fader down a third: the readout falls, the track is quieter at once, no
   click (headphones, §3's exception); double-tap the handle: 0 dB with a short move.
3. Two fingers on two faders: both follow independently (SC-006; the gains in the saved
   document equal the readouts within 0.1 dB after a reopen).
4. Undo: one step per completed gesture (two drags = two steps); redo restores.
5. Solo in the sheet lights the header's S; unsolo from the header dims the strip's Solo;
   mute two tracks from the sheet; a muted and soloed track is silent (SC-010 is the automated
   twin: 200 randomised toggles across both surfaces).
6. Drag the master fader: the speaker popup shows the same percentage; one undo step.
7. Tap a strip's name: the track is selected and its lane scrolls into view; select a track
   in the timeline, open the sheet: its strip is visible.

## 6. US3 — pan (P2; the one sounding step besides §8)

On headphones at a low volume, announced: turn the first track's knob hard left, hear it on
the left at the same loudness as centred (SC-004 is measured in §8); double-tap: centre;
release within 5 % of the centre: it settles on "C"; undo returns it; open the track's
settings: the Pan slider agrees; on the tablet the expanded header shows the pan readout.
Open `specs/009-timeline-arranger/fixtures/demo-project.json`'s saved copy from before this
feature: every knob is centred and the project sounds as before.

## 7. US2 — meters (P1)

With the project playing: each strip's two bars move with its track, the hold mark stays
1.5 s, a muted track's meter rests; the master meter shows the sum; raise a track to +6 dB
and pan it hard left until the master clips: the master's clip square lights and stays until
tapped or until play from a stop; the VU needle moves slowly and sits at 0 VU for a −18 dBFS
tone (§8 generates one); arm a track (feature 010): its strip shows the input meter; disarm:
the playback meter returns; stop: every meter falls to silence within a second.

## 8. Levels, pan law and latency (SC-002, SC-003, SC-004; the loopback step)

1. Build the measurement project: three tracks from the fixtures (a −18 dBFS sine, a full-scale
   mono burst, a stereo stem); loopback the output into the input on the Pixel 6 Pro and the
   iPhone 14 (cable where possible).
2. SC-003: play the sine on one track at 0 dB, centred, alone: the strip's peak reads the
   sine's level within 0.5 dB, the master the same; the VU reads 0 VU within 1 VU after
   300 ms; the meter lags the recording's onset by ≤ 50 ms (compare the first frame the bar
   lights, captured by `screenrecord`, with the loopback file).
3. SC-004: record the sine centred, then hard left, then hard right; compute `L² + R²` per
   recording (`tools/measure-pan-law.py`, the alignment script's sibling): equal within
   0.5 dB; the left recording's right channel below −60 dB.
4. SC-002: sweep a fader from silence to +6 dB over two seconds and a knob from full left to
   full right while a sine plays; the loopback shows no sample step larger than the 10 ms
   ramp's step (the script reports the largest step and the click count = 0); the first
   level change arrives within 20 ms of the gesture's start (video frame vs. file).
5. Record the values in `evidence/levels.md` with the device, the route and the script
   output.

## 9. US5 — windows, pointer, keyboard, screen readers; US6 — many tracks

Screenshots per FR-034 on Android (360, 600, 840 dp through `wm density` on the Pixel 6 Pro,
1024 and 1200 dp on the tablet, phone landscape, the fold's postures, the desktop emulator's
free-form window) and the iOS simulators (iPhone 18 Pro, iPad, the Duo) with the
`ScreenshotMatrix` states extended: `mixer-half`, `mixer-full`, `mixer-landscape`,
`mixer-clipped`, `mixer-armed`, `mixer-24-tracks`, `track-settings-pan`, each at the default
and the largest font; store under `specs/012-mixer/screenshots/{android/<device>,
ios/<sim>}/` with a README table and `NOTES.md`. Compare against
`design/stitch/screens/mixer-timeline/*/screenshot.jpg` and `theme.json` before accepting.
Keyboard (iOS lane and the desktop emulator): X, Tab order, arrows, Page, Home, M, O, Escape,
Back. Pointer (the API 34 desktop emulator has no touchscreen): hover tints and tooltips on
every control, the wheel on faders and knobs, a secondary click on a strip, the scrollbar on
the row. TalkBack (Pixel 6 Pro) and VoiceOver (iPhone 14) by ear: a fader, a knob, Solo,
Mute, a meter's level and "clipped", the master; the owner may do this step. Many tracks:
the 24-track project of the stress phase, the row scrolled end to end, no dropped frames in
the labelled session (§10).

## 10. Performance and the main-thread audit

1. Build the measurement artefacts (§4) and run a trial round: `cd perf && uv run tracepulse
   run --device pixel6 --label 012-mixer --rounds 1 --no-publish --no-build --apk … --test-apk …`;
   the `mixer` journey must complete with every section present and
   `audio.meterFramesDropped = 0`.
2. `uv run tracepulse profile generate --device pixel6`; commit both profiles; `profile
   verify`; `profile compare --journeys mixer` into `evidence/profile.md`.
3. The main-thread audit (skills contract §C): the construction table, the Catch2 and facade
   tests green on every target of §2, the StrictMode logcat read on the Pixel 6 Pro (device
   lane and a manual five-minute mixer session), and, after the merge, the Perfetto query of
   the labelled round for main-thread slices over 8 ms inside `journey.mixer`; all into
   `evidence/main-thread-audit.md`.
4. After the merge: the labelled sessions of the perf contract on `pixel6`, `iphone14`, the
   Pixel 7 Pro, the Pixel Tablet and the desktop emulator (`--rounds 5`), `sync`, `report
   --open`; read the "last session" block: every SUSPECT or CONFIRMED onset inside the session
   and every `above_limit` notice is either explained in `evidence/perf-session.md` or fixed
   before the pull request is closed. The startup family must stay within the gate.

## 11. The skill review (skills contract §B)

At the end of every implementation phase: route the phase's files through
`using-chrisbanes-skills`, walk them against the loaded skills, record each finding and its
fix in `evidence/skill-review.md`; the final review covers the whole diff against `main` and
the compose compiler report.

## 12. The separate window (US7, P3, cuttable; research R10)

On `Tembra_Desktop_API37` (user 10, a free-form window): open the sheet, tap "Open in a
window": the sheet closes and a second window shows the console strips; move it beside the
timeline window; drag a fader in one, watch the other; undo in either; press X in the
timeline window: the mixer window comes to the front; close the project: both close. On a
phone, the iPad and the Duo the button is absent. If the window phase is cut, this section is
marked "deferred" in `screenshots/NOTES.md` with the reason.

## 13. Stitch sync (after the implementation)

Render the new states (sheet half and full on the phone at 390 px wide, the tablet at 1280,
the desktop window, the strip at rest, clipped and armed, the track settings with pan) as
Stitch screens and upload each as a new screen to project `12040171611712589599` with the
`stitch-design:upload-to-stitch` script; place them with `PATCH
/v1/projects/{p}?updateMask=screenInstances` beside the timeline screens; re-pull
`design/stitch/screens.json` and `design/stitch/screens/mixer/*`; add the mixer prose (strips,
fader, knob, meters, VU) to `design/stitch/DESIGN.md` (prose only, never a full
`update_design_system`); note the slugs in `design/stitch/README.md`.

## 14. Troubleshooting

- The sheet does not rise: the lanes box reported 0 dp (an empty project shows
  `EmptyTimeline`; the sheet still opens over it with the master strip only).
- Meters stay dark: `setMetering` was not called (the refcount test) or the transport is
  closed (the project not yet open).
- A fader step clicks: a strip ramp was `snap`ped instead of `setTarget`ed after the first
  block (the strip test's max-step assertion).
- The device lane collides with a perf round ("UiAutomation already registered"): one target
  at a time; wait for the lock.
- The desktop emulator refuses the second window: record it in R10's spike note and cut the
  window phase.
