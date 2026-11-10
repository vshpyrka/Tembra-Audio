# Window spike (T100): a second free-form window of the app on Android desktop

Research R10 made the mixer's own window the last, cuttable phase and asked for a spike first: on `Tembra_Desktop_API37`
(Android 17 "Desktop (Preview)", user 10) start a second activity of the app with `FLAG_ACTIVITY_NEW_TASK |
FLAG_ACTIVITY_MULTIPLE_TASK` and `ActivityOptions.setLaunchBounds`, confirm a second free-form window appears and that both
activities see the same application graph, then decide go / cut.

## What was tried (2026-11-08)

The spike was run with the real pieces rather than a throwaway activity, because the platform question is the same and
the pieces are small:

- `MixerWindowActivity` in `:androidApp`: `singleTask`, `taskAffinity="com.tembraaudio.mixer"`, `resizeableActivity`,
  a 360 × 320 dp minimum and a 720 × 480 dp default (`<layout>`), the main window's `configChanges`, not exported.
- `AndroidWindowCapabilities.openMixerWindow()`: `Intent(context, MixerWindowActivity)` with `FLAG_ACTIVITY_NEW_TASK`,
  plus `FLAG_ACTIVITY_MULTIPLE_TASK` and `ActivityOptions.launchBounds` (720 × 480 dp centred) the first time; a later
  call finds the task through the launch mode and brings it forward.
- The graph: both activities read `(application as TembraApplication).appGraph`; the window's `MixerWindowViewModel`
  maps the same `ProjectSession.state` the timeline edits and holds one of its metering counts.
- `AndroidWindowCapabilitiesTest` (androidTest): on a device with `FEATURE_FREEFORM_WINDOW_MANAGEMENT`, open the window
  from the capability, wait for `MixerWindowActivity` to resume, assert the capability reads open, assert two app tasks,
  open again (still two tasks), finish the window with `finishAndRemoveTask()`, assert the capability reads closed and
  one task remains. On a phone the first test only checks the capability reads false.

## Result

`tools/gradlew-device.sh emulator-5582 :androidApp:connectedDebugAndroidTest`, 2026-11-08 (user 10, the emulator booted
with `-no-audio`): `AndroidWindowCapabilitiesTest.openingTheMixerWindowStartsItsOwnTaskAndClosingItReportsBack` passed in
35 s. The platform opened `MixerWindowActivity` in a second free-form window and a second task of the app beside the
timeline's, the capability read open, a second request did not add a task, and finishing the window brought the app back
to one task with the capability reading closed. `multipleWindowsFollowsTheFreeformFeature` passed as well (true on the
emulator). Both activities run in the one process over `TembraApplication.appGraph`, so the window's ViewModel edits the
same session the timeline shows (`MixerWindowViewModelTest`). The same run saw four of feature 011's full-screen tests time
out on the Android 17 preview's window shell (`FullScreenTest`, `WindowResizeAudioTest` × 2, `WindowForegroundTest`),
unrelated to the second window; they are recorded with the lane in `../screenshots/NOTES.md`.

## Decision

**Go.** The window phase is delivered: `WindowCapabilities` in `ui.platform` (Android true on free-form devices, iOS
false), `MixerWindowActivity` + `AndroidWindowCapabilities`, `MixerWindowViewModel` + `MixerWindowContent` + `MixerWindow`,
the handle's "Open in a window" button, the Timeline's `openMixerWindow()` and the Mixer button bringing the window to the
front while it is open. Quickstart §12 by hand and its captures are T105.
