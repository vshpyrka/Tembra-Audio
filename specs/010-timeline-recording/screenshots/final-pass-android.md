# Feature 010 final pass, Android connected-device lanes (T092)

Commit 9288305 (HEAD at the end; lanes ran 2026-10-28 03:06 to 06:43 EET). Lane = `tools/gradlew-device.sh <serial> checkAndroidDevice --warning-mode all --continue`
(shared modules only: core, engine, data, ui; the app module's androidApp XML in build/ is stale and not part of the lane). Zero Gradle warnings on every run (warning_fingerprints empty).

| Target | Serial | Full lane | core / data / engine / ui | Failing tests (full lane) | Engine re-run |
|---|---|---|---|---|---|
| Pixel 7 Pro (A17) | 33241FDH3000NZ | FAIL 15m23s (03:06) | 246 / 152 / 105 (1 fail) / 976 | engine `NativeAudioEngineRecordingTest.armsRecordsATakeWithTheCountInAndDisarms`: assertTrue at `armed.sampleRate > 0` (arm returned sampleRate 0, background-process input refused) | 03:26 green 105/105 (before the fix); 06:35-06:38 invalid (collided with the lead's perf round: UiAutomation "already registered"); final 06:43 green 105/105 |
| Pixel Tablet (A17) | 3621105H803UFX | FAIL 15m26s (03:26) | 246 / 152 / 105 (1 fail) / 976 | same engine test, same assertion | green 105/105 |
| Fold emulator Pixel_9_Pro_Fold (A16) | emulator-5554 | FAIL (03:42 to 06:09, about 2h27m, ui very slow) | 246 / 152 / 105 (1 fail) / 976 (3 fail) | engine: same arm test (`sampleRate > 0`); ui: `TembraCardTest.elevatedCardIsSurfaceLevel2` and `TempoDialTest.focusDrawsARing` (ComposeTimeoutException in captureToImage/forceRedraw, 2000 ms), `NoiseGateSliderTest.notListeningDrawsNoFillAndTheHandleStillMoves` (PixelCopyException result 2): emulator screenshot-capture quirks | FAIL: arm test (line 76, `armed.sampleRate > 0`) plus `MetronomeTimingEndToEndTest.ticksAreFiveHundredMillisecondsApartAndTheFirstArrivesQuickly` (fitted period 498.9 ms, spacings 488 to 517 ms: emulator timing jitter) |
| OPPO CPH2239 (A11) | O7PBYLZP4HMBW8S4 | FAIL 14m51s (06:10) | 246 / 152 / 105 (3 fail) / 977 (1 fail) | engine: `MetronomeTimingEndToEndTest.ticksAre...` (start latency 169.2 ms), `NativeAudioEngineRecordingTest.armsRecordsATakeWithTheCountInAndDisarms` and `aCancelledCountInLeavesNoFileAndArmIsRefusedWithoutATransport` (SecurityException "Error granting runtime permission", UiAutomation.grantRuntimePermission refused by ColorOS); ui: `TrackHeaderTest.togglesAndTheRowReportTheirIntents` (expected [mute(false), solo(true), select] but was [mute(false), solo(true), mute(false)]) | FAIL: both recording tests, SecurityException at grantTheMicrophone (NativeAudioEngineRecordingTest.kt:48); the metronome test passed this time |
| Redmi Note 5 (A9) | e9446ac2 | PASS 9m27s (06:25) | 246 / 152 / 105 / 977, 0 failures | none | not needed |

Alignment line (`TembraRecordingTest alignmentFrames=...`): absent on every target. `logcat -d -s TembraRecordingTest` and a full-buffer grep for `alignmentFrames` were empty after every lane and re-run (the instrumentation run clears or rotates the buffer, or the Log.i is not reached/kept); nothing to report.

Notes
- OPPO cannot be dimmed by adb (brightness settings refused; set by hand). The Redmi refused DND by adb (warning only).
- The arm test fails on A16/A17 targets with `sampleRate 0` before the lead's foreground fix (audio policy refuses the input to a background process); after the fix it is green on the Pixel Tablet and the Pixel 7 Pro, still red on the emulator (sampleRate 0, no mic path) and on the OPPO (permission grant refused).
- The Pixel 7 Pro re-runs between 06:35 and 06:38 failed with "UiAutomationService already registered / Not connected" because of the concurrent perf round; during diagnosis I also ran `settings put secure enabled_accessibility_services ""` on that phone (it showed no bound services).
- No product code changed, nothing committed.
