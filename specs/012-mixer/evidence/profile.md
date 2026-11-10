# Baseline profile (T110) — Pixel 6 Pro, 2026-11-09

## Generate

`uv run tracepulse profile generate --device pixel6` on the merged branch with the clip-source fix (`fffcc1a4`),
after the trial round's mixer and stress journeys had passed (the generator drives every journey, the mixer's
included, until three iterations add no new rule). Device checks all passed (battery 100 %, 36.3 °C, 60 Hz pinned,
airplane mode, API 37). Result, committed as `c1d39486`:

| File | Before | After |
|---|---|---|
| `baseline-prof.txt` | 33,388 rules, 3,722,591 bytes | 35,696 rules, 4,022,696 bytes |
| `startup-prof.txt` | 26,084 rules, 2,757,782 bytes | 27,361 rules, 2,916,669 bytes |
| rules under `Laudio/tembra/ui/mixer/` | 0 | 330 (24 for `MixerSheetKt`) |

`git diff --stat`: 8,249 insertions, 4,666 deletions. The coverage test (`BaselineProfileCoverageTest`, run by the
generate step) passed with the new row of `androidApp/baseline-profile-coverage.txt`: covered `app.start`,
`app.activity`, `shell`, `timeline`, `tuner`, `metronome`, `projects`, `mixer` (`Laudio/tembra/ui/mixer/MixerSheetKt;`).
Committed because covered code changed (the mixer is new).

## Verify

`uv run tracepulse profile verify --device pixel6 --apk ../androidApp/build/outputs/apk/perf/androidApp-perf.apk`
(the perf APK rebuilt with the regenerated profile):

```
profile verify on Pixel 6 Pro (19021FDEE001AH)
  [ok] adb_device — 19021FDEE001AH present and authorized
  [ok] awake — mWakefulness=Awake
  [ok] unlocked — keyguard not showing
  [ok] profile present (13103 bytes, 0.0 %) in .../androidApp-perf.apk
  [ok] recognised (result 1): installed
  [ok] optimised (speed-profile)
profile present (13103 bytes, 0.0 %); recognised (result 1); optimised (speed-profile)
```

The release build of the same commit installed on the Pixel 7 Pro, the Pixel Tablet, the OPPO and the fold emulator
reports `speed-profile` too (`../screenshots/NOTES.md`, "Release builds"); the Redmi (API 28) gives no broadcast
result and the Android 17 desktop preview emulator does not take the compile (both as on every earlier release).

## Compare

`uv run tracepulse profile compare --device pixel6 --journeys mixer --no-build --apk … --test-apk …`: the states
`none` (compilation reset, the worst case after a direct install) and `profile` (the shipped profile compiled ahead
of time), each from a fresh install, 20 iterations. Three attempts: the first was stopped by hand to add the
comparison's JSON-only fallback for a pull that fails through a slow adb (`5592ecc3`); the second ran `none` and
timed out in `profile` at iteration 18 of 20, because under JIT the mixer journey takes close to 80 s an iteration
and the pipeline's instrument window was 25 minutes (the window is 40 minutes since `38a5d0f0`); the third,
`20261109T155733Z_19021FDEE001AH` (build `38a5d0f0`), completed. Medians, `profile` against `none`:

| Series (mixer journey) | none | profile | Change | Confirmed (Mann–Whitney p < 0.01) |
|---|---|---|---|---|
| `journey.duration_ms` | 49,170 | 49,360 | +0.4 % | no |
| `frames.p50_ms` / `p90` / `p99` | 9.6 / 12.3 / 14.5 | 9.6 / 12.4 / 14.5 | +0.2 / +0.3 / −0.1 % | no |
| `frames.overrun_p90_ms` | −1.6 | −1.6 | | no |
| `memory.peak_kb` / `memory.end_kb` | 179,674 / 132,386 | 179,046 / 133,886 | −0.3 / +1.1 % | no |
| `section.timeline.mixerFrame.sum_ms` (2,760 frames) | 443.8 | 469.1 | +5.7 % | no |
| `section.timeline.mixerOpen.sum_ms` | 0.0 | 0.0 | −8.1 % | no |
| `section.timeline.mixerClose.sum_ms` | 0.2 | 0.2 | −5.1 % | no |
| `section.timeline.faderDrag.sum_ms` / `panTurn` / `masterDrag` | 10,746 / 2,864 / 12,356 | 10,747 / 2,865 / 12,357 | +0.0 % | no |
| `section.engine.panApply.sum_ms` | 0.1 | 0.1 | +3.4 % | no |
| `section.timeline.playheadFrame.sum_ms` (2,810 frames) | 9.5 | 10.0 | +5.2 % | **yes** |
| `section.timeline.projectSave.sum_ms` | 170.7 | 168.0 | −1.6 % | no |
| `section.transport.play.sum_ms` | 3.2 | 3.7 | +14.9 % | no |

`startup.ttid_ms` was not measured (`--journeys mixer`; the startup journey's comparison belongs to the post-merge
session). The profile gives no measurable change to the mixer journey: the duration, the frame percentiles and
every mixer section are within noise, and the one confirmed difference is half a millisecond of playhead frames
over an iteration (9.5 to 10.0 ms for 2,810 frames). The same picture as feature 010's recording compare: the
journeys are gesture-paced (the drags and the knob turn take 26 of the 49 s) and the sheet's frame work is small
either way (0.16 ms a frame). The profile's value stays with the cold start, which this comparison did not measure.
Files: `app/perf/data/comparisons/20261109T155733Z_19021FDEE001AH/{comparison.json,comparison.md}`.
