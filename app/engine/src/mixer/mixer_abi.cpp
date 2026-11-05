// The revision-8 C ABI mixer functions (specs/012-mixer/contracts/engine-c-abi-v8.md): thin forwarders to the
// EngineCore singleton with argument checks. Every setter is lock-free and bounded (atomic stores behind the state
// check), so any host thread may call it while play or record-stop hold the command mutex; the read is a seqlock copy.
// Never called on an audio callback. The pan apply is traced here as engine.panApply (the core is a real-time unit).
#include <cmath>

#include "core/engine_core.h"
#include "tembra_engine.h"
#include "tembra_trace.h"
#include "tembra_trace_names.h"

namespace {

bool validGain(float gain, float maximum) { return std::isfinite(gain) && gain >= 0.0f && gain <= maximum; }

bool validPan(float pan) { return std::isfinite(pan) && pan >= -1.0f && pan <= 1.0f; }

}  // namespace

extern "C" {

int32_t tembra_mixer_set_track(uint32_t index, float gain_linear, float pan, uint32_t audible) {
    if (index >= TEMBRA_MAX_TRACKS || !validGain(gain_linear, 2.0f) || !validPan(pan)) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setMixTrack(index, gain_linear, pan, audible != 0);
}

int32_t tembra_mixer_set_pan(uint32_t index, float pan) {
    if (index >= TEMBRA_MAX_TRACKS || !validPan(pan)) return TEMBRA_ERR_ARGUMENT;
    TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_ENGINE_PAN_APPLY);
    return tembra::EngineCore::instance().setMixPan(index, pan);
}

int32_t tembra_mixer_set_master(float gain_linear) {
    if (!validGain(gain_linear, 1.0f)) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().setMixMaster(gain_linear);
}

int32_t tembra_mixer_set_metering(uint32_t enabled) {
    return tembra::EngineCore::instance().setMetering(enabled != 0);
}

void tembra_mixer_read(tembra_mix_snapshot* out) {
    if (out == nullptr) return;
    tembra::EngineCore::instance().readMix(*out);
}

int32_t tembra_mixer_reset_clip(uint32_t index) {
    if (index > TEMBRA_MIXER_MASTER_INDEX) return TEMBRA_ERR_ARGUMENT;
    return tembra::EngineCore::instance().resetMixClip(index);
}

}  // extern "C"
