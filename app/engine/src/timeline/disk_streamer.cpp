#include "timeline/disk_streamer.h"

#include <algorithm>
#include <chrono>

#if defined(__ANDROID__)
#include <pthread.h>
#include <sys/resource.h>
#include <unistd.h>
#elif defined(__APPLE__)
#include <pthread.h>
#include <pthread/qos.h>
#endif

#include "tembra_trace.h"
#include "tembra_trace_names.h"

namespace tembra {

namespace {

#if defined(__ANDROID__)
/** Android's THREAD_PRIORITY_AUDIO: what the platform's own media threads run at. */
constexpr int kAndroidAudioNice = -16;
#endif

/**
 * The streamer feeds the real-time thread, so it runs above the app's other threads (decoding an import, building
 * overviews, composing): Android's audio priority, Apple's user-interactive QoS. A refusal keeps the default; the
 * name shows the thread in traces.
 */
void raiseStreamerPriority() {
#if defined(__ANDROID__)
    static_cast<void>(setpriority(PRIO_PROCESS, static_cast<id_t>(gettid()), kAndroidAudioNice));
    pthread_setname_np(pthread_self(), "tembra-streamer");
#elif defined(__APPLE__)
    pthread_set_qos_class_self_np(QOS_CLASS_USER_INTERACTIVE, 0);
    pthread_setname_np("tembra-streamer");
#endif
}

}  // namespace

DiskStreamer::DiskStreamer(uint32_t rate, const std::atomic<bool>* readersActive) : rate_(rate), readersActive_(readersActive) {}

DiskStreamer::~DiskStreamer() { stop(); }

void DiskStreamer::setSources(std::vector<ClipSource*> sources) {
    std::lock_guard<std::mutex> lock(work_);
    sources_ = std::move(sources);
    reprimeTargets_.erase(std::remove_if(reprimeTargets_.begin(), reprimeTargets_.end(),
                                         [this](const PrimeTarget& target) {
                                             return std::find(sources_.begin(), sources_.end(), target.source) == sources_.end();
                                         }),
                          reprimeTargets_.end());
    if (reprimeTargets_.empty()) reprimePending_ = false;
    wakeRequested_ = true;
    wake_.notify_all();
}

void DiskStreamer::prime(int64_t positionFrame, std::vector<PrimeTarget> targets, double windowSeconds) {
    std::lock_guard<std::mutex> lock(work_);
    reprimePending_ = false;
    reprimeTargets_.clear();
    primeLocked(positionFrame, targets, windowSeconds);
}

void DiskStreamer::requestReprime(int64_t positionFrame, std::vector<PrimeTarget> targets) {
    std::lock_guard<std::mutex> lock(work_);
    reprimePending_ = true;
    reprimePosition_ = positionFrame;
    reprimeTargets_ = std::move(targets);
    wakeRequested_ = true;
    wake_.notify_all();
}

void DiskStreamer::primeLocked(int64_t positionFrame, std::vector<PrimeTarget>& targets, double windowSeconds) {
    TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_ENGINE_PRIME);
    const int64_t window = static_cast<int64_t>(windowSeconds * static_cast<double>(rate_));
    const auto soon = [&](const PrimeTarget& target) { return target.neededAt - positionFrame <= window; };
    std::stable_sort(targets.begin(), targets.end(), [&](const PrimeTarget& a, const PrimeTarget& b) {
        const bool soonA = soon(a);
        const bool soonB = soon(b);
        if (soonA != soonB) return soonA;
        return a.neededAt < b.neededAt;
    });
    for (const PrimeTarget& target : targets) {
        if (target.source == nullptr || target.sourceFrame < 0) continue;
        target.source->prime(target.sourceFrame);
    }
    primedPosition_.store(positionFrame, std::memory_order_release);
}

bool DiskStreamer::serviceLocked() {
    bool did = false;
    wakeRequested_ = false;
    if (reprimePending_ && (readersActive_ == nullptr || !readersActive_->load(std::memory_order_acquire))) {
        reprimePending_ = false;
        std::vector<PrimeTarget> targets = std::move(reprimeTargets_);
        reprimeTargets_.clear();
        primeLocked(reprimePosition_, targets, kDefaultWindowSeconds);
        did = true;
    }
    // The callback's park requests, nearest first: a parked source renders silence until it is served.
    std::vector<PrimeTarget> requests;
    for (ClipSource* source : sources_) {
        const int64_t frame = source->pendingRequest();
        if (frame >= 0) requests.push_back(PrimeTarget{source, frame, source->pendingNeededAt()});
    }
    if (!requests.empty()) {
        std::stable_sort(requests.begin(), requests.end(), [](const PrimeTarget& a, const PrimeTarget& b) { return a.neededAt < b.neededAt; });
        for (const PrimeTarget& request : requests) request.source->prime(request.sourceFrame);
        did = true;
    }
    for (ClipSource* source : sources_) {
        if (source->refill() > 0) did = true;
    }
    return did;
}

bool DiskStreamer::serviceOnce() {
    std::lock_guard<std::mutex> lock(work_);
    return serviceLocked();
}

void DiskStreamer::start() {
    if (running_.load(std::memory_order_acquire)) return;
    running_.store(true, std::memory_order_release);
    thread_ = std::thread([this] { stream(); });
}

void DiskStreamer::stop() {
    running_.store(false, std::memory_order_release);
    {
        std::lock_guard<std::mutex> lock(work_);
        wakeRequested_ = true;
    }
    wake_.notify_all();
    if (thread_.joinable()) thread_.join();
}

void DiskStreamer::stream() {
    raiseStreamerPriority();
    while (running_.load(std::memory_order_acquire)) {
        std::unique_lock<std::mutex> lock(work_);
        const bool did = serviceLocked();
        if (!did) {
            wake_.wait_for(lock, std::chrono::milliseconds(kIdleSleepMillis),
                           [this] { return !running_.load(std::memory_order_acquire) || wakeRequested_; });
        }
    }
}

}  // namespace tembra
