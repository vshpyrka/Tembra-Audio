// The transport clock (feature 009, research R3; data-model.md §6): the frame position the callback advances per
// block, the playing flag, and the seek request the host posts and the callback applies at its next prologue.
#ifndef TEMBRA_TRANSPORT_TRANSPORT_CLOCK_H
#define TEMBRA_TRANSPORT_TRANSPORT_CLOCK_H

#include <atomic>
#include <cstdint>

#include "metronome/triple_buffer.h"

namespace tembra {

/**
 * frame and playing are callback-owned plain state; requestSeek is the host's only entry (one host thread at a time,
 * serialised by EngineCore's command mutex) and publishes through a triple buffer so the callback never waits. Every
 * request carries a sequence number; the callback reports the last one it applied so the host can tell a stale
 * position from the new one.
 */
class TransportClock {
public:
    int64_t frame = 0;
    bool playing = false;

    /** Callback: moves the position by one block. */
    void advance(uint32_t frames) { frame += static_cast<int64_t>(frames); }

    /** Host: asks for [target] (clamped at 0) at the next block; returns the request's sequence. */
    uint64_t requestSeek(int64_t target) {
        SeekRequest request;
        request.sequence = ++sequence_;
        request.frame = target < 0 ? 0 : target;
        requests_.write(request);
        pending_.store(request.sequence, std::memory_order_release);
        return request.sequence;
    }

    /** Callback: applies a newer request if one was posted; true when the position changed because of it. */
    bool applyPending() {
        const SeekRequest& request = requests_.latest();
        if (request.sequence == applied_.load(std::memory_order_relaxed)) return false;
        frame = request.frame;
        applied_.store(request.sequence, std::memory_order_release);
        return true;
    }

    /** The sequence of the last applied request (0 before any). Any thread. */
    uint64_t appliedSeekSequence() const { return applied_.load(std::memory_order_acquire); }

    /** The sequence of the last posted request (0 before any). Any thread. */
    uint64_t pendingSeekSequence() const { return pending_.load(std::memory_order_acquire); }

private:
    struct SeekRequest {
        uint64_t sequence = 0;
        int64_t frame = 0;
    };

    uint64_t sequence_ = 0;  // host-owned
    TripleBuffer<SeekRequest> requests_;
    std::atomic<uint64_t> pending_{0};
    std::atomic<uint64_t> applied_{0};
};

}  // namespace tembra

#endif  // TEMBRA_TRANSPORT_TRANSPORT_CLOCK_H
