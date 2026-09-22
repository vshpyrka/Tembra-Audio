# OpenDaw (Glen Rhodes) — Open-Source DAW Evaluation

> Not to be confused with André Michelle's **openDAW** (a web/TypeScript DAW). This evaluates **glenwrhodes/OpenDaw** only — a native C++/Qt desktop project.

**Question:** Can OpenDaw (Glen Rhodes' open-source DAW) be used as a foundation — engine, mixer, or reusable library — for a free, cross-platform (Android + iOS) mobile DAW built in Kotlin Multiplatform?

**Verdict:** **No.** OpenDaw is rejected on the same two independent counts as Ardour, plus immaturity. Its **GPLv3 license** (kills App Store distribution, forces the whole app open) is compounded by a copyleft *engine* stack — **Tracktion Engine (GPLv3)** on **JUCE (AGPLv3)** — and its **desktop-monolith architecture** (C++20 + Qt 6, VST3 plugin host, desktop-only, no mobile audio backend). On top of that it is a **very young solo-developer project** (~32 stars, ~67 commits, one author, first commit March 2026, quiet since). Nothing here is a free-and-open, embeddable mobile building block; it *reconfirms* the report's guidance to avoid GPL/AGPL and build on an MIT/Apache/MPL stack.

- Repo: <https://github.com/glenwrhodes/OpenDaw>
- Verified against primary sources (repo, `LICENSE`, `README.md`, commit history) on 2026-08-27.

---

## 1. License — GPL v3 (strong copyleft), on an AGPL/GPL engine

- The repo's `LICENSE` file is verbatim **GNU GENERAL PUBLIC LICENSE, Version 3, 29 June 2007** — confirmed by reading the actual file. This *is* a licensed open-source project (unlike some hobby repos with no license), but it is **strong copyleft**.
- The README states the app is **GPLv3**, and correctly notes its two core dependencies: **Tracktion Engine (GPLv3)** and **JUCE (AGPLv3)**.

**Consequences for a would-be reuser:**
- Linking or copying any of OpenDaw's own code makes the **entire derived app GPLv3** — you must publish *all* source and cannot combine it with proprietary or GPL-incompatible code.
- The copyleft runs all the way down the audio stack:
  - **Tracktion Engine** is dual-licensed **GPLv3-or-later / commercial**. Free/open use means GPLv3; anything closed-source requires a *paid* commercial license.
  - **JUCE** is dual-licensed **AGPLv3 / commercial**. AGPLv3 is *more* aggressive than GPLv3 (network-use triggers source disclosure), and a JUCE commercial license must be purchased **separately** from Tracktion's — they are different companies.
- **iOS / App Store — effectively impossible.** GPLv3 and AGPLv3 both conflict with the App Store Terms' usage restrictions/DRM (the "no further restrictions" clause) — the same conflict that forced **VLC** off the store.
- **Android / Google Play — technically allowed**, but only if your whole app ships as GPLv3 open source (no proprietary components, no GPL-incompatible OSS).
- **Escape hatch, but not a free one:** because JUCE and Tracktion are *dual*-licensed, a funded team *could* buy commercial licenses for both to go closed-source — but (a) that costs money and is **not OSS**, and (b) it does **not** relicense OpenDaw's *own* GPLv3 application code, which has no commercial option. So OpenDaw-the-app remains unusable regardless.

---

## 2. What it uses for audio

Not a hand-written or web engine — OpenDaw is a **thin Qt 6 desktop app wrapped around Tracktion Engine**, which is itself built on **JUCE**. All the heavy audio lifting is delegated:

- **Audio engine:** **Tracktion Engine** — a full desktop DAW engine (transport, mixing, recording, automation, plugin hosting) running its own real-time audio thread independent of the GUI.
- **Underlying framework:** **JUCE** — provides the audio device I/O abstraction, DSP primitives, and plugin-host infrastructure. On desktop this maps to CoreAudio / ASIO / WASAPI / ALSA (all desktop APIs).
- **UI runtime:** **Qt 6 (6.10+)**, native widgets — entirely separate from the audio path.
- **Plugins / effects:** hosts **VST3** instruments and effects; ships ~8 built-in effects (reverb, EQ, compressor, delay, chorus, phaser, low-pass filter, pitch shift) — these come from Tracktion/JUCE, not a novel DSP library.
- **Other bundled assets:** **Bravura** music-notation font (SMuFL, for the piano-roll sheet-music view), Material Design + FontAudio icon fonts.
- **Notable non-audio feature:** an integrated **Claude (Anthropic API) AI assistant** that can drive the DAW via ~30 tool calls (create MIDI clips, add notes, set up routing, etc.). This is an application-level idea, not an audio component.

> Architecturally this is the "buy the engine" approach: OpenDaw contributes UI + workflow + AI glue, while Tracktion/JUCE contribute the DAW. That is the opposite of an embeddable, permissively-licensed audio library.

---

## 3. Mobile viability

### License (hard blocker)
- **iOS:** GPLv3 (app) + AGPLv3 (JUCE) are both incompatible with App Store distribution (see §1). No path for a free/open build.
- **Android:** allowed only if the entire app is released under GPLv3.
- The dual-license "buy commercial" route exists for JUCE/Tracktion but **costs money (not OSS)** and still can't touch OpenDaw's GPLv3 app code.

### Architecture (practical blocker)
- **Desktop-monolith, not a library.** OpenDaw is an application, tightly bound to Tracktion Engine, JUCE, and Qt. There is no cleanly liftable "mixer module."
- **UI is Qt 6** (desktop widgets) — no route to **Compose Multiplatform / SwiftUI**, and irrelevant to a KMP app.
- **No mobile audio backend.** The stack targets desktop CoreAudio/ASIO/WASAPI/ALSA; there is no **Oboe/AAudio** (Android) or **AVAudioEngine/AURemoteIO** (iOS) integration in this project. (JUCE *can* target mobile, but that is a property of JUCE, not of OpenDaw, and would still be an AGPL/commercial C++ framework you'd bridge to — not a KMP-native, permissive engine.)
- **Assumes desktop resources:** VST3 plugin scanning/hosting, CMake/Ninja/Visual Studio build, a real filesystem — heavyweight for a phone.

### Maturity (secondary blocker)
- **~32 stars, ~67 commits, a single author (glenwrhodes).** Repo began ~March 2026 (rebranded *FreeDaw → OpenDaw* on 2026-03-21), with visible commits clustering in March 2026 and little activity since — an early-stage, one-person WIP, not battle-tested software.

---

## 4. What you *can* legally take from it

- **Ideas only.** Reading the source is unrestricted. Two things are worth *studying* (not copying):
  1. The **"thin UI over Tracktion Engine"** integration pattern — a clean example of how much a DAW gets "for free" by adopting a full engine (and, by contrast, exactly the licensing trap we're avoiding).
  2. The **AI-assistant tool schema** — modelling DAW actions (create clip, add notes, route buses) as ~30 discrete tools an LLM can call in a loop. This is a portable *design idea* for our own optional Claude-assistant feature; the code itself is GPLv3.
- **No reusable code or libraries.** OpenDaw's own code is **GPLv3**; its engine (**Tracktion GPLv3**) and framework (**JUCE AGPLv3**) are copyleft or paid-commercial. There is **no permissively-licensed component** here to borrow — no MIT/Apache/BSD/MPL piece to lift.

**Net:** OpenDaw lands in the report's **🚫 reference-only / avoid** tier, alongside Ardour and JUCE/Tracktion themselves — study the integration and AI-tooling ideas, link none of the code. Keep building on the MIT/Apache/MPL engine stack (miniaudio/dr_libs, Oboe, Signalsmith Stretch, pffft/KissFFT; or the Rust CPAL/Symphonia route).

---

## Sources
- OpenDaw source repo — <https://github.com/glenwrhodes/OpenDaw>
- `LICENSE` (GPLv3, verbatim GPL v3 text) — <https://raw.githubusercontent.com/glenwrhodes/OpenDaw/master/LICENSE>
- `README.md` (platforms, C++20/Qt 6, Tracktion Engine + JUCE, license notes) — <https://raw.githubusercontent.com/glenwrhodes/OpenDaw/master/README.md>
- Commit history / maturity — <https://github.com/glenwrhodes/OpenDaw/commits/master>
- Tracktion Engine license (GPLv3-or-later / commercial) — <https://github.com/Tracktion/tracktion_engine/blob/develop/LICENSE.md>
- JUCE license (AGPLv3 / commercial) — <https://github.com/juce-framework/JUCE/blob/master/LICENSE.md>
