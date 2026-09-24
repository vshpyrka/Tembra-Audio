/*
 * Tembra Audio engine C ABI.
 *
 * This header is the only interface the Kotlin side ever sees (JNI shim on Android, cinterop on
 * iOS). Rules: plain C, no C++ types, no callbacks into the host, every returned pointer has static
 * storage and is never freed by the caller. See specs/001-project-scaffold/contracts/engine-c-abi.md.
 */
#ifndef TEMBRA_ENGINE_H
#define TEMBRA_ENGINE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ABI revision of this header. Incremented whenever a function is added, removed or changed. */
#define TEMBRA_ENGINE_ABI_VERSION 1u
uint32_t tembra_engine_abi_version(void);

/* Human-readable engine name, e.g. "Tembra Engine". Static storage. */
const char* tembra_engine_name(void);

/* Engine semantic version "major.minor.patch", e.g. "0.1.0". Static storage. */
const char* tembra_engine_version(void);

/*
 * One-line description: "<name> <version> (<lib> <ver>; <lib> <ver>; ...)".
 * Lists every third-party native library compiled into the engine with its version.
 * Static storage, ASCII, no trailing newline.
 */
const char* tembra_engine_describe(void);

#ifdef __cplusplus
}
#endif

#endif /* TEMBRA_ENGINE_H */
