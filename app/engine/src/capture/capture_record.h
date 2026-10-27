// The capture telemetry record (feature 010, data-model.md §7): what the callback publishes through a Seqlock every
// block while a track is armed, and what tembra_capture_read copies out (the latencies are added by the engine).
// Trivially copyable, as the Seqlock requires.
#ifndef TEMBRA_CAPTURE_CAPTURE_RECORD_H
#define TEMBRA_CAPTURE_CAPTURE_RECORD_H

#include <cstdint>

namespace tembra {

struct CaptureRecord {
    uint32_t armed = 0;
    uint32_t recording = 0;
    uint32_t preRoll = 0;
    uint32_t monitoring = 0;
    uint32_t channels = 0;
    uint32_t rate = 0;
    float peak[2] = {0.0f, 0.0f};  // max |x| over the last 64 ms per channel
    uint32_t clipped = 0;          // sticky until a reset
    int64_t framesCaptured = 0;    // kept frames of the running (or last) take
    int64_t takeStartFrames = 0;
    int64_t alignmentFrames = 0;
    uint32_t stopReason = 0;       // tembra_take_stop_reason; USER unless the engine ended the take
    uint32_t inputOverruns = 0;
    uint32_t backlogFrames = 0;    // frames waiting in the fifo at the end of the block
    uint64_t blocks = 0;           // blocks published since the arm
};

}  // namespace tembra

#endif  // TEMBRA_CAPTURE_CAPTURE_RECORD_H
