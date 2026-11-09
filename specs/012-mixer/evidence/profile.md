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

`uv run tracepulse profile compare --device pixel6 --journeys mixer --no-build --apk … --test-apk …` (the states
`none` and `profile`, 20 iterations each; comparison id `20261109T142240Z_19021FDEE001AH`). It started at 16:22 on
the Pixel 6 Pro through the owner's ssh tunnel to adb; at about 16:28 a local adb server took `127.0.0.1:5037` while
the tunnel's listener on `[::1]:5037` stopped answering, so the phone became unreachable from the Mac mid-measurement
(the pipeline's detached poll waits for it, three hours at most). The first attempt was stopped by hand before that,
to add the comparison's JSON-only fallback for a pull that fails through a slow adb (`5592ecc3`).

**Open**: the comparison's numbers once the phone is reachable again (`comparison.md` under
`app/perf/data/comparisons/20261109T142240Z_19021FDEE001AH/` or a rerun), with the headline `startup.ttid_ms`, the
mixer journey's duration and frame percentiles for `none` against `profile`, and the Mann–Whitney verdict per series.
