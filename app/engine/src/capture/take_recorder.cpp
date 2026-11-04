#include "capture/take_recorder.h"

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <sys/statvfs.h>

#if defined(__ANDROID__)
#include <pthread.h>
#include <sys/resource.h>
#include <unistd.h>
#elif defined(__APPLE__)
#include <pthread.h>
#include <pthread/qos.h>
#endif

#include "tembra_engine.h"

namespace tembra {

namespace {

/** The recorder serves the file, not the callback: an ordinary user-initiated thread, named for traces. */
void setRecorderPriority() {
#if defined(__ANDROID__)
    static_cast<void>(setpriority(PRIO_PROCESS, static_cast<id_t>(gettid()), 0));
    pthread_setname_np(pthread_self(), "tembra-recorder");
#elif defined(__APPLE__)
    pthread_set_qos_class_self_np(QOS_CLASS_USER_INITIATED, 0);
    pthread_setname_np("tembra-recorder");
#endif
}

}  // namespace

TakeRecorder::TakeRecorder(CaptureSource& source, PeakRing& ring, Config config, std::function<int64_t(const char*)> freeBytes)
    : source_(source), ring_(ring), config_(std::move(config)), freeBytes_(std::move(freeBytes)),
      staging_(kDrainFrames * CaptureSource::kMaxChannels, 0.0f) {
    if (!freeBytes_) freeBytes_ = &TakeRecorder::freeBytesOf;
}

TakeRecorder::~TakeRecorder() { stop(); }

int64_t TakeRecorder::freeBytesOf(const char* path) {
    if (path == nullptr) return -1;
    struct statvfs stat {};
    std::string directory = path;
    const size_t slash = directory.find_last_of('/');
    if (slash != std::string::npos) directory = slash == 0 ? "/" : directory.substr(0, slash);
    if (statvfs(directory.c_str(), &stat) != 0) return -1;
    return static_cast<int64_t>(stat.f_bavail) * static_cast<int64_t>(stat.f_frsize);
}

bool TakeRecorder::start() {
    if (running_.load(std::memory_order_acquire)) return true;
    if (!writer_.open(config_.path.c_str(), config_.channels, config_.rate)) return false;
    peaks_.reset(config_.channels);
    ring_.reset();
    framesWritten_.store(0, std::memory_order_release);
    backlogMax_.store(0, std::memory_order_release);
    writeFailed_.store(false, std::memory_order_release);
    capped_.store(false, std::memory_order_release);
    stopping_.store(false, std::memory_order_release);
    running_.store(true, std::memory_order_release);
    thread_ = std::thread([this] { run(); });
    return true;
}

void TakeRecorder::stop() {
    if (!running_.load(std::memory_order_acquire) && !thread_.joinable()) return;
    stopping_.store(true, std::memory_order_release);
    wakeCv_.notify_all();
    if (thread_.joinable()) thread_.join();
    running_.store(false, std::memory_order_release);
}

bool TakeRecorder::drainOnce() {
    const uint32_t channels = config_.channels;
    const size_t available = source_.fifo().available();
    const uint32_t backlog = static_cast<uint32_t>(available / channels);
    if (backlog > backlogMax_.load(std::memory_order_relaxed)) backlogMax_.store(backlog, std::memory_order_relaxed);
    source_.noteBacklog(backlog);
    if (available < channels) return false;
    const size_t frames = std::min<size_t>(kDrainFrames, available / channels);
    const size_t samples = source_.fifo().read(staging_.data(), frames * channels);
    const size_t got = samples / channels;
    if (got == 0) return false;
    if (capped_.load(std::memory_order_relaxed) || writeFailed_.load(std::memory_order_relaxed)) return true;  // drained and dropped
    size_t keep = got;
    const int64_t written = framesWritten_.load(std::memory_order_relaxed);
    if (config_.maxFrames > 0 && written + static_cast<int64_t>(keep) >= config_.maxFrames) {
        keep = static_cast<size_t>(std::max<int64_t>(0, config_.maxFrames - written));
        capped_.store(true, std::memory_order_release);
    }
    if (keep > 0) {
        if (!writer_.write(staging_.data(), keep)) {
            writeFailed_.store(true, std::memory_order_release);
            source_.endTake(TEMBRA_TAKE_STOP_WRITE_FAILED);
            return true;
        }
        framesWritten_.store(written + static_cast<int64_t>(keep), std::memory_order_release);
        peaks_.add(staging_.data(), keep, [this](const PeakBucket& bucket) { ring_.push(bucket); });
    }
    if (capped_.load(std::memory_order_relaxed)) source_.endTake(TEMBRA_TAKE_STOP_MAX_LENGTH);
    return true;
}

void TakeRecorder::refreshAndProbe() {
    if (writeFailed_.load(std::memory_order_relaxed)) return;
    if (!writer_.refreshSizes()) {
        writeFailed_.store(true, std::memory_order_release);
        source_.endTake(TEMBRA_TAKE_STOP_WRITE_FAILED);
        return;
    }
    if (config_.minFreeBytes > 0 && !capped_.load(std::memory_order_relaxed)) {
        const int64_t free = freeBytes_(config_.path.c_str());
        if (free >= 0 && static_cast<uint64_t>(free) < config_.minFreeBytes) {
            capped_.store(true, std::memory_order_release);
            source_.endTake(TEMBRA_TAKE_STOP_DISK_FULL);
        }
    }
    if (onHeaderRefresh) onHeaderRefresh();
}

void TakeRecorder::run() {
    setRecorderPriority();
    auto lastRefresh = std::chrono::steady_clock::now();
    for (;;) {
        const bool did = drainOnce();
        const auto now = std::chrono::steady_clock::now();
        if (std::chrono::duration<double>(now - lastRefresh).count() >= headerRefreshSeconds) {
            refreshAndProbe();
            lastRefresh = now;
        }
        if (stopping_.load(std::memory_order_acquire)) {
            // Drain everything the callback left (it stopped pushing before stop() was called), then finalise.
            while (drainOnce()) {
            }
            break;
        }
        if (!did) {
            std::unique_lock<std::mutex> lock(wake_);
            wakeCv_.wait_for(lock, std::chrono::milliseconds(kIdleWaitMillis), [this] { return stopping_.load(std::memory_order_acquire); });
        }
    }
    if (!writeFailed_.load(std::memory_order_relaxed)) {
        if (!writer_.finish()) writeFailed_.store(true, std::memory_order_release);
    } else {
        writer_.close();
    }
    running_.store(false, std::memory_order_release);
}

}  // namespace tembra
