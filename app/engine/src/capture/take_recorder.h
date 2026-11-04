// The take recorder thread (feature 010, research R6; contracts/engine-c-abi-v7.md "Threading"): "tembra-recorder"
// pops the capture fifo, converts to 24-bit PCM, appends to the WAV, rewrites the header every second and flushes
// (crash-safe, FR-023), publishes the 256-frame peak buckets the host polls, checks the frame cap and the free
// space once a second and ends the take itself with MAX_LENGTH, DISK_FULL or WRITE_FAILED. Modelled on the disk
// streamer's thread: named, a condition variable, a 10 ms idle wait, user-initiated QoS on Apple and nice 0 on Android
// (not real-time). Never the callback.
#ifndef TEMBRA_CAPTURE_TAKE_RECORDER_H
#define TEMBRA_CAPTURE_TAKE_RECORDER_H

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "capture/capture_source.h"
#include "capture/peak_ring.h"
#include "capture/wav_writer.h"

namespace tembra {

class TakeRecorder {
public:
    struct Config {
        std::string path;
        uint32_t channels = 1;
        uint32_t rate = 48000;
        int64_t maxFrames = 0;       // 0 = no cap
        uint64_t minFreeBytes = 0;   // 0 = no floor
    };

    static constexpr int kIdleWaitMillis = 10;
    static constexpr size_t kDrainFrames = 4096;

    /** [freeBytes] answers the free-space probe for a path; the default is statvfs of its volume. */
    TakeRecorder(CaptureSource& source, PeakRing& ring, Config config, std::function<int64_t(const char*)> freeBytes = {});
    ~TakeRecorder();

    TakeRecorder(const TakeRecorder&) = delete;
    TakeRecorder& operator=(const TakeRecorder&) = delete;

    /** Creates the file (control thread) and starts the thread; false when the file cannot be created. */
    bool start();

    /** Drains what the fifo still holds, finalises the file and joins. Idempotent. */
    void stop();

    bool running() const { return running_.load(std::memory_order_acquire); }
    int64_t framesWritten() const { return framesWritten_.load(std::memory_order_acquire); }
    uint32_t backlogMax() const { return backlogMax_.load(std::memory_order_acquire); }
    bool writeFailed() const { return writeFailed_.load(std::memory_order_acquire); }
    const std::string& path() const { return config_.path; }

    /** How often the header is rewritten and the free space probed; tests shorten it. */
    double headerRefreshSeconds = 1.0;
    /** Test hook: runs on the recorder thread after every periodic header refresh. */
    std::function<void()> onHeaderRefresh;

    /** The writer beneath the thread (tests provoke a write failure through it). */
    WavWriter& writerForTest() { return writer_; }

    /** statvfs of the volume holding [path]; -1 on error. */
    static int64_t freeBytesOf(const char* path);

private:
    void run();
    bool drainOnce();
    void refreshAndProbe();

    CaptureSource& source_;
    PeakRing& ring_;
    const Config config_;
    std::function<int64_t(const char*)> freeBytes_;
    WavWriter writer_;
    PeakAccumulator peaks_;
    std::vector<float> staging_;
    std::thread thread_;
    std::mutex wake_;
    std::condition_variable wakeCv_;
    std::atomic<bool> running_{false};
    std::atomic<bool> stopping_{false};
    std::atomic<bool> capped_{false};
    std::atomic<int64_t> framesWritten_{0};
    std::atomic<uint32_t> backlogMax_{0};
    std::atomic<bool> writeFailed_{false};
};

}  // namespace tembra

#endif  // TEMBRA_CAPTURE_TAKE_RECORDER_H
