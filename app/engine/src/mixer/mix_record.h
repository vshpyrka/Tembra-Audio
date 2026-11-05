// The mixer's meter record (feature 012, research R6; data-model.md §3): what the callback publishes through one
// Seqlock per block while metering is on and what tembra_mixer_read copies out: per strip and side the peak over the
// last 64 ms and the root mean square over 300 ms, the master's the same after the clamp, the strips' clip latches as
// a bit mask and the master's latch. About 1.1 KB; trivially copyable, as the Seqlock requires.
#ifndef TEMBRA_MIXER_MIX_RECORD_H
#define TEMBRA_MIXER_MIX_RECORD_H

#include <cstdint>

#include "tembra_engine.h"

namespace tembra {

struct MixRecord {
    uint64_t block = 0;      // the callback block the record was published on
    uint32_t count = 0;      // tracks in the arrangement; the entries above it are zero
    uint32_t metering = 0;   // 1 while measuring; a zeroed record with 0 goes out once when metering turns off
    float peak[TEMBRA_MAX_TRACKS][2] = {};
    float rms[TEMBRA_MAX_TRACKS][2] = {};
    float masterPeak[2] = {};
    float masterRms[2] = {};
    uint64_t clipMask = 0;   // bit i while strip i's latch is lit
    uint32_t masterClipped = 0;
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_MIX_RECORD_H
