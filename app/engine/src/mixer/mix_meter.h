// The mixer's meter (feature 012, research R6; contracts/engine-c-abi-v8.md "Renderer rules"): measured on the
// callback only while metering is on. Each strip's post-fader stereo block and the master's output after the clamp
// are accumulated per block (peak and sum of squares per side, across the segments a block is split into), then
// folded once per block into a 64 ms peak history, a 300 ms exponential mean square (the VU's source) and a sticky
// clip latch (|x| ≥ 0.9999 on either side; the master's also lights when the clamp engaged), and published as one
// MixRecord. Reset requests arrive as atomics from the host and apply at the next publish. No allocation after
// construction, no lock, no tracing (the file is in TEMBRA_RT_SOURCES).
#ifndef TEMBRA_MIXER_MIX_METER_H
#define TEMBRA_MIXER_MIX_METER_H

#include <array>
#include <atomic>
#include <cstdint>

#include "mixer/mix_record.h"
#include "mixer/recent_peak.h"
#include "tembra_engine.h"
#include "tuner/seqlock.h"

namespace tembra {

class MixMeter {
public:
    /** The master's index in the reset request and the channel tables: after the 64 strips. */
    static constexpr uint32_t kMaster = TEMBRA_MIXER_MASTER_INDEX;
    static constexpr float kClipLevel = 0.9999f;
    static constexpr double kMeanSquareSeconds = 0.3;
    static constexpr double kRecentPeakSeconds = 0.064;

    explicit MixMeter(uint32_t rate = 48000);

    MixMeter(const MixMeter&) = delete;
    MixMeter& operator=(const MixMeter&) = delete;

    /** Host, before the callback uses the meter (a renderer is built per stream rate). */
    void setRate(uint32_t rate);

    // --- Host, any thread ------------------------------------------------------------------------------------------------

    /** Clears strip [index]'s latch (0..63) or the master's (64) at the next publish; a larger index is ignored. */
    void requestReset(uint32_t index);

    // --- Host while the callback cannot run, or the callback itself ---------------------------------------------------------

    /** Every history, window and latch back to silence (a new transport session, or metering turned on again). */
    void reset();

    // --- Audio thread ---------------------------------------------------------------------------------------------------------

    /** Starts a block of [frames]: the accumulators of every strip and the master go to zero. */
    void beginBlock(uint32_t frames);

    /** Accumulates one strip's post-fader stereo segment of [frames]. */
    void measureStrip(uint32_t index, const float* stereo, uint32_t frames);

    /** Accumulates the master's post-clamp stereo segment of [frames]; [clampEngaged] lights the master's latch. */
    void measureMaster(const float* stereo, uint32_t frames, bool clampEngaged);

    /** Folds the block into the histories, the windows and the latches, then stores the record for [trackCount] tracks. */
    void publish(Seqlock<MixRecord>& out, uint64_t block, uint32_t trackCount);

    /** Metering off: a zeroed record with the block number (the readers see the switch). */
    void publishSilent(Seqlock<MixRecord>& out, uint64_t block);

    /** measureStrip and measureMaster calls so far (the renderer test proves nothing is measured while metering is off). */
    uint64_t measureCalls() const { return measureCalls_; }

private:
    static constexpr uint32_t kChannels = kMaster + 1;

    struct Accumulator {
        float peak[2] = {0.0f, 0.0f};
        double sumSquares[2] = {0.0, 0.0};
    };

    struct Side {
        RecentPeak history;
        double meanSquare = 0.0;
    };

    void accumulate(Accumulator& into, const float* stereo, uint32_t frames);
    void fold(uint32_t index);

    uint32_t rate_;
    uint32_t blockFrames_ = 0;
    double meanSquareCoefficient_ = 0.0;  // 1 − exp(−blockFrames / (0.3 × rate)), recomputed when the block length changes
    uint32_t coefficientFrames_ = 0;
    bool masterClampThisBlock_ = false;
    uint64_t measureCalls_ = 0;
    std::array<Accumulator, kChannels> accumulators_{};
    std::array<std::array<Side, 2>, kChannels> sides_{};
    std::array<bool, kChannels> clipped_{};
    std::atomic<uint64_t> stripResets_{0};
    std::atomic<bool> masterReset_{false};
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_MIX_METER_H
