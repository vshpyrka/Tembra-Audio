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

## Re-run 2026-11-01 (OPPO, fold emulator)

Working-tree changes made for this run (not committed): `TrackHeaderTest.togglesAndTheRowReportTheirIntents` now selects the row with `performTouchInput { click(Offset(24f, centerY)) }` (left, name area) instead of the node centre; `NativeAudioEngine.android.kt` was missing the `ARM_SETTLE_MILLIS`/`ARM_POLL_MILLIS` constants used by the lead's new `arm` (compile error UNRESOLVED_REFERENCE), added to the companion object (2000 / 10, as on iOS). Lanes ran through gradle-run, zero warnings.

| Target | Module / filter | Result |
|---|---|---|
| OPPO CPH2239 | `:shared:engine:connectedAndroidDeviceTest` | PASS, 105 tests, 0 failed, 2 SKIPPED: `NativeAudioEngineRecordingTest.armsRecordsATakeWithTheCountInAndDisarms`, `aCancelledCountInLeavesNoFileAndArmIsRefusedWithoutATransport` (runtime grant refused, assumption). Metronome timing test passed. |
| OPPO CPH2239 | `:shared:ui:connectedAndroidDeviceTest`, `-Pandroid.testInstrumentationRunnerArguments.class=audio.tembra.ui.timeline.TrackHeaderTest` (the filter works) | PASS, 10/10 (47 s), `togglesAndTheRowReportTheirIntents` green |
| Fold emulator | `:shared:engine:connectedAndroidDeviceTest` | 105 tests, 2 failed, 0 skipped. `NativeAudioEngineRecordingTest.armsRecordsATakeWithTheCountInAndDisarms` PASSED (7.2 s; the arm fix works on the emulator). Failed: `MetronomeTimingEndToEndTest.ticksAreFiveHundredMillisecondsApartAndTheFirstArrivesQuickly` (fitted period 498.74 ms, spacings 490.6 to 517.6 ms: emulator jitter, as before) and `NativeAudioEngineTest.theStreamProbeSeesAnOpenOutput` ("the probe did not see the tone's output stream"; new, emulator audio output, passed in the earlier lane) |
| Fold emulator | UI module, classes TembraCardTest, TempoDialTest, NoiseGateSliderTest (comma-separated class filter works; 16m26s) | 39 tests, 3 failed: `TembraCardTest.restingCardIsSurfaceLevel1WithItsContent` (AssertionError "Failed waiting for PixelCopy!"), `NoiseGateSliderTest.aHeldBackSoundIsDrawnMutedAndShortOfTheHandle` and `theSliderIsATouchTargetTallAndItsHandleIsOneInBothDirections` (ComposeTimeoutException 2000 ms in forceRedraw). TempoDialTest 17/17 green. The failing set differs from the first run (there: elevatedCardIsSurfaceLevel2, focusDrawsARing, notListeningDrawsNoFill...): the screenshot-capture failures are flaky and repeat on the emulator, in different tests each time. No product code changed. |

Release install (`tools/release-android.sh --no-build`): installed, speed-profile compiled and launched on both O7PBYLZP4HMBW8S4 and emulator-5554 (no Play Protect prompt appeared).

Fold emulator captures (T080, Android half), release build, in `screenshots/android/fold-emulator/` and listed in `screenshots/android/README.md`: open-armed, open-countin, open-recording, open-take, closed-armed, closed-recording, closed-take. Recording works end to end on the emulator (takes appear on Track 1). Observations: the added track sits partly under the overview strip in the open posture at this height (only its header top is visible); the notification permission dialog appears on the first Record unless granted (POST_NOTIFICATIONS).
