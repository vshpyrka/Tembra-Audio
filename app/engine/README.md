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
  descriptor functions, feature 001; revision 2: the tuner's capture, tone and sample functions, feature 003).
- Nothing Kotlin runs on the audio thread; Kotlin pushes commands and reads telemetry from ordinary
  threads (Constitution I). The audio callbacks in `src/platform/` only copy frames; no allocation, lock,
  logging or blocking call is allowed there, in `TunerAnalyzer::push`, `ToneGenerator::render` or
  `SamplePlayer::render`.
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
- `TunerEngine` (`src/tuner/tuner_engine.cpp`) is the state machine behind the C ABI: at most one stream
  open, `LISTENING` and `TONE` never overlap, a privacy watchdog on the analysis thread stops capture when
  `tembra_tuner_read` is not called for `watchdog_seconds` (5 by default), a lost device drops to `IDLE`.
- The output side (`ToneGenerator`, `SamplePlayer`, `WavDecoder`) renders reference tones and drop-in
  recordings; commands are atomics, the callback applies the 5 ms ramps itself.

## Host build and tests

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
when the folder is empty). Tests run with `TEMBRA_BUILD_TESTS`, which compiles the `TunerEngine::setDevice` hook that
swaps the platform device for `tests/fake_audio_device.h`.

## Adding a function

1. Write the Catch2 test in `tests/` (it must fail first).
2. Declare the function in `include/tembra_engine.h`, bump `TEMBRA_ENGINE_ABI_VERSION` if the ABI changes.
3. Implement it under `src/`, add the JNI forwarder in `android/src/main/cpp/tembra_jni.cpp`, and the
   Kotlin side in `:shared:engine` (`NativeAudioEngine.android.kt`, `NativeAudioEngine.ios.kt`,
   `FakeAudioEngine`). The cinterop picks up the header automatically.
