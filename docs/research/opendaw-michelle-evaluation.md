# openDAW — Open-Source DAW Evaluation

**Question:** Can openDAW (André Michelle's browser DAW) be used as a foundation — engine, mixer, or reusable library — for a free, cross-platform (Android + iOS) mobile DAW built with Kotlin Multiplatform?

**Verdict:** **No.** openDAW is a modern, well-engineered project and a great *reference* for a reactive audio-graph design, but it is unusable as a foundation on two independent counts: its **AGPL v3 (or later) license** (network copyleft — even worse than GPL for the App Store, and the paid "commercial license" alternative is not free/OSS) and its **browser-only architecture** (a TypeScript/Sass DOM app whose audio runs through the **Web Audio API / AudioWorklet**, with a Rust DSP engine compiled to `wasm32-unknown-unknown` for the *browser* — not a native, embeddable mobile library, and with no Oboe/AAudio or CoreAudio path). This *confirms* the report's standing guidance: avoid GPL/AGPL, and a Web-Audio-only design does not run natively on mobile.

- Repo: <https://github.com/andremichelle/openDAW>
- Site: <https://opendaw.org/>
- Verified against primary sources on 2026-08-27.

---

## 1. License — AGPL v3 (or later), dual-licensed with a paid commercial option

openDAW states its license **repeatedly and prominently** as **AGPL v3 (or later), © 2025 André Michelle**:

- The README badge links to <https://www.gnu.org/licenses/agpl-3.0.html> ("License: AGPLv3").
- The "Open-Source" section: *"The source code for openDAW is available under **AGPL v3 (or later)**."*
- The "License" section: *"[AGPL v3 (or later)](https://www.gnu.org/licenses/agpl-3.0.txt) © 2025 André Michelle."*
- A **Dual-Licensing Model** table offers **(A) AGPL v3 (or later)** — *"the entire work that includes openDAW … released under AGPL-compatible open-source terms … Must distribute complete corresponding source code under AGPL … Applies both to distribution **and** to public use via network/SaaS (§13)"* — or **(B) a paid Commercial Licence** for *"closed-source or otherwise licence-incompatible software or SaaS offerings."*

**Caveat on verification (be skeptical here):** there is **no root `LICENSE`/`COPYING` file** in the repo, and GitHub's own license auto-detector returns **None** (the API `license` field is null). The only file under `licenses/` is `signalsmith-stretch-MIT.txt` — an upstream attribution, not openDAW's own grant. Worse, the root **`package.json` declares `"license": "LGPL-3.0-or-later"`**, which *contradicts* the README's AGPL. The AGPL statement is far more prominent, dated, and backed by the dual-licensing model, so **AGPL v3-or-later is clearly the intended license**; the `package.json` LGPL field looks like stale metadata. Either reading is copyleft — AGPL is 🚫, and even the LGPL fallback would be ⚠️ (dynamic-link only, awkward on iOS).

**Consequences for a would-be reuser:**
- Linking any openDAW code into an app makes the **entire app AGPLv3** — you must publish *all* your source, and the **§13 network clause** extends copyleft to SaaS/network use, not just distribution.
- **iOS / App Store — effectively impossible.** AGPL inherits GPL's "no further restrictions" clause, which conflicts with the App Store's DRM/usage terms — the same conflict that pulled **VLC** from the store. AGPL is *stricter* than GPL, so the block is at least as hard.
- **Android / Google Play — technically allowed**, but only if your **whole app ships as AGPLv3 open source** (no proprietary components, no AGPL-incompatible OSS).
- The **Commercial Licence is not a way out**: it is a paid, closed-source arrangement — the opposite of "free and open-source," so it defeats the project's own goal. And even it carves out third-party code: some **Ported Plugins are "Excluded in commercial license"** (e.g. the **Compressor**, ported from **[CTAGDRC](https://github.com/p-hlp/CTAGDRC)**, which is **GPLv3**), because that code isn't André's to relicense. Subcomponents therefore carry **mixed, heavier copyleft** on top of the AGPL base.

---

## 2. What openDAW uses for audio

A **web application**: **TypeScript + Sass** front-end (DOM UI, minimal dependencies), a **Lerna + Turbo monorepo** with `packages/**` (TypeScript: `app`, `studio`, `lib`, `server`, `config`) and `crates/**` (Rust → WebAssembly). Build tooling is **Vite**; dev needs **Node ≥ 23** and **Rust ≥ 1.82 nightly** with the **`wasm32-unknown-unknown`** target.

**Audio runtime / I/O backend:**
- The app runs entirely **in the browser** ("Kostenlose Open-Source-DAW im Browser"). Audio is produced through the **Web Audio API** and an **AudioWorklet**; there is **no direct hardware backend** — the browser owns I/O. On mobile this would fall back to the *mobile browser's* Web Audio (with iOS Safari's background-audio and latency limits). The app never touches **Oboe/AAudio** (Android) or **AVAudioEngine/CoreAudio** (iOS).
- The DSP is being rewritten in **Rust compiled to WebAssembly** — package **`@opendaw/studio-core-wasm`** (`packages/studio/core-wasm`), built for `wasm32-unknown-unknown` and size-optimised with **`wasm-opt`/[binaryen](https://github.com/WebAssembly/binaryen)**. Roadmap item **"WASM Audio-Engine — 2026/Q2."**

**Signal graph / mixing model (openDAW's own design, not a third-party framework):**
- A reactive **"boxes" graph** — Rust crates **`boxgraph`**, **`studio-boxes`**, **`value`** — with **`engine`** / **`engine-env`** driving processing and **`transport`** the timeline.
- Shared DSP in **`dsp`** / **`math`** / **`processors`** / **`voicing`**; time/pitch stretching in **`stretch`** / **`stretch-wasm`** and the **`signalsmith`** crate (see below).
- ~30 **stock devices**, each its **own `cdylib` WASM crate** under `crates/stock-devices/*`: reverbs (**Dattorro**, convolver, revamp), dynamics (**compressor** [CTAGDRC/GPLv3], gate, maximizer), distortion (waveshaper, crusher, fold), **autotune/pitch**, delay, stereo-tool, vocoder, **neural-amp** (Neural Amp Modeler / Tone3000), synths (**Neon**, **Vaporisateur**, Cubed, Nano, Sine), samplers (**Playfield**, **SoundFont**), and JS-scripted devices (**Apparat**, **Werkstatt**).

**Third-party libraries it uses** (JS/web, mostly MIT — *not* openDAW's to relicense, and not mobile-relevant): `jszip`, `markdown-it`, `d3-force`, `soundfont2`, `zod`, and `ffmpeg.wasm` (decode/encode). The one notable **audio** library is **Signalsmith Stretch** — see §4.

> Architecturally this is a clean, contemporary take on a DAW: a reactive node/box graph, per-device isolated WASM modules, and a pure-Rust `no_std` DSP core. Worth *reading* for those ideas — but it is a browser program, not an engine you can link.

---

## 3. Mobile viability

### License (hard blocker)
- **iOS:** AGPLv3 is incompatible with App Store distribution (§1) — the VLC precedent, only stricter. No path.
- **Android:** allowed only if the **entire app** is released under AGPLv3.
- The **paid Commercial Licence** is not OSS and not free, so it fails the "free, open-source" requirement outright — and still excludes third-party copyleft plugins (CTAGDRC).

### Architecture (practical blocker)
- **Browser-only.** openDAW is a **DOM/TypeScript/Sass** web app. There is **no native mobile UI** and no route to **Compose/SwiftUI**.
- **Audio is Web Audio / AudioWorklet.** The Rust DSP compiles to **`wasm32-unknown-unknown`** to run *inside the browser worklet* — not a native ARM library for Android/iOS. It provides **no Oboe/AAudio or CoreAudio backend**.
- The only "native" story is **wrapping the web app in [Tauri](https://tauri.app/) (desktop)** or shipping a **PWA** — both are the *same browser app in a shell*. A mobile PWA still runs on the device browser's Web Audio, subject to iOS restrictions. Neither is a **KMP-embeddable native engine**.
- *Silver lining, then withdrawn:* the DSP is **pure Rust**, so in principle it could be retargeted to native `aarch64`. But it is **AGPL openDAW code**, so it is off-limits regardless of technical portability.

---

## 4. What you *can* legally take from it

- **Ideas & architecture** — reading the source is unrestricted. The **reactive "boxes" graph**, the **one-WASM-module-per-device** isolation, and the **pure-Rust `no_std` + `alloc` DSP** approach are all worth *studying* (not copying) for a KMP engine.
- **Signalsmith Stretch — MIT — the one genuinely reusable audio piece,** but take it from **upstream, not from openDAW.** openDAW bundles a **pure-Rust port** of it (crate `signalsmith`: *"a clean, self-contained pure-Rust port of Signalsmith Stretch (MIT) … `no_std` + `alloc` … its own FFT"*), and includes the upstream `licenses/signalsmith-stretch-MIT.txt`. But that port ships **inside the AGPL repo (© André Michelle)**; the MIT file is upstream *attribution*, not a separate MIT grant on his port. So use the **original Signalsmith Stretch from Signalsmith Audio (Geraint Luff), MIT** — which the report's recommended stack **already lists**.
- **Avoid taking any openDAW crate/package** (all AGPL), and specifically avoid the **CTAGDRC-derived Compressor (GPLv3)**.

**Net:** openDAW belongs in the report's **🚫 reference-only** tier alongside Ardour/JUCE — study it, don't link it. Keep building on the MIT/Apache/MPL engine stack (miniaudio/dr_libs, Oboe, **Signalsmith Stretch**, pffft/KissFFT; or the Rust CPAL/Symphonia alternative).

---

## Sources
- openDAW source repo — <https://github.com/andremichelle/openDAW> and its `crates/` and `packages/` trees
- README (AGPL badge, "Open-Source", "Dual-Licensing Model", "License", roadmap, dependencies) — <https://raw.githubusercontent.com/andremichelle/openDAW/main/README.md>
- Root `package.json` (`"license": "LGPL-3.0-or-later"` — contradicts README) — <https://github.com/andremichelle/openDAW/blob/main/package.json>
- GitHub license auto-detector returns None (no root LICENSE) — API `repos/andremichelle/openDAW` `license` field is null
- Bundled upstream license — <https://github.com/andremichelle/openDAW/blob/main/licenses/signalsmith-stretch-MIT.txt>
- Rust WASM workspace — <https://github.com/andremichelle/openDAW/blob/main/crates/Cargo.toml> and `crates/signalsmith/Cargo.toml`
- Ported Compressor upstream (GPLv3) — <https://github.com/p-hlp/CTAGDRC>
- AGPL v3 text — <https://www.gnu.org/licenses/agpl-3.0.txt>
- Project site — <https://opendaw.org/>
