# Tembra Audio wiki

Everything here was produced before any code existed. It is the input to the spec-kit constitution and the first feature specs.

## Start here

- **[DAW-App-Research-Report.md](DAW-App-Research-Report.md)** — the consolidated research report. Also available as a styled page: `DAW-App-Research-Report.html`.
- **[reports/competitor-native-audio-analysis.md](reports/competitor-native-audio-analysis.md)** — how BandLab, n-Track Studio and Remixlive do audio in native code (engines, I/O, DSP, licenses).

## Research briefs (`research/`)

| File | Topic |
|------|-------|
| [kmp-architecture-tools.md](research/kmp-architecture-tools.md) | Shared Kotlin vs native split, engine integration options, low-latency I/O per platform, license-safe library stack |
| [kmp-code-architecture.md](research/kmp-code-architecture.md) | **Decision:** source code architecture of the Kotlin side (module graph, MVVM + telemetry bypass, Navigation 3, Metro DI, Room/DataStore/JSON persistence, testing). Runner-ups and why they lost |
| [kmp-reference-repos.md](research/kmp-reference-repos.md) | Module layouts and library choices actually shipped by kotlinconf-app, Fruitties and the two JetBrains KMP templates (inspected from source, 2026-09-17) |
| [daw-internals.md](research/daw-internals.md) | How DAW engines work: real-time thread rules, processing graph, parameter smoothing, transport |
| [metronome-design.md](research/metronome-design.md) | Drift-free click scheduling and real-time data structures |
| [tuner-algorithms.md](research/tuner-algorithms.md) | Pitch detection (MPM, YIN) and the tuner pipeline |
| [tuner-noise-gate.md](research/tuner-noise-gate.md) | Whether a user-adjustable noise gate fits the built tuner: the pipeline's fixed gates, where a level gate would live, the rejected Stitch control and the spec-kit constraints (2026-09-29, written against the built feature 003) |
| [performance-profiling.md](research/performance-profiling.md) | **Decision input for a performance pipeline (2026-10-06):** trace-pulse (Macrobenchmark + Perfetto, step-fit detection) stays on Android and moves into the repo as a module; iOS joins through Apple's stock `xcodebuild test` + `XCTMetric` + `xcresulttool get test-results metrics` (per-iteration JSON, verified on Xcode 26.6), `xctrace` for deep dives and the audio thread; one tracing facade (ATrace / `os_signpost`) in Kotlin and C++; where every tool runs (dev, simulator CI, USB device, farm, field); two CI workflows (second Mac with USB devices; a small farm set, noting Firebase Test Lab's 2027 shutdown); engine-owned audio-thread counters; spikes and open questions |
| [profiling-web-findings.md](research/profiling-web-findings.md) | Primary-source findings behind `performance-profiling.md` with URLs and document dates (Apple XCTest/xctrace/MetricKit, Google Macrobenchmark/Perfetto/Oboe, Kotlin/Native and Compose Multiplatform internals, device farms, Maestro, precedents such as Flutter DeviceLab); raw, dated 2026-10-06 |
| [notebooklm-profiling-report.md](research/notebooklm-profiling-report.md), [notebooklm-profiling-qa.md](research/notebooklm-profiling-qa.md) | NotebookLM deep-research report and eight Q&A answers behind `performance-profiling.md` (raw output; a few claims corrected there) |
| [ardour-evaluation.md](research/ardour-evaluation.md), [audacity-evaluation.md](research/audacity-evaluation.md), [lmms-evaluation.md](research/lmms-evaluation.md), [maolan-evaluation.md](research/maolan-evaluation.md), [opendaw-michelle-evaluation.md](research/opendaw-michelle-evaluation.md), [opendaw-rhodes-evaluation.md](research/opendaw-rhodes-evaluation.md) | Open-source DAWs evaluated for fork-vs-build and license fit |
| [notebooklm-synthesis.md](research/notebooklm-synthesis.md), [notebooklm-deep-report-en.md](research/notebooklm-deep-report-en.md), [notebooklm-deep-report.md](research/notebooklm-deep-report.md) (Ukrainian), `notebooklm-mindmap.json` | NotebookLM cross-check of the research |
| [notebooklm-kmp-architecture-qa.md](research/notebooklm-kmp-architecture-qa.md), [notebooklm-kmp-architecture-report-en.md](research/notebooklm-kmp-architecture-report-en.md), [notebooklm-kmp-architecture-report.md](research/notebooklm-kmp-architecture-report.md) (Ukrainian) | NotebookLM research behind `kmp-code-architecture.md`: nine Q&A comparisons and the generated briefing report (raw output; version numbers in it are stale) |

## Diagrams (`diagrams/`)

Editable `.excalidraw` source plus rendered `.png` for each:

1. `01-architecture` — three-band KMP stack (Kotlin control plane, engine facade, C++ real-time engine)
2. `02-signal-graph` — track signal flow and mixer buses
3. `03-tuner-pipeline` — mic to pitch readout
4. `04-metronome` — drift-free scheduling model

## Reports (`reports/`)

- `competitor-native-audio-analysis.md` — the competitor binary analysis (source of truth).
- `handoff-competitor-analysis.md` — session handoff notes for that analysis (device, tooling, method).

## External sources

Four Google NotebookLM notebooks hold the research (Google account of the project owner):

| Notebook | Link | Contents |
|----------|------|----------|
| **DAW App Research — Audio, Mixing, Tuner, Metronome (KMP)** | https://notebook.google.com/notebook/6894967d-29c5-4dac-8c9a-1f0e94881190 | 6 sources: the competitor report, the handoff and the four UI mockups. Produced `research/notebooklm-synthesis.md`. |
| **DAW Deep Research — Mobile DAW / Tuner / Metronome (v2)** | https://notebook.google.com/notebook/9f3644d4-a948-4cde-a956-9fe95d7823f7 | 84 sources: the same six plus 78 imported web pages and PDFs on Oboe, AVAudioEngine, pitch detection (MPM, YIN, SwiftF0), Signalsmith Stretch, panning laws, tempo maps, UniFFI and KMP. Produced `research/notebooklm-deep-report*.md` and `notebooklm-mindmap.json`. |

| **KMP Source Code Architecture — Mobile DAW (Android + iOS)** | https://notebook.google.com/notebook/6c22014f-6f99-4f7d-a0d7-aaab57fb6d29 | 92 sources (2026-09-17): a deep web-research run on KMP app architecture plus hand-added JetBrains and Google docs (project structure, Navigation 3, adaptive layouts, cinterop, Swift export, Compose testing), library docs (Decompose, MVIKotlin, Orbit, Circuit, Molecule, Voyager, Koin, kotlin-inject, Metro, SQLDelight, Room KMP, DataStore, Okio, kotlinx-io, Turbine, SKIE), the four reference repos and `research/kmp-reference-repos.md`. Produced `research/notebooklm-kmp-architecture-*.md`. |

| **KMP App Profiling — Android (trace-pulse) + iOS (XCTest/xctrace) for Tembra Audio** | https://notebook.google.com/notebook/5cecd7ef-85e6-485e-a26d-b38922cb65c5 | 73 sources (2026-10-06): a deep web-research run on profiling KMP apps plus hand-added Apple (XCTest metrics, xctrace, xcresulttool, MetricKit, Device Hub), Google (Macrobenchmark, Perfetto, simpleperf, Oboe), Kotlin/Native and Compose Multiplatform docs, Firebase Test Lab / AWS Device Farm / Maestro / Marathon / GitHub runner docs, the trace-pulse SPEC and synthesis, and this project's constitution and architecture briefs. Produced `research/notebooklm-profiling-{report,qa}.md` and fed `research/performance-profiling.md`. |

All four can be queried from the terminal with the `notebooklm` CLI, for example `notebooklm ask "..." -n 9f3644d4-a948-4cde-a956-9fe95d7823f7`.

## Related

- App UI design from Stitch (design system, 16 screens): `../design/stitch/`
- Original UI mockups: `../design/mockups/`
- Brand assets: `../design/brand/`
