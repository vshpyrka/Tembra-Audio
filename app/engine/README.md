# Tembra engine (C++)

The shared real-time audio engine. One CMake project built three ways:

| Target | Built by | Output |
|---|---|---|
| Android (arm64-v8a, x86_64) | `:engine:android` (`com.android.library`, `externalNativeBuild`, NDK 29, CMake 3.31.6, Prefab Oboe) | `libtembra_engine.so` = core + JNI shim (`android/src/main/cpp/tembra_jni.cpp`) |
| iOS device and simulator | `:shared:engine` Gradle `Exec` tasks (`buildEngineIosArm64`, `buildEngineIosSimulatorArm64`) using the SDK's CMake with `-DCMAKE_SYSTEM_NAME=iOS` | `libtembra_engine.a`, embedded into the cinterop klib (`staticLibraries`) |
| macOS host (tests) | `./gradlew engineHostTest` or the commands below | `tembra_engine_tests` (Catch2 v3.16.0 via FetchContent) |

## Rules

- `include/tembra_engine.h` is the only interface Kotlin sees: plain C (`extern "C"`), no C++ types, no
  callbacks into the host, returned pointers have static storage and are never freed by the caller. The
  header carries `TEMBRA_ENGINE_ABI_VERSION`; every added, removed or changed function bumps it (revision 1:
  descriptor functions, feature 001; revision 2: the tuner's capture, tone and sample functions, feature 003;
  revision 3: the metronome's `tembra_metronome_*` functions, feature 004).
- Nothing Kotlin runs on the audio thread; Kotlin pushes commands and reads telemetry from ordinary
  threads (Constitution I). The audio callbacks in `src/platform/` only copy frames; no allocation, lock,
  logging or blocking call is allowed there, in `TunerAnalyzer::push`, `ToneGenerator::render`,
  `SamplePlayer::render` or `MetronomeRenderer::render`.
- The core is a static library named `tembra_engine_core` (archive `libtembra_engine.a`). Oboe is linked
  only under `if(ANDROID)`, AudioToolbox and CoreAudio only under `if(IOS)`.
- Third-party code is vendored under `third_party/` with its licence; see `third_party/README.md`.

## The tuner pipeline (feature 003)

```
platform input callback ─copy─▶ SpscRingBuffer ─hop─▶ analysis thread: BandPass → McLeodPitchDetector → PitchSmoother
                                                                          └─▶ Seqlock<tembra_pitch_reading> ◀─read─ tembra_tuner_read (UI thread, every frame)
```

- `src/platform/audio_device.h` is the I/O seam: `OboeAudioDevice` (Android, `platform/android/`),
  `RemoteIoAudioDevice` (iOS, `platform/apple/`, Core Audio C API), `NullAudioDevice` (macOS host) and
  `SyntheticAudioDevice` (all platforms: renders the harmonic test tone of `tembra_tuner_start_test_signal`
  on its own thread in 256-frame blocks so the whole capture path can be verified without a microphone).
- `EngineCore` (`src/core/engine_core.cpp`, moved from `src/tuner/tuner_engine` in feature 004) is the state machine behind
  the C ABI and the owner of the device and every renderer: at most one stream
  open, `LISTENING` and `TONE` never overlap, a privacy watchdog on the analysis thread stops capture when
  `tembra_tuner_read` is not called for `watchdog_seconds` (5 by default), a lost device drops to `IDLE`.
- The output side (`ToneGenerator`, `SamplePlayer`, `WavDecoder`) renders reference tones and drop-in
  recordings; commands are atomics, the callback applies the 5 ms ramps itself.

## The metronome (feature 004)

```
host: tembra_metronome_set_grid / select_sound / load_custom_sound ─TripleBuffer─▶ MetronomeRenderer::render (output callback)
                                                                                   ├─ framePos, lastTickFrame (double), one voice + one releasing voice
tembra_metronome_read (UI thread, every frame) ◀─Seqlock<MetronomeTick>─────────────┘
```

- `src/metronome/`: `ClickSynth` renders the five built-in sounds at the stream rate when the output opens;
  `CustomSoundLoader` (dr_wav, first 30 s) and `ClickSampleBuilder` (onset, 2 s cap, fades, levels) prepare a custom WAV,
  `SoundResampler` (miniaudio's `ma_resampler`, whose one implementation unit is `src/core/miniaudio_impl.cpp`) brings it to
  the stream rate. `MetronomeRenderer` schedules ticks per research R1/R5 and publishes telemetry per R7.
- `EngineCore` adds the `METRONOME` state (it refuses listening, tones, samples and previews); the metronome ignores
  `deviceLost()` because `OboeAudioDevice` reopens a disconnected output itself.

## The transport, timeline and mixer (feature 009)

```
host: tembra_transport_set_tempo / set_arrangement / set_track / set_master / set_metronome ─TripleBuffer─▶ TransportRenderer::render
      tembra_transport_play / pause / seek_beats (atomics, a seek sequence)                                 │  (output callback)
      DiskStreamer thread: prime(position) then refill each ClipSource's SpscFifo<float>  ─SPSC─▶ ClipSource::pop ─▶ TrackStrip (gain ramp, audible) ─▶ MasterBus (gain, metronome voice)
tembra_transport_read (UI thread, every frame) ◀─Seqlock<TransportRecord>───────────────────────────────────┘
```

- `src/transport/`: `TempoMap` (beats ↔ frames at the stream rate), `TransportClock` (frame position, playing, a
  pending seek applied at a block boundary), `TransportRenderer` (the arrangement's clips at their beat positions,
  the strips, the master bus and the metronome voice anchored to the beat grid), `TransportRecord` (what `read` copies).
- `src/timeline/`: `AudioDecoder` (miniaudio over WAV, FLAC and MP3, resampling to the stream rate; MP3 seek points),
  `ClipSource` (one decoder, one `SpscFifo<float>`, `primedFrom`, `eof`, `starved`; `pop` is RT-safe),
  `DiskStreamer` (one `std::thread` named `tembra-streamer` at audio priority — nice −16 on Android, user-interactive QoS on Apple — so the app's own decoding, overview and UI threads cannot stall it: a synchronous `prime` of two seconds, then a refill loop that sleeps 10 ms when
  every fifo is full; `TEMBRA_TRACE_ENGINE_PRIME`), `ArrangementSnapshot` (the published arrangement with the clip-source
  table), `OverviewBuilder` (the `.peaks` file: `TPK1`, four levels of int8 min/max pairs, a partial header while it writes).
- `src/mixer/`: `GainRamp` (10 ms linear ramps), `TrackStrip` (gain target and audibility from the solo/mute table,
  computed by Kotlin), `MasterBus` (master gain, soft limit, the click mixed in).
- `EngineCore` adds the `TRANSPORT` state (it refuses listening, tones, samples, the standalone metronome and previews):
  `openTransport` opens the output at the device rate, re-opens every clip source at that rate and starts the streamer;
  `closeTransport` pauses with the 5 ms release, joins the streamer and closes the stream. Sources opened while idle decode
  for the preferred rate; the stream's rate wins. `set_track` needs an arrangement published first.
- The RT-source list in `tests/CMakeLists.txt` (`TEMBRA_RT_SOURCES`, compiled with the allocation and locking poison)
  gained `clip_source.cpp`, `track_strip.cpp`, `master_bus.cpp` and `transport_renderer.cpp`; `disk_streamer.cpp`,
  `audio_decoder.cpp` and `overview_builder.cpp` stay off it (they allocate and block, on their own thread).
- Health (`tembra_audio_health`, 40 bytes since revision 6) adds `avg_us`, `starvation` and `clips_active`; the
  perf pipeline publishes them as `audio.avg_us`, `audio.starvation` and `audio.clips_active`.

## Host build and tests

```bash
CM="$HOME/Library/Android/sdk/cmake/3.31.6/bin"
"$CM/cmake" -S app/engine -B app/engine/build/host -G Ninja -DCMAKE_MAKE_PROGRAM="$CM/ninja" -DTEMBRA_BUILD_TESTS=ON
"$CM/cmake" --build app/engine/build/host
"$CM/ctest" --test-dir app/engine/build/host --output-on-failure
```

`./gradlew engineHostTest` runs the same three commands (build dir `app/shared/engine/build/engine/host`) and passes
`-DTEMBRA_TUNER_RECORDINGS_DIR=<repo>/app/shared/ui/src/commonMain/composeResources/files/tuner` so
`recordings_on_pitch_test` checks every drop-in recording within 1 cent of its note (it passes vacuously, and says so,
when the folder is empty). Feature 009's tests: `tempo_map_test`, `transport_clock_test`, `spsc_fifo_test`, `clip_source_test`
(with `fake_decoder.h`), `disk_streamer_test`, `audio_decoder_test` (the `specs/009-timeline-arranger/fixtures` files),
`overview_builder_test`, `track_strip_test`, `master_bus_test`, `transport_renderer_test` and the extended
`engine_core_state_test`, `audio_health_test` and `trace_shim_test`. Tests run with `TEMBRA_BUILD_TESTS`, which compiles the `EngineCore::setDevice` hook that
swaps the platform device for `tests/fake_audio_device.h`.

## Adding a function

1. Write the Catch2 test in `tests/` (it must fail first).
2. Declare the function in `include/tembra_engine.h`, bump `TEMBRA_ENGINE_ABI_VERSION` if the ABI changes.
   Revision 6 (feature 009) put the transport functions in `src/transport/transport_abi.cpp` and the clip-source,
   inspector and overview functions in `src/timeline/timeline_abi.cpp`: thin forwarders with argument checks, never
   called on an audio callback; a new transport or timeline function goes beside them.
3. Implement it under `src/`, add the JNI forwarder in `android/src/main/cpp/tembra_jni.cpp`, and the
   Kotlin side in `:shared:engine` (`NativeAudioEngine.android.kt`, `NativeAudioEngine.ios.kt`,
   `FakeAudioEngine`). The cinterop picks up the header automatically.
