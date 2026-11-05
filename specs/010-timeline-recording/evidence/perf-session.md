# Post-merge performance sessions (T097), 2026-11-04/05

The sessions the perf contract asks for after the merge of #13 (`ea78b2a`, feature 010 with `main` and feature 011 in
it), read against the right references, with every gate notice and onset explained.

## What ran

| Device | Label | Code | Rounds | Notes |
|---|---|---|---|---|
| Pixel 7 Pro (`33241FDH3000NZ`) | `010-recording` | perf build of `ea78b2a` | 5 succeeded: `20261104T214117Z` (local), `20261104T230734Z`, `20261105T005655Z`, `20261105T022500Z`, `20261105T035209Z` (published) | the first session stopped after round 1 on a pipeline bug (the record schema lacked `windowing`, fixed in 62358ff); later sessions were refused round after round at 36-39 °C battery on the owner's charger (limit 35 °C), so the Pixel 7 Pro now has the Pixel 6 Pro's 40 °C limit (7e7252e) |
| Pixel 6 Pro (`19021FDEE001AH`) | `010-closeout` | perf build of `8ca8d5a` (the two-burst input reservoir) | 5 succeeded, `20261104T234548Z` … `20261105T044727Z`, all published | battery 38.9-41 °C (limit 40 °C), one refusal at the start |
| Pixel 6 Pro | `010-metronome-dispatch` | perf build of `7e7252e` (the metronome fix below) | 1 round of the metronome and stress journeys, `20261105T060818Z`, local | the verification of the fix |
| iPhone 14 | `010-recording` | Perf configuration at `43dd7a8` | none completed: see § iPhone 14 | refused on heat, then two full rounds cut by the round time limit |

Every round of the Android sessions ran all seven journeys (startup, tuner, metronome, timeline, recording, stress,
windowing) with 20 iterations each.

## The report's verdict

`tracepulse report` after `sync` (91 rounds on six devices): **0 onsets in the last session** on every device, "nothing
confirmed". The gate lists 15 metrics above their limit on the Pixel 7 Pro and 14 on the Pixel 6 Pro. Those gates compare
with the published history, which for both phones ends on 2026-10-13/15 (the 007 pipeline, the noise baseline and the
008 baseline-profile rounds): features 009, 010 and 011 all landed since, and none of their rounds were published. Each
notice and its cause:

| Notice (Pixel 7 Pro / Pixel 6 Pro, against October) | Cause |
|---|---|
| `app.size_bytes` +285 % / +285 % | feature 009's demo project audio in the APK (7 MB to 27 MB, noted in 010's pull request); 010 itself added 0.6 % (26.64 to 26.80 MB against the pre-010 reference) |
| `startup.ttid_ms` +32 % / +39-47 %, `startup.ttfd_ms` +82 % / +92-107 % | since 009 the app opens on the Timeline with the demo project loaded (project, waveform overviews); against the reference built just before 010 (`20261102T055511Z_009-main-reference`, Pixel 7 Pro) ttid is +8.2 % and ttfd +0.8 %, and against the 010 trial round on the Pixel 6 Pro +5.1 % and +5.6 %: within the 15 % startup gate |
| `metronome/journey.duration_ms` +57 % / +51-55 %, `tuner/journey.duration_ms` +27 %, `tuner/section.tuner.frame.count` +180 % | journeys reshaped by 009 (they start from the Timeline); 010 and 011 then shortened both (metronome −7.3 %, tuner −11.2 % against the pre-010 reference) |
| `metronome/section.metronome.load.sum_ms` +12 884 % / +438 223 %, `stress/section.metronome.load.sum_ms` | **a real main-thread stall, fixed** (below) |
| `stress/*` (`audio.callbacks` +4 000 %, `journey.duration_ms` +450-490 %, `memory.*` +42-83 %, `audio.max_callback_us` +1 050-1 300 %, `frames.p50_ms` +25-29 %, `section.tuner.frame.count` +122 %) | the stress journey changed: since 009 it plays the project and since 010 it also records a take. Against the pre-010 reference: frames p50 +4.8 %, p99 −2.2 %, memory peak +20.9 % (the follow-up 010's pull request named), the longest callback 154 to 219 µs (an eighth of the 2 ms block), starvation and xruns 0 |

## Against the references (the regression check)

**Pixel 7 Pro, the post-merge rounds against the pre-010 reference** (`20261102T055511Z_009-main-reference`, built from
`f47014e`, medians of the round medians):

| Metric | Pre-010 | Post-merge | Change |
|---|---|---|---|
| startup ttid / ttfd | 238.3 / 348.9 ms | 257.7 / 351.7 ms | +8.2 % / +0.8 % |
| tuner frames p50 / p99 | 6.33 / 16.93 ms | 5.99 / 20.97 ms | −5.3 % / +23.9 % (see "To watch") |
| metronome frames p50 / p99 | 4.88 / 12.44 ms | 4.78 / 12.66 ms | −1.9 % / +1.7 % |
| timeline frames p50 / p99 | 8.05 / 15.42 ms | 8.14 / 15.64 ms | +1.1 % / +1.4 % |
| timeline memory peak | 177.8 MB | 183.0 MB | +2.9 % |
| stress frames p50 / p99 | 7.53 / 15.90 ms | 7.89 / 15.55 ms | +4.8 % / −2.2 % |

The recording journey did not exist before 010; against the 010 trial round on the same phone (`20261101T205913Z`):
frames p50 7.77 → 7.91 ms, p99 15.18 → 15.03 ms, journey −2.7 %, take alignment 66 → 68 ms, `audio.input_overruns` 1
→ 1 in the stress journey. (That trial round carries the count-in and record-start sections swapped, a bug fixed in 010
before its merge; the later trial `20261101T222912Z` reads 1972 and 19.8 ms, the post-merge rounds 1956 and 16.1 ms.)

**Pixel 6 Pro, the two-burst build against the 010 trial round** (`20261102T040607Z_010-recording`): startup ttid
+5.1 %, ttfd +5.6 %; tuner, metronome, timeline, recording and stress frames within ±2 % at p50 and p90; the recording
journey −2.8 %; memory within ±7 %; `audio.input_overruns` in the stress journey **6 → 0** (the reservoir); starvation,
xruns and over-budget callbacks 0; the stress journey's take alignment 132 → 135 ms (the 4 ms cushion, reported with
the input latency).

## Fixed: the Metronome held the main thread when it opened from the Timeline

`metronome.load` (the stored settings to the first content and the grid handed to the engine) read 0.03 ms on
2026-11-01/02 and 4.2 ms (Pixel 7 Pro) to 116 ms (Pixel 6 Pro) after the merge, 162 ms in the stress journey, with the
metronome's frame p99 up from 12.4 to 17.7 ms on the Pixel 6 Pro. Cause: since 957da4b (the 010 audit fix) the
transport close runs on `EngineDispatchers.blocking` and holds the engine's command lock while it stops the timeline's
stream, and the Metronome screen sent its grid and sound from the main thread, so opening the Metronome straight from
the Timeline waited for the close. Fix (7e7252e): the grid and the sound are engine commands on the same serial
dispatcher as the start, stop and preview, in the order asked (`MetronomeViewModelTest.theGridAndTheSoundReachTheEngine…`);
the iOS facade allocates the grid struct per call, as the Timeline's bridge still sends its grid from the main thread.
Verification round `20261105T060818Z` (Pixel 6 Pro): `metronome.load` 116 → **0.02 ms**, in the stress journey 158 →
**0.03 ms**, the metronome's frame p99 17.7 → 13.0 ms; `metronome.tempoChange` 1.84 → 2.72 ms over ten changes (the
coroutine hand-off, under 0.1 ms each).

## To watch (no onset, under the limits)

- **Tuner frame p99 on the Pixel 7 Pro**: 18.5-22.7 ms across the five post-merge rounds against one reference round at
  16.9 ms and the 010 trial at 18.5 ms (CoV 0.13-0.23 within rounds); under the 25 % frame limit and not an onset. The
  Pixel 6 Pro sits at 17.1-17.9 ms, inside its own October-November range of 16.1-20.8 ms.
- **Pixel 6 Pro `timeline.takeFrame`** 68 → 133 ms over about 675 frames (0.1 → 0.2 ms a frame) and **`recordStart`**
  4 → 13 ms, with the recording journey's frame times unchanged; the Pixel 7 Pro without the reservoir shows +11 % and no
  change. Small next to the 16 ms frame; worth a look in the next recording-related round.

## iPhone 14: not measured in this session

Three things stood in the way, the last one decisive:

1. **Heat.** Charging with the screen kept on (Auto-Lock off, so a round could start unattended), the phone refused
   rounds at thermal state "fair" from 04:00 to 07:30 and reached "serious" in the middle of one (09:26); it ran only
   after the owner let it cool.
2. **The 60-minute limit.** The first full round (`20261105T085714Z`) ran the tuner (5 min), the facade self-checks
   (10 min), startup (3 min), stress (23 min, it records since 010) and metronome (10 min) and was cut in windowing by
   `TEST_TIMEOUT_S` = 3600 s. The limit is now two hours (d068343).
3. **Two hours were not enough either.** The second full round (`20261105T100131Z`, 12:01 to 14:01) passed the facade
   self-checks, stress (23 min), recording (19.5 min) and the tuner (5 min), left windowing and timeline without a
   result, and was cut inside the metronome journey. On the physical phone XCUITest waits up to 60 s per step for the app
   to go idle while audio animations run, the open item of feature 011 (T129/T143), which stretches every journey;
   until that is fixed a round of all seven journeys does not fit in two hours. xcodebuild was killed before it
   finalised either result bundle, so no metrics can be read from them; both rounds were published as `failed`.

The iPhone 14's figures for feature 010 stay those of the recording journey round of 2026-11-02 (`20261102T085708Z`,
`evidence/trial-round.md`): count-in 1978 ms, arm 604 ms (since moved off the main thread, 55f6c16), record start 15
ms, record stop 38 ms, take alignment 20 ms, project save 22 ms. Its nine-track soak (`notes.md`) ran clean on the
merged code: every audio counter 0 over a 29-minute take. Next step: fix 011's T143, then run the iPhone session.
