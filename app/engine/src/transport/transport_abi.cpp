// The revision-6 C ABI transport functions (specs/009-timeline-arranger/contracts/engine-c-abi-v6.md): thin forwarders
// to the EngineCore singleton with argument checks. Never called on an audio callback.
// STUB (T053 replaces every body with the EngineCore forwarder): the functions exist so the Kotlin actuals link while
// the transport modules are built behind them; each returns TEMBRA_ERR_STATE and tembra_transport_read zeros its output.
#include <cstring>

#include "tembra_engine.h"

extern "C" {

int32_t tembra_transport_open(const tembra_transport_config* config) {
    if (config == nullptr) return TEMBRA_ERR_ARGUMENT;
    return TEMBRA_ERR_STATE;
}

void tembra_transport_close(void) {}

int32_t tembra_transport_set_tempo(uint32_t bpm, uint32_t beats_per_bar, uint32_t beat_unit) {
    if (bpm < 40 || bpm > 240 || beats_per_bar < 1 || beats_per_bar > 32 || (beat_unit != 4 && beat_unit != 8)) return TEMBRA_ERR_ARGUMENT;
    return TEMBRA_ERR_STATE;
}

int32_t tembra_transport_set_arrangement(const tembra_arrangement* arrangement) {
    if (arrangement == nullptr) return TEMBRA_ERR_ARGUMENT;
    return TEMBRA_ERR_STATE;
}

int32_t tembra_transport_set_track(uint32_t, float, uint32_t) { return TEMBRA_ERR_STATE; }

int32_t tembra_transport_set_master(float) { return TEMBRA_ERR_STATE; }

int32_t tembra_transport_set_metronome(uint32_t) { return TEMBRA_ERR_STATE; }

int32_t tembra_transport_play(void) { return TEMBRA_ERR_STATE; }

int32_t tembra_transport_pause(void) { return TEMBRA_ERR_STATE; }

int32_t tembra_transport_seek_beats(double) { return TEMBRA_ERR_STATE; }

void tembra_transport_read(tembra_transport_snapshot* out) {
    if (out != nullptr) std::memset(out, 0, sizeof(*out));
}

}  // extern "C"
