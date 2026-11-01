# Feature 010 iOS captures (T080)

Taken 2026-10-28 through `app/iosApp/TembraPerfTests/ScreenshotMatrix.swift` (`testRecordingStates`, `testLargestFont`) with
`xcodebuild test -scheme TembraPerf -configuration Perf -only-testing:...` and `TEST_RUNNER_TEMBRA_CAPTURE_DIR` per
simulator. Debug-like Perf build, demo project, microphone granted, simulator input is the Mac's microphone.

| Device | Slug | Width (pt) |
|---|---|---|
| iPhone 18 Pro (1374495F) | `iphone-18-pro` | 402 (portrait) |
| iPad Pro 13-inch (M5) (035F4A11) | `ipad-pro-13-m5` | 1032 (portrait) |
| iPhone Duo (8AF0F092) | `iphone-duo` | 466 (closed, upright) |

## Captured

| State | iPhone 18 Pro | iPad | Duo |
|---|---|---|---|
| armedRest, armedLoud, armedClipped | yes | yes | yes |
| countIn, recording, takeDone | yes | yes | yes |
| takeOverClips | yes | yes | no |
| monitorOn, monitorSuspended | yes | yes | yes |
| stopRecordingDialog | yes | yes | yes |
| sheetCountIn | yes | yes | yes |
| barRecord432 | no (no `transport.record` at 402 pt) | yes | yes |
| largestFont | yes | yes | yes |
| micDenied | yes | no | no |

## Limitations and missing states

- armedLoud and armedClipped are the same armed state as armedRest: the input is the Mac's microphone, so the level is
  whatever the room gives (the files are identical in intent, differing only by noise). A loud or clipped meter needs a device.
- monitorOn and monitorSuspended are the same tap: a simulator's output is the speaker, so monitoring is refused and the
  snackbar "Connect headphones to monitor the input" shows; monitorOn is therefore the suspended state too.
- countIn: caught as soon as the readout began with "Count-in" or the take appeared; the readout may already be moving.
- micDenied: iPhone 18 Pro only (2026-11-01, `iphone-18-pro/ios-micDenied.png`: snackbar "Microphone access is needed to record"
  with "Open settings"). The simulator runs the app under the signed bundle id `audio.tembra.appP5QJJ5Y5V7` (the
  team-suffixed install), so the permission must be revoked for that id, not `audio.tembra.app`:
  `xcrun simctl privacy <udid> revoke microphone audio.tembra.appP5QJJ5Y5V7`, run the state with
  `TEST_RUNNER_TEMBRA_STATES=micDenied` (the test taps "+ Track" and the arm toggle, declines a system alert if one shows),
  then `grant` again. Not taken on the iPad or the Duo.
- barRecord432 on the iPhone 18 Pro: the bar has no Record at 402 pt portrait (Record sits in the armed track's header);
  a landscape capture was not added. The iPad and Duo files show the bar's Record (the Duo is 466 pt wide).
- takeOverClips on the Duo: missing. The test could not scroll the track list back up to reach the drums track's arm
  switch (`timeline.track.arm.0` stayed off screen at y = -171 after drag and swipe attempts), so it failed there.
  `takeOverClips` is a take started from the playhead near bar 3 (ruler tap at 35 percent) over the drums clips; on the iPhone
  and the iPad the ruler tap position is not asserted, so check the file for the actual bar.
- largestFont uses the launch argument `-UIPreferredContentSizeCategoryName UICTContentSizeCategoryAccessibilityXXXL`.
- Side effect: the demo project on each simulator now holds the added "Track 1" (and a "Take 1"/take over drums); the matrix
  reuses the track on later runs. No commit was made.
- `xcodebuild test` hung after the first suite on one run (known from feature 009); the PNGs were already written.
