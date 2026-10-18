// The clip stream fifo (feature 009, research R5): one producer (the disk streamer) and one consumer (the audio
// callback), storage allocated once, power-of-two capacity, partial moves, never blocking and never overwriting.
#ifndef TEMBRA_TIMELINE_SPSC_FIFO_H
#define TEMBRA_TIMELINE_SPSC_FIFO_H

#include <algorithm>
#include <atomic>
#include <cstddef>
#include <cstring>
#include <type_traits>
#include <vector>

namespace tembra {

/**
 * Unlike the tuner's SpscRingBuffer (newest wins), a stream must keep every item in order: write() returns how many
 * items fit (0 when full) and read() how many were there. The head is written by the producer only, the tail by the
 * consumer only; they sit on separate cache lines so the two threads never false-share. reset() belongs to the
 * producer and may only run while the consumer is provably not reading (a parked clip source: see ClipSource).
 */
template <class T>
class SpscFifo {
    static_assert(std::is_trivially_copyable_v<T>, "SpscFifo items are copied with memcpy");

public:
    explicit SpscFifo(size_t minimumCapacity)
        : capacity_(roundUpToPowerOfTwo(minimumCapacity)), mask_(capacity_ - 1), data_(capacity_) {}

    SpscFifo(const SpscFifo&) = delete;
    SpscFifo& operator=(const SpscFifo&) = delete;

    size_t capacity() const { return capacity_; }

    /** Producer: copies up to [count] items; returns how many were copied (0 when full). Never blocks. */
    size_t write(const T* in, size_t count) {
        const size_t head = head_.load(std::memory_order_relaxed);
        const size_t tail = tail_.load(std::memory_order_acquire);
        const size_t free = capacity_ - (head - tail);
        const size_t n = std::min(count, free);
        if (n == 0) return 0;
        const size_t start = head & mask_;
        const size_t first = std::min(n, capacity_ - start);
        std::memcpy(data_.data() + start, in, first * sizeof(T));
        if (n > first) std::memcpy(data_.data(), in + first, (n - first) * sizeof(T));
        head_.store(head + n, std::memory_order_release);
        return n;
    }

    /** Consumer: copies up to [count] items; returns how many were there. Never blocks. */
    size_t read(T* out, size_t count) {
        const size_t head = head_.load(std::memory_order_acquire);
        const size_t tail = tail_.load(std::memory_order_relaxed);
        const size_t n = std::min(count, head - tail);
        if (n == 0) return 0;
        const size_t start = tail & mask_;
        const size_t first = std::min(n, capacity_ - start);
        std::memcpy(out, data_.data() + start, first * sizeof(T));
        if (n > first) std::memcpy(out + first, data_.data(), (n - first) * sizeof(T));
        tail_.store(tail + n, std::memory_order_release);
        return n;
    }

    /** Consumer: discards up to [count] items; returns how many were discarded. */
    size_t skip(size_t count) {
        const size_t head = head_.load(std::memory_order_acquire);
        const size_t tail = tail_.load(std::memory_order_relaxed);
        const size_t n = std::min(count, head - tail);
        if (n == 0) return 0;
        tail_.store(tail + n, std::memory_order_release);
        return n;
    }

    /** Consumer view: items ready to read. */
    size_t available() const {
        return head_.load(std::memory_order_acquire) - tail_.load(std::memory_order_relaxed);
    }

    /** Producer view: items that would fit. */
    size_t freeSpace() const {
        return capacity_ - (head_.load(std::memory_order_relaxed) - tail_.load(std::memory_order_acquire));
    }

    /** Producer, only while the consumer is parked: empties the fifo (see the class comment). */
    void reset() {
        tail_.store(0, std::memory_order_relaxed);
        head_.store(0, std::memory_order_release);
    }

private:
    static size_t roundUpToPowerOfTwo(size_t value) {
        size_t result = 1;
        while (result < value) result <<= 1;
        return result;
    }

    const size_t capacity_;
    const size_t mask_;
    std::vector<T> data_;
    alignas(64) std::atomic<size_t> head_{0};  // producer-owned
    alignas(64) std::atomic<size_t> tail_{0};  // consumer-owned
};

}  // namespace tembra

#endif  // TEMBRA_TIMELINE_SPSC_FIFO_H
