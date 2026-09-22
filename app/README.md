# app

Application source code. Intentionally empty until the first spec-kit feature (project scaffold) is implemented.

Planned layout, decided in `docs/research/kmp-code-architecture.md` (2026-09-17) on top of `docs/research/kmp-architecture-tools.md`:

```
app/                       Gradle root
├── build-logic/           convention plugins (tembra.kmp.library, tembra.kmp.compose, tembra.android.app)
├── engine/                shared C++ real-time engine, CMake, C ABI in include/tembra_engine.h, Catch2 tests
│   └── android/           :engine:android — com.android.library + CMake → libtembra_engine.so + JNI glue
├── shared/
│   ├── core/              :shared:core   — project model in beats, tempo map, ProjectEditor, undo history, JSON schema
│   ├── engine/            :shared:engine — expect/actual AudioEngine (JNI / cinterop), commands, telemetry, EngineSync
│   ├── data/              :shared:data   — Room KMP index, DataStore preferences, project folders (Okio)
│   └── ui/                :shared:ui     — Compose Multiplatform screens, ViewModels, Navigation 3, Metro graph,
│                                           iOS framework "TembraShared" exposing MainViewController()
├── androidApp/            :androidApp — Activity and Application only
└── iosApp/                Xcode project: Swift launcher that embeds TembraShared.framework
```

Dependency direction: apps → `:shared:ui` → `:shared:engine` / `:shared:data` → `:shared:core`. Nothing Kotlin runs on the audio callback; Kotlin pushes commands and reads telemetry through the C ABI only.
