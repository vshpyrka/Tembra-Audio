// The disk streamer (feature 009, research R5; contracts/engine-c-abi-v6.md "Threading"): the one thread per engine
// that decodes into every clip source's fifo ahead of the playhead. The callback never decodes, never waits and never
// primes; it pops, and when it needs a frame the fifo lacks it parks the source and posts a request this thread serves.
#ifndef TEMBRA_TIMELINE_DISK_STREAMER_H
#define TEMBRA_TIMELINE_DISK_STREAMER_H

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <thread>
#include <vector>

#include "timeline/clip_source.h"

namespace tembra {

/** One source, the output frame it must be positioned at and the transport frame at which that is first needed. */
struct PrimeTarget {
    ClipSource* source = nullptr;
    int64_t sourceFrame = -1;  // -1: nothing needs this source from the position on
    int64_t neededAt = 0;
};

/**
 * Every producer-side touch of a fifo goes through the work mutex: prime() (synchronous, for play and seek on the
 * control thread, bounded by two seconds of audio per source), requestReprime() (served by the thread once the
 * readers are inactive) and the loop itself, which serves the callback's park requests nearest-first, refills free
 * space and sleeps 10 ms when nothing needed doing. stop() joins. Sources are borrowed: EngineCore owns them and
 * removes one with setSources() before it retires it.
 */
class DiskStreamer {
public:
    static constexpr int kIdleSleepMillis = 10;
    static constexpr double kDefaultWindowSeconds = 2.0;

    /** [readersActive] (optional) tells the thread when the callback may be popping; a host-requested re-prime waits for it. */
    explicit DiskStreamer(uint32_t rate, const std::atomic<bool>* readersActive = nullptr);
    ~DiskStreamer();

    DiskStreamer(const DiskStreamer&) = delete;
    DiskStreamer& operator=(const DiskStreamer&) = delete;

    /** The sources to serve from now on; pending work on a source no longer listed is dropped. Host thread. */
    void setSources(std::vector<ClipSource*> sources);

    /**
     * Primes every target now: those first needed within [windowSeconds] of [positionFrame] first, nearest first, then
     * the rest. The caller guarantees the readers are inactive or parked. Cancels a pending re-prime.
     */
    void prime(int64_t positionFrame, std::vector<PrimeTarget> targets, double windowSeconds = kDefaultWindowSeconds);

    /** Asks the thread to prime [targets] once the readers are inactive (a seek while stopped pre-primes this way). */
    void requestReprime(int64_t positionFrame, std::vector<PrimeTarget> targets);

    /** The position the last completed prime served, -1 after invalidate() or before any prime. */
    int64_t primedPosition() const { return primedPosition_.load(std::memory_order_acquire); }

    /** The arrangement changed: a later play must prime again. */
    void invalidate() { primedPosition_.store(-1, std::memory_order_release); }

    void start();
    void stop();
    bool running() const { return running_.load(std::memory_order_acquire); }

    /** One pass of the loop's work on the calling thread (tests); true when it did anything. */
    bool serviceOnce();

private:
    void stream();
    void primeLocked(int64_t positionFrame, std::vector<PrimeTarget>& targets, double windowSeconds);
    bool serviceLocked();

    const uint32_t rate_;
    const std::atomic<bool>* readersActive_;
    std::mutex work_;
    std::condition_variable wake_;
    std::vector<ClipSource*> sources_;
    bool reprimePending_ = false;
    int64_t reprimePosition_ = 0;
    std::vector<PrimeTarget> reprimeTargets_;
    bool wakeRequested_ = false;
    std::atomic<int64_t> primedPosition_{-1};
    std::thread thread_;
    std::atomic<bool> running_{false};
};

}  // namespace tembra

#endif  // TEMBRA_TIMELINE_DISK_STREAMER_H
