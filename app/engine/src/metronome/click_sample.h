// One prepared click: immutable mono PCM at the stream rate (research R3, R4).
#ifndef TEMBRA_METRONOME_CLICK_SAMPLE_H
#define TEMBRA_METRONOME_CLICK_SAMPLE_H

#include <cstdint>
#include <vector>

namespace tembra {

/** Mono float PCM at [rate]; prepared on a host thread, then only read by the audio callback. */
struct ClickSample {
    std::vector<float> pcm;
    uint32_t rate = 0;
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_CLICK_SAMPLE_H
