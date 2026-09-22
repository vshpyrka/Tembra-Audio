# Building a Free, Open-Source Cross-Platform Mobile DAW — Research Report

*Kotlin Multiplatform (Android + iOS) · recording · timeline mixing · tuner · metronome*
*Compiled 2026-08-23. Combines (a) independent web research and (b) a NotebookLM deep-research pass over the app's design mockups and the reverse-engineering analysis of BandLab / n-Track / Remixlive.*

> Status placeholders below are filled in during the merge step once all research agents return. Diagrams live in `diagrams/` (Excalidraw + rendered PNG).

---

## 1. Product vision (from the UI mockups)

The four design screenshots describe a **collaborative, loop-based mobile DAW** — essentially a GarageBand/BandLab-class app that runs on phones and tablets. Concretely the UI shows:

- **Multitrack timeline** with horizontal waveform **clips** on 4–5+ tracks, a bars/beats ruler (1 · 4 · 8 · 12 · 16 …), a moving **playhead**, and tools for add / cut / snap.
- **Loop / sample library** panel with categories (**Piano, Bass, Drums, Rock, Pop, Soul, Vocals, Nature, Urban, Synth, Techno, Jazz**) and a grid of numbered loop pads (033, 034, …) — drag-in content authoring like Remixlive/GarageBand.
- **Per-track insert effects with automation**: one mockup shows an effect stack (**Delay, Reverb, Bass, Distortion, Noise**) and a **breakpoint automation envelope** editor with draggable points and a parameter tooltip (Speed 257 Hz, Time, Ease 0.01/1.1, Blend 0.7).
- **Transport bar**: play / pause / rewind / fast-forward / loop, a **time display** (00:42.20), a **volume** control, a **metronome** toggle (shown "Off"), and **playback-speed** selector (**1x / 1.5x / 2x**).
- **Per-track controls**: mute/solo (speaker), lock, and a mixer/EQ icon; a "…" menu per track.
- **Collaboration**: left/bottom nav for **Timeline, Users, Comments (badge count), Remote** — i.e. multi-user projects, commenting, and a remote-control surface.
- **Responsive**: distinct **phone** (single column, bottom nav) and **tablet** (timeline + side loop browser) layouts.

**Implication for the engine:** the app needs sample-accurate multitrack playback + recording, a real-time mixer with insert effects, parameter automation, tempo-synced loop playback (with time-stretch so loops match project tempo), a metronome locked to the transport, and a variable playback rate — all wrapped in a shared cross-platform codebase. Separately it needs a guitar/cello **tuner**.

---

## 2. How professional DAWs work with audio (mixing & timeline)

**A DAW is two loosely-coupled worlds.** (1) A **control/model plane** — the arrangement (tracks, clips, tempo map, automation), project file, and UI — running on ordinary threads that may allocate, block, and do I/O. (2) A **real-time audio plane** — a single high-priority callback that must render one buffer every few milliseconds *forever* with **no allocation, no locks, no I/O, no unbounded work** (Ross Bencina's "time waits for nothing"; locks are banned because a preempted low-priority lock-holder causes priority inversion and a missed deadline → an audible glitch). The two planes communicate **only** through lock-free single-producer/single-consumer FIFOs and atomics: the UI pre-builds objects off-thread and hands the audio thread a pointer; the audio thread never `delete`s — it pushes retired objects to a queue a normal thread frees.

**The audio plane is a processing graph**, not one big function. Nodes (clip source → time-stretch → insert effects → fader+pan → group/return/master buses → output) are topologically sorted and processed per-block. JUCE's `AudioProcessorGraph` and the open-source **Tracktion Engine** (a full DAW engine that builds an immutable node graph and **swaps it atomically** for the audio thread) are the reference designs to copy.

Key mechanisms this app needs:

- **Mixer:** tracks are summed sample-by-sample into buses. **Pan law** = the center compensation that keeps loudness constant: **−3 dB constant-power** (`L=cos θ, R=sin θ`) for stereo, **−6 dB linear** for mono-compatibility. **Insert** effects sit in series (100 % of signal — the Delay/Reverb/Distortion the mockups show per track); **send/return** effects tap a copy to a shared bus (one reverb serving many tracks). Groups → returns → master is exactly why a *graph* is the right structure.
- **Automation (the breakpoint-envelope UI):** store points as `(time, value)` in the **musical domain (beats)** with a per-segment curve; convert to samples via the tempo map at render time; apply **sample-accurately** by splitting the processing block at breakpoint boundaries; then pass every parameter through a short **smoother** (JUCE `SmoothedValue`-style ramp) to prevent **zipper noise**. Two layers: envelope interpolation (musical intent) + a few-ms per-parameter smoother (click-free even from knob twists).
- **Timeline/transport:** the transport advances a sample counter; a **tempo map** converts beats↔samples (`samplesPerBeat = 60/BPM·Fs`). Everything musical (clip starts, notes, automation, warp markers) is stored in beats and mapped to samples. Loops carry a native BPM and are **time-stretched** to the project tempo (store `stretchRatio = projectBPM/loopBPM`; warp markers can come later).
- **Latency (PDC):** effects that add delay report `latencySamples`; the host delays every other path by `max − own` so tracks stay aligned — and **automation/MIDI are delayed by the same amount**.
- **Playback speed 1×/1.5×/2× — two distinct operations:** **re-pitch/resample** (fractional read + interpolation — cheap, real-time-safe, but pitch rises with speed, like a turntable) vs **time-stretch** (keep pitch: **WSOLA** for v1 — simple and good on mono/drums; phase-vocoder/hybrid like élastique later for polyphonic). Offer both modes, as real DAWs do.

**Consequence for KMP:** the render loop must be **native C++/Rust** (Kotlin/Native and the JVM are garbage-collected → unsafe on the audio thread). KMP owns the control plane, project model, and Compose UI only. This directly shapes §3.

*Full detail (per-node contracts, thread/data-flow spec, and two diagram specs) is in `research/daw-internals.md`; those specs drive the diagrams in §7.*

## 3. Cross-platform architecture & tooling for a KMP DAW

**The dividing line is the real-time audio thread.** Anything that runs on or feeds the audio callback must be native C/C++ (no GC/alloc/locks); everything else lives in shared Kotlin. That yields a clean split:

- **Shared Kotlin (`commonMain`) — control plane:** project/timeline model, transport & mixer *state*, automation *curves* (as data), persistence, undo/redo, note-name mapping, and the **Compose Multiplatform UI** (timeline, mixer, waveform canvas, tuner/metronome screens). Compose for iOS went **Stable in Compose Multiplatform 1.8.0 (May 2025)** — draw waveforms with `Canvas` over **precomputed min/max peak "mip levels"** and move the playhead with a `graphicsLayer` translation rather than redrawing.
- **Native C++ — real-time plane:** the mixer graph, DSP inserts, sample-accurate clip scheduler, time-stretch, recording/monitoring, automation *application*, metronome click, and (optionally) the tuner's pitch DSP.

**Architecture decision: one shared C++ engine (not per-platform DSP).** Write the engine once in portable C++ behind an `expect class AudioEngine`; implement the `actual` via **JNI on Android** and **Kotlin/Native cinterop on iOS**, crossing the boundary only through a **lock-free SPSC ring buffer + atomics**. The rejected alternative (implement audio natively on each platform behind a Kotlin interface) means writing — and QA-ing — every effect twice, with two reverbs that sound different. Notably, **this shared-engine + thin-native-I/O design is exactly what BandLab, n-Track, and Remixlive all do** (see `REPORT.md`). I/O layer: **Oboe (Apache-2.0)** on Android; **AVAudioEngine/AURemoteIO + AVAudioSession(.playAndRecord)** on iOS — both request 48 kHz and a small buffer, and must accept a *variable* frame count per callback. Study **MWEngine (MIT)** — an Android C++ engine with exactly this JNI facade — as the reference.

**Licensing is the pivotal constraint for a free OSS app.** GPL/AGPL code (JUCE, Rubber Band, KFR, Tracktion) makes the *whole app* copyleft and clashes with App Store terms; commercial SDKs (Superpowered, zplane élastique) cost money; LGPL (SoundTouch, libsndfile, FFmpeg) carries a dynamic-relink burden that's a grey area on iOS. The winning move is to **mirror the shipping apps' architecture but swap their GPL/commercial DSP for permissive equivalents:**

| Library | License | Ship it? | Role |
|---|---|---|---|
| **Oboe** | Apache-2.0 | ✅ | Android low-latency I/O |
| **miniaudio**, **dr_libs** | public-domain / MIT-0 | ✅ | file decode + resampling + I/O fallback |
| **Signalsmith Stretch** | MIT | ✅ | time-stretch/pitch — *the key win* over Rubber Band/élastique |
| **STK**, **Maximilian** | MIT | ✅ | effect/synth algorithm references |
| **KissFFT / pffft** | BSD | ✅ | FFT for tuner + spectral |
| **MWEngine** | MIT | ✅ | reference Android engine + JNI pattern |
| SoundTouch, libsndfile, FFmpeg | LGPL | ⚠️ | only if unavoidable; dynamic-link; awkward on iOS |
| Rubber Band, KFR | GPL / commercial | 🚫 | copyleft infects the app |
| JUCE, Tracktion Engine | AGPL·GPL / commercial | 🚫 | reference only, don't link |
| Superpowered, élastique | commercial | 🚫 | not OSS |

*Full architecture options, per-platform I/O detail, Compose maturity notes, the complete cheat-sheet, and a layered diagram spec are in `research/kmp-architecture-tools.md`.*

### Evaluated: six existing open-source DAWs & editors — can we build on one?

To pressure-test the "build, don't fork" decision, I evaluated six real open-source projects on the two axes that decide it: **license** (can it ship free/OSS on the App Store?) and **architecture** (is there an embeddable, mobile-capable audio engine?). Five are DAWs; Audacity is a destructive waveform editor/recorder rather than a routed multitrack DAW, included because it's the best-known open-source audio app. Per-project briefs are in `research/*-evaluation.md`.

| DAW | License | Language / UI | Audio stack | Mobile |
|---|---|---|---|---|
| **Ardour** | GPLv2 | C++ / GTK · desktop | own route/Graph engine; JACK·ALSA·CoreAudio-desktop; libsndfile, libsamplerate, rubberband | 🚫 |
| **Audacity** | GPLv3 | C++ / wxWidgets + new Qt/QML · desktop | destructive editor, not a route graph; PortAudio·PortMixer (desktop-only); own DownmixStage; libsndfile, SQLite, SoundTouch, pffft | 🚫 |
| **LMMS** | GPLv2-or-later | C++ / Qt · desktop | own graph mixer + worker pool; JACK·ALSA·SDL·PortAudio; FFTW, libsamplerate, STK | 🚫 |
| **openDAW** (A. Michelle) | AGPLv3 (+ paid commercial) | TypeScript + Rust→WASM · browser | Web Audio + AudioWorklet; Rust-WASM DSP graph | 🚫 |
| **OpenDaw** (G. Rhodes) | GPLv3 | C++ / Qt · desktop | JUCE + Tracktion Engine; VST3; CoreAudio·ASIO·WASAPI | 🚫 |
| **maolan** | ✅ **BSD-2** | Rust / TUI + iced · desktop | headless engine; JACK·ALSA·cpal; symphonia, rubato, rtrb, basedrop | ⚠️ parts |

**Takeaway — don't fork a DAW; build the engine and harvest permissive parts.** Five of the six are **copyleft** (GPL/AGPL): linking any of them forces your *entire* app open and — decisively — **bars it from Apple's App Store** (the VLC problem). Audacity is the sharpest illustration: Muse Group deliberately moved it to **GPLv3** and can ship to the App Store *only* because a **contributor license agreement** hands them private dual-licensing rights — an escape hatch no third-party reuser has, and GPLv3's anti-Tivoization makes the raw license conflict worse than Ardour's GPLv2. All six are also **desktop- or browser-bound**, with **no Android/iOS audio backend** (no Oboe/AAudio, no AVAudioEngine). Only **maolan** clears the license bar (**BSD-2**) *and* has the right shape — **Rust with a headless engine split from its UI** — yet it still can't ship as-is (desktop-only I/O; first released 2026-04-25; v0.2.x). Its real value is as a **working template for the Rust engine option in §8**: it's built on exactly the permissive crates we'd pick — **CPAL** (Apache-2.0, the one backend crate that covers Android *and* iOS), **Symphonia** (MPL-2.0), **rtrb** + **basedrop** (lock-free, RT-safe reclamation), **rubato** (resampling). Reusable scraps from the copyleft five: **libsamplerate** (BSD-2), **STK** (MIT-ish), **lilv/suil** (ISC), **FLAC/ogg/vorbis** (BSD) from LMMS; **PortAudio/PortMixer/PortSMF** (MIT-style, but desktop-only backends), **pffft** (BSD) and **SQLite** (public domain) from Audacity — harvested individually from upstream, never as a GPL-linked whole — and **Signalsmith Stretch** (MIT), which openDAW independently confirms as the time-stretch pick (take it from Signalsmith Audio upstream, not openDAW's AGPL port). Per-project briefs: `research/ardour-evaluation.md`, `audacity-evaluation.md`, `lmms-evaluation.md`, `opendaw-michelle-evaluation.md`, `opendaw-rhodes-evaluation.md`, `maolan-evaluation.md`.

## 4. Guitar & cello tuner — algorithms

**Bottom line: use a time-domain periodicity detector, not FFT peak-picking.** The recommended primary estimator is the **McLeod Pitch Method (MPM)**, which works on the Normalized Square Difference Function (NSDF); **YIN / YINFFT** is an equally valid alternative or cross-check. Both lock onto the *true fundamental* even when overtones are stronger than it (the normal case for bowed cello and plucked guitar) and are explicitly designed to resist **octave errors** — YIN via its cumulative-mean-normalized difference and absolute-threshold "first dip", MPM via its "accept the first NSDF peak ≥ k·max" rule (k ≈ 0.9). Both also yield a **confidence measure** (YIN aperiodicity; MPM clarity in [−1, 1]) the tuner uses to reject bad frames. FFT/HPS and cepstrum are worse fits: their resolution is bin-limited, so telling E2 (82.4 Hz) from F2 (87.3 Hz) would need a ~16 k-point FFT (~340 ms of audio).

**The low-frequency constraint drives the window size.** A periodicity detector must see several full periods of the lowest note. Cello C2 ≈ 65.4 Hz has a 15.3 ms period, so designing for a ~60 Hz floor needs a window of ~5 periods ≈ **4096 samples**:

| Parameter | Recommended value | Why |
|---|---|---|
| Sample rate | **48 kHz** mono float | Native on most phones (no resampling); covers the ≤1.3 kHz band |
| Analysis window | **4096 samples (~85 ms)** | ~5 periods at 60 Hz → stable cello-C2 estimate (2048 is marginal that low) |
| Hop | **1024 (75 % overlap)** | New reading every ~21 ms → responsive meter |
| Pre-filter | band-pass **~60–1300 Hz** + DC removal | kills hum/rumble/hiss and octave-up errors |
| Sub-sample accuracy | **parabolic interpolation** of the NSDF/CMNDF peak | mandatory — 1 cent ≈ 0.05 Hz at low E; gets to ~1 cent |
| Confidence gate | NSDF clarity ≥ ~0.5, octave-jump sanity check | reject noise / octave slips |
| Reference & smoothing | A4 = 440 Hz (configurable); median-of-5 → EMA on cents | steady needle |

**Frequency → note + cents:** `n = round(69 + 12·log2(f/440))`, `cents = 100·(n_float − n)` (or `1200·log2(f/f_note)`); name = `[C,C#,…,B][n mod 12]`, octave = `n/12 − 1`. UI: note+octave, needle left=flat / right=sharp (±50 ¢), green within ±3–5 ¢.

**Reuse, don't reinvent.** TarsosDSP (`McLeodPitchMethod`, `Yin`, `FastYin`) on Android/JVM; AudioKit `PitchTap` / Beethoven on iOS; or — cleanest for KMP — port ~200 lines of MPM/YIN into `commonMain` (needs only a small Kotlin FFT) so **one** detector serves both platforms, keeping just mic capture platform-specific (Oboe/`AudioRecord` on Android with an UNPROCESSED source; `AVAudioEngine.installTap` on iOS). Note TarsosDSP is **GPL** — see §3's licensing discussion; the shared-Kotlin port or the MIT-licensed `sevagh/pitch-detection` C++ core avoids that.

*Full detail, algorithm comparison table, per-string sizing, and pipeline diagram spec: `research/tuner-algorithms.md`.*

## 5. A perfect, configurable metronome

**The one rule: never time clicks with a UI/OS timer.** `Handler.postDelayed`, `Thread.sleep`, `java.util.Timer`, `NSTimer`, `CADisplayLink` all run on preemptible threads against *wall-clock* time — they suffer **jitter** (random lateness) and, fatally, **drift** (the `play(); sleep(interval)` loop accumulates each iteration's error, so after a few hundred beats you're seconds off). They also measure the wrong clock: audio plays on the *audio device's* sample clock, which differs from the system clock by tens–hundreds of PPM. **Treat the audio sample clock as the single source of truth.**

**The correct model — a counter in the audio callback** (the native form of Chris Wilson's "A Tale of Two Clocks" look-ahead scheduler). Keep an absolute output-frame counter `framePos` and a `nextClickFrame` **`double` accumulator**. Each real-time callback emits any click whose position lands in the current block, then advances `nextClickFrame += framesPerBeat` in double precision — which bounds long-term error to **< 0.5 sample forever** (the trick is accumulating the fractional `framesPerBeat = 60/bpm·Fs` in double and rounding only at emit time). Android: Oboe/AAudio callback (`LowLatency` + `Exclusive`). iOS: an `AVAudioSourceNode` render block — or, if you already have an `AVAudioEngine` graph, Apple's *Hello Metronome* pattern (`AVAudioPlayerNode.scheduleBuffer(at:)` at a sample time). The callback-counter form is preferred for KMP because the same arithmetic is shared and tempo/transport response is a single buffer (~2–10 ms) vs a ~250 ms look-ahead window.

**Meeting each requirement:**
- **Swap the beat WAV at runtime** → decode to float PCM + resample to engine rate **off** the audio thread, then publish via an **atomic pointer swap** (`slot[ACCENT|NORMAL|WEAK]`); the callback loads the pointer once per block and never locks; the retired buffer goes to a deferred-free queue.
- **Per-beat accents** → an immutable `accentPattern` array maps each tick → a sample slot + gain (downbeat > beat > subdivision). Cheap fallback if you don't want separate WAVs: one click at higher gain/pitch for the accent.
- **Tempo/BPM control** → BPM is an atomic the callback reads once per block; recompute `framesPerBeat` and let it apply from the *next* beat boundary (never move an already-scheduled click) → smooth, glitch-free, drift-free. Changing tempo changes *spacing only* — never resample the click, so its pitch stays constant.
- **Default synth click (no WAV)** → pre-render an enveloped ~1000 Hz (normal) / ~1500 Hz (accent) sine/triangle, 30–50 ms, with a **2 ms cosine attack and an exponential decay ending exactly at zero** to avoid pops. Pre-render once; the RT thread only ever mixes finished PCM.
- **DAW/timeline sync** → anchor `nextClickFrame` to the transport position on the *same* sample clock; re-anchor on start/locate/loop; both stay drift-free thereafter. Compensate output latency (Oboe/`AAudioStream_getTimestamp`, iOS `presentationLatency`) for the UI beat-flash so it matches the audible click.

**Engine choice:** Oboe (Apache-2.0) over the commercial Superpowered SDK; study Oboe's *RhythmGame*/*DrumThumper* samples and Apple's *Hello Metronome*.

*Full detail — the callback pseudocode, RT-thread data structures, and a scheduling-model diagram spec — is in `research/metronome-design.md`.*

## 6. Recommended stack & build plan

**The stack in one picture:** Kotlin Multiplatform + Compose Multiplatform for the UI and all non-real-time logic → an `expect class AudioEngine` facade → **one portable C++ real-time engine** (mixer graph, DSP, scheduler, time-stretch, tuner/metronome DSP) → thin platform I/O (**Oboe** on Android, **AVAudioEngine/AURemoteIO** on iOS). All DSP is permissively licensed: **Signalsmith Stretch (MIT)** for time-stretch, **miniaudio/dr_libs (PD/MIT-0)** for files, **KissFFT/pffft (BSD)** for FFT, hand-written biquad/FDN/compressor effects with **STK/Maximilian (MIT)** as references. Nothing GPL/commercial ships in the binary.

**Why this is the right call:** it reproduces the proven "shared C++ engine + thin native I/O" design that BandLab, n-Track, and Remixlive all converged on (per `REPORT.md`), but replaces their **paid/copyleft** dependencies (Rubber Band, élastique, JUCE, Superpowered) with permissive equivalents — so the app stays fully free/OSS and ships cleanly on both stores.

**Suggested build order (de-risking the hard parts first):**

1. **Audio spine.** Stand up the C++ engine + `expect/actual` bridge (JNI + cinterop) playing one clip through Oboe and AVAudioEngine, at 48 kHz with a variable callback size. Prove the lock-free command/telemetry FIFOs. *This is the riskiest integration — do it first.*
2. **Transport + timeline.** Sample counter, tempo map (beats↔samples), multi-clip sample-accurate scheduling, loop playback, and the moving playhead in Compose (peak-mip waveforms).
3. **Mixer.** Per-track gain/pan (−3 dB law) + summing buses + master; metering back over the telemetry FIFO.
4. **Metronome.** The callback-counter scheduler (§5) — small, self-contained, and it validates sample-accurate scheduling + hot-swappable WAV assets before the effects work.
5. **Insert effects + automation.** Delay, reverb, distortion, EQ, compressor as graph nodes; breakpoint automation stored in beats, applied sample-accurately through `SmoothedValue` ramps; the automation-envelope UI.
6. **Recording + input monitoring** (full-duplex Oboe / `.playAndRecord`), then **loop library** with Signalsmith time-stretch to project tempo, then **1×/1.5×/2× speed** (re-pitch + keep-pitch modes).
7. **Tuner** (§4) — largely independent; a shared MPM/YIN core in `commonMain` + platform mic capture.
8. **Collaboration** (Users/Comments/Remote) last — it's a networking/sync layer on top of a working DAW, and the closest product model is BandLab's cloud projects.

**Two hard-won principles to hold throughout:** (a) everything musical is stored in **beats** and converted to samples at render time; (b) **nothing** Kotlin/JVM/Kotlin-Native ever runs on the audio callback — Kotlin only produces commands, C++ consumes them.

## 7. Diagrams

Four Excalidraw diagrams visualize the design (editable `.excalidraw` source + rendered `.png` in `diagrams/`):

1. **System architecture** — `diagrams/01-architecture.png` — the three-band KMP stack: shared Kotlin control plane → `expect class AudioEngine` facade (JNI/cinterop, lock-free boundary) → shared C++ real-time engine → per-platform Oboe / AVAudioEngine I/O, with the permissive DSP libraries called out.
2. **Signal graph + timeline** — `diagrams/02-signal-graph.png` — how tracks flow and mix: ClipSource → time-stretch → insert chain → fader/pan → buses → master → output, with the transport/tempo rail on top and the automation system feeding parameters from below.
3. **Tuner pitch-detection pipeline** — `diagrams/03-tuner-pipeline.png` — mic → ring buffer (4096/1024) → band-pass → MPM/YIN + parabolic interpolation → validation → smoothing → note+cents → UI, plus why time-domain beats FFT and why the window is large.
4. **Drift-free metronome scheduling** — `diagrams/04-metronome.png` — the control thread publishes immutable snapshots by atomic pointer swap; the real-time callback counts frames (double accumulator) to emit clicks with no drift; DAW sync anchors to the transport on the same sample clock.

---

## 8. NotebookLM deep-research cross-check & additions

Two NotebookLM passes were run. First a **chat synthesis** grounded in the six attached sources (mockups + reverse-engineering), whose conclusions **converge with the web research above**. Then a full **deep web-research** pass that gathered **94 web sources** (a 100-source notebook) and produced a report + mind map.

**Convergence (raises confidence):**
- **Architecture — same verdict:** a shared low-level engine (audio thread must not be GC-managed) behind a Kotlin bridge, Compose Multiplatform UI, Oboe on Android + Core Audio/AVAudioEngine on iOS. NotebookLM adds **UniFFI** as an alternative to Djinni for binding codegen, and endorses a Web-Audio-style node graph + an `AutomationTimeline` keyframe class.
- **Licensing — identical bottom line:** avoid élastique (commercial), JUCE & Rubber Band (GPL); SoundTouch/FFmpeg (LGPL) only dynamically linked; Freeverb (PD), pffft (BSD), Oboe (Apache-2.0) safe; **Signalsmith Stretch (MIT)** for time-stretch; **RBJ biquad cookbook** for filters — matching §3.
- **Collaboration:** the open-source **Ableton Link** SDK for cross-device BPM sync.

**New from the deep research (worth adopting):**
- **Rust is a viable engine language** alongside C++ — via FFI/JNI, with a fully permissive stack: **CPAL** (I/O, MIT), **Symphonia** (decode, MPL-2.0), **Crossbeam** (lock-free channels, MIT/Apache), **RustySynth** (SF2, MIT). Use **MPSC** channels (multi-producer) for UI→engine, not only SPSC.
- **iOS waveform rendering:** Compose-on-iOS (Skia→Metal) can suffer **shader-compilation stalls** on the first render of complex waveforms — drop to `UIKitView` / `AndroidView` for the low-level waveform renderer.
- **Zero-copy telemetry:** the JNI bridge bottlenecks at high visualization update rates — pass PCM peaks via **AHardwareBuffer** (Android shared memory) / `SharedMemory` to avoid serialization.
- **iOS scheduling pitfall:** using `AVAudioEngine`'s `completionHandler` to schedule the next buffer fires **too late** → glitches; keep at least **one buffer pre-scheduled in reserve**. Use **Unsafe Swift** (`UnsafeMutableAudioBufferListPointer`) in `AVAudioSourceNode` to avoid per-sample bounds checks.
- **Denormals:** enable **FTZ/DAZ** (flush-to-zero) — denormal floats can cost ~100× CPU; on by default on ARM, but x86_64 emulators need the MXCSR register set.
- **Crash isolation:** wrap the native audio callback in `catch_unwind` (a Rust panic in the callback → `SIGABRT`, instant crash); avoid CPAL device *enumeration* on some Android devices (it can panic — prefer `default_output_device()`).
- **App Store rule:** keep the mic **input stream closed until Record is pressed** — a permanently-open input risks App Store rejection.

Full outputs: `research/notebooklm-synthesis.md` (chat cross-check, EN), `research/notebooklm-deep-report.md` (deep-research report — generated in Ukrainian, your NotebookLM locale), `research/notebooklm-deep-report-en.md` (English version), `research/notebooklm-mindmap.json`, and the notebook *"DAW Deep Research … (v2)"* (100 sources).

---

## Sources
- Local: `REPORT.md` (BandLab/n-Track/Remixlive native-engine analysis), design mockups.
- Independent web research: `research/daw-internals.md`, `research/kmp-architecture-tools.md`, `research/tuner-algorithms.md`, `research/metronome-design.md`.
- NotebookLM synthesis: `research/notebooklm-synthesis.md`, notebook `DAW App Research — Audio, Mixing, Tuner, Metronome (KMP)`.
