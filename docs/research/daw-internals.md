# DAW Internals & a Mobile-DAW Engine Architecture

Research brief for the KMP (Android + iOS) open-source mobile DAW.
Author focus: how professional DAWs work internally, and a concrete, diagrammable engine architecture we can build.

Date: 2026-08-23

---

## 0. TL;DR

A DAW is two loosely-coupled worlds:

1. A **control / model plane** (the arrangement: tracks, clips, tempo map, automation, project file, UI). Runs on normal threads, can allocate, can block, can do IO.
2. A **real-time audio plane**: one high-priority audio callback that must render one buffer of samples every few milliseconds, forever, with **no allocation, no locks, no IO, no unbounded work**. Everything the audio thread needs is either pre-allocated or handed to it through **lock-free, single-reader/single-writer message passing**.

The audio plane is organized as a **processing graph** of nodes (sources → inserts → mixer buses → master → output). Each node exposes `prepare(sampleRate, maxBlockSize)` and `process(buffer, numSamples, transportInfo)`. Parameters (gain, pan, effect params, automation) are delivered to nodes as **smoothed / ramped values** to avoid zipper noise, ideally with **sample-accurate** break points inside a block.

For a mobile KMP DAW the honest architecture is: **shared C++ (or Rust) real-time DSP core** driven by the platform's native low-latency callback (**Oboe/AAudio on Android, an AVAudioEngine/RemoteIO render callback on iOS**), with **Kotlin/KMP owning the control plane, project model, and Compose UI**. Kotlin/Native (like the JVM) is garbage-collected and is **not** safe on the audio thread, so the render loop lives in C++. See §8–9 for the graph and the diagram spec.

---

## 1. The real-time audio processing model

### 1.1 The audio callback and the deadline

Audio hardware consumes samples at a fixed rate (typically 44.1 kHz or 48 kHz). The OS hands your app a **callback** that must fill the next **buffer** (a.k.a. block, or on Android a "burst") of samples. If the callback doesn't return in time, the hardware runs out of data and you get an audible glitch (a click, dropout, or "step change in output amplitude").

The core mental model, from Ross Bencina's canonical *Real-time audio programming 101: time waits for nothing*:

> "The result is that your code will eventually take longer than the buffer period and your audio will glitch." — the enemy is **code with unpredictable or unbounded execution time**.
> (http://www.rossbencina.com/code/real-time-audio-programming-101-time-waits-for-nothing , mirror discussion: https://lwn.net/Articles/452630/)

Concrete deadlines:
- iOS RemoteIO at 128 frames / 48 kHz → callback fires roughly **every 2.667 ms** and must return ≤ 2.667 ms of work (https://developer.apple.com/library/ios/qa/qa1715/_index.html , https://developer.apple.com/forums/thread/708168).
- Android Oboe/AAudio "low latency" streams call `onAudioReady()` once per burst; "the time between callbacks can be very short, just a few milliseconds, so it is very important that you don't do anything in the callback that could block" (https://github.com/google/oboe/blob/main/docs/FullGuide.md , https://developer.android.com/games/sdk/oboe/low-latency-audio).

### 1.2 Sample rate & buffer size

- **Sample rate (Fs)**: samples per second per channel (44100 / 48000; higher for pro work).
- **Buffer / block size**: frames processed per callback (e.g. 64, 128, 256, 512). Latency ≈ `bufferSize / Fs`. Smaller buffer = lower latency but more callbacks/sec = higher CPU risk of missing the deadline.
- Android exposes the device's **native burst size and sample rate** via `AudioManager.getProperty()`; Oboe recommends setting the buffer to **two bursts (double buffering)** and using `PerformanceMode::LowLatency` + `SharingMode::Exclusive` (https://developer.android.com/games/sdk/oboe/low-latency-audio).
- The engine's internal processing block size does **not** have to equal the hardware buffer, but keeping them aligned avoids extra buffering/latency.

### 1.3 The processing graph / node graph

Instead of one giant `process()`, DAWs model signal flow as a **directed graph of nodes**. Each node is an audio processor with input/output ports; **edges (connections)** wire an output channel of one node to an input channel of another. The classic reference implementation is **JUCE's `AudioProcessorGraph`**:

- Each processor added becomes a `Node` with a unique `NodeID`; `Connection` objects link `{sourceNode, channel} → {destNode, channel}`; special `AudioGraphIOProcessor` nodes represent the graph's own audio/MIDI in and out. MIDI is routed on a reserved channel index (`0x1000`). The graph is itself an `AudioProcessor`, so it exposes `prepareToPlay()`, `releaseResources()`, `processBlock()`.
- On `processBlock`, the graph **topologically sorts** the nodes and processes them in dependency order, reusing shared audio buffers between nodes.
  (https://docs.juce.com/master/classAudioProcessorGraph.html , tutorial: https://juce.com/tutorials/tutorial_audio_processor_graph/)

Why a graph: it makes routing (sends, buses, sidechains), parallel processing, latency compensation, and dynamic add/remove of effects tractable and analyzable.

### 1.4 Per-block (buffer) processing

Every node processes **N frames at a time**. A typical node contract:

```
prepare(sampleRate, maxBlockSize)   // non-realtime: allocate scratch buffers, resize filters
process(audioBuffer, numSamples, transport, events)  // realtime: no allocation
reset()                              // clear filter state
```

Block processing amortizes per-callback overhead and enables SIMD/vectorized DSP. The cost: control changes (automation, note events) are naturally **quantized to the block boundary** unless you split blocks at event times (see §3, §4 on sample-accuracy).

### 1.5 Thread model: audio thread vs UI thread

- The **audio thread** is a real-time, high-priority OS thread owned by the audio driver (Oboe/AAudio worker on Android; `AURemoteIO::IOThread` on iOS). It runs the callback and nothing else.
- The **UI/control threads** (Compose UI thread, background worker threads, disk-streaming threads) do everything the audio thread is forbidden from doing.

**Forbidden on the audio thread** (all can take unbounded / unpredictable time):
- **Memory allocation / free** (`new`, `malloc`, autorelease, boxing, GC) — allocators can lock and can page.
- **Locks / mutexes** — cause **priority inversion**: a low-priority thread holding the lock can be preempted, stalling the high-priority audio thread past its deadline. "Do not acquire locks or mutexes in time critical code!"
- **File / network IO**, logging to disk.
- **Anything unbounded**: unbounded loops, blocking syscalls, throwing/catching across boundaries, waiting on another thread.
  (Bencina, http://www.rossbencina.com/code/real-time-audio-programming-101-time-waits-for-nothing ; LWN summary https://lwn.net/Articles/452630/ ; Doumler, https://timur.audio/using-locks-in-real-time-audio-processing-safely)

### 1.6 Lock-free command queues / message passing to the audio thread

Because the audio thread can't lock, all communication is via **lock-free, wait-free** data structures — usually **single-producer/single-consumer (SPSC) ring buffers (FIFOs)** and **atomics**:

- **UI → audio (commands)**: "add node", "set param", "start clip at sample X", "load prepared buffer pointer". The UI thread *pre-builds* the object (allocates off the audio thread) and pushes a small message/pointer onto the FIFO. The audio thread pops and swaps it in with an atomic pointer exchange.
- **Audio → UI (telemetry)**: meter levels, playhead position, "node is now safe to delete". Pushed onto a second FIFO drained by the UI on a timer.
- **Object lifetime**: never `delete` on the audio thread. The audio thread pushes the retired object to a **garbage/return queue**; a non-realtime thread frees it. (JUCE's graph uses async rebuilds; Tracktion swaps whole node graphs.)
- More advanced sharing of large mutable state without locks: **RCU / SeqLock** patterns (Timur Doumler, ADC): https://www.youtube.com/watch?v=7fKxIZOyBCE , https://conference.audio.dev/session/2024/wait-free-thread-synchronisation-with-the-seqlock/ . Practical lock-free FIFO writeup: https://jatinchowdhury18.medium.com/wait-free-programming-from-scratch-5ac6a65c23c4

### 1.7 Mobile platform specifics

- **Android**: use **Oboe** (wraps AAudio on API 27+, OpenSL ES fallback). Implement `AudioStreamDataCallback::onAudioReady()`; run all DSP in C/C++ (NDK). Do **not** touch the JVM from the callback (a JNI call can trigger GC / JIT / lock). Get native buffer/sample-rate hints from `AudioManager`. (https://github.com/google/oboe/blob/main/docs/FullGuide.md , https://source.android.com/docs/core/audio/latency/app)
- **iOS**: use a **RemoteIO Audio Unit render callback** (or **AVAudioEngine**, which is "targeted for low latency"). Round-trip latencies under ~4 ms are achievable with wired headphones. The render callback runs on a real-time thread and "must return within a specific time-slice; calling APIs that can potentially block will adversely affect the render process." (https://developer.apple.com/forums/thread/708168 , https://developer.apple.com/library/ios/qa/qa1715/_index.html)
- **KMP consequence**: the callback body must be native, allocation-free C++ (or Rust). Kotlin/Native and the JVM both have a GC → not audio-thread safe. So KMP is used for the **control plane + UI**, not the render loop (see §8).

---

## 2. The mixer

### 2.1 Summing tracks

The mixer sums many track signals into buses and ultimately the master. Digitally, "summing" is literally sample-wise addition of each source's buffer into a destination bus buffer. Every mixer has a master output that "works as a summing point for every individual channel." (https://www.lmpro.ca/post/how-signal-flows-in-a-mixing-console-a-complete-signal-path)

### 2.2 Gain staging

Set sensible levels at each stage to avoid noise buildup and inter-stage clipping. Common target: average around **-18 dBFS RMS** with peaks below **-6 dBFS**, leaving headroom before the master. (https://kansamples.com/blogs/learn/gain-staging-routing-mix , https://vector-dsp.com/blog/audio-signal-flow-explained-step-by-step/)

### 2.3 Pan law (-3 dB / -6 dB / -4.5 dB)

Panning distributes a signal between L and R. The **pan law** is the center attenuation that keeps perceived loudness constant as you pan:

- **-3 dB (constant power)**: gains follow a sine/cosine quarter-cycle: `L = cos(θ)`, `R = sin(θ)` with `θ = pan·π/2`. Each channel is -3 dB at center; total *acoustic power* stays constant across the pan sweep. Best for independent stereo playback.
- **-6 dB (linear / constant voltage)**: `L = 1-pan`, `R = pan`. Each channel is -6 dB at center; keeps level constant when L+R are **summed to mono** (avoids a mono build-up bump).
- **-4.5 dB**: a compromise (geometric mean of the two) used by some DAWs.
  (https://noelborthwick.com/cakewalk/2014/11/19/panning-laws/ , https://www.fullcompass.com/gearcast/5-things-you-need-to-know-about-panning-laws , CMU reading: http://www.cs.cmu.edu/~music/icm-online/readings/panlaws/panlaws.pdf)

Implementation: compute per-channel gains from the pan value, then **smooth** them (see §3) so moving the pan control doesn't zipper.

### 2.4 Insert vs send/return effects

- **Insert**: effect sits **in series** in the channel's path — 100% of the signal passes through it (EQ, compressor, distortion, the app's Delay/Reverb/Distortion/Noise when used as inserts). "Every bit of audio on that track passes through the plugin." (https://www.izotope.com/en/learn/6-beginner-mistakes-with-sends-and-return-effects.html)
- **Send / return (aux)**: a **copy** of the signal is tapped off (pre- or post-fader) and routed to a shared **return bus** carrying one effect instance (classically reverb/delay). Multiple tracks share one reverb; the return's "wet" is mixed back into the master. **Pre-fader** send level is independent of the channel fader; **post-fader** follows it (usual for reverb). (https://www.izotope.com/en/learn/6-beginner-mistakes-with-sends-and-return-effects.html)

### 2.5 Buses / groups / master bus

- **Group / subgroup bus**: sum a set of tracks (e.g. all drums) to one bus for collective processing/level. (https://kansamples.com/blogs/learn/gain-staging-routing-mix)
- **Return bus**: destination for sends.
- **Master bus**: final sum before the output device; often carries a limiter/meter.
  This nested summing is exactly why a **graph** (not a flat list) is the right structure.

### 2.6 Metering

Meters read signal level for display: **peak** (max sample magnitude in the block), **RMS** (windowed average power), and often **true-peak/LUFS** for loudness. Meter values are computed on the audio thread and **pushed to the UI via the telemetry FIFO** (never read the audio buffer directly from the UI thread).

### 2.7 Plugin/effect delay compensation (PDC) & latency

Some effects (linear-phase EQ, look-ahead compressor/limiter, FFT-based reverb, oversamplers) output a block **delayed by K samples** and **report that latency** to the host. If uncompensated, processed tracks drift out of sync with dry tracks.

PDC algorithm:
1. Each node/effect reports its latency in samples (JUCE: `AudioProcessor::getLatencySamples()`; VST3 via `getLatencySamples`).
2. The host **sums latencies along each signal path**, finds the **maximum** path latency in the project, and **delays every other path by (max − its own latency)** so all paths line up at the master. "Even though only one channel has a plugin with huge delay, the DAW needs to add this delay to all your tracks."
3. Crucially, MIDI and **automation must be delayed by the same amounts** too.
   (https://www.macprovideo.com/article/recording-and-production/understanding-plug-in-delay-compensation , https://x.com/natemixing/status/1726345344487735808 , https://legacy.cakewalk.com/Documentation?product=CakewalkSonar&help=NewFeatures.38.html)

Implementation primitive: a per-path **delay line (compensation buffer)** sized to the needed sample offset, inserted where paths recombine.

---

## 3. Parameter automation (breakpoint envelopes)

Directly relevant to the app's automation-envelope UI.

### 3.1 Storage

An automation lane is a **breakpoint envelope**: an ordered list of points `(time, value)` plus a **curve/interpolation type per segment** (linear, hold/step, bezier/exponential). Time is stored in the **musical/timeline domain** (bars.beats or ppq), so it follows tempo changes; it's converted to samples at playback (see §4). Ableton exposes automation exactly this way — "automation can be viewed and edited as breakpoint envelopes." (https://www.ableton.com/en/manual/automation-and-editing-envelopes/)

### 3.2 Applying it: per-block vs per-sample

- **Per-block (control-rate)**: sample the envelope once per block, set the parameter, then **smooth**. Cheap; historically the norm — "for many years the standard was to update automation every once in a while, then smooth that value." Downside: coarse timing, can miss fast moves. (https://www.admiralbumblebee.com/music/2019/05/25/Daw-V-Daw-Automation-Part-2.html)
- **Per-sample (audio-rate) / sample-accurate**: evaluate the envelope every sample, or subdivide the block at each breakpoint and process sub-blocks (VST3's model: deliver multiple automation points at sample offsets within a block; "Ableton will deliver automation values at positions throughout the block"). Accurate but costlier. (https://www.kvraudio.com/forum/viewtopic.php?t=490162 , VST3 ProcessData/automation)

Practical hybrid (recommended): **break each processing block at breakpoint boundaries** into sub-blocks so automation is sample-accurate at the points that matter, and **linearly interpolate within a sub-block**, feeding the result through a smoother.

### 3.3 Interpolation & smoothing (zipper noise)

If a parameter jumps by a big step between blocks, you hear **zipper noise** (a stepping/buzzing artifact). Fix: **ramp** the parameter toward its target over some samples instead of snapping. Reference implementation: **JUCE `SmoothedValue`**:
- `setTargetValue(x)`, then `getNextValue()` returns the next per-sample interpolated value along a ramp; `reset(sampleRate, rampSeconds)` sets ramp length; `skip(n)` advances.
- **Linear** smoothing for gains in linear domain; **Multiplicative** smoothing for values best moved geometrically (frequency in Hz, gain in dB) — "a 12-step ramp from 440 Hz to 880 Hz increases frequency with equal-temperament tuning" (can't target 0).
  "All automatable parameters in the signal path must be smoothed" to avoid clicks/zipper noise. (https://docs.juce.com/master/classSmoothedValue.html , https://forum.juce.com/t/still-glitchy-zipping-noises-after-using-smoothedvalue/59589)

So there are **two layers**: (a) envelope interpolation between breakpoints (musical intent), and (b) a short parameter smoother (a few ms) on top to guarantee click-free changes even from UI knob twists.

---

## 4. Timeline / arrangement & transport

### 4.1 The transport clock

The transport is the master playback clock. Its canonical state (modeled well by **VST3 `ProcessContext`**) — what the engine passes to every node each block:
- `sampleRate` (double, always valid)
- `projectTimeSamples` — playhead position in samples (always valid)
- `continousTimeSamples` — position ignoring loop
- `projectTimeMusic` — musical position in **quarter notes** (1.0 = one quarter note; this is the ppq/beat clock)
- `tempo` — BPM
- `barPositionMusic` — last bar start, in quarter notes
- `timeSigNumerator` / `timeSigDenominator`
- `state` flags: `kPlaying`, `kRecording`, `kCycleActive` (loop) + per-field validity flags.
  (https://steinbergmedia.github.io/vst3_doc/vstinterfaces/structSteinberg_1_1Vst_1_1ProcessContext.html)

The engine advances `projectTimeSamples` by `numSamples` each block; the **tempo map** converts between samples/seconds and bars/beats.

### 4.2 Tempo map (bars/beats ⟷ samples/seconds)

A DAW keeps a **tempo map / TempoSequence**: an ordered list of tempo (and time-signature) changes. All musical positions (clip starts, automation breakpoints, MIDI notes, warp markers) are stored in the **musical domain (bars/beats or ppq)** and mapped to **samples** through this map at render time. Tracktion Engine models this exactly: a `TempoSequence` holds `TempoSetting` objects and you query positions through a `tempo::Sequence::Position` iterator. (https://tracktion.github.io/tracktion_engine/classtracktion_1_1engine_1_1TempoSequence.html)

Key conversions (constant-tempo segment):
```
samplesPerBeat = 60 / BPM * sampleRate
beatToSample(b) = segmentStartSample + (b - segmentStartBeat) * samplesPerBeat
```
With tempo ramps this becomes a piecewise integral, but the principle holds: **store in beats, convert to samples**.

### 4.3 Sample-accurate event scheduling

Events (note-on, clip launch, automation point, tempo change) carry a target sample position. When the block `[t, t+N)` contains an event at sample `e`, the engine **splits the block at `e`** (process `[t,e)`, apply the event, process `[e, t+N)`), so timing is exact rather than quantized to block boundaries. MIDI to instruments is delivered with per-event sample offsets within the block.

### 4.4 Warping loops / time-stretch to project tempo

Loops in the library have a native tempo. To play them at the project tempo without changing pitch, the engine **time-stretches** them (see §5). DAWs store **warp markers** that pin audio positions (usually transients) to musical positions; the stretch factor between markers is `targetBeats / sourceSeconds`. Ableton: warp markers "lock specific audio points to timeline locations," auto-placed at transients, producing an elastic stretch that "preserves pitch while synchronizing audio to project tempo." (https://www.ableton.com/en/manual/audio-clips-tempo-and-warping/)

For a v1 mobile app, a simpler model works: store each loop's **original BPM + beat length**; compute `stretchRatio = projectBPM / loopBPM`; feed the loop through a time-stretch node (§5). Warp markers can come later.

### 4.5 Clip launching

Two paradigms:
- **Arrangement / timeline** (Pro Tools, Logic, our main timeline): clips are fixed at absolute positions; the playhead sweeps them.
- **Session / clip launch** (Ableton, also present in BandLab): clips are triggered live and **quantized to the next bar/beat** boundary. The engine schedules the launch to the next quantization point via the tempo map, then starts streaming the clip sample-accurately.

Both reduce to the same primitive: *a clip source node that, given transport position, outputs the correct samples (possibly time-stretched) for this block, and starts/stops at scheduled sample offsets.*

---

## 5. Playback speed (1x / 1.5x / 2x): resampling vs time-stretch

Two fundamentally different operations:

### 5.1 Resampling (a.k.a. re-pitch / varispeed)
Change the sample playback rate. Playing faster makes it **shorter and higher-pitched** (like a turntable / tape speed-up). Cheap, artifact-free, and pitch-coupled. Ableton's **Re-Pitch** warp mode is exactly this: "adjust the sample's playback rate… change the pitch of the sample while modifying its tempo, similar to how DJs change playback speed on turntables." (https://www.ableton.com/en/manual/audio-clips-tempo-and-warping/)
- Implementation: read the source with a fractional index and **interpolate** (linear → cheap; windowed-sinc/polyphase → high quality, low aliasing).

### 5.2 Time-stretch (tempo change, pitch preserved)
Change duration **without** changing pitch. Needed if 1.5×/2× playback must keep pitch natural (e.g. speeding up a vocal/podcast without chipmunking). Approaches and tradeoffs:
- **Time-domain OLA — SOLA / WSOLA**: overlap-add of similar waveform segments. Cheap, good on **monophonic** material and transients, but **poor on polyphonic** music (artifacts) and can produce pre/post-echo on transients. (https://hpac.cs.umu.se/teaching/sem-mus-16/presentations/Schmakeit.pdf)
- **Frequency-domain — phase vocoder**: STFT, scale time, reconstruct. Works on **polyphonic** material but introduces **"phasiness" (loss of presence) and transient smearing**, worse at large stretch factors. (https://en.wikipedia.org/wiki/Phase_vocoder)
- **Hybrid (production quality)**: e.g. zplane **élastique** (used by Ableton's Complex modes) — separate transients/sinusoids/noise, preserve/reposition transients, phase-lock the tonal part. Higher CPU. (https://www.ableton.com/en/manual/audio-clips-tempo-and-warping/ , https://www.researchgate.net/publication/242019210_Improved_PVSOLA_Time-Stretching_and_Pitch-Shifting_for_Polyphonic_Audio)

### 5.3 Recommendation for the app's 1x/1.5x/2x control
- Offer **two modes**, matching how real DAWs expose it:
  - **Re-pitch (resample)** — default for a "tape-style" speed control: trivial, real-time-safe, no artifacts. Best for musical loops where pitch shift is acceptable/desired.
  - **Time-stretch (keep pitch)** — WSOLA for v1 (simple, real-time, good enough for the 1.5×–2× range, decent on drums/mono); optionally upgrade to a phase-vocoder/hybrid later for polyphonic material.
- Time-stretch has inherent **algorithmic latency** (windowing/lookahead) → report it to PDC (§2.7).

---

## 6. How real DAWs & frameworks structure this

- **JUCE `AudioProcessorGraph`**: the reference node-graph. Nodes = `AudioProcessor`s, edges = channel connections, special IO nodes, MIDI on channel `0x1000`, topological processing per block, per-node `getLatencySamples()` feeding PDC, async graph rebuilds so the audio thread never allocates. Great template for our engine. (https://docs.juce.com/master/classAudioProcessorGraph.html , https://juce.com/tutorials/tutorial_audio_processor_graph/)
- **Tracktion Engine**: a full open-source DAW engine on JUCE. High-level **document model** (`Edit` → tracks, clips, `TempoSequence`, racks, master plugins) compiled down to a low-level **audio node graph**; `EditPlaybackContext` drives playback, latency compensation, MIDI dispatch, and a thread pool / audio workgroup that processes the node graph. The modern `tracktion_graph` builds an immutable node graph and **swaps it atomically** for the audio thread. Excellent architectural model for us. (https://github.com/Tracktion/tracktion_engine , https://tracktion.github.io/tracktion_engine/classtracktion_1_1engine_1_1Edit.html , https://github.com/Tracktion/tracktion_engine/blob/master/tutorials/01%20-%20PlaybackDemo.md)
- **Ableton Live**: clip/session + arrangement, warp engine (Beats/Tones/Texture/Re-Pitch/Complex/Complex Pro via élastique), breakpoint automation, sample-accurate automation delivery. (https://www.ableton.com/en/manual/audio-clips-tempo-and-warping/)
- **Logic / GarageBand (Apple)**: built on Core Audio / Audio Units; GarageBand is the closest UX cousin to our app (loop browser, track stacks, smart tempo). AVAudioEngine is the modern node-graph API on Apple platforms.
- **FL Studio, Pro Tools**: pattern/step vs linear timeline; Pro Tools historically fixed buffer/HDX DSP; both do PDC and (in modern versions) sample-accurate automation.
- **BandLab**: cloud-collaborative cross-platform mobile DAW — the closest product analog for our **collaboration** feature (project stored server-side, mixdowns/stems synced).
- **Ardour**: open-source, C++; its routing/bus/PDC model and its lock-free session→RT command design are good reading for an open-source engine.

---

## 7. Design principles we should adopt

1. **Two planes, one queue between them.** Control plane (KMP/Kotlin + Compose UI + project model) never touches the audio buffer; audio plane never allocates/locks/IOs. All crossing is via SPSC lock-free FIFOs + atomics. (§1.6)
2. **Immutable graph, atomic swap.** Build/modify the graph off-thread; hand the audio thread a new graph via a single atomic pointer swap; retire the old one on a GC thread. (Tracktion model.)
3. **Everything musical is stored in beats;** convert to samples via the tempo map at render time. (§4.2)
4. **Two-layer parameter delivery:** breakpoint-interpolated automation + a short per-parameter smoother to kill zipper noise. (§3.3)
5. **Report latency, compensate globally.** Every node reports `latencySamples`; the engine aligns paths with compensation delay lines. (§2.7)
6. **Native, allocation-free render loop.** Shared C++ DSP core, platform callback (Oboe / AVAudioEngine). (§1.7)

---

## 8. Recommended internal architecture for the mobile DAW engine

### 8.1 Layered structure

```
┌─────────────────────────────────────────────────────────────────┐
│  UI (Compose Multiplatform)   timeline · loop browser · mixer ·   │
│                               automation editor · transport bar   │
├─────────────────────────────────────────────────────────────────┤
│  CONTROL PLANE  (shared Kotlin / KMP, non-realtime)               │
│   • Project model: Tracks, Clips, TempoMap, AutomationLanes,      │
│     Buses/Sends, EffectChains, LoopLibrary metadata               │
│   • Command builder → serializes edits into RT commands           │
│   • Persistence (project file), collaboration sync, undo/redo     │
│   • Sample/loop loader & decoder (disk/network → PCM, off-thread) │
├───────────────── lock-free SPSC FIFOs (commands ↓ / telemetry ↑) ─┤
│  AUDIO ENGINE CORE  (shared C++/Rust, realtime-safe)              │
│   • Transport clock + tempo map evaluator                         │
│   • Immutable audio node GRAPH (atomically swapped)               │
│   • Pre-allocated buffer pool, no alloc/lock/IO on this thread    │
├─────────────────────────────────────────────────────────────────┤
│  PLATFORM AUDIO OUTPUT                                             │
│   Android: Oboe/AAudio onAudioReady()  │  iOS: AVAudioEngine /     │
│                                        │  RemoteIO render callback │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 Threads & data flow

- **Audio thread (RT)**: driver → engine `render(outBuffer, N)` → walk graph in topo order → fill hardware buffer. Reads commands from the command FIFO at the top of each block; writes meters/playhead to the telemetry FIFO. Never allocates.
- **UI thread**: Compose; reads telemetry FIFO on a frame timer to draw playhead/meters; turns user edits into commands.
- **Loader thread(s)**: decode loops/samples to PCM, build clip buffers and new graph nodes, then hand pointers to the audio thread via commands. Also streams long audio from disk into ring buffers (Bencina's "interfacing real-time audio and file IO" pattern: http://www.rossbencina.com/code/interfacing-real-time-audio-and-file-io).
- **Command semantics**: `AddNode`, `RemoveNode` (returns node on GC queue), `Connect/Disconnect`, `SetParam(nodeId, paramId, value, atSample)`, `LoadClip(trackId, bufferPtr, startSample, stretchRatio)`, `SetTempoMap`, `Transport(play/stop/loop, positionSamples)`, `SwapGraph(newGraphPtr)`.

### 8.3 The audio node graph (nodes & edges)

**Node interface** (every node): `prepare(Fs, maxBlock)`, `process(ctx)`, `reset()`, `latencySamples()`. `ctx` carries the output buffer slice, `numSamples`, transport `ProcessContext`, and the list of automation/MIDI events for this block.

**Node types**:

1. **ClipSource / SamplePlayer** (one per active clip) — reads from a pre-loaded PCM buffer (or disk-stream ring buffer) at the transport position; handles clip start/stop at scheduled sample offsets; loops.
2. **TimeStretch / Resample node** (optional, per warped clip) — WSOLA/phase-vocoder (keep pitch) or fractional-read resampler (re-pitch); implements the 1×/1.5×/2× speed and tempo-warp; reports latency.
3. **InsertEffect nodes** — chainable per track: **Delay, Reverb, Distortion, Noise** (from the mockups) + Gain/EQ. Each exposes automatable params fed by the automation system + smoother.
4. **TrackStrip node** (or subgraph) — sums its clip sources, applies **insert chain**, then **gain (fader) + pan-law**; taps **pre/post-fader sends**; outputs meters.
5. **Summing / Bus nodes** — **Group buses** (sum sets of tracks), **Return/FX buses** (host one shared effect for sends), **Master bus**.
6. **Send taps** — copy track signal (pre/post fader) into a return bus input.
7. **DelayCompensation nodes** — per-path delay lines inserted where paths recombine, sized `maxLatency − pathLatency`.
8. **MetronomeSource** — click generator gated by transport + tempo map, summed into master (respects the METRONOME toggle).
9. **MasterMeter / Limiter** — final metering + optional brickwall limiter before output.
10. **GraphInput / GraphOutput (IO) nodes** — the graph's boundary to the platform callback buffers (mirrors JUCE `AudioGraphIOProcessor`); GraphInput also feeds live mic/record.

**Automation subsystem** (cross-cutting, not a node in the signal path): per (nodeId, paramId) an **AutomationLane** (breakpoints in beats) → evaluated per block via tempo map → produces sample-accurate target values → each target passes through a per-param **SmoothedValue** ramp inside the node. Automation is delayed by the same PDC amount as its audio path.

**Transport & clock** (engine-global): advances `projectTimeSamples`; tempo-map evaluator provides beats↔samples; schedules clip launches/loop wrap and event splits.

### 8.4 KMP specifics

- **Shared Kotlin (`commonMain`)**: project model, tempo/automation math, command encoding, persistence, collaboration, loop-library metadata. Pure, testable, no audio-thread code.
- **Native engine core**: one **C++ (or Rust) library** compiled for both ABIs, containing the graph + DSP + RT command consumer. Exposed to Kotlin via **cinterop (iOS/Kotlin-Native)** and **JNI (Android)** behind a KMP `expect/actual` `AudioEngine` interface. The FIFOs live in the native layer; Kotlin calls thin `pushCommand(...)` / `pollTelemetry(...)` bridges from non-RT threads only.
- **Output**: `actual` Android = Oboe stream whose callback calls `engine.render()`; `actual` iOS = AVAudioEngine `AVAudioSourceNode` / RemoteIO callback calling the same `engine.render()`.
- **Rule**: nothing Kotlin/JVM/Kotlin-Native ever runs on the audio callback (GC). Kotlin only produces commands; C++ consumes them.

---

## 9. Diagram spec (for the engine architecture diagram)

Use this to draw the architecture. Two coordinated diagrams: **(A) threading/data-flow** and **(B) audio signal graph**.

### Diagram A — Threads & data flow (swimlanes)

**Lanes (top→bottom):** UI Thread · Control/Loader Threads (non-RT) · [FIFO boundary] · Audio Thread (RT) · Platform Driver.

**Nodes:**
- A1 `Compose UI` (UI lane)
- A2 `Project Model (KMP)` (control lane)
- A3 `Command Builder` (control lane)
- A4 `Sample/Loop Loader + Decoder` (control lane)
- A5 `Command FIFO (SPSC, lock-free)` (boundary)
- A6 `Telemetry FIFO (SPSC, lock-free)` (boundary)
- A7 `GC / Retire Queue` (boundary)
- A8 `Engine.render()` (RT lane)
- A9 `Audio Node Graph (immutable, atomically swapped)` (RT lane)
- A10 `Buffer Pool (pre-allocated)` (RT lane)
- A11 `Platform Callback: Oboe onAudioReady / iOS render callback` (driver lane)
- A12 `Audio Hardware Out` (driver lane)

**Edges (directed):**
- A1 → A2 (user edits) ; A2 → A3 (build commands) ; A3 → A5 (push)
- A4 → A5 (push LoadClip / SwapGraph with pre-built native objects)
- A5 → A8 (audio thread pops commands at block start)
- A8 → A9 (drives graph) ; A9 ↔ A10 (borrow scratch buffers)
- A9 → A6 (push meters + playhead) ; A6 → A1 (UI drains on frame timer)
- A9 → A7 (retire old nodes/graph) ; A7 → A4 (non-RT frees)
- A11 → A8 (callback calls render) ; A8 → A11 (filled buffer) ; A11 → A12
- Label the A5/A6/A7 boundary: "no alloc / no lock / no IO crosses below this line."

### Diagram B — Audio signal graph (left→right dataflow)

**Source layer (per track, repeatable):**
- B1 `ClipSource[n]` → B2 `TimeStretch/Resample[n]` (optional)

**Track strip (per track):**
- B3 `Insert Chain`: `Distortion → Delay → Reverb → Noise → EQ/Gain` (ordered, each with automatable params)
- B4 `Fader (Gain) + Pan Law (-3dB/-6dB)`
- B5 `Meter tap`
- B6 `Send tap (pre/post-fader)`

**Bus layer:**
- B7 `Group Bus` (sums a set of TrackStrips) — optional, may have its own insert chain + fader
- B8 `Return/FX Bus` (hosts one shared effect for sends, e.g. Reverb) with wet gain
- B9 `Master Bus`: `Sum → Master Inserts → Master Fader → Limiter → MasterMeter`
- B10 `MetronomeSource` (gated by transport + tempo map, METRONOME toggle)
- B11 `GraphOutput (IO node)` → to platform buffer

**Edges:**
- For each track: B1 → B2 → B3 → B4 → (B5 meter tap) → into B7 (or directly B9 if ungrouped)
- B4/B6 → B8 (send copy) ; B8 → B9 (return wet summed into master)
- B7 → B9 (group sum into master)
- B10 → B9 (click summed into master)
- B9 → B11 → platform out
- Insert `DelayCompensation` delay-line boxes on any path that recombines with a lower-latency path (annotate: `delay = maxLatency − pathLatency`).

**Cross-cutting overlays (draw as a side rail feeding B-nodes):**
- `Transport Clock` + `Tempo Map` → feeds ClipSources (positions), TimeStretch (ratios), Metronome, and all Automation evaluation.
- `Automation System`: `AutomationLane (breakpoints in beats)` → `Tempo-map eval → sample-accurate target` → `SmoothedValue ramp` → dashed arrows into the automatable params of B2/B3/B4/B8/B9. Annotate: "automation delayed by same PDC as its audio path."

---

## 10. Sources

Real-time audio programming / threading
- Ross Bencina, *Real-time audio programming 101: time waits for nothing* — http://www.rossbencina.com/code/real-time-audio-programming-101-time-waits-for-nothing (mirror/discussion: https://lwn.net/Articles/452630/ )
- Ross Bencina, *Interfacing Real-Time Audio and File I/O* — http://www.rossbencina.com/code/interfacing-real-time-audio-and-file-io
- Timur Doumler, *Using locks in real-time audio processing, safely* — https://timur.audio/using-locks-in-real-time-audio-processing-safely
- Timur Doumler, ADC — *Thread Synchronisation in RT Audio with RCU* — https://www.youtube.com/watch?v=7fKxIZOyBCE ; *Wait-Free Synchronisation with the SeqLock* — https://conference.audio.dev/session/2024/wait-free-thread-synchronisation-with-the-seqlock/
- Jatin Chowdhury, *Wait-free programming from scratch* — https://jatinchowdhury18.medium.com/wait-free-programming-from-scratch-5ac6a65c23c4

Frameworks / engines
- JUCE `AudioProcessorGraph` — https://docs.juce.com/master/classAudioProcessorGraph.html ; tutorial — https://juce.com/tutorials/tutorial_audio_processor_graph/
- JUCE `SmoothedValue` — https://docs.juce.com/master/classSmoothedValue.html
- Tracktion Engine — https://github.com/Tracktion/tracktion_engine ; `Edit` — https://tracktion.github.io/tracktion_engine/classtracktion_1_1engine_1_1Edit.html ; `TempoSequence` — https://tracktion.github.io/tracktion_engine/classtracktion_1_1engine_1_1TempoSequence.html ; playback tutorial — https://github.com/Tracktion/tracktion_engine/blob/master/tutorials/01%20-%20PlaybackDemo.md
- VST3 `ProcessContext` — https://steinbergmedia.github.io/vst3_doc/vstinterfaces/structSteinberg_1_1Vst_1_1ProcessContext.html

Mixer / pan / PDC
- Pan laws — https://noelborthwick.com/cakewalk/2014/11/19/panning-laws/ ; https://www.fullcompass.com/gearcast/5-things-you-need-to-know-about-panning-laws ; CMU reading (PDF) — http://www.cs.cmu.edu/~music/icm-online/readings/panlaws/panlaws.pdf
- Sends/returns/buses/gain staging — https://www.izotope.com/en/learn/6-beginner-mistakes-with-sends-and-return-effects.html ; https://kansamples.com/blogs/learn/gain-staging-routing-mix ; https://vector-dsp.com/blog/audio-signal-flow-explained-step-by-step/ ; https://www.lmpro.ca/post/how-signal-flows-in-a-mixing-console-a-complete-signal-path
- PDC — https://www.macprovideo.com/article/recording-and-production/understanding-plug-in-delay-compensation ; https://legacy.cakewalk.com/Documentation?product=CakewalkSonar&help=NewFeatures.38.html

Automation
- Ableton automation & breakpoint envelopes — https://www.ableton.com/en/manual/automation-and-editing-envelopes/
- Sample-accurate automation analysis — https://www.admiralbumblebee.com/music/2019/05/25/Daw-V-Daw-Automation-Part-2.html ; https://www.kvraudio.com/forum/viewtopic.php?t=490162

Timeline / warp / time-stretch / resampling
- Ableton Audio Clips, Tempo & Warping (warp modes incl. Re-Pitch) — https://www.ableton.com/en/manual/audio-clips-tempo-and-warping/
- Phase vocoder — https://en.wikipedia.org/wiki/Phase_vocoder
- WSOLA — https://hpac.cs.umu.se/teaching/sem-mus-16/presentations/Schmakeit.pdf
- PVSOLA / hybrid stretching — https://www.researchgate.net/publication/242019210_Improved_PVSOLA_Time-Stretching_and_Pitch-Shifting_for_Polyphonic_Audio

Mobile audio platforms
- Android Oboe Full Guide — https://github.com/google/oboe/blob/main/docs/FullGuide.md ; low-latency audio — https://developer.android.com/games/sdk/oboe/low-latency-audio ; app latency — https://source.android.com/docs/core/audio/latency/app
- iOS Core Audio render callback / low latency — https://developer.apple.com/library/ios/qa/qa1715/_index.html ; https://developer.apple.com/forums/thread/708168
