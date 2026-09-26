// The revision-2 C ABI functions: thin forwarders to the TunerEngine singleton with argument checks.
#include "tembra_engine.h"
#include "tuner/tuner_engine.h"

extern "C" {

uint32_t tembra_audio_state(void) {
    return tembra::TunerEngine::instance().state();
}

void tembra_tuner_default_config(tembra_tuner_config* out) {
    if (out == nullptr) return;
    tembra::TunerEngine::defaultConfig(*out);
}

int32_t tembra_tuner_start(const tembra_tuner_config* config) {
    if (config == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::TunerEngine::instance().startListening(*config);
}

void tembra_tuner_stop(void) {
    tembra::TunerEngine::instance().stopListening();
}

void tembra_tuner_read(tembra_pitch_reading* out) {
    if (out == nullptr) return;
    tembra::TunerEngine::instance().read(*out);
}

int32_t tembra_tuner_start_test_signal(const tembra_tuner_config* config, const tembra_test_signal* signal) {
    if (config == nullptr || signal == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::TunerEngine::instance().startTestSignal(*config, *signal);
}

int32_t tembra_tone_start(double frequency_hz, uint32_t duration_ms) {
    return tembra::TunerEngine::instance().startTone(frequency_hz, duration_ms);
}

int32_t tembra_sample_load_wav(const uint8_t* bytes, uint32_t length) {
    if (bytes == nullptr || length == 0) return TEMBRA_ERR_ARGUMENT;
    return tembra::TunerEngine::instance().loadSample(bytes, length);
}

int32_t tembra_sample_play(int32_t handle, uint32_t loop) {
    return tembra::TunerEngine::instance().playSample(handle, loop != 0);
}

void tembra_sample_unload(int32_t handle) {
    tembra::TunerEngine::instance().unloadSample(handle);
}

void tembra_tone_stop(void) {
    tembra::TunerEngine::instance().stopTone();
}

}  // extern "C"
