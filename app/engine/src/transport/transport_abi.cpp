// The revision-6 C ABI transport functions (specs/009-timeline-arranger/contracts/engine-c-abi-v6.md): thin forwarders
// to the EngineCore singleton with argument checks. Never called on an audio callback.
#include <cmath>
#include <cstring>

#include "core/engine_core.h"
#include "tembra_engine.h"

extern "C" {

int32_t tembra_transport_open(const tembra_transport_config* config) {
    if (config == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().openTransport(*config);
}

void tembra_transport_close(void) {
    tembra::EngineCore::instance().closeTransport();
}

int32_t tembra_transport_set_tempo(uint32_t bpm, uint32_t beats_per_bar, uint32_t beat_unit) {
    if (bpm < 40 || bpm > 240 || beats_per_bar < 1 || beats_per_bar > 32 || (beat_unit != 4 && beat_unit != 8)) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setTransportTempo(bpm, beats_per_bar, beat_unit);
}

int32_t tembra_transport_set_arrangement(const tembra_arrangement* arrangement) {
    if (arrangement == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setTransportArrangement(*arrangement);
}

int32_t tembra_transport_set_track(uint32_t index, float gain_linear, uint32_t audible) {
    if (index >= TEMBRA_MAX_TRACKS || !std::isfinite(gain_linear) || gain_linear < 0.0f || gain_linear > 2.0f) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setTransportTrack(index, gain_linear, audible != 0);
}

int32_t tembra_transport_set_master(float gain_linear) {
    if (!std::isfinite(gain_linear) || gain_linear < 0.0f || gain_linear > 1.0f) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setTransportMaster(gain_linear);
}

int32_t tembra_transport_set_metronome(uint32_t enabled) {
    return tembra::EngineCore::instance().setTransportMetronome(enabled != 0);
}

int32_t tembra_transport_play(void) {
    return tembra::EngineCore::instance().transportPlay();
}

int32_t tembra_transport_pause(void) {
    return tembra::EngineCore::instance().transportPause();
}

int32_t tembra_transport_seek_beats(double beats) {
    if (!std::isfinite(beats) || beats < 0.0) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().transportSeekBeats(beats);
}

void tembra_transport_read(tembra_transport_snapshot* out) {
    if (out == nullptr) return;
    tembra::EngineCore::instance().readTransport(*out);
}

}  // extern "C"
