# Baseline profile (T087) — Pixel 6 Pro

Not run on 2026-11-01. `tracepulse profile generate|verify|compare` refuse below 50% battery and the Pixel 6 Pro was at
8-12% (charging about 1-2% per half hour on the Mac's USB hub), so the profile in
`app/androidApp/src/main/generated/baselineProfiles/` is unchanged (the release APK installed on 2026-11-01 carries the
committed profile of 12118 bytes). Rerun after charging the phone to 50%: `adb -s 19021FDEE001AH shell pm clear audio.tembra.app`,
then `uv run tracepulse profile generate --device pixel6`, `profile verify`, `profile compare --journeys recording`.
Note that the `recording` journey must first pass on a device (see `trial-round.md`: it fails at the anchor
`timeline.track.meter.4` on the Pixel 7 Pro), or the generated profile will not cover the recording path.
