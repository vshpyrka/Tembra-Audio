# KMP Cross-Platform Architecture & Audio-Engine Tool Stack
### Research brief for a FREE, OPEN-SOURCE cross-platform mobile DAW (Kotlin Multiplatform, Android + iOS)

**Date:** 2026-08-23
**Scope:** Architecture split (shared Kotlin vs. native), two engine-integration options, low-latency I/O per platform, OSS library survey **with licenses**, Compose Multiplatform maturity, OSS projects to study, and a recommended license-safe stack.

**Licensing lens (non-negotiable for this project):** the app ships free + open-source on the App Store and Play Store. That means we can *technically* use GPL/AGPL code (our app would then have to be GPL/AGPL too), but doing so creates **App Store distribution friction** and **forecloses any future dual-licensing / closed builds**. This document strongly prefers permissive licenses (MIT/BSD/Apache/ISC/Zlib/public-domain) and explicitly flags every GPL/AGPL/commercial dependency and its consequences.

---

## 0. TL;DR — Recommended stack

- **Shared Kotlin (KMP `commonMain`):** project/timeline data model, transport & mixer state, automation curves, undo/redo, persistence, and **Compose Multiplatform** UI (timeline, mixer, waveform canvas). Compose for iOS went **Stable** in Compose Multiplatform 1.8.0 (May 2025).
- **Audio engine:** a **single shared C++ real-time engine**, called from Android via **JNI** and from iOS/Kotlin-Native via **cinterop**, wrapped behind a Kotlin `expect`/`actual` facade. (Option A below — recommended.)
- **I/O layer:** **Oboe (Apache-2.0)** on Android (AAudio/OpenSL ES); **AVAudioEngine / AURemoteIO + AVAudioSession** on iOS.
- **DSP building blocks (all permissive):** hand-written biquads/one-poles for EQ/filters; **Signalsmith Stretch (MIT)** for time-stretch/pitch; **miniaudio (public-domain/MIT-0)** and/or **dr_libs (public-domain/MIT-0)** for file decode + resampling; **STK (MIT)** / **Maximilian (MIT)** as references for synth/effect algorithms; **KissFFT / pffft / PFFFT (BSD-ish)** for spectral work.
- **Avoid in shipped binary:** **Rubber Band (GPL/commercial)**, **JUCE/Tracktion Engine (AGPL-GPL/commercial)**, **KFR (GPL/commercial)**, **Superpowered (commercial)**, **SoundTouch / libsndfile / FFmpeg (LGPL — dynamic-link nuance, hard on iOS)**. Details in §5 and the cheat-sheet.

---

## 1. What belongs in shared Kotlin vs. the native audio layer

The reliable dividing line is the **real-time audio thread**. Anything that runs on (or feeds) the audio callback must be native C/C++ with no allocation, locking, or GC. Everything else can and should live in shared Kotlin.

**Shared Kotlin (`commonMain`) — the "control plane":**
- **Project / timeline data model:** tracks, clips, regions, tempo map, time signature, sample-rate metadata, marker list.
- **Transport & mixer *state*:** play/stop/record flags, playhead position (as a value the UI reads), per-track gain/pan/mute/solo, send levels, insert-chain ordering.
- **Parameter automation** *authoring*: automation curves/breakpoints as data (the *rendering* of automation to per-sample values happens native, but the curve model is shared).
- **Tuner & metronome logic** that is *not* sample-accurate: UI, tempo config, note-name mapping, pitch display smoothing. (The pitch *detector* and the click *scheduler* are native.)
- **Persistence / project file format**, undo-redo, and all **UI** via **Compose Multiplatform**.

**Native engine (C/C++) — the "real-time plane":**
- Audio device I/O callback (Oboe / AURemoteIO).
- The mixer graph: summing buses, gain/pan smoothing, metering (RMS/peak).
- DSP insert effects (reverb, delay, EQ, compressor, distortion).
- Sample-accurate playback scheduler, loop playback, time-stretch.
- Recording capture + input monitoring path.
- Automation *application* (interpolating breakpoints into per-block parameter ramps).
- Metronome click scheduling and (optionally) the tuner's pitch-detection DSP.

**Crossing the boundary safely:** the UI thread never talks to the audio thread with locks. Use **lock-free single-producer/single-consumer ring buffers** and **atomic parameter snapshots**: Kotlin writes desired parameter values into a lock-free structure; the audio callback reads the latest snapshot and ramps to it. Playhead/meter values flow back the same way (audio thread writes atomics, UI polls on a frame tick). This is the pattern every real DAW engine uses and it maps cleanly onto a KMP facade.

---

## 2. Two architecture options

### Option A — One shared C++ engine, JNI on Android + cinterop on iOS *(recommended)*

Write the engine once in portable C++ (I/O backends `#ifdef`-ed per platform). Expose a small C ABI. Wrap it behind a Kotlin `expect class AudioEngine` whose `actual`s bridge to the native library.

- **Android `actual`:** JNI. The C++ is built with the NDK/CMake; a thin JNI shim (often generated with SWIG, as MWEngine does, or hand-written) exposes methods to Kotlin/JVM. Kotlin calls `System.loadLibrary("engine")` and talks to `external fun` declarations. See Kotlin/Native C-interop background: <https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html>, and a practical KMP + C/C++ walkthrough: <https://medium.com/@adman.shadman/kmp-native-c-c-a7b62e4e9c1f>.
- **iOS `actual`:** **Kotlin/Native cinterop**. You write a `.def` file pointing at the engine's C header; cinterop generates Kotlin bindings so `iosMain` can call the C ABI directly (no Swift needed for the hot path). Background on cinterop mechanics: <https://staticvar.dev/post/kotlin-native-c-interop/> and <https://andreas-tennert.de/blog/a-comparison-of-jni-jna-and-cinterop-in-kotlin/>. On iOS the C++ engine's I/O backend uses Core Audio / AURemoteIO; you can implement that render callback in C/C++ directly or delegate to a tiny Objective-C++ file.

**How `expect`/`actual` wraps a C/C++ engine:** declare `expect class AudioEngine { fun start(); fun setTrackGain(id: Int, db: Float); fun playheadFrames(): Long }` in `commonMain`. `androidMain` implements it over JNI `external` functions; `iosMain` implements it over the cinterop-generated Kotlin package. Common code — and Compose UI — only ever sees the `expect` type.

**Pros**
- DSP written **once**; identical sound and behaviour on both platforms — critical for a DAW where users compare mixes.
- Full control of the real-time path (lock-free, no GC anywhere near the callback).
- Reuses the enormous body of portable C/C++ audio code (miniaudio, dr_libs, Signalsmith, STK…).
- This is exactly what the real apps we dissected do (BandLab's custom C++ engine; Remixlive via JUCE; n-Track's `libaudiomodule`) — see `../REPORT.md`.

**Cons**
- Two interop mechanisms to maintain (JNI + cinterop); JNI boilerplate; C++ build (CMake) in the KMP build.
- Debugging across the Kotlin↔C++ boundary is harder.
- Objective-C++/Core Audio glue still needed on iOS for device I/O.

### Option B — Platform-native audio behind a shared Kotlin interface

Define `interface AudioEngine` in `commonMain`; implement it **entirely per platform**: Android over **Oboe/AAudio** (Kotlin+JNI or C++), iOS over **AVAudioEngine / AudioUnits** in Swift/Obj-C. No shared DSP.

**Pros**
- Each side uses first-class platform APIs (AVAudioEngine gives you nodes, an effect graph, and format conversion "for free"); no C++ ABI to design.
- Simpler for *simple* apps (play a file, one effect).

**Cons**
- **You implement every DSP effect twice** (or accept two different reverbs/compressors that sound different) — a deal-breaker for a DAW's consistency and for QA cost.
- Platform effect units differ: iOS `AVAudioUnitEffect`/Apple AUs have no Android equivalent; parity work is endless.
- Sample-accurate scheduling and lock-free automation must be re-solved on each platform.

**Verdict:** Option A (shared C++ engine) for the DSP/mixer/transport; a **thin** platform I/O layer per target. This is the "shared engine, native I/O" hybrid that all three shipping apps we analyzed converge on.

---

## 3. Low-latency I/O specifics

### Android — Oboe / AAudio

Oboe (Apache-2.0, <https://github.com/google/oboe>) is Google's recommended C++ wrapper: **AAudio on API 27+, OpenSL ES below**. Low-latency guide: <https://developer.android.com/games/sdk/oboe/low-latency-audio>; full guide: <https://github.com/google/oboe/blob/main/docs/FullGuide.md>.

- **Callback model:** implement `AudioStreamDataCallback::onAudioReady(stream, audioData, numFrames)`; register via `setDataCallback()`. The stream calls it on a **high-priority audio thread** once per burst; you must fill/consume `numFrames * channels` samples and return quickly.
- **Lowest latency = two settings together:** `setPerformanceMode(PerformanceMode::LowLatency)` **and** `setSharingMode(SharingMode::Exclusive)` (API 26+, bypasses the mixer). LowLatency uses smaller buffers + an optimized path.
- **Buffer sizing in bursts:** query `getFramesPerBurst()`; set buffer size to a small multiple of the burst (Oboe defaults to **2 bursts** = double-buffering). Bigger buffers = more latency; you can auto-tune buffer size at runtime against underrun counts.
- **Sample rate:** don't assume — call `getSampleRate()` after open; request 48 kHz (the native rate on most modern devices) to avoid a resampler in the fast path.
- **Callback must NOT:** allocate (`new`/`malloc`), do file/network I/O, take mutexes, `sleep`, or call `stop/close/read/write` on the calling stream. Only lock-free reads and `get*()` are safe.
- **Recording + monitoring / full-duplex:** open an input stream and an output stream; make **one** stream the master with the callback and do **non-blocking** (`timeoutNanos = 0`) I/O on the other inside the callback. Match sample rate/format/channel counts. This is how you get low-latency **input monitoring** (mic → effects → output) on Android. Note: MMAP/EXCLUSIVE input isn't available on every device, so degrade gracefully.

### iOS — AVAudioEngine / AURemoteIO / AVAudioSession

Two layers exist: high-level **AVAudioEngine** (node graph) and low-level **AURemoteIO / AudioUnit render callbacks**. AVAudioEngine is itself built for low latency and can hit ~1.5 ms buffers in `PlayAndRecord`.

- **AVAudioSession** is the gatekeeper. Set category **`.playAndRecord`** for a DAW (record + monitor), choose options (`.mixWithOthers`, `.defaultToSpeaker`, `.allowBluetoothA2DP`), then request the fast path:
  - `setPreferredIOBufferDuration(...)` — e.g. ~0.003 s (≈128–256 frames @ 48 kHz). Apple honours it approximately.
  - `setPreferredSampleRate(48000)`.
  - For lowest latency your app should **not** be mixable (avoid `.mixWithOthers` when you need the tightest path). Refs: <https://developer.apple.com/forums/thread/25197>, <https://developer.apple.com/forums/thread/65675>.
- **Render callback:** for sample-accurate work drop to the **AURemoteIO** unit's render callback (a C function) where you get exact frame counts (typically matching `preferredIOBufferDuration`, e.g. 256 frames). AVAudioEngine also exposes `manualRenderingBlock` for offline/bounce.
- **Sample-accurate scheduling:** `AVAudioPlayerNode.scheduleBuffer/scheduleSegment(at: AVAudioTime)` lets you place audio at exact sample frames on the engine's timeline — the basis for a clip scheduler and metronome. Watch out for the documented player-node latency quirks (<https://developer.apple.com/forums/thread/705620>) if you need the tightest sync; the AURemoteIO route avoids them.
- **Recording + monitoring:** tap the input node or route mic → effect nodes → mainMixer → output. Manage session interruptions (calls, route changes) and re-activate the session.
- **Background audio** needs the `audio` UIBackgroundMode; microphone use needs `NSMicrophoneUsageDescription`.

**Cross-platform reality:** on both OSes you cannot guarantee a fixed buffer size — you *request* a preference and adapt. Design the engine to accept a variable `numFrames` per callback.

---

## 4. Compose Multiplatform maturity & drawing waveforms/timelines

- **Status:** Compose for iOS went **Stable / production-ready in Compose Multiplatform 1.8.0 (May 2025)** — JetBrains: <https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/>. iOS rendering is **Skia mapped to a Metal-backed canvas**, enabling 120 Hz on ProMotion. KMP itself is used in production by Netflix, McDonald's, Cash App. 2026 field guides: <https://medium.com/@thanhnh98/is-compose-multiplatform-production-ready-in-2026-a-practical-field-guide-b4863748dbe7>, <https://www.kmpship.app/blog/compose-multiplatform-ios-stable-2025>.
- **Waveforms / timeline:** Compose `Canvas` (`drawLine`, `drawPath`, `drawRect`, `Path`) is the right tool for peak/RMS waveforms, timeline grid, mixer meters. **Caveat flagged in the field guides:** heavy custom `Canvas` drawing and deeply nested composables can bottleneck on low-end devices. Mitigations for a DAW: pre-compute **min/max peak "waveform pyramids"** (mip levels) off the audio thread and draw down-sampled peaks per pixel column; cache paths; clip to viewport; use `drawIntoCanvas`/`ImageBitmap` caching for static layers; drive the playhead with a lightweight `graphicsLayer` translation rather than redrawing the whole waveform. Metering/waveform values arrive from the native engine via the lock-free channel (§1).
- **Net:** UI in shared Compose is a good fit; treat waveform rendering as a mini-optimization problem, not a blocker.

---

## 5. OSS library survey — suitability + license implications

### The LGPL dynamic-linking nuance (why it still hurts on mobile)
LGPL (SoundTouch, libsndfile, default FFmpeg) permits closed/permissive apps **only if the user can replace the LGPL library** — i.e. you must **dynamically link** and ship enough (object files or a shared lib) for relinking. On **iOS** this is the sticking point: App Store apps are effectively statically linked / signed, users can't relink, so LGPL static linking is a compliance grey area many teams avoid. On Android a `.so` is already a dynamic lib, so LGPL is more defensible there — but you still owe the relink provision and license notices. For a **fully open-source** app this is less fatal (you can publish source), but it complicates any future non-GPL/closed build and adds packaging burden. Refs: FFmpeg legal <https://www.ffmpeg.org/legal.html>; mobile-ffmpeg's writeup of the App Store relink problem <https://github.com/tanersener/mobile-ffmpeg/wiki/Using-MobileFFmpeg-in-Commercial-Applications>.

### Why GPL/AGPL is problematic for App Store distribution
GPL/AGPL are **strong copyleft**: linking them (static *or* dynamic) makes the **whole app** GPL/AGPL. Two mobile-specific problems: (1) **GPL vs. App Store ToS conflict** — the App Store's DRM/usage terms have historically been argued to conflict with GPLv2/v3's "no additional restrictions" (the VLC-on-App-Store episode); it's contentious and best avoided. (2) **AGPL** adds a network-use clause. For a free/OSS app you *could* license the whole app GPLv3-compatible, but it permanently blocks any closed/dual-licensed edition and invites store friction. **Recommendation: keep the shipped binary free of GPL/AGPL code.**

### Library-by-library

**I/O & engine substrate**
- **Oboe — Apache-2.0.** ✅ Perfect. Android low-latency I/O (AAudio/OpenSL). Use it. <https://github.com/google/oboe>
- **miniaudio — public-domain OR MIT-0 (dual).** ✅ Ideal. Single-header cross-platform playback/capture, decoding (WAV/FLAC/MP3 via dr_libs), resampling, node graph. Great as the iOS/desktop I/O fallback and for file I/O everywhere. <https://miniaud.io/> , license <https://github.com/mackron/miniaudio/blob/master/LICENSE>
- **dr_libs (dr_wav/dr_flac/dr_mp3) — public-domain OR MIT-0.** ✅ Ideal. Tiny single-header decoders. <https://github.com/mackron/dr_libs>

**File I/O (heavier)**
- **libsndfile — LGPL-2.1.** ⚠️ Broad format support, but LGPL (see nuance above). Prefer dr_libs/miniaudio to avoid it. <https://github.com/libsndfile/libsndfile>
- **FFmpeg — LGPL-2.1+ by default, GPL if `--enable-gpl`.** ⚠️/🚫 Only if you need many container/codec formats. Ship the **LGPL** build, dynamically linked, and never enable GPL components. Heavy; overkill for a WAV/AIFF/FLAC-centric DAW. <https://www.ffmpeg.org/legal.html>

**Time-stretch / pitch (loop + tempo features)**
- **Signalsmith Stretch — MIT.** ✅✅ Recommended. Modern, high-quality polyphonic time-stretch/pitch-shift, permissive, single-purpose. <https://github.com/Signalsmith-Audio/signalsmith-stretch>
- **SoundTouch — LGPL-2.1.** ⚠️ Good, widely used (Remixlive/n-Track use it), but LGPL. Usable on Android as a `.so`; awkward on iOS. Prefer Signalsmith. <https://codeberg.org/soundtouch/soundtouch>
- **Rubber Band — GPLv2 OR commercial (dual).** 🚫 for a permissive app. Best quality, but using the GPL edition makes the whole app GPL (and needs a paid licence otherwise). n-Track ships it *commercially*. Avoid unless you deliberately GPL the app. <https://breakfastquay.com/rubberband/> , <https://github.com/breakfastquay/rubberband>
- **zplane élastique — commercial.** 🚫 (What Remixlive licenses; not OSS.)

**DSP frameworks / effect algorithm sources**
- **STK (Synthesis ToolKit) — MIT (MIT-STK variant).** ✅ Permissive; great reference/impl for synths, filters, reverbs, effects. <https://github.com/thestk/stk>
- **Maximilian — MIT.** ✅ Permissive C++ audio/DSP (oscillators, filters, FFT, effects); good building blocks/reference. <https://github.com/micknoise/Maximilian>
- **Faust — compiler is GPL, BUT generated DSP code carries a licensing *exception*** allowing use in any (incl. proprietary/permissive) project; many Faust `.lib` functions are permissively licensed. ✅ *with care* — you can author effects in Faust and **export permissive C++** to embed. Verify the per-function license of libraries you pull in. <https://en.wikipedia.org/wiki/FAUST_(programming_language)>
- **KFR — GPLv2+ OR commercial (dual).** 🚫 for a permissive app (FFT, biquads, resampling are excellent, but GPL infects the app; commercial otherwise). <https://kfrlib.com/>
- **JUCE — AGPLv3 OR commercial** (JUCE 8 adds a free "Starter" tier up to **$20k** gross revenue, no splash screen). 🚫 as a *permissive* dependency: the OSS path is **AGPLv3**, which would make the whole app AGPL. Excellent framework (`juce_dsp`, what Remixlive uses) but wrong copyleft for us. <https://juce.com/legal/juce-8-licence/>
- **Tracktion Engine — GPLv3 OR commercial**, *and still requires a JUCE licence*. 🚫 A full DAW engine, but GPL/commercial and JUCE-coupled. Study it, don't link it. <https://github.com/Tracktion/tracktion_engine>
- **Superpowered — commercial** (per-app licence; LGPL code can't even be combined with it). 🚫 Not OSS. <https://superpowered.com/license>

**FFT (for tuner pitch detection, spectral effects)**
- **KissFFT — BSD-3-Clause.** ✅ Simple, permissive. **pffft/PFFFT — BSD-like (FFTPACK-derived).** ✅ Fast, permissive (Remixlive bundles pffft). Use these instead of KFR's GPL FFT. <https://github.com/mborgerding/kissfft>

---

## 6. Real open-source mobile DAW / audio-engine projects to study

- **MWEngine — MIT** (igorski). Android C++ real-time audio engine + DSP with a Java/Kotlin API over JNI (SWIG-generated), OpenSL **and** AAudio backends, sample-accurate sequencing, effects, synthesis, recording. The single most relevant OSS reference for our Android engine + JNI facade. <https://github.com/igorski/MWEngine> , license <https://github.com/igorski/MWEngine/blob/master/LICENSE>
- **Oboe samples** (Apache-2.0) — `RhythmGame`, `MegaDrone`, full-duplex, and recorder samples show the callback/latency patterns from §3. <https://github.com/google/oboe/tree/main/samples>
- **Superpowered cross-platform example** (SDK commercial, but the repo shows an Android+iOS+macOS engine structure). Architecture reference only. <https://github.com/superpoweredSDK/Low-Latency-Android-iOS-Linux-Windows-tvOS-macOS-Interactive-Audio-Platform>
- **Zrythm — AGPLv3** (desktop FOSS DAW). Study the *architecture* (graph, automation, transport); do **not** copy code into a permissive project. <https://www.zrythm.org/>
- **LMMS — GPL-2.0** (desktop). Mature FOSS DAW; same "study don't link" caveat. <https://lmms.io/>
- **Ardour — GPL** (desktop, pro). Excellent engine/automation design reference; GPL, so reference only.
- Our own **`../REPORT.md`** — reverse-engineered native stacks of BandLab (custom C++, Oboe), n-Track (`libaudiomodule`, Rubber Band+SoundTouch), Remixlive (JUCE + élastique + SoundTouch + pffft). Concrete evidence of what shipping mobile DAWs actually link.

---

## 7. License cheat-sheet

| Library | License | OK for free OSS app? | Notes |
|---|---|---|---|
| **Oboe** | Apache-2.0 | ✅ Yes | Android low-latency I/O (AAudio/OpenSL). No conditions beyond notice. |
| **miniaudio** | Public-domain / MIT-0 | ✅ Yes | Cross-platform I/O + decode + resample. Zero friction. |
| **dr_libs** (dr_wav/flac/mp3) | Public-domain / MIT-0 | ✅ Yes | Single-header decoders. |
| **Signalsmith Stretch** | MIT | ✅ Yes | Time-stretch/pitch. **Preferred stretch lib.** |
| **STK** | MIT (MIT-STK) | ✅ Yes | Synth/effect algorithms & reference. |
| **Maximilian** | MIT | ✅ Yes | DSP building blocks & reference. |
| **KissFFT** | BSD-3-Clause | ✅ Yes | FFT for tuner/spectral. |
| **pffft / PFFFT** | BSD-like | ✅ Yes | Fast FFT (permissive). |
| **MWEngine** | MIT | ✅ Yes | Reference Android engine + JNI pattern; reusable. |
| **Faust (generated code)** | GPL compiler + **exception** on output | ✅ With care | Exported C++ usable in permissive apps; verify per-`.lib` license. |
| **SoundTouch** | LGPL-2.1 | ⚠️ Caution | Dynamic-link only; awkward on iOS. Prefer Signalsmith. |
| **libsndfile** | LGPL-2.1 | ⚠️ Caution | LGPL relink burden; prefer dr_libs/miniaudio. |
| **FFmpeg** | LGPL-2.1+ (GPL if enabled) | ⚠️ Caution | Ship LGPL build, dynamic link, no `--enable-gpl`. Heavy. iOS relink issue. |
| **Rubber Band** | GPLv2 / commercial | 🚫 Avoid | GPL infects whole app; else paid licence. |
| **KFR** | GPLv2+ / commercial | 🚫 Avoid | Same copyleft/commercial trap. |
| **JUCE** | AGPLv3 / commercial (free ≤ $20k) | 🚫 Avoid (as dep) | OSS path is AGPL → whole app AGPL. Reference only. |
| **Tracktion Engine** | GPLv3 / commercial | 🚫 Avoid (as dep) | Also requires a JUCE licence. Reference only. |
| **Superpowered** | Commercial (per-app) | 🚫 Not OSS | Proprietary; can't combine with LGPL/GPL. |
| **zplane élastique** | Commercial | 🚫 Not OSS | Reference only. |

Legend: ✅ permissive/safe · ⚠️ usable but adds LGPL relink/packaging burden (esp. iOS) · 🚫 copyleft-infecting or proprietary — keep out of the shipped binary.

---

## 8. Diagram spec — layered architecture (for a diagram)

A layered/stacked diagram, top = UI, bottom = OS audio hardware. Three horizontal bands with a clear **"real-time boundary"** line, plus two platform columns where the engine forks.

**Band 1 — Shared Kotlin `commonMain` (control plane)** *(single wide box spanning both platforms)*
- Sub-boxes: `Compose Multiplatform UI` (Timeline · Mixer · Waveform Canvas · Tuner/Metronome views); `Project/Timeline Data Model`; `Transport & Mixer State`; `Automation Curves`; `Persistence / Undo-Redo`.
- Label the whole band: *"Non-real-time. Kotlin, GC-managed."*

**Boundary — `expect class AudioEngine` facade** *(a horizontal bar straddling Band 1 and Band 2)*
- Left half annotated `androidMain actual → JNI`; right half `iosMain actual → cinterop`.
- Two arrow types crossing it: **down** = commands/param snapshots (lock-free SPSC ring buffer + atomics); **up** = playhead/meter/waveform peaks (atomics polled per UI frame). Label: *"lock-free, no locks, no allocation across this line."*

**Band 2 — Shared C++ Real-Time Engine (single box spanning both columns)**
- Sub-boxes: `Mixer Graph (sum buses, gain/pan smoothing, metering)`; `Clip Scheduler (sample-accurate, loop)`; `DSP Inserts: EQ · Comp · Reverb · Delay · Distortion`; `Time-Stretch (Signalsmith)`; `Automation Apply (ramps)`; `Record + Input Monitor`; `Tuner Pitch Detect (FFT) · Metronome Click`.
- Bundled permissive libs listed as chips: `miniaudio · dr_libs · Signalsmith(MIT) · KissFFT/pffft · STK/Maximilian`.
- Label: *"Real-time thread. C++, no GC/alloc/locks."*

**Band 3 — Platform I/O backends (the engine forks into two columns here)**
- **Android column:** `Oboe (Apache-2.0)` → `AAudio (API 27+) / OpenSL ES` → `Audio HAL / Device`. Note "high-priority `onAudioReady` callback, LowLatency + Exclusive, buffer = N× burst."
- **iOS column:** `AVAudioEngine / AURemoteIO` + `AVAudioSession (.playAndRecord)` → `Core Audio / RemoteIO` → `Device`. Note "`preferredIOBufferDuration`, 48 kHz, render callback."

**Arrows:** UI ↔ facade ↔ engine ↔ I/O, vertical. A dashed vertical "real-time boundary" line separates Bands 1 (above) from 2–3 (below). Optional side note box: *"Same C++ engine binary compiled twice; only Band 3 differs per platform."*

---

## 9. Recommended stack — justified, license-safe

1. **UI + control plane:** **Kotlin Multiplatform + Compose Multiplatform** (Stable on iOS since 1.8.0). Shared data model, transport/mixer state, automation, persistence, and all screens. Waveform via `Canvas` with precomputed peak mip-levels.
2. **Engine:** **one portable C++ real-time engine** behind an `expect class AudioEngine`; **JNI** `actual` on Android, **cinterop** `actual` on iOS. Lock-free SPSC + atomics across the boundary. (Model on **MWEngine**, MIT.)
3. **I/O:** **Oboe (Apache-2.0)** on Android; **AVAudioEngine/AURemoteIO + AVAudioSession** on iOS. Request 48 kHz, small buffer (N× burst / ~3 ms), variable `numFrames`.
4. **File I/O + resampling:** **miniaudio + dr_libs** (public-domain/MIT-0). Avoid libsndfile/FFmpeg unless exotic formats are required (then LGPL, dynamic-link, Android-friendly only).
5. **Time-stretch / pitch:** **Signalsmith Stretch (MIT)** — the key license win vs. Rubber Band (GPL) / élastique (commercial) that the shipping apps had to pay for or GPL around.
6. **DSP effects:** hand-write biquad EQ, one-pole filters, feedback-delay-network or Freeverb-style reverb, compressor (envelope follower + gain computer), delay, and waveshaper distortion, using **STK/Maximilian (MIT)** as references. Author complex effects in **Faust** and export permissive C++ if desired.
7. **FFT** (tuner + spectral): **KissFFT (BSD)** or **pffft (BSD-like)**.
8. **Explicitly excluded from the shipped binary:** Rubber Band, KFR, JUCE, Tracktion Engine, Superpowered, élastique (copyleft-infecting or proprietary); libsndfile/SoundTouch/FFmpeg only if unavoidable and only dynamically linked with notices.

**Why this is the right call:** it mirrors the proven "shared C++ engine + thin native I/O" architecture of real shipping mobile DAWs (BandLab/n-Track/Remixlive, per `../REPORT.md`) while swapping their **GPL/commercial** stretch and DSP dependencies (Rubber Band, élastique, JUCE, Superpowered) for **permissive** equivalents (Signalsmith, STK/Maximilian, miniaudio, KissFFT). The result stays fully free/OSS, ships cleanly on both stores with no copyleft or App-Store-ToS conflict, and keeps the door open to a future closed or dual-licensed edition.

---

## Sources
- Kotlin `expect`/`actual`: <https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html>
- KMP + C/C++ interop: <https://medium.com/@adman.shadman/kmp-native-c-c-a7b62e4e9c1f> · <https://staticvar.dev/post/kotlin-native-c-interop/> · <https://andreas-tennert.de/blog/a-comparison-of-jni-jna-and-cinterop-in-kotlin/>
- Oboe: <https://github.com/google/oboe> · low-latency <https://developer.android.com/games/sdk/oboe/low-latency-audio> · FullGuide <https://github.com/google/oboe/blob/main/docs/FullGuide.md> · AAudio <https://developer.android.com/ndk/guides/audio/aaudio/aaudio> · Android app latency <https://source.android.com/docs/core/audio/latency/app>
- iOS audio: AVAudioSession latency <https://developer.apple.com/forums/thread/25197> · AURemoteIO host <https://developer.apple.com/forums/thread/65675> · player-node latency <https://developer.apple.com/forums/thread/705620>
- Compose Multiplatform 1.8.0 Stable: <https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/> · 2026 field guide <https://medium.com/@thanhnh98/is-compose-multiplatform-production-ready-in-2026-a-practical-field-guide-b4863748dbe7> · <https://www.kmpship.app/blog/compose-multiplatform-ios-stable-2025>
- miniaudio: <https://miniaud.io/> · license <https://github.com/mackron/miniaudio/blob/master/LICENSE>
- dr_libs: <https://github.com/mackron/dr_libs>
- Signalsmith Stretch (MIT): <https://github.com/Signalsmith-Audio/signalsmith-stretch>
- Rubber Band (GPL/commercial): <https://breakfastquay.com/rubberband/> · <https://github.com/breakfastquay/rubberband>
- SoundTouch (LGPL): <https://codeberg.org/soundtouch/soundtouch>
- libsndfile (LGPL): <https://github.com/libsndfile/libsndfile>
- FFmpeg license: <https://www.ffmpeg.org/legal.html> · App Store relink issue <https://github.com/tanersener/mobile-ffmpeg/wiki/Using-MobileFFmpeg-in-Commercial-Applications>
- JUCE 8 licence: <https://juce.com/legal/juce-8-licence/> · Tracktion Engine <https://github.com/Tracktion/tracktion_engine>
- KFR: <https://kfrlib.com/> · Faust: <https://en.wikipedia.org/wiki/FAUST_(programming_language)>
- Superpowered: <https://superpowered.com/license>
- STK (MIT): <https://github.com/thestk/stk> · Maximilian (MIT): <https://github.com/micknoise/Maximilian> · KissFFT (BSD): <https://github.com/mborgerding/kissfft>
- MWEngine (MIT): <https://github.com/igorski/MWEngine> · license <https://github.com/igorski/MWEngine/blob/master/LICENSE>
- Zrythm (AGPLv3): <https://www.zrythm.org/> · LMMS (GPL-2.0): <https://lmms.io/>
- Companion analysis of shipping apps: `../REPORT.md`
