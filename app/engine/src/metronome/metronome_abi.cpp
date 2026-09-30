// The revision-3 C ABI metronome functions (contracts/engine-c-abi-v3.md): thin forwarders to the EngineCore singleton
// with argument checks. Never called on an audio callback.
#include "core/engine_core.h"
#include "metronome/metronome_grid.h"
#include "tembra_engine.h"

extern "C" {

int32_t tembra_metronome_set_grid(const tembra_metronome_grid* grid) {
    if (grid == nullptr || !tembra::MetronomeGrid::validate(*grid)) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setGrid(*grid);
}

int32_t tembra_metronome_select_sound(uint32_t sound) {
    if (sound > TEMBRA_SOUND_CUSTOM) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().selectSound(sound);
}

int32_t tembra_metronome_load_custom_sound(const char* wav_path, tembra_custom_sound_info* out_info) {
    if (wav_path == nullptr || out_info == nullptr) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().loadCustomSound(wav_path, out_info);
}

void tembra_metronome_unload_custom_sound(void) {
    tembra::EngineCore::instance().unloadCustomSound();
}

int32_t tembra_metronome_start(void) {
    return tembra::EngineCore::instance().startMetronome();
}

void tembra_metronome_stop(void) {
    tembra::EngineCore::instance().stopMetronome();
}

int32_t tembra_metronome_preview(uint32_t level) {
    if (level > TEMBRA_CLICK_ACCENT) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().previewClick(level);
}

void tembra_metronome_set_output_latency_ns(int64_t nanos) {
    tembra::EngineCore::instance().setOutputLatencyNs(nanos);
}

void tembra_metronome_read(tembra_metronome_tick* out) {
    if (out == nullptr) return;
    tembra::EngineCore::instance().readTick(*out);
}

}  // extern "C"
