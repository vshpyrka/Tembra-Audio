# Audacity — Open-Source Audio Editor Evaluation

**Question:** Can Audacity (the open-source audio editor/recorder) be used as a foundation — engine, mixer, or reusable library — for a free, cross-platform (Android + iOS) mobile DAW?

**Verdict:** **No.** Audacity is a good *reference* for waveform editing and simple track downmixing, but it is unusable as a foundation on two independent counts: its **GPLv3 license** (kills App Store distribution and forces the whole app open under GPL — *stronger* copyleft than Ardour's GPLv2, and the anti‑Tivoization clause makes the Apple conflict worse) and its **desktop-monolith architecture** (wxWidgets in the legacy engine, a new Qt/QML rewrite on top, desktop-only PortAudio/PortMixer, no mobile audio backend, not an embeddable library). Note too that Audacity is primarily a **destructive waveform editor/recorder**, not a real-time routed multitrack DAW like Ardour. This *confirms* the report's standing guidance: avoid GPL/AGPL, build on an MIT/Apache/MPL stack.

- Repo: <https://github.com/audacity/audacity>
- Site: <https://www.audacityteam.org/>
- Verified against primary sources on 2026-09-04.

---

## 1. License — GPL v3 (strong copyleft)

- The repo's `LICENSE.txt` opens verbatim: *"Audacity is released under the GNU General Public License version 3 (GPLv3). Individual source files may be available under other licenses… many source files are available under GPL version 2 (GPLv2) or (at your option) any later version, and this is the default license used where no other license is specified."* Documentation is CC-BY 3.0. The file then reproduces the full GPLv2, GPLv3, and CC-BY 3.0 texts.
- The `README.md` restates it: *"Audacity is open source software licensed GPLv3. Most code files are GPLv2-or-later, with the notable exceptions being `/au3/lib-src` (third party libraries) as well as VST3-related code."*
- **Relicensing history:** Audacity shipped under **GPLv2** from 2000. After **Muse Group** acquired it in **2021**, it introduced a **Contributor License Agreement** (GitHub discussion #932, May 2021) and moved the project umbrella to **GPLv3**. Muse Group's stated reasons: enable **VST3** (GPLv2-incompatible), share code with sister project **MuseScore**, and — via the CLA's dual-licensing rights — ship Audacity on **Apple's App Store** despite GPL. The CLA assigns Muse Group broad rights, *"including… licensing, dual licensing… and to use the code in other products that may not be open source."*

**Consequences for a would-be reuser:**
- Linking Audacity's engine libraries into an app makes the **entire app GPLv3** — you must publish *all* your source, and you cannot combine it with proprietary code or GPL-incompatible OSS.
- **iOS / App Store — effectively impossible.** GPLv2 and GPLv3 both conflict with the App Store Terms' usage restrictions/DRM (the "no further restrictions" clause); GPLv3's anti‑Tivoization language makes it strictly worse. This is the same conflict that forced **VLC** off the store. Crucially, **only Muse Group** can ship a store build, because *they* hold the CLA-assigned rights to dual-license — a third-party reuser has no such escape hatch.
- **Android / Google Play — technically allowed**, but your whole app must ship as GPLv3 open source (no proprietary components, no GPL-incompatible OSS).

---

## 2. What Audacity uses for audio I/O and mixing tracks

Audacity now spans **two codebases in one repo**: the master branch is the in-progress **Audacity 4** rewrite — a **Qt/QML** UI built on the **MuseScore "muse" framework** (git submodules `muse` + `muse_deps`, with `src/` the new app shell). Beneath it, the entire legacy **Audacity 3** engine is preserved under **`au3/`** as ~80 modular libraries (`au3/libraries/au3-*`), still built on **wxWidgets** (`wxBase`). Build system is **CMake** (+ CMakePresets); deps are resolved through the muse framework's manifest rather than a vendored `lib-src` tree.

**Signal flow / mixing model:**
- **Audio I/O:** `au3-audio-io` is *"an audio engine, implemented with the cross-platform portaudio library."* It links **`portaudio::portaudio`** plus optional **PortMixer** (`AU_USE_PORTMIXER`), and contains `AudioIO`, `RingBuffer`, and `PlaybackSchedule`. PortAudio's host backends are all **desktop** (CoreAudio, WASAPI/DirectSound/WMME, ALSA, JACK, OSS) — PortMixer's `src/` confirms this (`px_mac_coreaudio`, `px_win_wasapi`, `px_linux_alsa`, `px_unix_oss`, …). No AAudio/Oboe, no AVAudioEngine.
- **Mixing:** `au3-mixer` is Audacity's **own** code (`Mix.cpp`, `MixerSource`, `DownmixStage`, `DownmixSource`, `EffectStage`, `Envelope`). Its header: *"Mix combines multiple WideSampleSequences into one output stream of samples, also handling resampling."* It is a **downmix stage** — a linear timeline of clips summed for playback/export — plus a realtime-effects `EffectStage`, **not** a routed process graph with buses/sends like Ardour.
- **File I/O:** `au3-file-formats` links **`SndFile::sndfile`** (libsndfile). Projects themselves are stored in an **SQLite** `.aup3` database. Sample formats are 16-bit int, 24-bit int, and 32-bit float.
- **DSP satellites** (`thirdparty/`): **pffft** (FFT, also `au3-fft`), **soundtouch** + **sbsms** (time-stretch/pitch, via `au3-time-and-pitch`/`au3-stretching-sequence`), **soxr** (resampling), **twolame** (MP2 encode), plus **lv2sdk** and **vst3** for plugins. Nyquist/LADSPA/LV2/VST3 effect hosting lives in `au3-nyquist-effects`, `au3-ladspa`, `au3-lv2`, `au3-vst3`.

> **Distinction from Ardour:** Audacity is a **destructive waveform editor/recorder**. It *does* mix multiple tracks (the `DownmixStage` in `au3-mixer`) and supports realtime effects, but the model is a clip timeline summed at playback/export time, not a multi-threaded route graph with topological scheduling, sends, and a disk-butler thread. It is a simpler, more editing-oriented design.

---

## 3. Mobile viability

### License (hard blocker)
- **iOS:** GPLv3 (and the GPLv2-or-later default) is incompatible with App Store distribution (see §1); GPLv3's anti-Tivoization terms make it worse than Ardour's GPLv2. Only Muse Group's CLA rights provide a store path — not available to third parties. No path.
- **Android:** allowed only if the entire app is released under GPLv3.

### Architecture (practical blocker)
- **Monolithic desktop application, not a library.** The engine is ~80 tightly-coupled `au3-*` libraries wired to **wxWidgets** (`wxBase`), the wx event/config model, and the SQLite `.aup3` project store; "just the mixer" cannot be cleanly lifted out.
- **UI is desktop-bound twice over:** legacy Audacity 3 is **wxWidgets** (X11/Quartz/GDI); the Audacity 4 rewrite is **Qt/QML on the MuseScore framework** — a *desktop* toolkit with no Compose/SwiftUI path.
- **No mobile audio backend exists** — PortAudio/PortMixer ship only desktop host APIs; there is no **AAudio/Oboe** (Android) or **AVAudioEngine/AURemoteIO** (iOS). You would write these from scratch.
- Assumes desktop resources: large RAM, a real filesystem, plugin scanning (VST3/LV2/LADSPA/Nyquist), and a CMake + submodule build — heavyweight for a phone.

---

## 4. What you *can* legally take from it

- **Ideas & architecture** — reading the source is unrestricted; the block-file/SQLite project store and the `DownmixStage` are worth studying (not copying code) for a simple editor.
- Audacity's **permissively-licensed satellites** are reusable on their own terms and already fit the report's permissive / LGPL-with-caution tiers (each verified against its own license file):
  - **PortAudio** — **MIT-style permissive** (Bencina/Burk) — audio I/O. Ship-safe *but desktop host APIs only; no mobile backend included.*
  - **PortMixer** — **MIT-style permissive** (Audacity-maintained) — hardware mixer control. Desktop backends only.
  - **PortSMF** — **MIT-style permissive** (Dannenberg) — Standard MIDI File I/O.
  - **pffft** — **BSD-style / FFTPACK license** (Julien Pommier) — FFT. Ship-safe.
  - **SQLite** — **public domain** — project database. Ship-safe.
  - **libsndfile** — **LGPL 2.1** — file I/O (dynamic-link only; awkward on iOS).
  - **SoundTouch** — **LGPL 2.1** — time-stretch/pitch (dynamic-link only).
  - **libsoxr** — **LGPL 2.1** — resampling (dynamic-link only).
  - **TwoLAME** — **LGPL 2.1** — MP2 encoding (dynamic-link only).
  - ⚠️ **Do NOT take: sbsms (libsbsms) — GPLv3**, and the **VST3 SDK** (Steinberg's GPLv3/proprietary dual license) — both copyleft/restricted, same trap as the app itself.

**Net:** Audacity belongs in the report's **🚫 reference-only** tier alongside Ardour and JUCE/Tracktion — study it, don't link it. The permissive satellites (PortAudio, PortMixer, PortSMF, pffft, SQLite) are the only pieces you can actually ship, and they overlap what the report's MIT/Apache/MPL engine stack already provides (miniaudio/dr_libs, Oboe, Signalsmith Stretch, pffft/KissFFT; or the Rust CPAL/Symphonia/Crossbeam alternative).

---

## Sources
- Audacity source repo & tree — <https://github.com/audacity/audacity> (`au3/libraries/`, `src/`, `thirdparty/`, `.gitmodules`)
- `LICENSE.txt` (GPLv3 + GPLv2 + CC-BY 3.0) — <https://raw.githubusercontent.com/audacity/audacity/master/LICENSE.txt>
- `README.md` License section — <https://raw.githubusercontent.com/audacity/audacity/master/README.md>
- CLA / relicensing announcement (2021) — <https://github.com/audacity/audacity/discussions/932>
- `au3-audio-io` CMake (PortAudio/PortMixer) — <https://raw.githubusercontent.com/audacity/audacity/master/au3/libraries/au3-audio-io/CMakeLists.txt>
- `au3-mixer` CMake/header (Mix/DownmixStage) — <https://raw.githubusercontent.com/audacity/audacity/master/au3/libraries/au3-mixer/CMakeLists.txt>
- `au3-file-formats` CMake (libsndfile) — <https://raw.githubusercontent.com/audacity/audacity/master/au3/libraries/au3-file-formats/CMakeLists.txt>
- PortAudio license (MIT-style) — <https://raw.githubusercontent.com/PortAudio/portaudio/master/LICENSE.txt>
- PortMixer license header (`px_mixer.h`, MIT-style) — <https://raw.githubusercontent.com/audacity/audacity/master/thirdparty/portmixer/src/px_mixer.h>
- PortSMF `license.txt` (MIT-style) — <https://raw.githubusercontent.com/audacity/audacity/release-3.7.5/lib-src/portsmf/license.txt>
- pffft header (BSD/FFTPACK) — <https://raw.githubusercontent.com/audacity/audacity/release-3.7.5/lib-src/pffft/pffft.c>
- libsndfile `COPYING` (LGPL 2.1) — <https://raw.githubusercontent.com/libsndfile/libsndfile/master/COPYING>
- SoundTouch `COPYING.TXT` (LGPL 2.1) — <https://codeberg.org/soundtouch/soundtouch/raw/branch/master/COPYING.TXT>
- libsoxr `LICENCE` (LGPL 2.1) — <https://raw.githubusercontent.com/chirlu/soxr/master/LICENCE>
- TwoLAME `COPYING` (LGPL 2.1) — <https://raw.githubusercontent.com/njh/twolame/master/COPYING>
- libsbsms `LICENSE` (GPLv3) — <https://raw.githubusercontent.com/audacity/audacity/release-3.7.5/lib-src/libsbsms/LICENSE>
