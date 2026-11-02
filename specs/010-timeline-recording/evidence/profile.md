# Baseline profile (T087) — Pixel 6 Pro

Not run on 2026-11-01. `tracepulse profile generate|verify|compare` refuse below 50% battery and the Pixel 6 Pro was at
8-12% (charging about 1-2% per half hour on the Mac's USB hub), so the profile in
`app/androidApp/src/main/generated/baselineProfiles/` is unchanged (the release APK installed on 2026-11-01 carries the
committed profile of 12118 bytes). Rerun after charging the phone to 50%: `adb -s 19021FDEE001AH shell pm clear audio.tembra.app`,
then `uv run tracepulse profile generate --device pixel6`, `profile verify`, `profile compare --journeys recording`.
Note that the `recording` journey must first pass on a device (see `trial-round.md`: it fails at the anchor
`timeline.track.meter.4` on the Pixel 7 Pro), or the generated profile will not cover the recording path.

## Pixel 6 Pro, 2026-11-02

Battery 57 to 58 %. The app was cleared (`pm clear`) and `profile generate --device pixel6` ran: baseline-prof.txt 33389 rules
(3722591 bytes, 33388 lines), startup-prof.txt 26085 rules (2757782 bytes, 26084 lines); coverage test passed (covered: app.start,
app.activity, shell, timeline, tuner, metronome, projects; 196 baseline rules in a `recording/` package, 369 mention recording).
`git diff --stat` against the committed files: baseline-prof.txt 3496 lines changed, startup-prof.txt 1383 (3220 insertions,
1659 deletions). Not committed. `profile verify --device pixel6`: "profile present (12276 bytes, 0.0 %); recognised (result 1);
optimised (speed-profile)" (checked against the prebuilt perf APK, whose profile is the one compiled before this generation).
`profile compare --device pixel6 --journeys recording`: exit 3, no numbers. State "none" passed (20 iterations); state "profile"
failed on `perf anchor 'timeline.track.meter.4' not found within 10000 ms` (RecordingJourney.kt:28) after eight completed iterations (iteration 9)
(the same anchor failure as the Pixel 7 Pro's first trial, here in the comparison's fresh-install flow); logs in
`app/perf/data/comparisons/20261102T053402Z_19021FDEE001AH/{gradle.log,profile/instrument.log}`. Journey not edited.
