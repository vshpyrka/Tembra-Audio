#include "mixer/track_strip.h"

#include "mixer/pan_law.h"

namespace tembra {

void TrackStrip::targets(float& left, float& right) const {
    float sideLeft = 1.0f;
    float sideRight = 1.0f;
    PanLaw::gains(pan_, stereo_, sideLeft, sideRight);
    const float fader = audible_ ? gain_ : 0.0f;
    left = fader * sideLeft;
    right = fader * sideRight;
}

void TrackStrip::setTarget(float gain, float pan, bool audible, bool stereo) {
    gain_ = gain;
    pan_ = pan;
    audible_ = audible;
    stereo_ = stereo;
    float left = 0.0f;
    float right = 0.0f;
    targets(left, right);
    left_.setTarget(left);
    right_.setTarget(right);
}

void TrackStrip::snap(float gain, float pan, bool audible, bool stereo) {
    gain_ = gain;
    pan_ = pan;
    audible_ = audible;
    stereo_ = stereo;
    float left = 0.0f;
    float right = 0.0f;
    targets(left, right);
    left_.snap(left);
    right_.snap(right);
}

void TrackStrip::process(const float* in, uint32_t channels, float* out, uint32_t frames) {
    const bool stereo = channels > 1;
    if (stereo != stereo_) setTarget(gain_, pan_, audible_, stereo);
    if (!stereo) {
        for (uint32_t i = 0; i < frames; ++i) {
            const float sample = in[i];
            out[2 * i] = sample * left_.next();
            out[2 * i + 1] = sample * right_.next();
        }
        return;
    }
    // Stereo passes through with balance; wider sources contribute their first pair (the importer refuses them anyway).
    for (uint32_t i = 0; i < frames; ++i) {
        out[2 * i] = in[i * channels] * left_.next();
        out[2 * i + 1] = in[i * channels + 1] * right_.next();
    }
}

}  // namespace tembra
