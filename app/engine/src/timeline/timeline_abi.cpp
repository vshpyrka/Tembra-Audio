// The revision-6 C ABI clip-source and offline functions (specs/009-timeline-arranger/contracts/engine-c-abi-v6.md):
// thin forwarders with argument checks. Never called on an audio callback. The clip sources go through the
// EngineCore singleton (they live in its table, under its command mutex); the offline tools need no engine state.
// STUB (T052/T053 replace the clip-source and overview bodies): inspect is live.
#include "tembra_engine.h"
#include "timeline/audio_decoder.h"
#include "timeline/overview_builder.h"

extern "C" {

int32_t tembra_clip_source_open(const char* path, tembra_clip_info* out_info) {
    if (path == nullptr || out_info == nullptr) return TEMBRA_ERR_ARGUMENT;
    return TEMBRA_ERR_DECODE;
}

void tembra_clip_source_close(int32_t) {}

int32_t tembra_audio_inspect(const char* path, tembra_audio_info* out) {
    if (path == nullptr || out == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::AudioDecoder::inspect(path, *out);
}

int32_t tembra_audio_overview_build(const char* path, const char* out_path) {
    if (path == nullptr || out_path == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::OverviewBuilder::build(path, out_path);
}

}  // extern "C"
