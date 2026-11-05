// The mixer's lock-free parameter block (feature 012, research R4; contracts/engine-c-abi-v8.md): per-track gain,
// pan and audibility as atomics, plus a version word the callback compares once per block and a metering switch.
// The host's setters validate and store from any thread without a lock, so a fader step from the main thread never
// waits behind the command mutex that play and record-stop hold; the callback reads the 64 × 3 atomics only when the
// version changed. The TrackTable of revision 6 seeds this block when an arrangement is published and is never read
// by the callback again. Header-only, no allocation, no lock (used from TEMBRA_RT_SOURCES).
#ifndef TEMBRA_MIXER_MIX_PARAMS_H
#define TEMBRA_MIXER_MIX_PARAMS_H

#include <atomic>
#include <cmath>
#include <cstdint>

#include "tembra_engine.h"
#include "timeline/arrangement_snapshot.h"

namespace tembra {

class MixParams {
public:
    static constexpr uint32_t kTracks = TEMBRA_MAX_TRACKS;
    static_assert(std::atomic<float>::is_always_lock_free, "the mixer parameters must be lock-free atomics");
    static_assert(std::atomic<uint32_t>::is_always_lock_free, "the mixer parameters must be lock-free atomics");
    static_assert(std::atomic<uint64_t>::is_always_lock_free, "the mixer version must be a lock-free atomic");

    MixParams() { resetValues(); }

    // --- Host, any thread -----------------------------------------------------------------------------------------------

    /** All three targets of one track; false (nothing stored) for an index ≥ 64, a non-finite value or a value out of range. */
    bool setTrack(uint32_t index, float gain, float pan, bool audible) {
        if (index >= kTracks || !validGain(gain) || !validPan(pan)) return false;
        gain_[index].store(gain, std::memory_order_relaxed);
        pan_[index].store(pan, std::memory_order_relaxed);
        audible_[index].store(audible ? 1u : 0u, std::memory_order_relaxed);
        announce();
        return true;
    }

    /** The revision-7 fast path: gain and audibility, the pan kept. */
    bool setGainAndAudible(uint32_t index, float gain, bool audible) {
        if (index >= kTracks || !validGain(gain)) return false;
        gain_[index].store(gain, std::memory_order_relaxed);
        audible_[index].store(audible ? 1u : 0u, std::memory_order_relaxed);
        announce();
        return true;
    }

    bool setPan(uint32_t index, float pan) {
        if (index >= kTracks || !validPan(pan)) return false;
        pan_[index].store(pan, std::memory_order_relaxed);
        announce();
        return true;
    }

    /** Metering on or off; the callback measures and publishes only while on. */
    void setMetering(bool enabled) { metering_.store(enabled ? 1u : 0u, std::memory_order_release); }

    // --- Host, under the command mutex (the arrangement) or while the callback cannot run (open, silence) --------------

    /** Takes gain and audibility for the table's tracks and keeps every pan (the session pushes pans after an arrangement). */
    void seed(const TrackTable& table) {
        const uint32_t count = table.count < kTracks ? table.count : kTracks;
        for (uint32_t t = 0; t < count; ++t) {
            gain_[t].store(table.tracks[t].gain, std::memory_order_relaxed);
            audible_[t].store(table.tracks[t].audible != 0 ? 1u : 0u, std::memory_order_relaxed);
        }
        announce();
    }

    /** Every track back to unity, the centre and audible; metering off. */
    void reset() {
        resetValues();
        announce();
    }

    // --- Any thread --------------------------------------------------------------------------------------------------------

    uint64_t version() const { return version_.load(std::memory_order_acquire); }
    bool metering() const { return metering_.load(std::memory_order_acquire) != 0; }
    float gain(uint32_t index) const { return gain_[index].load(std::memory_order_relaxed); }
    float pan(uint32_t index) const { return pan_[index].load(std::memory_order_relaxed); }
    bool audible(uint32_t index) const { return audible_[index].load(std::memory_order_relaxed) != 0; }

private:
    static bool validGain(float gain) { return std::isfinite(gain) && gain >= 0.0f && gain <= 2.0f; }
    static bool validPan(float pan) { return std::isfinite(pan) && pan >= -1.0f && pan <= 1.0f; }

    void announce() { version_.fetch_add(1, std::memory_order_release); }

    void resetValues() {
        for (uint32_t t = 0; t < kTracks; ++t) {
            gain_[t].store(1.0f, std::memory_order_relaxed);
            pan_[t].store(0.0f, std::memory_order_relaxed);
            audible_[t].store(1u, std::memory_order_relaxed);
        }
        metering_.store(0u, std::memory_order_relaxed);
    }

    std::atomic<float> gain_[kTracks];
    std::atomic<float> pan_[kTracks];
    std::atomic<uint32_t> audible_[kTracks];
    std::atomic<uint64_t> version_{1};
    std::atomic<uint32_t> metering_{0};
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_MIX_PARAMS_H
