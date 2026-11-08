# iOS screenshots (feature 012, T090)

Taken 2026-11-08 through `app/iosApp/TembraPerfTests/ScreenshotMatrix.swift` (`testMixerStates`, `testMixerLargestFont`;
the Perf configuration built once with `xcodebuild build-for-testing -scheme TembraPerf -configuration Perf`, then
`xcodebuild test-without-building -xctestrun build/Build/Products/TembraPerf_TembraPerf_iphonesimulator27.1-arm64.xctestrun`
per simulator with `TEST_RUNNER_TEMBRA_CAPTURE_DIR` and `TEST_RUNNER_TEMBRA_CAPTURE_PREFIX=ios` in the *environment*:
passed as build-setting arguments they reach the build, not the test runner, and the matrix skips with
"TEMBRA_CAPTURE_DIR is not set"). Demo project; the Mac's output muted (nothing sounded). `mixer-clipped` is live
playback with every strip at +6 dB and the master at 100 %; `mixer-armed` adds a fifth track and arms it;
`track-settings-pan` opens the first track's settings (the expanded iPad layout shows them inline:
`…-pan-inline`); `mixer-landscape` rotates the simulator and relaunches the app before the tap (Compose keeps the
portrait accessibility frames after a rotation, so the toolbar's hit point landed above the screen); the
`largestFont` file is the accessibility XXXL size.

| Simulator | Slug | Size (pt) | Files |
|---|---|---|---|
| iPhone 18 Pro (1374495F) | `iphone-18-pro` | 402 × 874 | `ios-mixer-half`, `ios-mixer-full`, `ios-mixer-clipped`, `ios-mixer-armed`, `ios-track-settings-pan`, `ios-mixer-landscape`, `ios-mixer-largestFont` |
| iPad Pro 13-inch (M5) (035F4A11) | `ipad-pro-13-m5` | 1032 × 1376 | `ios-mixer-half`, `ios-mixer-full`, `ios-mixer-clipped`, `ios-mixer-armed`, `ios-track-settings-pan-inline`, `ios-mixer-landscape`, `ios-mixer-largestFont` |
| iPhone Duo (8AF0F092), closed, upright | `iphone-duo` | 466 × 678 | `ios-mixer-half`, `ios-mixer-full`, `ios-mixer-clipped`, `ios-mixer-armed`, `ios-track-settings-pan`, `ios-mixer-landscape`, `ios-mixer-largestFont` |

## What they show

- iPhone 18 Pro: four strips and the master at the half height in the compact form; the full sheet is the full form
  (VU dials and knobs: the lanes box is 480 dp tall or more); the largest font ellipsises "Master" ("Mast…") and the
  track names; landscape is the single anchor.
- iPad Pro: the expanded layout's half anchor leaves 192 dp of lanes, so the half sheet is about 1,050 dp tall and
  still the compact form (data model §5: half means compact strips), with faders over 900 dp long; the full sheet
  shows the dials and knobs; the inline track settings carry the pan row; in landscape the two-anchor layout opens
  at the half anchor (compact strips about 500 dp tall). Both tall-half cases are listed for the owner (T124) in
  `../NOTES.md`.
- iPhone Duo (closed, upright): the experimental end-rail layout (the navigation rail and the clock on the right);
  four strips with the master pinned over the row's end; the Pocket metronome is untouched by the mixer. The open
  posture needs the Device Hub's pose buttons (GUI automation on an unlocked Mac) and was not captured tonight.

The landscape files are stored in the display's portrait buffer by `XCUIScreen.screenshot()` and were rotated
upright with `sips -r -90`.
