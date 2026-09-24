# Specification Quality Checklist: Project Scaffold

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-19
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Validation iteration 1 (2026-09-19): two vocabulary leaks found and fixed before sign-off —
  "Android/JVM side" became "Android side" (FR-007, User Story 2) and "Material scheme roles"
  became "base scheme roles" (Key Entities). Remaining named items are inputs, not
  implementation: the design source files (`design/stitch/DESIGN.md`, `theme.json`), the
  constitution, and the locally installed `android` CLI the user asked to use for screenshots.
- The feature's users are contributors, so "user value" is read as contributor value: a
  codebase that builds, tests and renders the design correctly on both platforms.
- Zero [NEEDS CLARIFICATION] markers: every open point had a reasonable default, recorded in
  the Assumptions section (minimum OS versions, dark theme only, simulator-only iOS tests,
  manual screenshot review, CI deferred, navigation shell deferred).
- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`

## Implementation evidence (2026-09-24, feature 001)

- SC-002: full run of all four lanes (`checkAndroidHost checkIos checkAndroidDevice engineHostTest`) on a
  warm cache: `BUILD SUCCESSFUL in 37s`; first cold run of each lane stayed under 3 minutes. Seeded failure
  (T038): changing one expected value in `SemanticVersionTest` produced `5 tests completed, 1 failed` on
  both the JVM host and the iOS simulator; reverted.
- Test counts: host 19 (core 5, engine 5, ui 9); iOS simulator 30 (core 5, data 2, engine 8, ui 15);
  Pixel 6 Pro (Android 17) 30 (core 5, data 2, engine 8, ui 15); C++ Catch2 5. Zero compiler warnings in
  every lane (`--warning-mode all`).
- SC-003 screenshots reviewed against `design/stitch/theme.json`: `screenshots/android-compact.png`,
  `android-wide.png` (landscape, 891 dp, centred 720 dp column), `ios-compact.png` (iPhone 17),
  `ios-wide.png` (iPad Pro 13-inch). No overlapping or clipped elements. Notes: the Pixel screenshots
  show a Material tooltip at launch because the device had been left out of touch mode by the keyboard
  test (focus-triggered tooltip; a tap dismisses it); the iPad screenshot carries a simulator system
  notification banner. 200 % font scale (Android) and the largest accessibility text size (iOS) reflow
  without clipping; the button stays reachable.
- SC-004: `ThemeJsonParityTest` compares all 47 scheme roles, 18 DAW colours, 12 text styles, radii,
  spacing and touch target with `theme.json` and passes; the literal-value grep over `:shared:ui`
  screens outside `audio.tembra.ui.theme` returns nothing.
- SC-005: `NativeAudioEngineTest` passes on the Pixel (descriptor
  `Tembra Engine 0.1.0 (oboe 1.11.0; miniaudio 0.11.25; dr_wav 0.14.5; signalsmith-stretch 1.1.0)`)
  and on the iOS simulator (same without the oboe entry); both captions visible in the screenshots.
- FR-006 no-audio checks (T050): no `<uses-permission>` in the source manifests; the merged manifest only
  carries the AGP-generated `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no `NSMicrophoneUsageDescription`;
  no audio device API referenced in `engine/src` (only `oboe::Version::Text`).
- Constitution II direction check (T019): adding `implementation(project(":shared:data"))` to `:shared:core`
  fails configuration with the Constitution II message; removed.
- Environment facts: Compose Multiplatform 1.12.1 requires compileSdk 37; Android 17 devices need
  Espresso 3.7.0 for Compose UI tests; Compose keeps clickables unfocusable in touch mode, so the keyboard
  test switches input mode first; Kotlin/Native links iOS binaries for iOS 15.0, so the engine static
  library is built with the same minimum; the iOS linker warns about `libicu.icudtl_dat.o` inside the
  Kotlin/Native runtime (toolchain, not project code).
- SC-001 (T067 dry run): a fresh `git clone` of the repository into a temporary directory, following
  `app/README.md` literally on this machine (prerequisites already installed, Gradle and Kotlin/Native
  caches warm): Android built, installed and launched on the Pixel 6 Pro in 8 s; both platforms running
  (iOS via `xcodebuild` + `simctl`) in 30 s wall-clock. Every command in the README worked as written.
  A truly cold machine additionally downloads the Kotlin/Native toolchain and Gradle distribution
  (several minutes on a normal connection); the 15-minute budget holds.
- SC-006: the README's "Where does X go?" table answers the four questions (screen → `:shared:ui`
  feature package; domain rule → `:shared:core`; persistence → `:shared:data`; engine command →
  `engine/include/tembra_engine.h` then `:shared:engine`) in one table; self-assessed, no external
  reader was available in this session.
- T070 hygiene gate (2026-09-24): `./gradlew clean build -x check --warning-mode all` → BUILD SUCCESSFUL in
  1 m 49 s, zero warnings from project code (six Kotlin metadata-jar KLIB `unique_name` warnings originate in
  Compose Multiplatform 1.12.1's own dependency graph and Metro's `runtime` klib name; documented in
  `app/README.md`). All four lanes from the cold build directory: BUILD SUCCESSFUL in 38 s, zero warnings.
  `TembraShared` release framework: `current ar archive` (static), 116.9 MB / 271,364 symbols vs the
  debug build's 252.1 MB / 623,936 symbols. `xcodebuild` of the iOS app succeeds with one toolchain
  linker warning (`libicu.icudtl_dat.o` in the Kotlin/Native runtime).
- Constitution VII audit (T069): every Kotlin file in `app/shared` and `app/androidApp` declares exactly one
  top-level type named after the file; no wildcard imports; no TODOs; no literal colours, sizes or fonts
  outside `audio.tembra.ui.theme` (the 720 dp reading width became `TembraSpacing.wideContentMaxWidth`).

## Quickstart run-through (T071, 2026-09-24)

- [x] §1 Prerequisites installed and verified (JDK 21, Xcode 26.6 + iOS 26.5 runtime, platform 37, NDK 29, CMake 3.31.6, `android` CLI, Pixel 6 Pro attached)
- [x] §2 Android built, installed and launched from the documented commands
- [x] §3 iOS built with `xcodebuild`, installed and launched on the iPhone 17 simulator
- [x] §4 Four lanes green; seeded failure reported as exactly one failing test on host and simulator
- [x] §5 Design system verified: parity test, counter, resize, hover tooltip, keyboard activation (device in keyboard mode)
- [x] §6 Native engine link asserted on the Pixel and the simulator; no permissions, no microphone key
- [x] §7 Screenshots captured at compact and wide widths on both platforms and reviewed
- [x] §8 `app/README.md` answers the "where does X go" questions; commands work from a fresh clone (30 s to both apps running with warm caches)
- [x] §9 Clean build warning-free from project code; licence inventory contains only permitted licences
