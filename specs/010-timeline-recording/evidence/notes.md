
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

## Soak (T096), iPhone 14 after the merge with main

iPhone 14 (00008110-001049660E3A401E, iOS build 24A94232), 2026-11-04 (local 14:49 to 15:19), the `Perf` configuration
of the `TembraPerf` scheme built for testing at 394aac8 (main with feature 011 merged into this branch) plus the soak
test itself, driven by `TembraPerfTests/RecordingSoakTests` (by hand; `orchestrate_ios` skips it in a round). Phone
volume down (owner), the screen kept awake by the test-only `-tembra.keepScreenOn` launch argument. Not the specified
nine-track project: the demo's four tracks plus a new track (five), count-in 1 bar, the input is the room.

- One take of 29:35.00 (Record at t = 33 s, the readout rolling at t = 36 s, stop tapped at t = 1809.9 s through the
  armed header's Record, as the bar's Record is absent at 390 pt; the readout read 29:37.00 of a 29:35.00 project right
  after the stop because the transport keeps playing). It stayed under the engine's 30:00 cap.
- One stop tap ended the recording: the growing take was gone 1.5 s after the tap and undo was enabled 2.6 s after it
  (the take was adopted); no second take.
- Counters (`engine-health.json` written by `devtools.writeHealth` right after the stop, copied off the phone with
  `xcrun devicectl device copy from`; 335,016 callbacks, about 30 minutes of the one stream session that recorded the
  take): `input_overruns` 0, `starvation` 0, `xruns` 0, `over_budget` 0, `max_us` 87, `avg_us` 12, `record_backlog` 0,
  `take_alignment_ms` 20, `monitor_delay_ms` 0, `realtime` 1. SC-003 holds on the iPhone 14 for a 29:35 take.
- xcodebuild reported the test failed with "Test exceeded execution time allowance of 30 minutes" at t = 1814 s, after
  every assertion had passed and while the last tap ran: `TembraPerf.xctestplan` caps every test at 1800 s, which wins
  over the test's own 45 minutes. The test's doc comment now gives the by-hand command with
  `-maximum-test-execution-time-allowance 2700`; the measured rounds keep the plan's cap.
- Not checked here: the playback of the take at its start, middle and end by ear (a sounding step, the owner's), and the
  nine-track project.

## Soak (T096), the nine-track project, 2026-11-05

**iPhone 14** (iOS build 24A94232, the `Perf` configuration at the merge `ea78b2a`, by hand through
`RecordingSoakTests.testNineTrackTake`, phone volume down): the demo restored, four new tracks each given a 10-second
take (iOS has no folder the Files picker could hand the app the fixture stems from, so recorded takes stand in for the
imported ones; same load: eight tracks with audio), then a ninth track records 29 minutes with the room as the input.
The take stopped at 29:05.00 (the readout 29:09 of 29:05 right after the stop, the transport playing on). With the
track still armed, the take played back silently for about 9 s at its start (00:01 → 00:10), middle (14:32 → 14:42) and
end (28:13 → 28:22); the demo was restored at the end, which deleted the soak's audio. `engine-health.json` (written
after the playback, 363,418 callbacks, about 32 minutes of one stream session): `input_overruns` 0, `starvation` 0,
`xruns` 0, `over_budget` 0, `max_us` 77, `avg_us` 12, `record_backlog` 0, `take_alignment_ms` 20. SC-003 holds on the
iPhone 14 for a nine-track project; starvation and xruns 0 through the playback mean no dropout at the start, middle
and end.

**Pixel 6 Pro** (`19021FDEE001AH`, the perf build of the merge `ea78b2a`, run conditions applied): a copy of the demo
("Demo project copy"), the four fixture stems `tone-44k1-mono-16.wav`, `tone-48k-stereo.wav`, `tone.flac`, `tone.mp3`
imported through "Add audio as new track" in the system picker (four tracks, 1 s clips), a ninth track armed and one
take of 28:51 (Record at 23:48:56, stopped at 00:17:49 through the armed header). Counters from a 100 s trace spanning
the stop: `audio.inputOverruns` **6**, `audio.starvation` 0, `audio.xruns` 0, `audio.overBudget` 0, `audio.maxUs` 746,
`audio.recordBacklog` 960 frames, `audio.takeAlignmentMs` 17. A 5 s probe two minutes into the take already read 5:
the overruns are short non-blocking reads of the input inside the output callback (the fifo never filled: the
backlog peaked at 960 frames of a 4 s fifo), zero-padded into the take. Playback of the take with the track armed, a
75 s trace: start (00:01 → 00:11), middle (14:25 → 14:36), end (27:59 → 28:09), `audio.starvation` 0, `audio.xruns` 0,
`audio.overBudget` 0 with up to five clips streaming. SC-003 fails on the Pixel 6 Pro at `ea78b2a` on the overrun
count, as in the two earlier Pixel 6 Pro runs (6 and 6); the Pixel 7 Pro (24:40) and the iPhone 14 (two takes) show 0.
The fix is the one-burst input reservoir (`app/engine/src/capture/input_reservoir.h`, 874a404); its soak follows.

## Device rows run silently by the agent (quickstart §7, T070 and T075), 2026-11-05

Pixel Tablet (`3621105H803UFX`, Android 17, the release build of the merge `ea78b2a`, desktop windowing with the
window maximised, run conditions applied, no headphones), driven by `uiautomator` and `input`:

| Row | What was done | Result |
|---|---|---|
| Speaker guard | armed the Pad track, tapped the toolbar's monitoring toggle | the toggle stays off; snackbar "Connect headphones to monitor the input" — as expected |
| Leaving | recorded, tapped Tuner; "Keep recording"; Tuner again, confirm | dialog "Stop recording?" / "The take is kept up to now." / "Stop recording and leave" / "Keep recording"; Keep: the take ran on (00:06 → 00:19) on the Timeline; confirm: the Tuner opened listening ("MIC ACTIVE"); back on the Timeline "Take 1 … 29 seconds" kept, transport paused — as expected |
| Tuner hand-over | the same run: the Pad track was armed when the Tuner took the microphone | the track was disarmed (no meter) and the Tuner listened as before — as expected |
| Screen lock | recorded, `KEYCODE_SLEEP` for two minutes | the take kept recording (00:04 before the lock, 02:11 after it); the notification "Recording" was posted while the screen was off; Record still read "Recording" — as expected |
| Force quit | recorded 22 s, `am force-stop` | the relaunch showed "Take 1 … 23 seconds" (at least the first 18 s) — as expected |
| Permission denied | revoked and cleared the user flags, armed, answered "Don't allow" | the track stayed unarmed; snackbar "Microphone access is needed to record" with its settings action; after a grant (`pm grant`, standing in for Settings) arming worked without a prompt — as expected |
| Call during a take (the Pixel 9 Pro Fold emulator, release build, ring and media volume 0) | recorded 8 s, `adb emu gsm call`, answered after 4 s of ringing, ended after 10 s | on answering, the take ended and was kept ("Take 1 … 14 seconds"), playback stopped (00:13), Record idle, the track still armed with its meter; after the call nothing resumed (00:13.70 three seconds later) and the meter was there — as expected |
| Permission revoked while armed | `pm revoke` 7 s into a take | Android kills the process the moment a runtime permission is revoked, so no in-app message can show; the relaunch kept the take ("Take 2 … 8 seconds", recovered like a force quit) and the track was disarmed. The spec's "the message shown" cannot happen on Android; the owner should decide whether the row reads "take kept, disarmed" on Android (T094) |

Still the owner's (they need sound, hardware or a person): monitoring through wired and wireless headphones and the
unplug (T070), the wired headset microphone mid-take and a call on a real phone (T075; the emulator's call passed), TalkBack and VoiceOver by ear
(T081), the loopback alignment and monitoring delay (T090), Record's placement on a portrait phone (T094), and the
30-minute cap and the free-space floor (the cap was exercised on the Pixel 6 Pro on 2026-11-01).

**Pixel 6 Pro with the input reservoir** (the same nine-track project, the perf build of `010-closeout`): with a
one-burst cushion (874a404) a 28:47 take on a tenth track counted `audio.inputOverruns` 1 (a 150 s trace of a fresh
arm and take put it 33 s into the take, so it is jitter longer than one 2 ms burst, not the arm); with two bursts
(8ca8d5a) a 28:32 take on an eleventh track, Record at 00:58:48: `audio.inputOverruns` **0** (also 0 at the 4-minute
probe), `audio.starvation` 0, `audio.xruns` 0, `audio.overBudget` 0, `audio.maxUs` 617, `audio.recordBacklog` 1152 frames,
`audio.takeAlignmentMs` 21 (17 + the 4 ms cushion, reported with the input latency, so the alignment stays exact).
Playback of the take with the track armed, a 60 s trace (the ruler tapped at the take's start, middle and end at a
zoom showing the whole take): 01:19 → 01:33, 14:42 → 14:56, 28:15 → 28:28 with up to three clips streaming,
`audio.starvation` 0, `audio.xruns` 0, `audio.overBudget` 0. SC-003 holds on the Pixel 6 Pro with the reservoir.
