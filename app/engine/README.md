# Tembra engine (C++)

The shared real-time audio engine. One CMake project built three ways:

| Target | Built by | Output |
|---|---|---|
| Android (arm64-v8a, x86_64) | `:engine:android` (`com.android.library`, `externalNativeBuild`, NDK 29, CMake 3.31.6, Prefab Oboe) | `libtembra_engine.so` = core + JNI shim (`android/src/main/cpp/tembra_jni.cpp`) |
| iOS device and simulator | `:shared:engine` Gradle `Exec` tasks (`buildEngineIosArm64`, `buildEngineIosSimulatorArm64`) using the SDK's CMake with `-DCMAKE_SYSTEM_NAME=iOS` | `libtembra_engine.a`, embedded into the cinterop klib (`staticLibraries`) |
| macOS host (tests) | `./gradlew engineHostTest` or the commands below | `tembra_engine_tests` (Catch2 v3.16.0 via FetchContent) |

## Rules

- `include/tembra_engine.h` is the only interface Kotlin sees: plain C (`extern "C"`), no C++ types, no
  callbacks into the host, returned pointers have static storage and are never freed by the caller.
- Nothing Kotlin runs on the audio thread; Kotlin pushes commands and reads telemetry from ordinary
  threads (Constitution I). No audio device is opened in this feature.
- The core is a static library named `tembra_engine_core` (archive `libtembra_engine.a`). Oboe is linked
  only under `if(ANDROID)`.
- Third-party code is vendored under `third_party/` with its licence; see `third_party/README.md`.

## Host build and tests

```bash
CM="$HOME/Library/Android/sdk/cmake/3.31.6/bin"
"$CM/cmake" -S app/engine -B app/engine/build/host -G Ninja -DCMAKE_MAKE_PROGRAM="$CM/ninja" -DTEMBRA_BUILD_TESTS=ON
"$CM/cmake" --build app/engine/build/host
"$CM/ctest" --test-dir app/engine/build/host --output-on-failure
```

`./gradlew engineHostTest` runs the same three commands (build dir `app/shared/engine/build/engine/host`).

## Adding a function

1. Write the Catch2 test in `tests/` (it must fail first).
2. Declare the function in `include/tembra_engine.h`, bump `TEMBRA_ENGINE_ABI_VERSION` if the ABI changes.
3. Implement it under `src/`, add the JNI forwarder in `android/src/main/cpp/tembra_jni.cpp`, and the
   Kotlin side in `:shared:engine` (`NativeAudioEngine.android.kt`, `NativeAudioEngine.ios.kt`,
   `FakeAudioEngine`). The cinterop picks up the header automatically.
