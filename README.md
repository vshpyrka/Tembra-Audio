# Tembra Audio

A free, open-source mobile DAW for Android and iOS.
Shared Kotlin Multiplatform control plane and Compose Multiplatform UI on top of a shared C++ real-time audio engine.

## Repository layout

| Folder | What lives here |
|--------|-----------------|
| `app/` | Application source code (KMP project, native engine, platform shells). Empty until the scaffold feature is implemented. |
| `design/` | Brand assets, original mockups, and the Stitch design export (design system tokens + 16 screens) under `design/stitch/`. |
| `docs/` | Wiki: research, reports, diagrams. Start at `docs/README.md`. |
| `reference/` | Third-party binaries used for analysis (competitor APKs and extracted `.so` files). Git-ignored. |
| `specs/` | Feature specifications produced by spec-kit (`/speckit.specify`). Created on first use. |
| `.specify/` | Spec-kit templates, scripts and the project constitution. Created by `specify init`. |

## Where to start

1. Read `docs/README.md` for the research summary and architecture decisions.
2. Read `.specify/memory/constitution.md` for the non-negotiable engineering rules (once created).
3. Look in `specs/` for the feature currently in progress.
