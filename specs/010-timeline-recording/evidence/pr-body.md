## Summary

Feature 010: record the microphone on the Timeline. Arm a track, press Record, and a take grows on the timeline with its waveform, aligned to the beat by the device's reported latencies. Spec, plan and tasks are in `specs/010-timeline-recording/`. `main` (with 011) is merged in.

```text
arm          track header toggle / A key; Record arms the selected or a new track first (FR-008)
record       transport bar from 432 dp, the armed compact header below; R key; count-in Off / 1 / 2 bars
take         grows with its waveform; one stop ends it, the take is adopted as a clip with undo
meter        input level with hold and clip latch on the track header; monitoring through the track strip, I key, never on the built-in speaker
survives     screen off (Android: microphone type on the playback service); ends cleanly on calls, input changes, revocation, leaving, termination
permission   ask, "Open settings" when denied; a device without a microphone says so and asks for nothing
perf         recording journey on both platforms; sections countIn, arm, recordStart, recordStop, takeAlignment; four audio counters
```

```text
Record pressed
  RecordingController.record(countIn, armFirst)       # policy: RecordingPolicy, a pure function
    arm + open the input → engine: clock to start − countIn, click forced on
  C++ output callback, one duplex block               # nothing Kotlin on the callback (Constitution I)
    read the input block non-blocking → render the same block
    capture source → SPSC fifo
  tembra-recorder thread: fifo → 24-bit WAV
  stop: the take shifted by input + output latency, adopted with undo
```

Decisions and the principle behind each:

- **One duplex callback (Constitution I, research R2).** The input is read inside the output callback right before the render of the same block (Oboe second stream; RemoteIO bus 1).
- **Pre-roll inside the engine (Constitution II, FR-009/FR-015).** Capture begins at the downbeat in the block that reaches it. The session stays `Stopped` through the pre-roll, so leaving cancels without a dialog (FR-022).
- **Alignment by the engine (FR-011).** Every take is shifted by the latencies the streams report (`tembra_capture_set_input_latency_ns`, ABI 7). The loopback check of quickstart §8 is the judge (owner, the one sounding step).
- **Refusals in the ViewModel and the content (FR-009/FR-018).** Seek, structural toolbar entries and their keys are disabled during a take; undo is refused while a take finalises.
- **Meter and growing take read per frame in the draw phase** (`InputTelemetry`, `rememberFrameTick`), never through `StateFlow`; a 10 Hz mirror feeds semantics only, idle while nothing is armed.
- **Android foreground service:** the microphone type only for the "Recording" notification and only with the permission granted (Android 14 refuses it otherwise).
- Skill contracts: `compose-component-design`, `compose-state-and-effects`, `compose-performance`, `compose-focus-navigation`, `compose-ui-testing-patterns`, `kotlin-concurrency-and-flow`.

Merging main brought 011's keyboard catalogue in: A / R / I are catalogue entries now (Timeline scope, listed in the Ctrl/⌘+/ sheet), Escape cancels a count-in, and 011's "microphone not required" devices get a "No microphone is available" message instead of a permission prompt.

## Evidence

- **Before (main):** a track has no input; there is no Record control.
  **After:** [phone, 360 dp, the armed header's Record](https://github.com/vshpyrka/Tembra-Audio/blob/010-timeline-recording/specs/010-timeline-recording/screenshots/android/redmi-note-5/360dp-recording.png?raw=true) · [tablet, 1024 dp, Record in the bar](https://github.com/vshpyrka/Tembra-Audio/blob/010-timeline-recording/specs/010-timeline-recording/screenshots/android/pixel-tablet/1024dp-recording.png?raw=true) · [every state on every width](https://github.com/vshpyrka/Tembra-Audio/blob/010-timeline-recording/specs/010-timeline-recording/screenshots/)
- **Soak (SC-003), after the merge:** iPhone 14, one 29:35 take: input overruns 0, starvation 0, xruns 0, over budget 0, max callback 87 µs. Pixel 7 Pro, one 24:40 take: the same three counters 0. [Notes](https://github.com/vshpyrka/Tembra-Audio/blob/010-timeline-recording/specs/010-timeline-recording/evidence/notes.md)
- **Perf (before the merge, [trial-round.md](https://github.com/vshpyrka/Tembra-Audio/blob/010-timeline-recording/specs/010-timeline-recording/evidence/trial-round.md)):** recording journey 20/20 on the Pixel 7 Pro (frames p50 7.8 ms, p99 15.1 ms) and the Pixel 6 Pro (p50 8.5 ms, p99 14.5 ms); iPhone 14 countIn 1978 ms, recordStart 15 ms, recordStop 38 ms. Against the pre-010 code on the same Pixel 7 Pro: startup ttid +4 %, tuner/metronome/timeline journeys within 2 %.
- **Lanes after the merge, all green:** host (`checkAndroidHost` 1005 JVM tests, `engineHostTest`, `assembleRelease`, zero warnings); `checkAndroidDevice` on the Pixel 6 Pro, Pixel 7 Pro, Pixel Tablet, OPPO CPH2239 (Android 11) and Redmi Note 5 (Android 9), 1610 tests each, plus the app module's 48 on each; `checkIos` on the iPhone 18 Pro, iPad Pro 13-inch (M5) and iPhone Duo simulators, 1614 tests each; the iOS perf target builds for the simulator and the iPhone 14; `uv run pytest` 364 passed.
  - **Before:** the OPPO failed the recording-notification test (ColorOS refuses the runner's microphone grant).
    **After:** the grant is asked for only from Android 14, where the microphone type is checked (6b58d0d).

## Merge Danger

**Door:** two-way

Revertable as one merge commit. Takes are ordinary WAV assets and clips, and the project document gains one field, `countIn` (default one bar), which builds without 010 skip (`ignoreUnknownKeys`), so they still open projects that contain takes.

**Blast Radius:** app-wide

A merge to main publishes an internal-testing build to Google Play. The bundle adds `FOREGROUND_SERVICE_MICROPHONE` (`mediaPlayback|microphone` on the playback service). Play Console's foreground service declaration needs the microphone type added, with a short video (`docs/google-play-publishing.md`); until then Play may refuse the upload. The engine ABI moves to 7, and the stress journey now records, so its memory peak is +21 %.

Open for the owner: the sounding and by-hand rows (T070 headphones and the speaker guard, T075 headset mic, calls, lock, force quit, T081 TalkBack/VoiceOver, T090 loopback alignment), T094 (Record's placement at 360 dp), T095 (Stitch sync), T096's nine-track project and playback by ear, T097's post-merge `--rounds 5` sessions.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
