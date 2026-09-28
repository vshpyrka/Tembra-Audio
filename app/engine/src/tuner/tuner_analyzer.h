// Owns the capture-side pipeline (research R1, R4): the ring buffer the callback pushes into, the pre-filter, the
// detector and the smoother the analysis thread runs every hop, and the seqlock that publishes the reading. The
// heartbeat of every read feeds the privacy watchdog (research R7).
#ifndef TEMBRA_TUNER_TUNER_ANALYZER_H
#define TEMBRA_TUNER_TUNER_ANALYZER_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

#include "tembra_engine.h"
#include "tuner/band_pass.h"
#include "tuner/mcleod_pitch_detector.h"
#include "tuner/pitch_smoother.h"
#include "tuner/ring_buffer.h"
#include "tuner/seqlock.h"

namespace tembra {

class TunerAnalyzer {
public:
    TunerAnalyzer(const tembra_tuner_config& config, uint32_t sampleRate, bool synthetic = false);

    static bool validate(const tembra_tuner_config& config);

    /** Producer side, from the input callback: copies into the ring buffer and nothing else. */
    void push(const float* mono, uint32_t frames);

    /** Consumer side, from the analysis thread: consumes one hop when available and publishes a reading. */
    bool analyseAvailable();

    /** Any thread: the latest reading; also stamps the watchdog heartbeat. */
    void read(tembra_pitch_reading& out) const;

    /** True when nobody has read for longer than the configured watchdog (0 disables). */
    bool watchdogExpired(int64_t nowNanos) const;

    /** Publishes a resting reading (called when capture stops). */
    void publishResting();

    uint64_t sequence() const { return sequence_.load(std::memory_order_acquire); }
    uint32_t sampleRate() const { return sampleRate_; }

    static int64_t nowNanos();

private:
    void publish(float hz, float clarity, float inputPeak, bool hasPitch);

    const tembra_tuner_config config_;
    const uint32_t sampleRate_;
    const bool synthetic_;
    SpscRingBuffer<float> ring_;
    BandPass filter_;
    McLeodPitchDetector detector_;
    PitchSmoother smoother_;
    std::vector<float> hop_;
    std::vector<float> window_;
    size_t filled_ = 0;
    Seqlock<tembra_pitch_reading> reading_;
    std::atomic<uint64_t> sequence_{0};
    mutable std::atomic<int64_t> lastReadNanos_;
    const int64_t watchdogNanos_;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_TUNER_ANALYZER_H
