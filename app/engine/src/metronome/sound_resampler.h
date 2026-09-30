// Offline resampling of a prepared custom click to the stream rate (research R4): miniaudio's linear resampler with
// its 8th-order low-pass. Host thread only; the audio callback never resamples.
#ifndef TEMBRA_METRONOME_SOUND_RESAMPLER_H
#define TEMBRA_METRONOME_SOUND_RESAMPLER_H

#include <cstdint>
#include <vector>

namespace tembra {

class SoundResampler {
public:
    /** [mono] at [from] Hz resampled to [to] Hz: round(size × to / from) frames, delay-compensated; identity when equal. */
    static std::vector<float> resample(const std::vector<float>& mono, uint32_t from, uint32_t to);

    static constexpr uint32_t kLowPassOrder = 8;
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_SOUND_RESAMPLER_H
