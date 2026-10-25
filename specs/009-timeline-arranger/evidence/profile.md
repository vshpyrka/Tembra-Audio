# Baseline profile (feature 008 cadence, task T130)

Date: 2026-10-19. Device: Pixel 6 Pro `19021FDEE001AH` (Android 17, API 37), 60 Hz pinned, airplane mode, 50 %
battery. Everything through `cd app/perf && uv run tracepulse …`.

## Coverage rule

`app/androidApp/baseline-profile-coverage.txt` gained the line the Projects destination needs:

```
projects     Laudio/tembra/ui/projects/ProjectsScreenKt;
```

`BaselineProfileCoverageTest` (host lane) reads it; `profile generate` checks every label against the generated profile.

## Generate

`uv run tracepulse profile generate --device pixel6` (log `app/perf/data/profile-generate/20261019T153xxxZ_19021FDEE001AH/`):

| File | Rules | Bytes |
|---|---|---|
| `androidApp/src/main/generated/baselineProfiles/baseline-prof.txt` | 30 911 | 3 435 942 |
| `androidApp/src/main/generated/baselineProfiles/startup-prof.txt` | 22 757 | 2 354 504 |

Covered: `app.start`, `app.activity`, `shell`, `timeline`, `tuner`, `metronome`, `projects` (36 rules name
`ProjectsScreenKt`). The journeys that fed it are the five of feature 009 (startup, tuner, metronome, timeline, stress).

Two attempts failed before this one and both were the pipeline's environment, not the profile: the first found 230
projects from the SC-010 seed on the device (the perf build shares `audio.tembra.app` with the debug app, so the demo
card sat far down the grid); the second tapped the ruler at a quarter of its width, which on a phone is the signature
pill that overlays the ruler's left edge, so the lane behind the opened menu was not found. The journeys now seek at
60 % of the ruler on both platforms (`perf-benchmarks/.../TimelineJourney.kt`, `TembraPerfTests/Journeys/TimelineJourney.swift`).

## Verify

`uv run tracepulse profile verify --device pixel6 --apk androidApp/build/outputs/apk/perf/androidApp-perf.apk`:
profile present (11 923 bytes in the APK), recognised by the device (result 1: installed), optimised `speed-profile`.

## Compare

See the section below once `profile compare` has run (it measures one perf build with and one without the profile).
