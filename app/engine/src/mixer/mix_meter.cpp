#include "mixer/mix_meter.h"

#include <algorithm>
#include <cmath>

namespace tembra {

MixMeter::MixMeter(uint32_t rate) : rate_(rate) { setRate(rate); }

void MixMeter::setRate(uint32_t rate) {
    rate_ = rate == 0 ? 48000 : rate;
    const uint32_t window = static_cast<uint32_t>(kRecentPeakSeconds * rate_);
    for (auto& channel : sides_) {
        channel[0].history.setWindowFrames(window);
        channel[1].history.setWindowFrames(window);
    }
    coefficientFrames_ = 0;
}

void MixMeter::requestReset(uint32_t index) {
    if (index < kMaster) {
        stripResets_.fetch_or(1ull << index, std::memory_order_acq_rel);
    } else if (index == kMaster) {
        masterReset_.store(true, std::memory_order_release);
    }
}

void MixMeter::reset() {
    for (auto& channel : sides_) {
        for (Side& side : channel) {
            side.history.reset();
            side.meanSquare = 0.0;
        }
    }
    for (Accumulator& accumulator : accumulators_) accumulator = Accumulator{};
    for (bool& clipped : clipped_) clipped = false;
    masterClampThisBlock_ = false;
    stripResets_.store(0, std::memory_order_release);
    masterReset_.store(false, std::memory_order_release);
}

void MixMeter::beginBlock(uint32_t frames) {
    blockFrames_ = frames;
    if (frames != coefficientFrames_) {
        coefficientFrames_ = frames;
        meanSquareCoefficient_ = 1.0 - std::exp(-static_cast<double>(frames) / (kMeanSquareSeconds * rate_));
    }
    for (Accumulator& accumulator : accumulators_) accumulator = Accumulator{};
    masterClampThisBlock_ = false;
}

void MixMeter::accumulate(Accumulator& into, const float* stereo, uint32_t frames) {
    float peakLeft = into.peak[0];
    float peakRight = into.peak[1];
    double sumLeft = 0.0;
    double sumRight = 0.0;
    for (uint32_t i = 0; i < frames; ++i) {
        const float left = stereo[2 * i];
        const float right = stereo[2 * i + 1];
        const float magnitudeLeft = std::fabs(left);
        const float magnitudeRight = std::fabs(right);
        if (magnitudeLeft > peakLeft) peakLeft = magnitudeLeft;
        if (magnitudeRight > peakRight) peakRight = magnitudeRight;
        sumLeft += static_cast<double>(left) * left;
        sumRight += static_cast<double>(right) * right;
    }
    into.peak[0] = peakLeft;
    into.peak[1] = peakRight;
    into.sumSquares[0] += sumLeft;
    into.sumSquares[1] += sumRight;
}

void MixMeter::measureStrip(uint32_t index, const float* stereo, uint32_t frames) {
    if (index >= kMaster) return;
    ++measureCalls_;
    accumulate(accumulators_[index], stereo, frames);
}

void MixMeter::measureMaster(const float* stereo, uint32_t frames, bool clampEngaged) {
    ++measureCalls_;
    accumulate(accumulators_[kMaster], stereo, frames);
    if (clampEngaged) masterClampThisBlock_ = true;
}

void MixMeter::fold(uint32_t index) {
    const Accumulator& accumulator = accumulators_[index];
    const double frames = blockFrames_ == 0 ? 1.0 : static_cast<double>(blockFrames_);
    for (uint32_t side = 0; side < 2; ++side) {
        Side& channel = sides_[index][side];
        channel.history.push(accumulator.peak[side], blockFrames_);
        const double blockMeanSquare = accumulator.sumSquares[side] / frames;
        channel.meanSquare += (blockMeanSquare - channel.meanSquare) * meanSquareCoefficient_;
        if (accumulator.peak[side] >= kClipLevel) clipped_[index] = true;
    }
}

void MixMeter::publish(Seqlock<MixRecord>& out, uint64_t block, uint32_t trackCount) {
    const uint64_t resets = stripResets_.exchange(0, std::memory_order_acq_rel);
    if (resets != 0) {
        for (uint32_t t = 0; t < kMaster; ++t) {
            if ((resets >> t) & 1ull) clipped_[t] = false;
        }
    }
    if (masterReset_.exchange(false, std::memory_order_acq_rel)) clipped_[kMaster] = false;
    for (uint32_t index = 0; index < kChannels; ++index) fold(index);
    if (masterClampThisBlock_) clipped_[kMaster] = true;

    MixRecord record;
    record.block = block;
    record.count = std::min<uint32_t>(trackCount, TEMBRA_MAX_TRACKS);
    record.metering = 1;
    uint64_t mask = 0;
    for (uint32_t t = 0; t < record.count; ++t) {
        record.peak[t][0] = sides_[t][0].history.max();
        record.peak[t][1] = sides_[t][1].history.max();
        record.rms[t][0] = static_cast<float>(std::sqrt(sides_[t][0].meanSquare));
        record.rms[t][1] = static_cast<float>(std::sqrt(sides_[t][1].meanSquare));
        if (clipped_[t]) mask |= 1ull << t;
    }
    record.masterPeak[0] = sides_[kMaster][0].history.max();
    record.masterPeak[1] = sides_[kMaster][1].history.max();
    record.masterRms[0] = static_cast<float>(std::sqrt(sides_[kMaster][0].meanSquare));
    record.masterRms[1] = static_cast<float>(std::sqrt(sides_[kMaster][1].meanSquare));
    record.clipMask = mask;
    record.masterClipped = clipped_[kMaster] ? 1u : 0u;
    out.store(record);
}

void MixMeter::publishSilent(Seqlock<MixRecord>& out, uint64_t block) {
    MixRecord record;
    record.block = block;
    out.store(record);
}

}  // namespace tembra
