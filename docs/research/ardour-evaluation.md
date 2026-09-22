# Ardour — Open-Source DAW Evaluation

**Question:** Can Ardour (the open-source DAW) be used as a foundation — engine, mixer, or reusable library — for a free, cross-platform (Android + iOS) mobile DAW?

**Verdict:** **No.** Ardour is a first-class *reference* for how a real DAW mixes audio, but it is unusable as a foundation on two independent counts: its **GPLv2 license** (kills App Store distribution and forces the whole app open under GPL) and its **desktop-monolith architecture** (GTK UI + desktop-only audio backends, ~1M LOC, not an embeddable library). This *confirms* the report's standing guidance: avoid GPL/AGPL, build on an MIT/Apache/MPL stack.

- Repo: <https://github.com/Ardour/ardour>
- Site: <https://ardour.org/>
- Verified against primary sources on 2026-08-26.

---

## 1. License — GPL v2 (strong copyleft)

- The repo's `COPYING` file is verbatim **GNU General Public License, Version 2, June 1991**, followed by an Ardour-specific "Plugin Clarification."
- Ardour's development page states plainly: *"Ardour is licensed under the GNU Public License v2… all contributed code must use GPL licensing"* (copyright retained by contributors).

**Consequences for a would-be reuser:**
- Linking `libardour` into an app makes the **entire app GPLv2** — you must publish *all* your source, and you cannot combine it with proprietary code or GPL-incompatible OSS.
- The copyleft compounds through Ardour's own dependencies: **rubberband (GPL)**, **aubio (GPL)**, **fftw (GPL unless separately licensed)** are all GPL; **soundtouch** is LGPL.
- **iOS / App Store — effectively impossible.** The App Store Terms impose usage restrictions/DRM that conflict with GPLv2's "no further restrictions" clause. This is the same conflict that forced **VLC** off the store. A GPLv2 app cannot be distributed through Apple's store.
- **Android / Google Play — technically allowed**, but your whole app must ship as GPLv2 open source (no proprietary components, no GPL-incompatible OSS).

---

## 2. What Ardour uses for mixing tracks

A hand-written **C++ engine (`libs/ardour`, ~160k LOC)** — not a third-party mixing framework. UI is **gtkmm/GTK+** (~210k LOC). ~1M LOC total. Build system is **waf** (Python). Architecture is **MVC** with a heavy **asynchronous signal/callback** system (libsigc++).

**Signal flow / mixing model:**
- **Backends** (`libs/backends`) abstract hardware I/O: **JACK, ALSA, CoreAudio (desktop), PortAudio/ASIO, WASAPI, PulseAudio, Dummy**. All desktop APIs.
- A routed **process Graph**: `Session`/`Route` objects are summed through a multi-threaded `Graph` that **topologically sorts** routes and parallelizes their processing across CPU cores.
- A dedicated **Butler** thread streams audio to/from disk so the realtime audio thread never blocks on I/O.
- **Panning** via pluggable pan-law modules (`libs/panners`); gain via `Amp`; **sends / inserts / returns**; effects come from hosted **LADSPA / LV2 / VST2 / VST3 / AU** plugins.
- **evoral** — MIDI + automation model; **temporal** — tempo map / timeline; **audiographer** — export.

**Bundled / third-party DSP** (from `libs/` and the dependency list):
- Bundled: **fluidsynth** (SF2 synth, LGPL), **zita-convolver** (convolution reverb), **zita-resampler**, **qm-dsp** (Queen Mary analysis DSP), **vamp-plugins / vamp-pyin** (feature extraction + pitch), **libltc** (LTC timecode), **lua** (scripting), **staffpad**.
- External: **libsndfile** (file I/O, LGPL), **libsamplerate** (resampling, BSD-2), **rubberband** + **soundtouch** (time-stretch/pitch), **fftw**, **taglib**, **flac**, **aubio**.

> These map almost one-to-one onto §2 of the main report: node/process graph, disk-streaming thread separate from the RT thread, pan laws, and insert-vs-send/return routing. Ardour is worth reading precisely because it is a clean, production example of that design.

---

## 3. Mobile viability

### License (hard blocker)
- **iOS:** GPLv2 is incompatible with App Store distribution (see §1). No path.
- **Android:** allowed only if the entire app is released under GPLv2.

### Architecture (practical blocker)
- **Monolithic ~1M-LOC desktop application, not a library.** `libardour` is tightly coupled to **PBD**, **libsigc++**, and the **XML session state model**; "just the mixer" cannot be cleanly lifted out.
- **UI is GTK+/gtkmm2** (X11 / Quartz / GDI). There is no mobile UI and no route to Compose/SwiftUI.
- **No mobile audio backend exists** — no **AAudio/Oboe** (Android), no **AVAudioEngine/AURemoteIO** (iOS). You would have to write these from scratch.
- Assumes desktop resources: large RAM, a real filesystem, plugin scanning, and a waf/Python build — heavyweight for a phone.

---

## 4. What you *can* legally take from it

- **Ideas & architecture** — reading the source is unrestricted; the process-Graph + Butler-thread + panner design is a strong model to study (not copy code) for the engine.
- A few of Ardour's **permissively-licensed satellites** are reusable on their own terms, and already fit the report's permissive / LGPL-with-caution tiers:
  - **libsamplerate** — BSD-2-Clause (since 0.1.9) — resampling.
  - **libsndfile** — LGPL — file I/O (dynamic-link only).
  - **fluidsynth** — LGPL — SF2 synthesis (dynamic-link only).

**Net:** Ardour belongs in the report's **🚫 reference-only** tier alongside JUCE/Tracktion — study it, don't link it. Continue building on the MIT/Apache/MPL engine stack (miniaudio/dr_libs, Oboe, Signalsmith Stretch, pffft/KissFFT; or the Rust CPAL/Symphonia/Crossbeam alternative).

---

## Sources
- Ardour source repo — <https://github.com/Ardour/ardour> and `libs/` tree
- `COPYING` (GPLv2) — <https://raw.githubusercontent.com/Ardour/ardour/master/COPYING>
- Ardour development / architecture — <https://ardour.org/development.html>
- Current dependencies — <https://ardour.org/current_dependencies.html>
