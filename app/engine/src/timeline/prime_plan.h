// The host side of priming (feature 009, research R5): from an arrangement and a position, the first frame every
// source will be asked for and when, so the DiskStreamer can fill the fifos nearest-first before play or after a seek.
// Control thread only (it allocates); the renderer does the same arithmetic without allocation on the callback.
#ifndef TEMBRA_TIMELINE_PRIME_PLAN_H
#define TEMBRA_TIMELINE_PRIME_PLAN_H

#include <algorithm>
#include <cstdint>
#include <vector>

#include "timeline/arrangement_snapshot.h"
#include "timeline/disk_streamer.h"
#include "transport/tempo_map.h"

namespace tembra {

/** One target per source the arrangement still needs at or after [position]; sources nothing needs are left out. */
inline std::vector<PrimeTarget> planPrime(const ArrangementSnapshot& arrangement, const TempoMap& tempo, int64_t position) {
    std::vector<PrimeTarget> targets;
    for (uint32_t i = 0; i < arrangement.clipCount; ++i) {
        const ClipState& clip = arrangement.clips[i];
        if (clip.source == nullptr) continue;
        const int64_t start = tempo.beatsToFrame(clip.startBeats);
        const int64_t end = start + clip.outLength;
        if (end <= position) continue;
        const int64_t neededAt = std::max(start, position);
        const int64_t frame = start <= position ? clip.outOffset + (position - start) : clip.outOffset;
        auto existing = std::find_if(targets.begin(), targets.end(), [&](const PrimeTarget& t) { return t.source == clip.source; });
        if (existing == targets.end()) {
            targets.push_back(PrimeTarget{clip.source, frame, neededAt});
        } else if (neededAt < existing->neededAt) {
            existing->sourceFrame = frame;
            existing->neededAt = neededAt;
        }
    }
    return targets;
}

}  // namespace tembra

#endif  // TEMBRA_TIMELINE_PRIME_PLAN_H
