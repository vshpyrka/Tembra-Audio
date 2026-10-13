// The revision-6 C ABI clip-source and offline functions (specs/009-timeline-arranger/contracts/engine-c-abi-v6.md):
// thin forwarders with argument checks. Never called on an audio callback.
// STUB (T053 replaces every body): the functions exist so the Kotlin actuals link while the timeline module is built.
#include "tembra_engine.h"

extern "C" {

int32_t tembra_clip_source_open(const char* path, tembra_clip_info* out_info) {
    if (path == nullptr || out_info == nullptr) return TEMBRA_ERR_ARGUMENT;
    return TEMBRA_ERR_DECODE;
}

void tembra_clip_source_close(int32_t) {}

int32_t tembra_audio_inspect(const char* path, tembra_audio_info* out) {
    if (path == nullptr || out == nullptr) return TEMBRA_ERR_ARGUMENT;
    return TEMBRA_ERR_DECODE;
}

int32_t tembra_audio_overview_build(const char* path, const char* out_path) {
    if (path == nullptr || out_path == nullptr) return TEMBRA_ERR_ARGUMENT;
    return TEMBRA_ERR_DECODE;
}

}  // extern "C"
