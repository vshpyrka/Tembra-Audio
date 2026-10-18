// A clip source (feature 009, research R5; data-model.md §6): one decoder on an imported asset and the fifo the disk
// streamer fills ahead of the playhead so the callback only pops. Shared by every clip of the asset (the Kotlin
// registry keeps one handle per asset), so a pop names the source frame it wants and the source re-positions itself.
#ifndef TEMBRA_TIMELINE_CLIP_SOURCE_H
#define TEMBRA_TIMELINE_CLIP_SOURCE_H

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "tembra_engine.h"
#include "timeline/audio_decoder.h"
#include "timeline/spsc_fifo.h"

namespace tembra {

/**
 * Producer side (the DiskStreamer's thread, or the control thread while the readers are inactive, serialised by the
 * streamer's work mutex): prime(frame) seeks, empties the fifo and fills it; refill() tops it up. Consumer side (the
 * callback only): pop(frame, n, out) reads n frames that start at source frame [frame]. When the fifo already holds
 * that frame further on, the gap is skipped; when it does not, the source parks itself (it renders silence and touches
 * the fifo no more) and posts a request the streamer answers with a prime. A prime bumps a generation the consumer
 * acquires before it reads again, so the producer's reset never races a read. Nothing here allocates after
 * construction; pop and park are the only members the callback uses and both are bounded by the block size.
 */
class ClipSource {
public:
    static constexpr size_t kFifoFrames = 16384;
    static constexpr size_t kMaxChannels = AudioDecoder::kMaxChannels;
    static constexpr size_t kRefillFrames = 2048;  // one decode; refill() waits until at least this many frames are free

    ClipSource(std::unique_ptr<Decoder> decoder, const tembra_clip_info& info, std::string path);

    ClipSource(const ClipSource&) = delete;
    ClipSource& operator=(const ClipSource&) = delete;

    // --- Producer (streamer / control thread while the readers are inactive) -------------------------------------

    /** Positions the stream at output frame [frame] and fills the fifo; clears any request. False when the seek failed. */
    bool prime(int64_t frame);

    /** Decodes one chunk when at least kRefillFrames are free and the source is primed and not at its end; frames written. */
    size_t refill();

    /** Replaces the decoder (the stream opened at another rate); control thread, only while no stream is open. */
    void setDecoder(std::unique_ptr<Decoder> decoder);

    // --- Consumer (audio callback) ------------------------------------------------------------------------------

    /**
     * Fills [interleavedOut] with [frames] frames from source frame [frame]. True when every frame came from the
     * stream or lies past its end; false when silence stood in for missing audio (the fifo ran dry, or the source is
     * parked waiting for a prime), which is what the health counter calls starvation.
     */
    bool pop(int64_t frame, uint32_t frames, float* interleavedOut);

    /** Parks the source and asks the streamer for [frame], needed at output frame [neededAt] (ordering only). */
    void park(int64_t frame, int64_t neededAt);

    /** True when a pop at [frame] would read from the fifo (or lies past the end) rather than park. Callback only. */
    bool holds(int64_t frame);

    // --- Streamer service and telemetry (any thread) -----------------------------------------------------------

    int64_t pendingRequest() const { return request_.load(std::memory_order_acquire); }
    int64_t pendingNeededAt() const { return requestNeededAt_.load(std::memory_order_relaxed); }
    int64_t primedFrom() const { return primedFrom_.load(std::memory_order_acquire); }
    bool eof() const { return eof_.load(std::memory_order_acquire); }
    uint32_t starved() const { return starved_.load(std::memory_order_relaxed); }
    /** Frames the consumer could pop right now. */
    size_t available() const { return fifo_.available() / channels_; }

    uint32_t channels() const { return channels_; }
    uint32_t outputRate() const { return outputRate_; }
    int64_t outputFrames() const { return outputFrames_; }
    const tembra_clip_info& info() const { return info_; }
    const std::string& path() const { return path_; }
    size_t fifoFrames() const { return kFifoFrames; }

    // Scratch the transport renderer uses on the callback to find each source's first needed frame; nobody else.
    int64_t rtNeededAt = INT64_MAX;
    int64_t rtNeededFrame = -1;

private:
    size_t decodeInto(size_t frames);
    int64_t consumedSincePrime() const;
    void syncGeneration();

    std::unique_ptr<Decoder> decoder_;
    tembra_clip_info info_;
    std::string path_;
    uint32_t channels_;
    uint32_t outputRate_;
    int64_t outputFrames_;
    SpscFifo<float> fifo_;
    std::vector<float> staging_;

    // Producer-owned; primedFrom_ and eof_ are read by the consumer after it acquires generation_.
    int64_t writeFrame_ = 0;
    std::atomic<int64_t> primedFrom_{-1};
    std::atomic<bool> eof_{false};
    std::atomic<uint32_t> generation_{0};

    // Consumer → streamer.
    std::atomic<int64_t> request_{-1};
    std::atomic<int64_t> requestNeededAt_{-1};
    std::atomic<uint32_t> starved_{0};

    // Consumer-owned.
    uint32_t seenGeneration_ = 0;
    int64_t readFrame_ = 0;
    bool parked_ = true;
};

}  // namespace tembra

#endif  // TEMBRA_TIMELINE_CLIP_SOURCE_H
