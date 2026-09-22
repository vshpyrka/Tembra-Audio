# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Tembra Audio is a free, open-source mobile DAW for Android and iOS: a Kotlin Multiplatform control plane and Compose Multiplatform UI on top of one shared C++ real-time audio engine (Oboe on Android, AVAudioEngine/RemoteIO on iOS).

**There is no application code yet.** `app/` holds only a README with the planned layout. The repository is research, design and reports that feed the spec-kit constitution and the first feature specs. There are no build, lint or test commands until the project-scaffold feature lands; when it does, record them here.

## Workflow: spec-kit drives all feature work

The project uses GitHub spec-kit. It was initialised on 2026-09-18 with `uvx --from git+https://github.com/github/spec-kit.git specify init --here --integration claude --script sh` (the old `--ai` flag is gone; re-run the same command with `--force` to refresh templates). The skills live in `.claude/skills/speckit-*/` and are invoked with hyphens: `/speckit-constitution`, `/speckit-specify`, `/speckit-clarify`, `/speckit-plan`, `/speckit-checklist`, `/speckit-tasks`, `/speckit-analyze`, `/speckit-implement`, `/speckit-converge` (not the older dotted `/speckit.*` form). Helper scripts are in `.specify/scripts/bash/`. `specs/` is created by the first `/speckit-specify`.

- `.specify/memory/constitution.md` is the ratified constitution (v1.0.0, 2026-09-19): seven principles plus stack constraints and quality gates, seeded from the research briefs below and `docs/research/kmp-code-architecture.md`. It supersedes this file where they disagree; amend it only through `/speckit-constitution`.
- `specs/<feature>/spec.md` says WHAT (user-facing behaviour); `plan.md` holds technology choices. Keep them apart.
- The first feature is the project scaffold that creates `app/shared`, `app/engine`, `app/androidApp`, `app/iosApp`.
- Build order after that is in `docs/DAW-App-Research-Report.md` §6: audio spine first (engine + JNI/cinterop bridge playing one clip), then transport/timeline, mixer, metronome, effects + automation, recording + loops + speed, tuner, collaboration last.

## Architecture rules that every spec and plan must respect

These come from `docs/research/` and are the reason the research exists; treat them as settled.

1. **Nothing Kotlin runs on the audio callback.** Kotlin/JVM and Kotlin/Native are garbage collected. The C++ engine owns the real-time thread; Kotlin only produces commands. The two sides talk exclusively through lock-free SPSC FIFOs and atomics. No allocation, locks, I/O or unbounded work inside the callback.
2. **One shared C++ engine, not per-platform DSP.** Exposed to Kotlin through an `expect class AudioEngine` facade with JNI (Android) and cinterop (iOS) actuals. Platform code is a thin I/O shell that must accept a variable frame count per callback at 48 kHz.
3. **Everything musical is stored in beats** (clip starts, automation points, loop positions) and converted to samples through the tempo map at render time.
4. **Permissive licenses only in the shipped binary.** Allowed: Oboe, miniaudio, dr_libs, Signalsmith Stretch, STK, Maximilian, KissFFT/pffft, MWEngine as a pattern. Never link JUCE, Tracktion, Rubber Band, KFR, Superpowered, élastique. LGPL (SoundTouch, libsndfile, FFmpeg) only if unavoidable and dynamically linked. Full table: `docs/research/kmp-architecture-tools.md` §5.

Detailed mechanics for each rule: `docs/research/daw-internals.md` (graph, pan law, PDC, automation smoothing, transport), `docs/research/metronome-design.md`, `docs/research/tuner-algorithms.md`.

**Kotlin-side code architecture** (module graph, presentation pattern, navigation, DI, persistence, testing) is decided in `docs/research/kmp-code-architecture.md`: four shared Gradle modules (`:shared:core`, `:shared:engine`, `:shared:data`, `:shared:ui`) plus thin apps; MVVM with multiplatform `ViewModel` + `StateFlow`; 60–120 Hz telemetry read in the draw phase, never through `StateFlow`; Jetpack Navigation 3 multiplatform with Material 3 adaptive; Metro for DI; Room KMP + DataStore + kotlinx.serialization JSON project files; `kotlin.test` + Turbine + `runComposeUiTest`, C++ tests in Catch2. Plans must follow it or amend it there first. Evidence: `docs/research/kmp-reference-repos.md` (what JetBrains and Google actually ship) and `docs/research/notebooklm-kmp-architecture-qa.md`.

## Sources of truth and where they conflict

- **UI look and feel:** `design/stitch/DESIGN.md` and `design/stitch/theme.json`. The per-screen HTML under `design/stitch/screens/` drifts (six screens carry no Tailwind config, the drum-sequencer phone screen uses its own palette); when they disagree, DESIGN.md and theme.json win. Build notes for turning tokens into a Compose theme, fonts, icons and the navigation model are in `design/stitch/README.md`.
- **Navigation labels:** canonical primary destinations are Timeline, Tuner, Effects, Pads, Projects. The tablet timeline screen's "Tracks/FX" labels are a known inconsistency, not a decision.
- **Product name** is "Tembra Audio". The Stitch design system was renamed from "Tembra Pro Audio" on 2026-09-17; do not reintroduce the old name.
- **Competitor analysis:** `docs/reports/competitor-native-audio-analysis.md` is the write-up; `handoff-competitor-analysis.md` is session notes only. Both are complete. Do not re-derive them.
- **Research report vs briefs:** `docs/DAW-App-Research-Report.md` is the consolidated summary; the per-topic briefs in `docs/research/` carry the detail and win on specifics.
- `docs/research/notebooklm-deep-report.md` is in Ukrainian; `notebooklm-deep-report-en.md` is the English version.

## Things that bite

- `reference/competitor-apps/` is about 720 MB of pulled APKs and extracted `.so` files, git-ignored and not present on a fresh clone. It contains over 8,000 files; scope any repository-wide search or listing to exclude it.
- Diagrams in `docs/diagrams/` have an editable `.excalidraw` beside each `.png`. Edit the source and re-render; do not edit the PNG alone.
- To refresh the Stitch export after design edits, follow the re-pull steps in `design/stitch/README.md` (Stitch project `12040171611712589599`, MCP server `stitch`). Screenshot URLs need the `=s0` suffix or you get 512px thumbnails.
- The NotebookLM notebooks behind the research are listed in `docs/README.md` and can be queried with the `notebooklm` CLI.
