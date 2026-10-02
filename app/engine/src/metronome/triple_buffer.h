// Three-slot snapshot buffer from one writer (a host thread) to one reader (the audio callback) (research R2).
#ifndef TEMBRA_METRONOME_TRIPLE_BUFFER_H
#define TEMBRA_METRONOME_TRIPLE_BUFFER_H

#include <atomic>
#include <cstdint>

namespace tembra {

/**
 * The writer fills its back slot and swaps it with the middle slot, marking the middle as fresh; the reader swaps its
 * front slot with the middle only when the fresh bit is set. The writer therefore never touches the slot being read,
 * a retired slot is rewritten only after the reader has moved off it, neither side blocks or loops, and nothing is
 * allocated after construction. One writer and one reader only.
 */
template <class T>
class TripleBuffer {
public:
    TripleBuffer() : slots_{T{}, T{}, T{}} {}

    TripleBuffer(const TripleBuffer&) = delete;
    TripleBuffer& operator=(const TripleBuffer&) = delete;

    /** Publishes [value]. Writer thread only. */
    void write(const T& value) {
        slots_[back_] = value;
        back_ = middle_.exchange(back_ | kFresh, std::memory_order_acq_rel) & kIndexMask;
    }

    /** The newest published value (the default-constructed value before any write). Reader thread only. */
    const T& latest() {
        if (middle_.load(std::memory_order_relaxed) & kFresh) {
            front_ = middle_.exchange(front_, std::memory_order_acq_rel) & kIndexMask;
        }
        return slots_[front_];
    }

private:
    static constexpr uint32_t kFresh = 4u;
    static constexpr uint32_t kIndexMask = 3u;

    T slots_[3];
    std::atomic<uint32_t> middle_{1u};
    uint32_t front_ = 0u;  // reader-owned
    uint32_t back_ = 2u;   // writer-owned
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_TRIPLE_BUFFER_H
