// C ABI revision 5 (feature 007): the audio-thread health getter. Non-real-time: reads the atomics the callback
// writes and samples the platform stream's xrun counter on the calling thread.
#include "core/engine_core.h"
#include "tembra_engine.h"

extern "C" int32_t tembra_engine_audio_health(tembra_audio_health* out) {
    if (out == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().readAudioHealth(*out) ? 0 : -1;
}
