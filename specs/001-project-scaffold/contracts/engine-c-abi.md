# Contract: Engine C ABI (scaffold subset)

**Header**: `app/engine/include/tembra_engine.h` | **Consumers**: `:shared:engine` (JNI shim on Android, cinterop on iOS), Catch2 tests

The header is the only interface Kotlin ever sees. It is plain C (`extern "C"`), has no C++
types, no callbacks into the host, and is stable across both platforms. This feature ships the
descriptor subset only; audio functions arrive with the audio-spine feature.

```c
#ifndef TEMBRA_ENGINE_H
#define TEMBRA_ENGINE_H
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

/* ABI revision of this header. Incremented whenever a function is added, removed or changed. */
#define TEMBRA_ENGINE_ABI_VERSION 1u
uint32_t tembra_engine_abi_version(void);

/* Human-readable engine name, e.g. "Tembra Engine". Static storage, never freed by the caller. */
const char* tembra_engine_name(void);

/* Engine semantic version "major.minor.patch", e.g. "0.1.0". Static storage. */
const char* tembra_engine_version(void);

/* One-line description: "<name> <version> (<lib> <ver>; <lib> <ver>; ...)".
 * Lists every third-party native library compiled into the engine with its version.
 * Static storage, ASCII, no trailing newline. */
const char* tembra_engine_describe(void);

#ifdef __cplusplus
}
#endif
#endif
```

**Guarantees**
- All functions are safe to call from any thread, at any time, without prior initialisation.
- Returned pointers are valid for the process lifetime; callers never free them.
- No function allocates on the audio thread (there is no audio thread in this feature).
- `tembra_engine_describe()` for this feature returns exactly
  `Tembra Engine 0.1.0 (oboe 1.11.0; miniaudio 0.11.25; dr_wav 0.14.5; signalsmith-stretch 1.1.0)`
  on Android and the same string without the `oboe` entry on iOS (Oboe is Android-only).

**Platform bindings**
- Android: `app/engine/android/src/main/cpp/tembra_jni.cpp` exposes
  `Java_audio_tembra_engine_NativeAudioEngine_nativeDescribe` (and `nativeName`,
  `nativeVersion`, `nativeAbiVersion`) returning `jstring`/`jint`; the library is
  `libtembra_engine.so`.
- iOS: `app/shared/engine/src/nativeInterop/cinterop/tembra_engine.def` with
  `headers = tembra_engine.h`, `package = audio.tembra.engine.native`,
  `staticLibraries = libtembra_engine.a`; the Kotlin actual converts `CPointer<ByteVar>` with
  `toKString()`.

**Tests (Catch2, `app/engine/tests/`)**: `abi_version` equals `TEMBRA_ENGINE_ABI_VERSION`;
`name` is `Tembra Engine`; `version` matches `^\d+\.\d+\.\d+$`; `describe` starts with
`name version (` and ends with `)`; every vendored library appears in `describe`.
