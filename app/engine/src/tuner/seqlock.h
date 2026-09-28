// Single-writer seqlock carrying one trivially copyable value from the analysis thread to any reader (research R4).
#ifndef TEMBRA_TUNER_SEQLOCK_H
#define TEMBRA_TUNER_SEQLOCK_H

#include <atomic>
#include <cstdint>
#include <cstring>
#include <type_traits>

namespace tembra {

/**
 * The writer bumps the sequence to odd, copies the value and bumps it to even; a reader retries while the sequence
 * is odd or changed under it. Neither side blocks, no allocation, safe to call every frame. One writer only.
 */
template <class T>
class Seqlock {
    static_assert(std::is_trivially_copyable_v<T>, "Seqlock values must be trivially copyable");

public:
    Seqlock() : value_{} {}

    void store(const T& value) {
        const uint64_t sequence = sequence_.load(std::memory_order_relaxed);
        sequence_.store(sequence + 1, std::memory_order_relaxed);
        std::atomic_thread_fence(std::memory_order_release);
        std::memcpy(&value_, &value, sizeof(T));
        std::atomic_thread_fence(std::memory_order_release);
        sequence_.store(sequence + 2, std::memory_order_release);
    }

    T load() const {
        T out;
        for (;;) {
            const uint64_t before = sequence_.load(std::memory_order_acquire);
            if (before & 1u) continue;
            std::memcpy(&out, &value_, sizeof(T));
            std::atomic_thread_fence(std::memory_order_acquire);
            const uint64_t after = sequence_.load(std::memory_order_relaxed);
            if (before == after) return out;
        }
    }

private:
    std::atomic<uint64_t> sequence_{0};
    T value_;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_SEQLOCK_H
