# Building a Perfect, Drift-Free, Configurable Metronome on Mobile

Research + recommended design for a Kotlin Multiplatform (Android + iOS) music app.
Requirements: swappable beat **sound** (user WAV), per-beat **accents**, **tempo/BPM**
control, and the ability to stay **phase-locked to a DAW transport/timeline**.

> TL;DR — Never time clicks with a UI/OS timer. Treat the audio device's sample clock
> as the single source of truth. Compute each click as an **absolute sample position**
> and emit it from (or scheduled against) the **real-time audio callback**. Everything
> else (BPM, sound, accents) is data the audio thread reads; it is never a timer.

---

## 1. Why naive approaches drift (and are not sample-accurate)

Every timer-based approach shares one fatal property: it schedules work on a clock that
is **not** the clock the audio hardware actually plays samples on, and that runs on a
thread which can be preempted.

- **UI timers / main-loop callbacks, `Handler.postDelayed`, `java.util.Timer`,
  `Thread.sleep`, `CADisplayLink`, `NSTimer`, JS `setInterval`/`setTimeout`** all fire
  on a general-purpose thread subject to layout, rendering, garbage collection, input
  handling, and other callbacks. Chris Wilson's article notes such callbacks "can be
  delayed for tens of milliseconds or more by layout, rendering, garbage collection,
  and XMLHTTPRequest and other callbacks."
  (<https://web.dev/articles/audio-scheduling>)
- **`Thread.sleep(ms)` / `usleep`** guarantee only a *minimum* sleep. The OS may wake
  the thread late. Worse, the classic loop `play(); sleep(interval); play();` accumulates
  error: each iteration adds `play()` execution time + wake-up jitter, so the error is
  **cumulative** — after 200 beats you are seconds off. This is *drift*, distinct from
  per-beat *jitter*.
- **`CADisplayLink`** is locked to the display refresh (60/120 Hz ⇒ ~16.6/8.3 ms grid).
  It can never be finer than a frame and stutters when the render loop is busy.
- **Millisecond precision is too coarse anyway.** At 48 kHz one sample is ~0.0208 ms.
  A metronome quantized to 1 ms integer timers is already ±1 sample-grid-worth of slop
  per event, and rounding BPM→ms throws away the fractional part every beat, which drifts.
- **Two-clock mismatch.** Even if a timer fired perfectly, it measures *wall-clock*
  time, while audio is produced against the *audio device clock*. The two crystals differ
  by tens to hundreds of PPM and drift apart over minutes — audible phase slip against a
  DAW. Any correct design must reference the **audio clock**, not the system clock.

Key distinction to keep in the design:
- **Jitter** = random per-event error (early/late). Annoying, not cumulative.
- **Drift** = systematic accumulation. This is what makes naive metronomes unusable and
  what makes DAW sync fall apart. Sample-accurate scheduling eliminates both because the
  Nth beat's time is computed from an absolute origin, never from "previous beat + delay."

---

## 2. The correct approach: sample-accurate scheduling

### 2.1 The insight (Chris Wilson, "A Tale of Two Clocks")

Source: <https://web.dev/articles/audio-scheduling> (originally
<https://www.html5rocks.com/en/tutorials/audio/scheduling/>).

Web Audio exposes two clocks:
1. **The audio hardware clock** (`AudioContext.currentTime`), high precision, sample-
   addressable, and it keeps playing already-scheduled events even if the main thread
   stalls.
2. **The JavaScript clock** (`setTimeout`/`Date.now`), imprecise and stallable.

The trick is to **let the imprecise clock schedule work far enough ahead that its own
imprecision no longer matters**, and pin the actual sound start to the precise audio
clock.

**Look-ahead scheduler loop** (runs on a coarse timer ~every 25 ms):

```js
// lookahead = 25 ms (timer period); scheduleAheadTime = 0.1 s (100 ms window)
while (nextNoteTime < audioContext.currentTime + scheduleAheadTime) {
    scheduleNote(current16thNote, nextNoteTime);  // osc.start(nextNoteTime)
    nextNote();                                    // advance nextNoteTime + beat index
}

function nextNote() {
    var secondsPerBeat = 60.0 / tempo;   // reads CURRENT tempo each call
    nextNoteTime += 0.25 * secondsPerBeat;  // advance one 16th note
}
```

Two parameters:
- **lookahead** (~25 ms): how often the scheduler wakes.
- **schedule-ahead window** (~100 ms): how far ahead it commits events.

The window must comfortably exceed worst-case timer delay so a late wake-up (e.g. the
270 ms callback that actually fires at 320 ms) still has already-queued events covering
the gap. The cost: tempo changes take up to one window (~100 ms) to take effect — a good
trade. Crucially `nextNoteTime` is advanced by exact `secondsPerBeat` each note, so there
is **no cumulative rounding**; the sound itself is started with `osc.start(exactTime)`
on the sample-accurate audio clock.

### 2.2 Native equivalents

The same idea maps to native mobile, in two variants:

**(A) Pure audio-callback counter (recommended for a click-only metronome).**
The real-time audio callback is invoked to fill N frames. Maintain an absolute
`framePosition` counter (total frames rendered) and a `nextBeatFrame`. Inside the
callback, for each output block `[framePosition, framePosition+N)`, if `nextBeatFrame`
falls in that range, mix the click PCM starting at `nextBeatFrame - framePosition`, then
`nextBeatFrame += framesPerBeat`. No secondary thread, no timer at all — the audio clock
*is* the scheduler. This is exactly what sample-accurate AudioTrack/Oboe metronomes do:
compute frames-per-beat from BPM and sample rate, then count frames in the callback
rather than sleeping.
(<https://moshenskyi.medium.com/building-a-sample-accurate-metronome-with-audiotrack-in-android-7da27ac7dae1>)

- **Android**: Oboe/AAudio `AudioStreamDataCallback::onAudioReady(stream, audioData,
  numFrames)`. Set `PerformanceMode::LowLatency` + `SharingMode::Exclusive`.
  (<https://developer.android.com/games/sdk/oboe/low-latency-audio>)
- **iOS**: `AVAudioSourceNode` (iOS 13+) with an `AVAudioSourceNodeRenderBlock`
  `(isSilence, timestamp, frameCount, outputData)` attached to `AVAudioEngine`; the
  block runs on the real-time audio thread with the timestamp's `mSampleTime`.
  (<https://orjpap.github.io/swift/real-time/audio/avfoundation/2020/06/19/avaudiosourcenode.html>,
  header:
  <https://github.com/xybp888/iOS-SDKs/blob/master/iPhoneOS13.0.sdk/System/Library/Frameworks/AVFoundation.framework/Frameworks/AVFAudio.framework/Headers/AVAudioSourceNode.h>)

**(B) Look-ahead scheduler feeding a player node (Apple's Hello Metronome model).**
Instead of mixing PCM by hand, schedule pre-loaded buffers onto a player node at an
absolute sample time. Apple's *Hello Metronome* uses `AVAudioEngine` +
`AVAudioPlayerNode.scheduleBuffer(_:at:options:completionHandler:)` where `at:` is an
`AVAudioTime(sampleTime:atRate:)`. It keeps a queue of beats scheduled ~250 ms ahead and
uses each buffer's completion handler to schedule the next beat — a look-ahead scheduler
whose "timer" is the audio graph itself.
(<https://developer.apple.com/library/archive/samplecode/HelloMetronome/Introduction/Intro.html>)

```swift
let secondsPerBeat = 60.0 / tempoBPM
let samplesPerBeat = secondsPerBeat * bufferSampleRate
nextBeatSampleTime += samplesPerBeat
let playerBeatTime = AVAudioTime(sampleTime: AVAudioFramePosition(nextBeatSampleTime),
                                 atRate: bufferSampleRate)
player.scheduleBuffer(soundBuffer[bufferNumber]!, at: playerBeatTime,
                      options: [], completionHandler: { /* schedule next */ })
// beatsToScheduleAhead = kTempoChangeResponsivenessSeconds(0.25) / secondsPerBeat
```

**Contrast (A) vs (B):**

| | (A) Callback counter | (B) Look-ahead → player node |
|---|---|---|
| Timing source | absolute frame counter in RT callback | `AVAudioTime` sampleTime on player node |
| Who mixes | you, into the output block | the audio graph |
| Latency of tempo change | one audio buffer (~2–10 ms) | one look-ahead window (~250 ms) |
| Complexity | you own mixing + polyphony/tails | framework owns buffer playback |
| Best for | tight, click-only, DAW-synced metronome | app that also plays other buffers/tracks |
| iOS API | `AVAudioSourceNode` render block | `AVAudioPlayerNode.scheduleBuffer(at:)` |
| Android | Oboe callback (no node concept) | (n/a — Android has no equivalent node graph; emulate with callback + queue) |

For a KMP app that must be phase-locked to a DAW, **(A)** is the primary recommendation
because the tempo/transport response is a single buffer and the same conceptual code
(frame counter + click PCM) is shared across Android (Oboe) and iOS
(`AVAudioSourceNode`). (B) is the natural choice on iOS if you already have an
`AVAudioEngine` graph mixing tracks and prefer the framework to own playback.

**Real-time thread rules (both platforms), non-negotiable in the callback:** no memory
alloc/free, no file/network I/O, no mutex/lock waits, no `sleep`, no heavy one-off CPU,
no Obj-C/Swift ARC or JNI/GC-triggering calls. Do only bounded arithmetic + memcpy/mix.
(<https://developer.android.com/games/sdk/oboe/low-latency-audio>)

---

## 3. The click sound

### 3.1 Loading a user-supplied WAV

1. **Decode to PCM float** (`[-1, 1]` `float32`). Parse WAV (PCM int16/int24/float32) →
   normalize to float. On iOS you can decode via `AVAudioFile`/`ExtAudioFile`; on Android
   via `MediaExtractor`/`MediaCodec` or a small WAV parser (WAV is trivial to parse
   directly, which avoids codec quirks for the common case).
2. **Resample to the engine's sample rate** (usually 48 kHz on Android, device rate on
   iOS). Use a decent resampler (linear is audible on transients; prefer a windowed-sinc
   / polyphase resampler — Oboe ships `FlowGraph`/resampler utilities; on iOS
   `AVAudioConverter`). Do this **off the audio thread**, once, at load.
3. **Downmix / channel-match** to the output channel count (mono click → duplicated or
   center-panned into stereo).
4. **Store as an immutable, pre-rendered `ClickSample`** (float PCM + frame count). The
   audio thread only ever reads it.

Keep click buffers short (tens of ms). If a WAV tail is longer than one beat at fast
tempo, you must support **overlap** (see polyphony in §7): allow a new click to start
before the previous one finished by mixing multiple active voices.

### 3.2 Swapping the sound at runtime safely

Never mutate a buffer the audio thread might be reading. Use a **lock-free hand-off**:

- Build the new `ClickSample` fully on a worker thread.
- Publish it via an **atomic pointer swap** (`std::atomic<ClickSample*>` in C++, or an
  `AtomicReference`/`atomic` in Kotlin/Native; on iOS an `os_unfair_lock`-free atomic
  swap or a triple-buffer). The callback does `auto* s = current.load(acquire);` once at
  the top of each block and uses it for that block.
- **Reclaim safely**: don't `free` the old buffer immediately (the callback may still be
  mid-block). Hand the retired pointer to a garbage/deferred-free queue drained by a
  non-RT thread (e.g. after two callback periods, or via a hazard-pointer/RCU scheme).
  Pre-allocate a small pool so no allocation ever happens on the RT thread.

### 3.3 Separate accent vs normal-beat samples

Hold an array of sample slots, e.g. `samples[ACCENT]`, `samples[NORMAL]`, optionally
`samples[SUB]` (weak subdivision). The **accent pattern** (see §5) selects, per beat
index, which slot to trigger and at what gain. Both accent and normal are independently
swappable via the same atomic mechanism. A simple, robust default is **one sample +
per-beat gain/pitch**: accent = same click at higher gain (e.g. +6 dB) and/or higher
pitch; normal = unity. Distinct WAVs give better character; gain/pitch is the cheap
fallback and always available.

### 3.4 Synthesized default click (no WAV needed)

Generate at load into a float buffer at engine rate. Two good options:

**Enveloped sine/triangle "beep":**
- Frequencies: accent ~ **1500 Hz**, normal ~ **1000 Hz** (or 1200/800). Triangle adds a
  little bite; sine is cleanest.
- Duration: **~30–50 ms** total.
- **Envelope is what prevents clicks/pops.** A raw sine that starts/ends at non-zero
  amplitude produces a discontinuity = a broadband "tick." Apply a fast-attack /
  exponential-decay envelope:
  - **Attack**: 1–5 ms linear (or raised-cosine) ramp 0→1. A 2 ms cosine ramp is a good
    default. Never start at full amplitude on sample 0.
  - **Decay/Release**: exponential to near-zero over the remaining ~25–45 ms, and force
    the **last ~1–2 ms to ramp to exactly 0** so the buffer ends on a zero crossing.
  - Equivalently: multiply the tone by `env[n]` where `env` rises with a short
    raised-cosine and falls with `exp(-n / tau)`, `tau ≈ duration/4`.
- Optionally start the tone at phase 0 and stop at a zero crossing to be extra safe.

**Filtered noise burst (a "tick"/"rimshot" feel):**
- White noise × very short envelope (attack < 1 ms, total 5–15 ms), band-passed
  (e.g. 2–5 kHz) for a percussive click. Same rule: windowed so head and tail are zero.

Either way, **pre-render once** into a `ClickSample` and treat it exactly like a loaded
WAV. The RT callback never synthesizes; it only mixes finished PCM.

---

## 4. Tempo control (smooth, drift-free)

- **Store BPM as an atomic** the audio thread reads once per block. Changing it is just
  an atomic store from the UI thread — no locks.
- **Recompute from the transport position, never by patching the old interval.** The rule
  that eliminates drift: the next click's sample position is derived from an **absolute
  origin** (transport frame 0, or the last beat boundary) using the *current* tempo, not
  from "lastBeatFrame + oldFramesPerBeat."
- **`framesPerBeat = 60.0 / bpm * sampleRate` is fractional.** Keep it as `double`. Keep
  `nextBeatFrame` as a `double` (or fixed-point) accumulator and only round when
  comparing to the integer output-block boundary:
  ```
  framesPerBeat = 60.0 / bpm * sampleRate        // double, e.g. 24000.0 at 120 BPM/48k
  emitFrame     = llround(nextBeatFrameDouble)    // integer position to place the click
  nextBeatFrameDouble += framesPerBeat            // accumulate in double → no drift
  ```
  Accumulating in `double` and rounding only at emit time keeps long-term error bounded
  to <0.5 sample forever, instead of losing the fraction every beat.
- **Smooth tempo change without glitches:** on a BPM change, do **not** move a click that
  is already within the current output block (or already scheduled ahead in model B).
  Recompute `framesPerBeat` and let it apply from the *next* beat boundary. Because you
  advance beat-by-beat reading the live BPM (exactly like `nextNote()` reading the
  current tempo), tempo ramps/automation "just work": read BPM per beat (or per block)
  and the grid stretches continuously with no pop.
- **Continuous tempo automation / ramp:** if the DAW ramps tempo, sample the tempo at
  each beat boundary (or integrate BPM(t) over frames). For a linear BPM ramp you can
  integrate analytically; in practice per-beat re-evaluation is sufficient and matches
  how DAWs quantize tempo automation.
- **Avoid pitch artifacts:** changing tempo must change *spacing*, not resample the click
  (a metronome click's pitch is constant regardless of BPM). Keep click playback rate
  fixed; only the beat grid changes.

---

## 5. Musical features

Model the beat grid abstractly and let the callback walk it.

- **Time signature `num/den`.** `den` sets the beat unit (4 = quarter, 8 = eighth).
  `framesPerBeat` is computed for the beat unit; `num` beats make a bar.
- **Subdivisions.** Render clicks at `framesPerSubdivision = framesPerBeat / subdiv`
  (e.g. subdiv=2 eighths, 3 triplets, 4 sixteenths). Each subdivision slot has its own
  accent level (downbeat > beat > subdivision). Keep the subdivision accumulator in
  `double` too.
- **Accent pattern.** An array of length `num` (or `num * subdiv`) of accent levels, e.g.
  `[ACCENT, NORMAL, NORMAL, NORMAL]` for 4/4, `[ACCENT, NORMAL, WEAK, NORMAL, WEAK]` for
  a 5/8 grouped 2+3. Each level maps to a sample slot + gain (§3.3). This is a
  copy-on-write immutable array published atomically like the samples.
- **Count-in.** Play `num` (or 2 bars) of clicks before enabling transport/recording.
  Implement as a beat-index offset: transport "arms" at a future beat boundary; count-in
  beats have index < 0 and simply feed the same grid.
- **Phase-lock to the DAW transport/timeline.** The DAW exposes a transport position
  (in PPQ/beats or in samples) and a play state. Convert the DAW's current musical
  position to an absolute output-frame position and set `nextBeatFrame` so the metronome
  grid coincides with the DAW's beat grid:
  ```
  // beatsElapsed from DAW transport (e.g. ppqPosition)
  fractionalBeat = beatsElapsed - floor(beatsElapsed)
  nextBeatFrameDouble = currentOutputFrame + (1 - fractionalBeat) * framesPerBeat
  ```
  Re-sync on transport start/locate/loop-jump by recomputing `nextBeatFrame` from the new
  position — never by nudging the old value. Because both the DAW render and the
  metronome reference the **same audio device sample clock**, once aligned they stay
  aligned with zero drift. (If the metronome runs in a *separate* process from the DAW,
  align via the shared output timestamp — see §6 — and periodically re-anchor.)

---

## 6. Mobile specifics & pitfalls

### Buffer size / latency
- **Android (Oboe/AAudio):** request `PerformanceMode::LowLatency` +
  `SharingMode::Exclusive`; use the **callback**, not blocking writes. Tune the buffer to
  a small multiple of the burst: `setBufferSizeInFrames(framesPerBurst * 2)` (double-
  buffering), and watch `getXRunCount()` — increase the buffer if underruns appear.
  Prefer the device native rate (usually 48 kHz) to avoid a resampler in the output path.
  Optimal devices reach ~20 ms round-trip.
  (<https://developer.android.com/games/sdk/oboe/low-latency-audio>)
- **iOS (AVAudioEngine / AudioSession):** set the audio session category to `playback`,
  request a small `preferredIOBufferDuration` (e.g. 0.005 s), 48 kHz preferred rate. The
  render block gets a hard deadline (~11 ms at 512 frames / 44.1 kHz) — stay well inside.
  (<https://orjpap.github.io/swift/real-time/audio/avfoundation/2020/06/19/avaudiosourcenode.html>)

### Output-latency compensation (critical for perceived + DAW sync)
The *scheduled* sample time is not when sound hits the speaker; there is output latency
(buffering + DAC + analog path). For **visual sync** (flashing UI, "beat N" callbacks)
you must delay the UI event by the output latency so the flash matches the audible click:
- **iOS:** use `AVAudioTime` host-time conversion + `output.presentationLatency`
  (Hello Metronome dispatches its `metronomeTicking` delegate at
  `nodeBeatTime.hostTime + hostTicks(presentationLatency)`).
  (<https://developer.apple.com/library/archive/samplecode/HelloMetronome/Introduction/Intro.html>)
- **Android:** query the presentation timestamp. `AudioTrack.getTimestamp` /
  `AAudioStream_getTimestamp` return a (framePosition, nanoTime) pair accurate to
  **±2 ms**; combine with `getFramesWritten()`/`getFramesPresented()` to map a scheduled
  frame to a wall-clock presentation time (and thus to a UI dispatch time).
  (<https://google.github.io/oboe/classoboe_1_1_audio_stream.html>,
  <https://android.googlesource.com/platform/compatibility/cdd/+/refs/heads/master/5_multimedia/5_6_audio-latency.md>)

For **DAW transport sync** across processes, the shared reference is that same output
timestamp: convert the DAW's transport-position-at-presentation into the metronome's
frame domain using `getTimestamp()`; re-anchor periodically to absorb the ±2 ms and any
clock crystal drift between separately opened streams. When the metronome shares the
DAW's own audio graph/callback, use the render timestamp directly and skip cross-process
conversion.

### Other pitfalls
- **Route/rate changes** (plug/unplug headphones, Bluetooth) can change sample rate and
  latency mid-session → re-open stream, recompute `framesPerBeat` and re-resample clicks
  to the new rate, re-anchor to transport.
- **Bluetooth output** adds large, variable latency — compensation must be dynamic (poll
  the timestamp), not a constant.
- **App backgrounding / audio focus / interruptions** must pause and cleanly re-anchor.
- **Stream restart** resets frame counters — persist musical position, re-derive
  `nextBeatFrame` from transport, don't assume continuity.
- **Denormals / clipping** when mixing multiple click voices — clamp the mix; flush
  denormals if you add filters.

### OSS examples / implementations (note licenses)
- **Oboe** and all Oboe **samples** — Apache-2.0. Relevant samples: **RhythmGame**
  ("copy the clap patterns … by tapping on the screen" — demonstrates scheduling audio
  events against the frame clock and low-latency callback), **DrumThumper** (WAV drum
  pads → WAV decode/playback), **MegaDrone** (low-latency + polyphony), **hello-oboe**.
  (<https://android.googlesource.com/platform/external/oboe/+/HEAD/samples/README.md>,
  <https://github.com/google/oboe>)
- **Apple "Hello Metronome"** (sample code, Apple sample license) — the canonical
  `AVAudioEngine` + `scheduleBuffer(at:)` look-ahead metronome.
  (<https://developer.apple.com/library/archive/samplecode/HelloMetronome/Introduction/Intro.html>)
- **Alexander-Nagel/Metronome-using-AVAudioEngine** (GitHub) — small sample-accurate
  AVAudioEngine metronome with two sounds + visual sync; check its repo license before
  reuse. (<https://github.com/Alexander-Nagel/Metronome-using-AVAudioEngine>)
- **"Sample-accurate metronome with AudioTrack"** (Moshenskyi, Medium) — the frames-per-
  beat + frame-counting technique in Kotlin/AudioTrack (article, code MIT-ish snippets —
  verify). (<https://moshenskyi.medium.com/building-a-sample-accurate-metronome-with-audiotrack-in-android-7da27ac7dae1>)
- **Superpowered SDK** examples (e.g. the "Again"/frequency-domain samples) show low-
  latency RT callbacks, but the **Superpowered SDK is proprietary/commercial-licensed** —
  fine as a reference, not for direct inclusion in a free OSS app. Prefer Oboe (Apache-2.0)
  as the Android engine.
- **Chris Wilson metronome demo** (`webaudiodemos/metronome`, the article's companion
  code) — reference for the look-ahead algorithm itself.
  (<https://web.dev/articles/audio-scheduling>)

---

## 7. Concrete recommended design

**Engine choice.** Android: **Oboe (Apache-2.0)** in C++, `LowLatency` + `Exclusive`,
callback-driven. iOS: **`AVAudioEngine` + `AVAudioSourceNode`** render block. Share the
*scheduling logic* (pure, RT-safe arithmetic over a frame counter) in a single
implementation — either a small C/C++ core called from both platforms via KMP/cinterop,
or duplicated Kotlin-shaped logic; the algorithm is identical. This gives model **(A)**
(callback counter) on both platforms with sub-buffer tempo/transport response.

### 7.1 Scheduling model (the counter approach)

The audio callback is the scheduler. On each invocation it renders `numFrames` starting
at absolute output position `framePos`:

```
onAudioReady(out, numFrames):
    grid   = gridState.load(acquire)      # immutable: bpm, sig, subdiv, accentPattern
    slots  = sampleSlots.load(acquire)    # immutable array of ClickSample*
    framesPerBeat = 60.0 / grid.bpm * sampleRate / (grid.den/4)   # double
    fps    = framesPerBeat / grid.subdiv                          # frames per subdivision

    zeroFill(out, numFrames)

    # 1) advance any already-sounding voices, mixing their PCM into out
    for v in activeVoices: mixVoice(v, out, numFrames)

    # 2) emit new clicks whose position falls in [framePos, framePos+numFrames)
    while nextClickFrame < framePos + numFrames:
        offset = round(nextClickFrame) - framePos          # >= 0
        (slotId, gain) = grid.accentAt(clickIndex)         # ACCENT / NORMAL / WEAK
        startVoice(slots[slotId], gain, offset)            # from a pre-alloc voice pool
        mixVoiceTail(...)                                   # mix this block's part now
        clickIndex = (clickIndex + 1) % grid.ticksPerBar
        nextClickFrame += fps                              # double accumulate → no drift

    framePos += numFrames
```

- `nextClickFrame` is a **double** accumulator (no per-beat rounding error).
- On transport start/locate, `nextClickFrame` and `clickIndex` are **recomputed from the
  DAW transport position** (§5), never nudged.
- Voices come from a **pre-allocated pool** (e.g. 8 voices) so overlapping click tails at
  fast tempo need no allocation on the RT thread. A voice = `{ ClickSample* sample; int
  playhead; float gain; }`.

### 7.2 Audio-thread data structures

All shared state is **immutable snapshots published by atomic pointer swap**; the RT
thread loads once per block and never locks:

```
struct ClickSample { const float* pcm; int frames; int channels; };   // immutable
struct GridState {                                                     // immutable
    double bpm; int num, den, subdiv;
    const uint8_t* accentPattern; int ticksPerBar;                     // slot id per tick
    int64_t transportAnchorFrame; double anchorBeat;                   // for DAW lock
};
struct Voice { const ClickSample* s; int playhead; float gain; bool active; };

atomic<GridState*>   gridState;        // UI publishes new grid (BPM, sig, accents)
atomic<ClickSample*> slot[NUM_SLOTS];  // ACCENT / NORMAL / WEAK, hot-swappable WAVs
Voice                voices[POOL];      // RT-owned, fixed size
double               nextClickFrame;    // RT-owned accumulator
int                  clickIndex;        // RT-owned position in bar
int64_t              framePos;          // RT-owned absolute output frame counter

// retired snapshots go to a deferred-free queue drained by a worker thread (RCU-style)
```

### 7.3 How changes are applied

- **Tempo change:** UI atomically stores a new `GridState` with new `bpm`. RT loads it
  next block, recomputes `framesPerBeat`; the change takes effect from the next tick.
  Ticks already emitted this block are untouched → no glitch, no drift.
- **Sound swap:** worker builds a new `ClickSample` (decode → resample → float), then
  `slot[NORMAL].store(newSample, release)`. RT picks it up next block. Old sample is
  handed to the deferred-free queue.
- **Accent pattern / time signature / subdivision change:** new immutable `GridState`
  published atomically; RT re-reads `ticksPerBar`, `accentPattern`, `subdiv` next block.
- **DAW transport sync:** on transport start/locate/loop, the control layer computes
  `transportAnchorFrame` + `anchorBeat` (using the output timestamp / render time), and
  the RT thread sets `nextClickFrame` so the grid coincides with the DAW beat grid, then
  free-runs on the sample clock. Periodic re-anchor absorbs cross-stream drift.
- **UI beat indicator:** derived from the *presentation* time, not the schedule time —
  each emitted tick's frame is mapped to a wall-clock time via the platform timestamp
  API and dispatched to the UI thread with output-latency compensation, so the flash
  matches the audible click.

### 7.4 Why this satisfies every requirement

- **Swap sound** → atomic `slot[]` pointer swap of pre-rendered float PCM (§3.2).
- **Per-beat accents** → `accentPattern` maps tick → slot + gain (§5, §3.3).
- **Tempo/BPM** → atomic BPM in `GridState`; double-accumulated frame grid = no drift (§4).
- **DAW sync** → grid anchored to the DAW transport on the shared sample clock; re-anchor
  on locate; latency-compensated UI (§5, §6).

---

## Scheduling model spec (for a diagram)

```
                     ┌──────────────────────────────────────────────────────┐
   CONTROL / UI      │   Non-real-time threads (safe to allocate & lock)     │
   thread            │                                                        │
   ─────────         │  • BPM / time-sig / subdivision / accent-pattern edits │
   BPM=120           │        └─► build immutable GridState ──┐               │
   accent=[A,N,N,N]  │  • user WAV → decode → resample(float) │ atomic        │
   swap WAV          │        └─► build ClickSample ──────────┤ pointer       │
                     │  • DAW transport pos/locate            │ swap          │
                     └────────────────────────────────────────┼───────────────┘
                                                               │  (publish)
                     ┌─────────────────────────────────────────▼──────────────┐
   TRANSPORT CLOCK   │   Snapshot state read once per audio block (lock-free)  │
   (single source    │   gridState*  slot[ACCENT|NORMAL|WEAK]*  transportAnchor │
    of truth =       └─────────────────────────────────────────┬──────────────┘
    audio device                                                │
    SAMPLE CLOCK)                                                ▼
                     ┌────────────────────────────────────────────────────────┐
   framePos ────────►│         REAL-TIME AUDIO CALLBACK (Oboe / AVAudioSourceNode)
   (absolute output  │                                                          │
    frame counter)   │   framesPerBeat = 60/bpm * sampleRate / (den/4)  (double)│
                     │   fps           = framesPerBeat / subdiv                 │
                     │                                                          │
   NEXT-BEAT ───────►│   while (nextClickFrame < framePos + numFrames):         │
   SAMPLE POSITION   │       offset = round(nextClickFrame) - framePos          │
   nextClickFrame    │       (slot, gain) = accentPattern[clickIndex]           │
   (double accum,    │       startVoice(slot, gain, offset)  ◄── from pool      │
    no rounding      │       clickIndex   = (clickIndex+1) % ticksPerBar        │
    drift)           │       nextClickFrame += fps        ◄── double accumulate  │
                     │                                                          │
                     │   mix active voices' PCM into out[offset..]              │
                     │   framePos += numFrames                                  │
                     └───────────────────────┬──────────────────────────────────┘
                                              │ rendered PCM
                                              ▼
                              ┌───────────────────────────────┐
                              │  Output buffer → DAC → speaker │
                              └───────────────┬───────────────┘
                                              │ getTimestamp() / presentationLatency
                                              ▼
                              ┌───────────────────────────────┐
                              │ UI beat flash (latency-comp.)  │
                              └───────────────────────────────┘

   DAW SYNC: transport position ──► anchorFrame/anchorBeat ──► sets nextClickFrame
             so the click grid coincides with the DAW beat grid on the SAME sample
             clock; re-anchor on start/locate/loop; both drift-free thereafter.
```

**Pipeline in one line:** *transport (audio sample) clock → next-beat absolute sample
position (double accumulator, recomputed from tempo/transport, never nudged) → real-time
audio callback emits the click PCM at `nextClickFrame - framePos` within the output
block → DAC; UI/DAW events are derived back from the presentation timestamp with
output-latency compensation.*

---

## Sources
- Chris Wilson, "A Tale of Two Clocks — Scheduling Web Audio with Precision" —
  <https://web.dev/articles/audio-scheduling>
- Android / Oboe, "Low-latency audio" — <https://developer.android.com/games/sdk/oboe/low-latency-audio>
- Oboe samples (Apache-2.0) — <https://android.googlesource.com/platform/external/oboe/+/HEAD/samples/README.md>, <https://github.com/google/oboe>
- Oboe `AudioStream` API (getTimestamp) — <https://google.github.io/oboe/classoboe_1_1_audio_stream.html>
- Apple, "Hello Metronome" (AVAudioEngine + `scheduleBuffer(at:)`) — <https://developer.apple.com/library/archive/samplecode/HelloMetronome/Introduction/Intro.html>
- `AVAudioSourceNode` overview + header — <https://orjpap.github.io/swift/real-time/audio/avfoundation/2020/06/19/avaudiosourcenode.html>, <https://github.com/xybp888/iOS-SDKs/blob/master/iPhoneOS13.0.sdk/System/Library/Frameworks/AVFoundation.framework/Frameworks/AVFAudio.framework/Headers/AVAudioSourceNode.h>
- Android CDD 5.6 audio latency (getTimestamp ±2 ms) — <https://android.googlesource.com/platform/compatibility/cdd/+/refs/heads/master/5_multimedia/5_6_audio-latency.md>
- Moshenskyi, "Building a Sample-Accurate Metronome with AudioTrack" — <https://moshenskyi.medium.com/building-a-sample-accurate-metronome-with-audiotrack-in-android-7da27ac7dae1>
- Alexander-Nagel, Metronome-using-AVAudioEngine — <https://github.com/Alexander-Nagel/Metronome-using-AVAudioEngine>
