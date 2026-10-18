#include "mixer/track_strip.h"

namespace tembra {

void TrackStrip::process(const float* in, uint32_t channels, float* out, uint32_t frames) {
    if (channels <= 1) {
        for (uint32_t i = 0; i < frames; ++i) {
            const float sample = in[i] * ramp_.next();
            out[2 * i] = sample;
            out[2 * i + 1] = sample;
        }
        return;
    }
    // Stereo passes through; wider sources contribute their first pair (the importer refuses them anyway).
    for (uint32_t i = 0; i < frames; ++i) {
        const float gain = ramp_.next();
        out[2 * i] = in[i * channels] * gain;
        out[2 * i + 1] = in[i * channels + 1] * gain;
    }
}

}  // namespace tembra
