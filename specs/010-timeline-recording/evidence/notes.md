
## Soak (T096)

Pixel 6 Pro (19021FDEE001AH), 2026-11-01, release build installed by `tools/release-android.sh --no-build` (APK of the
b737335 tree), run conditions applied (brightness floor, music stream 0, DND), stay-awake on. Battery 10-12% on the Mac's
USB hub (the phone stayed alive and charging for the run). Not the specified project: the demo's four tracks plus a new
track "Track 1" (no imported stems, the system picker was not driven), so five tracks instead of nine. Count-in left at
1 bar (the metronome sheet was not reached). The microphone prompt was answered "While using the app" (a reinstall resets it).

- Take 1: armed Track 1, pressed Record at 22:10:36, stopped at about 22:42:55 (the readout showed 32:04 at 22:42:43).
  The app was foregrounded the whole time and the UI stayed responsive; logcat shows only GC lines for the app.
- Ending the take was confusing: after my single stop tap the screen showed a second take "Take 2" growing from about
  32:07 (readout 32:16, then 32:31 while playing) and I needed a second tap to stop it; the project length ended at
  32:23.98. I did not establish whether Take 1 was cut at 32:07 by the app (a limit or a rollover) or whether the first tap
  started Take 2 after Take 1 had already ended. Needs an owner look: one stop should end the recording.
- Playback (readout of the transport, ten seconds each by wall clock): start 00:12.31 to 00:22.37 (10.06 s), middle
  14:45.02 after seeking by the ruler (the ten seconds run to about 14:55), later 20:08.95 to 20:18.37 and 20:54.28 to
  21:03.67 (about 9.4 s of readout in 9 s of wall clock plus capture latency). The end of the take was not reached (the
  overview strip tap only moves its window, the ruler tap seeks inside the window). Dropouts cannot be heard from here.
- Health counters of the release build: the release build has no devtools and does not log them. Read from the perf build
  (`androidApp-perf.apk`, installed over the release build afterwards, so the Pixel 6 Pro now runs the perf build): the
  `engine-health.json` of `devtools.writeHealth` is written to the app's private files dir and the perf APK is not
  debuggable, so the file cannot be pulled on Android (the pipeline reads the `audio.*` counter tracks instead). I
  therefore recorded a 70 s perfetto trace (atrace app `audio.tembra.app`) during a take of that perf build, after a 3-minute
  take (22:44-22:48) and several short takes; the counters are cumulative since the app process started, the 32 samples were
  constant over the 70 s: `audio.inputOverruns` 6, `audio.starvation` 3, `audio.xruns` 0, `audio.recordBacklog` 864,
  `audio.takeAlignmentMs` 19, `audio.monitorDelayMs` 0, `audio.overBudget` 0, `audio.maxUs` 340, `audio.avgUs` 16,
  `audio.callbacks` 156281 to 171848. So: zero change during the traced minute, but the cumulative inputOverruns 6 and
  starvation 3 are not zero, they happened earlier in that process (the 3-minute take, its start/stop or the playbacks);
  which event produced them is not known. SC-003 (zero overruns/starvation/xruns over the 30-minute take) is therefore NOT
  confirmed: the 30-minute take itself ran on the release build whose counters could not be read. Needs a rerun with the
  counters read at its end (for example a perf build soak with a trace spanning the take, or an owner-run with the
  health file pulled on a debuggable build).

## Soak (T096), clean run on the Pixel 7 Pro

Pixel 7 Pro (33241FDH3000NZ), 2026-11-02 (local 00:01 to 00:28), perf build (`androidApp-perf.apk`, installed by the
second trial round, fresh process: force-stop, launch, demo, "+ Track", arm Track 1, Record via `timeline.track.record.4`;
the bar's Record is absent at this width). Run conditions applied; the Mac drove the UI by uiautomator dump plus input tap.
Not the specified nine-track project: the demo's four tracks plus the new track (five), count-in 1 bar.

- One take of 24:39.97 (Record at 00:01:44 local, stopped at 00:26:26 when the readout showed 24:32.31 plus the tap
  latency; the take stayed under the engine's 30:00 cap, so the cap was not exercised). The UI stayed responsive and the
  growing take kept drawing (screenshot at 24:32).
- One stop tap ended the recording: no second take appeared (the Pixel 6 Pro run's "Take 2" did not recur, but that run
  went past 30:00, so this run does not explain it). Transport kept playing after the stop (the playhead passed the take's end).
- After the stop the undo button was enabled within 2 s of the tap (the take was adopted); the project length is the take's
  24:39.97. No "Take ended" snackbar was seen in the dump taken at 2 s and 5 s, nor in the screenshot at about 60 s
  (the snackbar is expected only on the engine's automatic end, which did not happen).
- Counters (70 s perfetto trace right after the stop, 70 samples each, cumulative since the process start, so they cover the
  whole 25-minute take): `audio.inputOverruns` 0, `audio.starvation` 0, `audio.xruns` 0, `audio.recordBacklog` 1152 frames
  (constant), `audio.takeAlignmentMs` 17, `audio.monitorDelayMs` 0, `audio.overBudget` 1, `audio.maxUs` 2984, `audio.avgUs` 17,
  `audio.callbacks` 755441 to 790024 (plus 34.6k over the 70 s). SC-003 holds for zero overruns, starvation and xruns over a
  24:40 take. Open points: one over-budget callback (max 2.98 ms, once in the process) and no 30-minute take (stopped at 24:40
  to stay under the cap).
- Trace: scratchpad file soak.pftrace (not committed).
