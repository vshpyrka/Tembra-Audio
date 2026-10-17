# Data Model: Baseline Profiles (feature 008)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contracts**: [contracts/](contracts/)

All entities are files or command results; none is persisted in a database or in the performance history.

## Baseline profile (committed file)

- **Location**: `app/androidApp/src/main/generated/baselineProfiles/baseline-prof.txt`.
- **Format**: ART human-readable profile, one rule per line. A class rule is a descriptor (`Laudio/tembra/ui/tuner/TunerScreenKt;`); a method rule is `[H][S][P]<descriptor>-><method>(<params>)<return>` where `H` hot, `S` startup, `P` post-startup. Lines are sorted by the generator; comments are not used.
- **Validation**: non-empty (D9 build check); covers every descriptor of the coverage rule (D8 test); compiled form in the APK ≤ 1.5 MB (plugin limit; reported by `profile verify`).
- **Lifecycle**: written only by `tracepulse profile generate` (or the underlying Gradle task); committed when covered code changed; never edited by hand; read by the build (merged into `assets/dexopt/baseline.prof`, rewritten by R8 to obfuscated names at build time) and by the coverage test.

## Startup profile (committed file)

- **Location**: `app/androidApp/src/main/generated/baselineProfiles/startup-prof.txt`.
- **Format**: same rule syntax, restricted to what the generator collected with `includeInStartupProfile = true` (the cold-start path).
- **Validation**: non-empty whenever the baseline profile is (the generator writes both); used by R8 for DEX layout only when minification is on (it is, D3).
- **Lifecycle**: same as the baseline profile; produced and committed together.

## Coverage rule (committed file)

- **Location**: `app/androidApp/baseline-profile-coverage.txt`; format in [contracts/coverage-rule.md](contracts/coverage-rule.md).
- **Fields per line**: `label` (free identifier, e.g. `app.start`, `shell`, `timeline`; `home` until 2026-10-17), `descriptor` (a class descriptor exactly as it appears in a profile rule, ending in `;`).
- **Validation**: each descriptor must appear as a class rule or as the class part of a method rule in the baseline profile; a descriptor that does not fails the check naming the label and the descriptor.
- **Lifecycle**: edited by hand when a primary destination or the startup path changes (the same feature regenerates the profile); read by the coverage test and, through it, by `profile generate`.

## Generation run (command result, not persisted)

- **Inputs**: device id from the registry (`--device`), eligibility (`sdk >= 33`), the committed profile files (backed up), the Gradle task.
- **Outcome**: `succeeded` (files written, coverage test green), `refused` (a device check or eligibility failed; exit 2, files untouched), `failed` (Gradle task or coverage test failed; exit 3, backups restored), `usage` (exit 4).
- **Output**: the two profile files; a log under `app/perf/data/comparisons/` is not written — Gradle's console output is the log (captured by the runner when invoked from an agent session).

## Optimisation state (enum, Kotlin `CompareState` and the CLI argument)

| Value | Kotlin `CompilationMode` | Meaning |
|---|---|---|
| `none` | `CompilationMode.None()` | compilation reset; nothing optimised ahead of time (worst case after a direct install) |
| `profile` | `CompilationMode.Partial(BaselineProfileMode.Require)` | the shipped profile installed and compiled ahead of time; the comparison fails the state if the profile is absent |
| `full` | `CompilationMode.Full()` | everything compiled ahead of time (what the rounds measure; reference only) |

Parsing is owned by `CompareState.parse(argument)`; an unknown value fails the test with the accepted values listed.

## Comparison (file, schema [contracts/comparison.schema.json](contracts/comparison.schema.json))

- **Identity**: `comparison_id` in exactly the round-id shape of `records.py` (`<yyyymmdd>T<hhmmss>Z_<device_id>`, pattern `^[0-9]{8}T[0-9]{6}Z_[A-Za-z0-9._-]+$`), directory `app/perf/data/comparisons/<comparison_id>/`, so comparison and round artefacts sort identically under `data/`.
- **Build**: `commit`, `dirty`, `build_type` (`perf`), the APK and test APK paths used.
- **Device**: `device_id`, `model`, `fingerprint` (as the round record), `driver` (host name, metadata only).
- **States measured**: ordered list of `none`, `profile`, optionally `full`; `iterations`; `journeys`.
- **Metrics**: per metric key (catalogue keys, e.g. `startup.ttid_ms`, `startup.ttfd_ms`, `frames.p50_ms`, `memory.peak_kb`, `journey.duration_ms`, `tuner.load`): per state the `samples` (per-iteration values) and `median`; `change_pct` (`profile` vs `none`, signed), `direction` (`better` | `worse` | `none`, from the catalogue's higher-is-worse flag), `p_value` and `confirmed` (Mann–Whitney U, α = 0.01), and when `full` was measured `gap_closed_pct` = (median_none − median_profile) / (median_none − median_full) × 100 (undefined when the denominator is 0; written as `null`).
- **Artefacts**: per state the pulled `benchmarkData.json` path and the instrumentation log path.
- **Rendering**: `comparison.md` — one table (metric, median none, median profile, [median full], change %, direction, confirmed, [gap closed]) and a one-line headline for SC-001.
- **Never**: written to `tracepulse.db`, `records/`, the history branch or the report.

## Verification result (command output, not persisted)

- **APK**: `assets/dexopt/baseline.prof` present (`true` | `false`), its size in bytes, APK size in bytes, profile share in percent (FR-013's 3 %).
- **Recognised**: broadcast result code of `INSTALL_PROFILE` (`1` installed, `2` already installed, `6` no profile in the APK, other codes → error text), after one launch.
- **Optimised**: `dumpsys package dexopt` status for the package after `cmd package compile -r bg-dexopt` — expected `speed-profile`; anything else is reported verbatim.
- **Outcome**: exit 0 when present, recognised and `speed-profile`; exit 3 otherwise with the failing step named; exit 2 for a refused device; exit 4 for usage.

## Relationships

- The **coverage rule** constrains the **baseline profile**; both are read by one test used by the lane and by **generation**.
- **Generation** produces both profile files; **comparison** and **verification** consume the built APK that carries them; none of the three touches the **round** or the **history** of feature 007.
- **Optimisation state** is a parameter of the **comparison** only; rounds always use `full`.
