# Maolan — Open-Source DAW Evaluation

**Question:** Can Maolan (Goran Mekić's open-source DAW) be used as a foundation — engine, mixer, or reusable library — for a free, cross-platform (Android + iOS) mobile DAW built with Kotlin Multiplatform?

**Verdict:** **Reusable parts, not a foundation.** Maolan is the *opposite* problem to Ardour: its license is **perfect** (**BSD 2-Clause** — permissive, ship-safe on both stores) and it is written in **Rust** with a *headless* `maolan-engine` crate split out from the UI. But it is still a **desktop application** — a `ratatui` terminal UI plus an emerging `iced` (winit/wgpu) desktop GPU GUI, wired to **desktop-only audio backends (JACK / ALSA / Windows)** with **no mobile backend and no mobile UI**, and the engine crate is **young** (first published April 2026). You cannot lift the app onto a phone, but because everything is Rust + permissive you *can* legally borrow the engine's design and directly reuse the permissive crates it is built from — which is exactly the Rust stack the main report already recommends.

- Repo (picked): <https://github.com/maolan/maolan> — "Maolan DAW", the primary DAW app in the `maolan` GitHub org
- Engine sub-repo: <https://github.com/maolan/engine> — `maolan-engine`, the headless audio engine
- Site: <https://maolan.rs>
- Verified against primary sources on 2026-08-27.

> **Which repo & why:** the `maolan` org hosts ~10 repos (`maolan`, `engine`, `plugins`, `editor`, `widgets`, `mixosc`, `generate`, `devops`, `doc`). `maolan/maolan` is the DAW application itself (133★); it is a Cargo workspace that pulls the rest in as crates. Note the task's C/C++ hint is stale — GitHub's language stats report the tree as **100 % Rust** (`{"Rust":3923448,"Shell":27144,"PowerShell":9809,"NSIS":4157}`); the repo (created 2019) has been rewritten in Rust (edition 2024).

---

## 1. License — BSD 2-Clause "Simplified" (permissive) ✅

- The repo's `LICENSE` file is the **BSD 2-Clause License**, © **Goran Mekić, 2020**. GitHub's own license detection reports **`spdx_id: BSD-2-Clause`**, and every workspace crate declares `license = "BSD-2-Clause"` in its `Cargo.toml` (`maolan` 0.2.6, `maolan-engine` 0.2.64, `maolan-widgets` 0.0.36). This is a **real, verified license file** — not a missing/"all rights reserved" situation.
- **This is the best-case tier.** BSD-2 imposes only attribution (retain the copyright notice + disclaimer). You may fork, embed, modify, and ship it inside a **closed-source** app on **both** the Apple App Store and Google Play with **no copyleft obligation**.
- **Contrast with Ardour:** Ardour's GPLv2 killed App Store distribution and forced the whole app open. Maolan has *neither* problem. On licensing alone, Maolan's code is genuinely take-able.

---

## 2. What Maolan uses for audio

Maolan is a **Cargo workspace** that cleanly separates a **headless engine crate** (`maolan-engine`) from its frontends (`maolan-widgets` = iced GUI, `maolan-editor`, the `ratatui` TUI in the main binary, and an out-of-process `plugin-host`). This is architecturally *much* closer to "an embeddable library" than Ardour's monolith — the engine has no UI dependency.

**Audio I/O backends** (all conditionally compiled, all desktop):
- **JACK** (`jack` 0.13, Unix) and **ALSA** (`alsa` 0.11, Linux) — the primary Linux/Unix path.
- **Windows** native audio via the `windows` 0.62 crate; **`midir`** for Windows MIDI I/O; **`nvtree`** for FreeBSD.
- The main app additionally pulls **`cpal` 0.17** — but only on **Windows**.
- **No CoreAudio / macOS backend, no Oboe/AAudio, no iOS/AVAudioEngine** — there is no mobile (or even macOS) audio path in the tree today.

**Real-time engine infrastructure** (the genuinely valuable part):
- **`rtrb`** — lock-free SPSC ring buffer for RT audio, **`basedrop`** — RT-safe memory reclamation (audio-thread-safe drop), **`arc-swap`**, `num_cpus`, `tokio`. This is a textbook lock-free / RT-safe Rust audio core.

**Codecs & DSP** (third-party, permissive):
- **`symphonia`** 0.5.4 (**MPL-2.0**) — pure-Rust decoders (AAC/ALAC/FLAC/MP4/MP3/OGG/Vorbis/WAV); plus Maolan's own **`oxideav-*`** crates (core/basic/flac/mp3/ogg) for encode/decode.
- **`rubato`** (MIT) — resampling; **`timestretch`** (MIT) — time-stretch; **`ebur128`** (MIT) — loudness (EBU R128) metering; **`pitch-detection`** — pitch tracking.
- **`midly`** (Unlicense/public-domain) — MIDI parsing.

**Plugin hosting:** an out-of-process host (`maolan-plugin-host` + `maolan-plugin-protocol`) supporting **CLAP, VST3, LV2** on Unix and **CLAP, VST3** on Windows. Export targets: WAV, MP3, OGG, FLAC.

> Unlike Ardour, none of these DSP dependencies are GPL — they are MIT / Apache-2.0 / MPL-2.0 / public-domain, i.e. the report's ✅ permissive tier. The plugin-hosting layer, however, is desktop-shared-library territory and is irrelevant on mobile (no dylib plugin ecosystem on iOS; App Store sandboxing forbids it).

---

## 3. Mobile viability

### License (no blocker) ✅
- **iOS + Android:** BSD-2-Clause is fully compatible with both stores and with a closed-source shell. This is the one axis where Maolan strictly beats every GPL DAW.

### Architecture (the real blockers) ⚠️
- **Desktop-only UI, twice over.** The shipping frontend is `iced` (winit + wgpu, a *desktop* GPU GUI — timeline, piano roll, mixer per <https://maolan.rs>), and the main binary still carries a `ratatui`/`crossterm` **terminal UI**. Neither maps to **Compose Multiplatform / SwiftUI**, which a KMP mobile app needs. `iced` has only experimental mobile support and is not a KMP UI layer.
- **No mobile audio backend exists.** The engine hard-wires JACK/ALSA/Windows via `cfg`. To run on a phone you would have to write an **Oboe/AAudio** (Android) and **CoreAudio/AVAudioEngine** (iOS) backend from scratch — the same gap Ardour has, just in Rust.
- **Immature and moving.** `maolan-engine` was first published **2026-04-25** (4★, versions still 0.0.x–0.2.x); the app README warns "under active development — behavior and UI details may evolve between commits." Not a stable base to build a product on yet.
- **Platform scope is desktop:** the site lists **Linux, FreeBSD, Windows** only — Android, iOS, and mobile are never mentioned.

### Net
The *app* is **REJECTED** as a drop-in foundation (desktop UI + desktop backends + young). But because it is **Rust + BSD-2**, a KMP mobile DAW built on a **Rust core** (exposed to Compose/SwiftUI via UniFFI / JNI / cinterop) could **fork the engine's design and reuse its permissive building blocks** — a real, legal head start, which is why this lands at **REUSABLE-PARTS**.

---

## 4. What you *can* legally take from it

- **All Maolan-authored code is BSD-2-Clause** — `maolan-engine`, `widgets`, `editor`, `plugin-host`, `mixosc`, `oxideav-*`. You may copy, fork, embed (even into a closed app), and ship on both stores, needing only attribution. This is the rare OSS DAW whose *own* code you can actually take.
- **Engine design worth studying (and partly lifting):** the headless engine crate, the out-of-process plugin host, and especially the **`rtrb` + `basedrop` RT-safe pattern** are a clean, modern reference for the report's Rust-core option.
- **The permissive crates it composes drop straight into your own Rust core** — all already in the report's ✅ permissive tier:
  - **`cpal`** — Apache-2.0 — cross-platform audio I/O that *does* cover **Android (Oboe)** and **iOS (CoreAudio)**; this is the crate you'd actually base a mobile backend on (Maolan only uses it on Windows).
  - **`symphonia`** — MPL-2.0 (file-level copyleft, ship-safe) — pure-Rust, mobile-friendly decoders.
  - **`rubato`** — MIT — SIMD resampling. **`ebur128`** — MIT — loudness metering. **`timestretch`** — MIT — time-stretch.
  - **`midly`** — Unlicense (public domain) — MIDI parsing, `no_std`-friendly.
  - **`rtrb`** — MIT OR Apache-2.0 — lock-free SPSC ring buffer. **`basedrop`** — MIT/Apache-2.0 — RT-safe memory reclamation.

**Net:** Maolan belongs in the report's **✅ permissive / reference-and-parts** tier — a Rust, BSD-2 DAW you may legally borrow from, unlike GPL Ardour/JUCE. Treat it as a design reference and a curated list of vetted permissive crates; you still supply the mobile audio backends (Oboe / CoreAudio) and the Compose/SwiftUI UI yourself. This lines up exactly with the report's recommended Rust alternative stack (CPAL / Symphonia / rtrb / basedrop).

---

## Sources
- Maolan DAW repo — <https://github.com/maolan/maolan>
- `maolan-engine` repo — <https://github.com/maolan/engine>
- `LICENSE` (BSD-2-Clause, © Goran Mekić 2020) — <https://raw.githubusercontent.com/maolan/maolan/main/LICENSE>
- App `Cargo.toml` (ratatui/crossterm UI, `license = "BSD-2-Clause"`) — <https://raw.githubusercontent.com/maolan/maolan/main/Cargo.toml>
- Engine `Cargo.toml` (jack/alsa/windows I/O, rtrb/basedrop, symphonia) — <https://raw.githubusercontent.com/maolan/engine/main/Cargo.toml>
- Widgets `Cargo.toml` (iced GUI) — <https://raw.githubusercontent.com/maolan/widgets/main/Cargo.toml>
- Project site (desktop-only: Linux/FreeBSD/Windows) — <https://maolan.rs>
- License spot-checks (crates.io): [symphonia MPL-2.0](https://crates.io/crates/symphonia), [cpal Apache-2.0](https://crates.io/crates/cpal), [rubato MIT](https://crates.io/crates/rubato), [ebur128 MIT](https://crates.io/crates/ebur128), [timestretch MIT](https://crates.io/crates/timestretch), [rtrb MIT/Apache-2.0](https://crates.io/crates/rtrb), [basedrop MIT/Apache-2.0](https://crates.io/crates/basedrop), [midly Unlicense](https://crates.io/crates/midly)
