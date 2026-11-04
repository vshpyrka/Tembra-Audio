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
    // Feature 010 (contracts/engine-c-abi-v7.md "Appended fields"): the take and the count-in.
    uint32_t recording = 0;        // 1 while a take captures
    uint32_t preRoll = 0;          // 1 while the count-in pre-roll runs (the position is negative relative to the start)
    int32_t countInBeatsLeft = -1; // beats of the count-in still to sound, down to 1; -1 when none runs
    int64_t takeStartFrame = 0;    // the transport frame of the running (or last) take's first kept sample
};

}  // namespace tembra

#endif  // TEMBRA_TRANSPORT_TRANSPORT_RECORD_H
