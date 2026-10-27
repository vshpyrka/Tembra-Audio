// The transport's per-block telemetry record (feature 009, research R7): published by the callback through a Seqlock,
// read by tembra_transport_read at display rate. Trivially copyable, as the Seqlock requires.
#ifndef TEMBRA_TRANSPORT_TRANSPORT_RECORD_H
#define TEMBRA_TRANSPORT_TRANSPORT_RECORD_H

#include <cstdint>

namespace tembra {

struct TransportRecord {
    int64_t frame = 0;          // the position at the start of the block
    uint32_t playing = 0;
    uint32_t rate = 48000;
    int64_t blockTimeNs = 0;    // monotonic time at the start of the block (MetronomeRenderer::nowNanos)
    double framesPerBeat = 24000.0;
    uint64_t seekSequence = 0;  // the last seek request the callback applied
    uint64_t blocks = 0;        // blocks rendered including this one
};

}  // namespace tembra

#endif  // TEMBRA_TRANSPORT_TRANSPORT_RECORD_H
