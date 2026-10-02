// The record the audio callback publishes through a seqlock after every block (research R7).
#ifndef TEMBRA_METRONOME_METRONOME_TICK_H
#define TEMBRA_METRONOME_METRONOME_TICK_H

#include <cstdint>

namespace tembra {

/** The last emitted tick and the block that was rendered last; trivially copyable for Seqlock. */
struct MetronomeTick {
    uint64_t tickIndex = 0;       // +1 per emitted tick since start; 0 before the first
    uint32_t beatIndex = 0;       // 0-based beat in the bar of the last emitted tick
    uint32_t level = 0;           // tembra_click_level of that tick
    int64_t tickFrame = 0;        // output frame of that tick
    int64_t blockStartFrame = 0;  // output frame of the first frame of the last rendered block
    int64_t blockTimeNs = 0;      // monotonic time when that block was rendered
    double bpm = 0.0;             // tempo at the time of the tick
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_METRONOME_TICK_H
