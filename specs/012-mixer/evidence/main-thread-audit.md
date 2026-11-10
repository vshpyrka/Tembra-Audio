# Main-thread audit (feature 012, T121; contracts/skills-and-audit.md §C, research R12)

The mixer's rule: the main thread composes, lays out, draws and handles input; every engine call it makes is a
bounded atomic store or one seqlock copy. Anything that can block stays on `EngineDispatchers.blocking` or the
autosave's path as features 009 and 010 left it. This file holds the five parts of the audit; parts 4 and 5 are
filled by the device runs named in each.

## 1. Construction: every engine call the mixer makes

The Kotlin side reaches the engine through `TimelineAudio` (`:shared:engine`, ABI revision 8). The mixer's
surfaces (`MixerSheet`, `MixerWindow`, the track header's controls, the track settings' pan row) call only
`ProjectSession`, which calls the six functions below; nothing else of the engine is touched by the mixer.

| Call (`TimelineAudio`) | Who, when | Thread | Bound in the engine | Verified by |
|---|---|---|---|---|
| `setTrack(index, gainLinear, audible)` | `ProjectSession.pushTrack` on a settled `SetGain`, `SetMuted` or `SetSoloed` edit (solo re-pushes every track: at most 64 calls), on undo/redo and on the transport's rebind | main | `tembra_mixer_set_track` → `MixParams`: two atomic stores (gain, audible) and one version increment; no command mutex (`mixer_abi.cpp` forwards without `commands_`) | `mixer_abi_test` "the mixer setters return within a millisecond while another thread holds the command mutex"; `NativeAudioEngineMixerCostTest` (part 3) |
| `setPan(index, pan)` | `ProjectSession.pushPan` on a settled `SetPan` edit, after an arrangement push for the indices the engine does not hold yet (`pushPans`, differential), on undo/redo | main | `tembra_mixer_set_pan` → one atomic store and the version increment; the callback ramps toward it over 10 ms at its next block | the same two tests; `mix_params_test` "the parameter atomics are lock-free on this platform" |
| `setMaster(gainLinear)` | `SetMasterVolume` edits, undo/redo, the rebind | main | one atomic store of the master's target (10 ms ramp), since feature 009 | `NativeAudioEngineMixerCostTest` (the master is a `setTrack`-class store) |
| `setMetering(enabled)` | `ProjectSession.setMetering` while any meter surface is open (a holder count: the sheet and a window each hold one), and again on the rebind | main | `tembra_mixer_set_metering` → one atomic flag; off zeros the published record at the next block | `mix_params_test` "the metering flag is a plain switch"; `NativeAudioEngineMixerTest` "setMetering(false) zeros the snapshot" |
| `readMix(into)` | `MixTelemetry` from draw lambdas, once per frame (`readMixFrame` keys on the frame's nanos; later readers in the same frame copy the cached snapshot) | main (draw phase) | one seqlock copy of `tembra_mix_snapshot` (64 strips × peak and rms per side, the master, the latches, the block count) into one scratch per engine; retried on a torn read; no allocation | `NativeAudioEngineMixerCostTest` (1,000 reads in the 3,000-call run); `mixer_abi_test` "the read reports the callback's metering, track count and block while the transport runs" |
| `resetMixClip(index)` | `resetTrackClip`, `resetMasterClip` (a tap on a lit latch), `resetLatches` at play from a stop (up to 65 calls) | main | one atomic flag per strip the callback consumes at its next block; index 64 is the master | `mixer_abi_test` "…index 64 rejected by the setters and accepted by `tembra_mixer_reset_clip`"; `NativeAudioEngineMixerTest` "resetMixClip clears a latch" |

Calls the mixer never makes on the main thread, and never makes at all: `openTransport` and `closeTransport`
(the session's transport mutex on `EngineDispatchers.blocking`, features 009 and 010), `arm`, `disarm`, `play`,
`seek`, `recordStart`, `recordStop`, `inspect`, `buildOverview`, and the saves (a mixer edit calls
`autosave.onEdit()`, which only schedules; `persist` runs on the IO dispatcher). The mixer adds no
`withContext(blocking)` and needs none: `MixerWindowViewModel` and `TimelineViewModel` call `session.edit(...)`,
`closeEditGroup()`, `setMetering()`, `resetTrackClip()` and `resetMasterClip()` and read `mixTelemetry` and
`inputTelemetry`; the list of `session.*` members the mixer package uses is exactly these.

Two older paths the mixer exercises indirectly, both bounded since 009: an arrangement push (`setArrangement`, the
triple buffer copy) happens for a mute/solo only through `setTrack`'s fast path, never a new arrangement; the
mixer's edits land in `EditHistory` on the main thread like every other edit of 009.

## 2. Engine tests (`engineHostTest`)

`pan_law_test.cpp`, `mix_params_test.cpp` (7 cases: unity defaults, visibility after the version, the argument
checks, the metering switch, seeding from the track table, reset, lock-free atomics), `mix_meter_test.cpp` (9
cases: the −6 dBFS sine, the 64 ms peak history, the clip latch, the 300 ms mean-square window, metering off, one
record per block, the master after the clamp, reset, the record mirrors the ABI snapshot's capacity) and
`mixer_abi_test.cpp` (5 cases, including the 1 ms return while `commands_` is held from another thread and the
error states). 292 Catch2 cases green with zero warnings on 2026-11-06 (NOTES.md lane table). No allocation on the
callback is by construction (fixed-capacity records mirroring the ABI snapshot, `MixMeter` owned by the renderer)
and the real-time units compile a second time under `TEMBRA_TRACE_POISON=1` (`tests/CMakeLists.txt`), which
refuses any tracing-facade use inside them; there is no allocation poison in the build, so that claim rests on the
code and the review.

## 3. Facade tests (`NativeAudioEngineMixerCostTest`, `NativeAudioEngineBlockingDispatchTest`)

With the transport open, 1,000 `setPan`, 1,000 `setTrack` and 1,000 `readMix` calls on the main thread; bounds:
median < 50 µs, p99 < 1 ms, max < 10 ms (a single preemption shows as one outlier). The dispatch test asserts the
mixer setters and `readMix` leave `CountingDispatcher.dispatches` unchanged.

| Target | Date | Median | p99 | Max | Dispatch test |
|---|---|---|---|---|---|
| Pixel 6 Pro (Android 17) | 2026-11-08 | 3.174 µs | 35.7 µs | 268.9 µs | green |
| Pixel 9 Pro Fold emulator (API 36) | 2026-11-06 | green (the printed values were not kept) | | | green |
| iPhone 18 Pro simulator (iOS 27.1) | 2026-11-08 | 0.083 µs | 36.2 µs | 962.0 µs | green |
| iPad Pro 13-inch (M5) simulator | 2026-11-06 | green with the lane | | | green |
| iPhone Duo simulator | 2026-11-06 | green with the lane | | | green |
| Pixel 7 Pro, Pixel Tablet, OPPO CPH2239, Redmi Note 5, desktop emulators | T112–T117 | see NOTES.md once those lanes ran | | | |

## 4. Debug guard: `StrictMode` on the Pixel 6 Pro

`TembraApplication` installs `StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog()` in debug builds
(`StrictModePolicy`, `StrictModePolicyTest`). Read with `adb logcat -s StrictMode` during the `:shared:ui` device
lane and a five-minute manual mixer session (the sheet open and closed, every fader and knob moved, mute and solo
toggled, the master dragged, playback running).

Manual session, 2026-11-08 22:47–22:52 (the branch's debug build, the demo project playing with the music stream
muted, driven by test tag over adb: three cycles of the sheet opened, the four faders and the master dragged, Mute
and Solo toggled, the master latch tapped, the handle pulled to the full form, the four knobs turned, the sheet
closed with Back, undo). `adb logcat -v threadtime -s StrictMode` beside it:

| Window | `StrictMode` lines | Cause |
|---|---|---|
| the app's start (22:47:17, 10 violations within 7 ms) | 10 × `DiskReadViolation`, ~450 ms | pre-existing: `MainActivity.onCreate` → `App` → the Metro graph's construction on the main thread (`AndroidAppDirectories.<init>`, `ProvideProjectRepository`, `ProvideAudioEngine`/`ProvideTimelineAudio`, `ProjectSession`), features 001–010's DI prebuild (the cold-launch work of 2026-10-21 reads the app directories there) |
| the five minutes of mixer use (22:47:20–22:52:30) | 0 | — |

Zero `StrictMode` lines caused by the mixer. The ten start-up reads are handed to the owner with this file (a
`Dispatchers.IO` warm-up of the directories would move them; not this feature's scope). The `:shared:ui` device
lane that followed (1,231 UI tests, 18 min 36 s, 2026-11-08 22:53–23:12) logged zero `StrictMode` lines: the test
runner's process installs the same policy through `TembraApplication`, so the mixer's UI tests exercised every
control under it.

## 5. Traces (the labelled session, T125)

The Perfetto main-thread query of contracts/perf-instrumentation.md on the `--rounds 5` Pixel 6 Pro session after
the merge: no main-thread slice over 8 ms from `timeline.mixerOpen`, `timeline.mixerClose`, `timeline.mixerFrame`,
`timeline.faderDrag`, `timeline.knobTurn` or `engine.panApply`; other slices attributed.

Pending: filled with `EVID/perf-session.md` after the merge.
