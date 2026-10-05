// Owns the capture-side pipeline (research R1, R4): the ring buffer the callback pushes into, the pre-filter, the
// detector and the smoother the analysis thread runs every hop, and the seqlock that publishes the reading. The
// heartbeat of every read feeds the privacy watchdog (research R7). Feature 006 adds the input level every hop (the
// RMS of the analysis window after the pre-filter, research R2) and the noise gate: while the gate is closed the
// detector is not run and the smoother is fed a rejected estimate, so the reading is held, then rests (research R1).
#ifndef TEMBRA_TUNER_TUNER_ANALYZER_H
#define TEMBRA_TUNER_TUNER_ANALYZER_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

#include "tembra_engine.h"
#include "tuner/band_pass.h"
#include "tuner/level_gate.h"
#include "tuner/mcleod_pitch_detector.h"
#include "tuner/pitch_smoother.h"
#include "tuner/ring_buffer.h"
#include "tuner/seqlock.h"

namespace tembra {

class TunerAnalyzer {
public:
    /** A gate value under the level floor means "no gate"; this is the one the engine stores for it. */
    static constexpr float kGateOff = LevelGate::kOff;

    /** The lowest input level reported, in dB below full scale. */
    static constexpr float kLevelFloorDb = LevelGate::kFloorDb;

    TunerAnalyzer(const tembra_tuner_config& config, uint32_t sampleRate, bool synthetic = false, float gateDb = kGateOff);

    static bool validate(const tembra_tuner_config& config);

    /** Producer side, from the input callback: copies into the ring buffer and nothing else. */
    void push(const float* mono, uint32_t frames);

    /** Consumer side, from the analysis thread: consumes one hop when available and publishes a reading. */
    bool analyseAvailable();

    /** Any thread that is not the callback: the level a sound must reach to be read, or [kGateOff]; from the next hop. */
    void setGate(float gateDb) { gateDb_.store(gateDb, std::memory_order_relaxed); }

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
    /** Adds the filtered hop in [hop_] to the window's energy and returns the window's level in dB. */
    float measureLevel(size_t hop);
    void publish(float hz, float clarity, float inputPeak, float levelDb, bool gateOpen, bool hasPitch);

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
    std::vector<double> hopEnergies_;  // the energy of each of the window's hops, a ring
    size_t hopEnergyNext_ = 0;
    size_t hopEnergyCount_ = 0;
    std::atomic<float> gateDb_;
    LevelGate gate_;
    Seqlock<tembra_pitch_reading> reading_;
    std::atomic<uint64_t> sequence_{0};
    mutable std::atomic<int64_t> lastReadNanos_;
    const int64_t watchdogNanos_;
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_TUNER_ANALYZER_H
