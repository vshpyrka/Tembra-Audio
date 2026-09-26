// Lock-free single-producer single-consumer ring buffer between the input callback and the analysis thread.
#ifndef TEMBRA_TUNER_RING_BUFFER_H
#define TEMBRA_TUNER_RING_BUFFER_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace tembra {

/**
 * Capacity rounds up to a power of two; storage is allocated once in the constructor and never again. The producer
 * (audio callback) only writes data and `head`; the consumer (analysis thread) only writes `tail`. When the producer
 * outruns the consumer it keeps writing, so the oldest frames are overwritten and an overrun is counted; the consumer
 * notices (`head - tail > capacity`) and fast-forwards to the oldest retained frame, and it re-validates every copy
 * so a block overwritten while being read is discarded and read again. The newest audio therefore always survives,
 * which is what a tuner wants after a stall.
 */
template <class T>
class SpscRingBuffer {
public:
    explicit SpscRingBuffer(size_t minimumCapacity)
        : capacity_(roundUpToPowerOfTwo(minimumCapacity)), mask_(capacity_ - 1), data_(capacity_) {}

    size_t capacity() const { return capacity_; }

    /** Producer side. Never blocks, never allocates. */
    void push(const T* in, size_t count) {
        const size_t head = head_.load(std::memory_order_relaxed);
        const size_t tail = tail_.load(std::memory_order_acquire);
        if (count > capacity_) {
            in += count - capacity_;
            count = capacity_;
            overruns_.fetch_add(1, std::memory_order_relaxed);
        } else if (head - tail + count > capacity_) {
            overruns_.fetch_add(1, std::memory_order_relaxed);
        }
        for (size_t i = 0; i < count; ++i) data_[(head + i) & mask_] = in[i];
        head_.store(head + count, std::memory_order_release);
    }

    /** Consumer side: frames ready to read, at most the capacity. */
    size_t available() const {
        const size_t head = head_.load(std::memory_order_acquire);
        const size_t tail = tail_.load(std::memory_order_relaxed);
        const size_t pending = head - tail;
        return pending > capacity_ ? capacity_ : pending;
    }

    /** Consumer side: copies exactly [count] frames when that many are available, else returns false and copies nothing. */
    bool read(T* out, size_t count) {
        for (int attempt = 0; attempt < kMaxAttempts; ++attempt) {
            const size_t head = head_.load(std::memory_order_acquire);
            size_t tail = tail_.load(std::memory_order_relaxed);
            if (head - tail > capacity_) {
                tail = head - capacity_;  // the oldest frames were overwritten: skip to the oldest retained
                tail_.store(tail, std::memory_order_relaxed);
            }
            if (head - tail < count) return false;
            for (size_t i = 0; i < count; ++i) out[i] = data_[(tail + i) & mask_];
            std::atomic_thread_fence(std::memory_order_acquire);
            const size_t headAfter = head_.load(std::memory_order_relaxed);
            if (headAfter - tail <= capacity_) {
                tail_.store(tail + count, std::memory_order_release);
                return true;
            }
        }
        return false;
    }

    /** Number of pushes that overwrote unread frames. */
    uint64_t overruns() const { return overruns_.load(std::memory_order_relaxed); }

private:
    static constexpr int kMaxAttempts = 4;

    static size_t roundUpToPowerOfTwo(size_t value) {
        size_t power = 1;
        while (power < value) power <<= 1;
        return power;
    }

    const size_t capacity_;
    const size_t mask_;
    std::vector<T> data_;
    std::atomic<size_t> head_{0};
    std::atomic<size_t> tail_{0};
    std::atomic<uint64_t> overruns_{0};
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_RING_BUFFER_H
