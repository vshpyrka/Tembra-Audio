// Turns a decoded custom sound into a ClickSet (research R4; contracts/click-sounds.md): onset alignment, the 2 s
// cap, fades, the three levels and resampling to the stream rate. Host thread only (allocates).
#ifndef TEMBRA_METRONOME_CLICK_SAMPLE_BUILDER_H
#define TEMBRA_METRONOME_CLICK_SAMPLE_BUILDER_H

#include <cstdint>
#include <vector>

#include "metronome/click_set.h"
#include "tembra_engine.h"

namespace tembra {

class ClickSampleBuilder {
public:
    /**
     * Prepares [mono] at [sourceRate] for playback at [targetRate] into [out]:
     * 1. refuses a peak below −60 dBFS (TEMBRA_ERR_SILENT);
     * 2. the onset is the first frame whose trailing 2 ms RMS reaches −40 dB re peak; the sample starts 1 ms earlier
     *    with a 1 ms raised-cosine fade-in;
     * 3. it ends at the last frame above −60 dB re peak, at most 2 000 ms after the onset, with a 5 ms raised-cosine
     *    fade-out to exactly zero (over the last half of a sound shorter than 5 ms);
     * 4. it is resampled to [targetRate]; Click peaks at −9 dBFS, Accent is +6 dB (limited to −1 dBFS), Sub −8 dB.
     * Fills source_rate, kept_frames, trimmed_ms and kept_ms of [info] (source rate terms) when [info] is not null.
     */
    static int32_t prepare(const std::vector<float>& mono, uint32_t sourceRate, uint32_t targetRate, tembra_custom_sound_info* info,
                           ClickSet* out);

    static constexpr double kSilentDbfs = -60.0;
    static constexpr double kOnsetDbRePeak = -40.0;
    static constexpr double kEndDbRePeak = -60.0;
    static constexpr double kRmsWindowMs = 2.0;
    static constexpr double kBackUpMs = 1.0;
    static constexpr double kFadeInMs = 1.0;
    static constexpr double kFadeOutMs = 5.0;
    static constexpr double kMaxKeptMs = 2000.0;
    static constexpr double kClickDbfs = -9.0;
    static constexpr double kAccentBoostDb = 6.0;
    static constexpr double kAccentLimitDbfs = -1.0;
    static constexpr double kSubCutDb = -8.0;
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_CLICK_SAMPLE_BUILDER_H
