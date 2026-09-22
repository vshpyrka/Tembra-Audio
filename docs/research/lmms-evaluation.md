# LMMS — Open-Source DAW Evaluation

**Question:** Can LMMS (Linux MultiMedia Studio) be used as a foundation — engine, mixer, or reusable library — for a free, cross-platform (Android + iOS) mobile DAW built with Kotlin Multiplatform?

**Verdict:** **No.** LMMS is a mature, genuinely cross-platform *desktop* DAW and a readable example of a multithreaded pull-based mixer, but it fails as a mobile foundation on the same two independent counts as Ardour: its **GPLv2-or-later license** (kills App Store distribution and forces the whole app open under GPL) and its **desktop-monolith architecture** (Qt Widgets UI + desktop-only audio backends, not an embeddable library, no AAudio/Oboe or AVAudioEngine path). Unlike Ardour it is GPL **v2-or-later** (so it could move to GPLv3), but that changes nothing for us — every GPL version is still copyleft and still App-Store-incompatible. LMMS does, however, sit on a pile of **permissively-licensed satellite libraries** worth harvesting individually.

- Repo: <https://github.com/LMMS/lmms>
- Site: <https://lmms.io/>
- Verified against primary sources on 2026-08-27.

---

## 1. License — GPL v2-or-later (strong copyleft)

- The repo's `LICENSE.txt` is the verbatim **GNU General Public License, Version 2, June 1991** text.
- **But** the per-file source headers add the "or later" grant. `src/core/main.cpp` reads: *"you can redistribute it and/or modify it under the terms of the GNU General Public License … either version 2 of the License, **or (at your option) any later version**."* So LMMS is properly **GPLv2-or-later**, not the strict GPLv2 that Ardour uses.

**Consequences for a would-be reuser:**
- Linking LMMS core into an app makes the **entire app GPL** — you must publish *all* your source, and you cannot combine it with proprietary code or GPL-incompatible OSS.
- The copyleft compounds through LMMS's own bundled dependencies: **FFTW3 (GPLv2+)** and the bundled **Carla** plugin host (GPLv2+) are GPL; **Qt (LGPLv3/GPL/commercial)**, **libsndfile (LGPL)**, **FluidSynth (LGPL)**, **LAME (LGPL)**, **libgig (LGPL)** and **FLTK (LGPL + linking exception)** are LGPL.
- **iOS / App Store — effectively impossible.** The App Store Terms impose usage restrictions/DRM that conflict with the GPL's "no further restrictions" clause. This is the same conflict that forced **VLC** off the store. The "or later" option does not help: GPLv2 and GPLv3 are *both* App-Store-incompatible.
- **Android / Google Play — technically allowed**, but the whole app would have to ship as GPL open source (no proprietary components, no GPL-incompatible OSS).

---

## 2. What LMMS uses for audio (engine + mixer)

A hand-written **C++ engine** on a **Qt5/Qt6 Widgets** desktop UI (Core, Gui, Widgets, Xml, Svg; Qt5 ≥ 5.15, experimental Qt6). Build system is **CMake** with **vcpkg** for dependencies. Song/project state is serialized to XML.

**Signal flow / mixing model** (a clean, worth-reading example):
- **Pull-based model.** The output device requests buffers; `AudioEngine::renderNextBuffer` (the class was historically named `Mixer`) drives the graph: it removes finished `PlayHandle`s, calls `Song::processNextBuffer`, and queues per-channel jobs.
- **Track sources** — `InstrumentTrack` and `SampleTrack` produce buffers, which feed **FX mixer channels**. Each `MixerChannel::doProcessing()` accumulates its receive routes (with volume/send scaling), runs a sequential **EffectChain**, computes peaks for metering, then notifies downstream channels. **Channel 0 is the master.**
- **Multithreaded summing** — `AudioEngineWorkerThread` pool processes mixer channels in parallel by **dependency tracking** (`m_dependenciesMet`); buffer summing uses optimized `MixHelpers` routines. This is the same node/dependency-graph + parallel-process design the main report describes.
- **Plugin formats hosted:** **LADSPA, LV2** (via **lilv** + **suil**), **VST2** (via bundled **Carla**/VeSTige), **SoundFont2** (FluidSynth), **GIG** (libgig), **STK**-based instruments, **GUS patches**.

**Audio I/O backends** (via the abstract `AudioDevice` class, chosen in the Setup dialog):
- **JACK** (cross-platform), **ALSA** (Linux), **PulseAudio** (Linux), **OSS** (legacy Unix), **PortAudio** (cross-platform), **SDL2** (cross-platform), **libsoundio / sndio**, and a **Dummy** (offline render) backend.
- **All are desktop APIs.** There is **no AAudio/Oboe** (Android) and **no AVAudioEngine/AURemoteIO/Core Audio** (iOS) backend. MIDI backends are ALSA-seq, WinMM, Apple (macOS CoreMIDI) and JACK-MIDI — again none mobile.

**Bundled / third-party DSP libraries** (from `vcpkg.json` + CMake), with licenses:
- **FFTW3F** — **GPLv2+** (or paid commercial) — FFT / spectral analysis.
- **libsamplerate** — **BSD-2-Clause** — sample-rate conversion.
- **libsndfile** — **LGPL-2.1+** — audio file I/O.
- **FluidSynth** — **LGPL-2.1+** — SoundFont2 synthesis.
- **STK** (Synthesis ToolKit) — **permissive MIT-style** license (caveat: a few classes historically referenced Stanford/Yamaha waveguide patents, now largely expired) — synthesis primitives.
- **lilv** + **suil** — **ISC** — LV2 host / LV2-UI host.
- **LAME (mp3lame)** — **LGPL** — MP3 encode; **libFLAC** — **BSD-3**, **libvorbis/libogg** — **BSD** — codecs.
- **FLTK** — **LGPL + static-linking exception** — UI for the bundled ZynAddSubFX synth, not LMMS's own UI.
- **SDL2** / **zlib** — **Zlib**; **Carla** — **GPLv2+** (VST hosting); **libgig** — **LGPL**.

---

## 3. Mobile viability

### License (hard blocker)
- **iOS:** GPL (v2 or v3) is incompatible with App Store distribution (see §1). No path.
- **Android:** allowed only if the entire app is released under the GPL.

### Architecture (practical blocker)
- **Monolithic desktop application, not a library.** The engine is coupled to Qt (even non-GUI core pulls in `QObject`, `QDomElement` XML state, Qt containers) and to the FX-mixer/PlayHandle model; "just the mixer" cannot be cleanly lifted out.
- **UI is Qt Widgets** (X11 / Cocoa / Win32). There is no mobile UI and no route to Compose/SwiftUI. (Qt itself technically runs on mobile, but LMMS's desktop-Widgets UI, docking, and drag-drop workflow do not.)
- **No mobile audio backend exists** — every `AudioDevice` backend targets a desktop OS API. You would have to write AAudio/Oboe and AVAudioEngine paths from scratch.
- Assumes desktop resources: a real filesystem, plugin scanning, VST hosting, and a CMake/vcpkg build — heavyweight for a phone.

---

## 4. What you *can* legally take from it

- **Ideas & architecture** — reading the source is unrestricted; the pull-based `renderNextBuffer` + dependency-graph worker-thread mixer + FX-channel routing is a compact, approachable model to **study** (not copy code) for the engine, arguably easier to read than Ardour's.
- Several of LMMS's **permissively-licensed satellites** are reusable on their own upstream terms and already fit the report's permissive / LGPL-with-caution tiers — take them *from upstream*, never as a GPL-linked whole:
  - **libsamplerate** — BSD-2-Clause — resampling (✅ ship-safe).
  - **STK** — permissive MIT-style — synthesis classes (✅, mind the legacy patent note).
  - **lilv / suil** — ISC (✅) — only relevant if you ever host LV2.
  - **libFLAC / libogg / libvorbis** — BSD (✅) — codecs; **SDL2 / zlib** — Zlib (✅).
  - **libsndfile**, **FluidSynth**, **LAME** — LGPL — usable dynamic-link only (⚠️, awkward on iOS static builds).

**Net:** LMMS belongs in the report's **🚫 reference-only** tier alongside Ardour/JUCE/Tracktion — study the mixer design, don't link the engine. Keep building on the MIT/Apache/MPL stack (miniaudio/dr_libs, Oboe, Signalsmith Stretch, pffft/KissFFT; or the Rust CPAL/Symphonia route), and harvest LMMS's BSD/ISC/MIT satellites individually from their upstreams.

---

## Sources
- LMMS source repo — <https://github.com/LMMS/lmms>
- `LICENSE.txt` (GPLv2 text) — <https://raw.githubusercontent.com/LMMS/lmms/master/LICENSE.txt>
- Per-file "v2 or later" header — `src/core/main.cpp` — <https://raw.githubusercontent.com/LMMS/lmms/master/src/core/main.cpp>
- Dependency manifest — `vcpkg.json` — <https://raw.githubusercontent.com/LMMS/lmms/master/vcpkg.json>
- Supported audio/MIDI backends — <https://docs.lmms.io/developer-guides/core/audio-and-midi-i-o/supported-backends-and-features>
- Mixer & audio routing (engine design) — <https://deepwiki.com/LMMS/lmms/5.1-mixer-and-audio-routing>
- LMMS architecture wiki — <https://github.com/LMMS/lmms/wiki/LMMS-Architecture>
- STK license — <https://github.com/thestk/stk>; FLTK license — <https://www.fltk.org/COPYING.php>; LV2/lilv (ISC) — <https://en.wikipedia.org/wiki/LV2>
