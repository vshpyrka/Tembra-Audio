// The tuner's C ABI functions (revision 2, and tembra_tuner_set_gate of revision 4): thin forwarders to the EngineCore
// singleton (core/engine_core.h) with argument checks.
#include <cmath>

#include "tembra_engine.h"
#include "core/engine_core.h"

extern "C" {

uint32_t tembra_audio_state(void) {
    return tembra::EngineCore::instance().state();
}

void tembra_tuner_default_config(tembra_tuner_config* out) {
    if (out == nullptr) return;
    tembra::EngineCore::defaultConfig(*out);
}

int32_t tembra_tuner_start(const tembra_tuner_config* config) {
    if (config == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().startListening(*config);
}

void tembra_tuner_stop(void) {
    tembra::EngineCore::instance().stopListening();
}

void tembra_tuner_read(tembra_pitch_reading* out) {
    if (out == nullptr) return;
    tembra::EngineCore::instance().read(*out);
}

int32_t tembra_tuner_start_test_signal(const tembra_tuner_config* config, const tembra_test_signal* signal) {
    if (config == nullptr || signal == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().startTestSignal(*config, *signal);
}

int32_t tembra_tuner_set_gate(uint32_t enabled, float open_db) {
    if (enabled != 0 && !(std::isfinite(open_db) && open_db >= -120.0f && open_db <= 0.0f)) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setGate(enabled != 0, open_db);
}

int32_t tembra_tone_start(double frequency_hz, uint32_t duration_ms) {
    return tembra::EngineCore::instance().startTone(frequency_hz, duration_ms);
}

int32_t tembra_sample_load_wav(const uint8_t* bytes, uint32_t length) {
    if (bytes == nullptr || length == 0) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().loadSample(bytes, length);
}

int32_t tembra_sample_play(int32_t handle, uint32_t loop) {
    return tembra::EngineCore::instance().playSample(handle, loop != 0);
}

void tembra_sample_unload(int32_t handle) {
    tembra::EngineCore::instance().unloadSample(handle);
}

void tembra_tone_stop(void) {
    tembra::EngineCore::instance().stopTone();
}

}  // extern "C"
