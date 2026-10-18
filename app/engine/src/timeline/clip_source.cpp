#include "timeline/clip_source.h"

#include <algorithm>
#include <cstring>

namespace tembra {

ClipSource::ClipSource(std::unique_ptr<Decoder> decoder, const tembra_clip_info& info, std::string path)
    : decoder_(std::move(decoder)),
      info_(info),
      path_(std::move(path)),
      channels_(std::max<uint32_t>(1, std::min<uint32_t>(decoder_->channels(), static_cast<uint32_t>(kMaxChannels)))),
      outputRate_(decoder_->outputRate()),
      outputFrames_(decoder_->outputFrames()),
      fifo_(kFifoFrames * channels_),
      staging_(kRefillFrames * channels_) {}

// --- Producer -------------------------------------------------------------------------------------------------------

int64_t ClipSource::consumedSincePrime() const {
    return writeFrame_ - primedFrom_.load(std::memory_order_relaxed) - static_cast<int64_t>(fifo_.available() / channels_);
}

bool ClipSource::prime(int64_t frame) {
    request_.store(-1, std::memory_order_relaxed);
    requestNeededAt_.store(-1, std::memory_order_relaxed);
    if (frame == primedFrom_.load(std::memory_order_relaxed) && consumedSincePrime() == 0) return true;  // still there
    // The consumer is parked or inactive (the caller's contract), so the fifo may be emptied by its producer.
    fifo_.reset();
    writeFrame_ = frame;
    const bool seekable = frame >= 0 && frame < outputFrames_ && decoder_->seek(frame);
    if (!seekable) {
        // Past the end (or a decoder that refuses): the source has nothing more; pops render silence without starving.
        eof_.store(true, std::memory_order_release);
        primedFrom_.store(frame < 0 ? -1 : frame, std::memory_order_release);
        generation_.fetch_add(1, std::memory_order_release);
        return frame >= 0 && frame <= outputFrames_;
    }
    eof_.store(false, std::memory_order_release);
    for (;;) {
        const size_t freeFrames = fifo_.freeSpace() / channels_;
        if (freeFrames == 0 || eof_.load(std::memory_order_relaxed)) break;
        decodeInto(std::min(freeFrames, kRefillFrames));
    }
    primedFrom_.store(frame, std::memory_order_release);
    generation_.fetch_add(1, std::memory_order_release);
    return true;
}

size_t ClipSource::refill() {
    if (primedFrom_.load(std::memory_order_relaxed) < 0 || eof_.load(std::memory_order_relaxed)) return 0;
    if (fifo_.freeSpace() / channels_ < kRefillFrames) return 0;
    return decodeInto(kRefillFrames);
}

size_t ClipSource::decodeInto(size_t frames) {
    const size_t got = decoder_->read(staging_.data(), frames);
    if (got > 0) {
        fifo_.write(staging_.data(), got * channels_);  // whole frames: the caller sized [frames] to the free space
        writeFrame_ += static_cast<int64_t>(got);
    }
    if (got < frames) eof_.store(true, std::memory_order_release);  // after the data: the consumer sees both in order
    return got;
}

void ClipSource::setDecoder(std::unique_ptr<Decoder> decoder) {
    decoder_ = std::move(decoder);
    outputRate_ = decoder_->outputRate();
    outputFrames_ = decoder_->outputFrames();
    fifo_.reset();
    writeFrame_ = 0;
    eof_.store(false, std::memory_order_release);
    primedFrom_.store(-1, std::memory_order_release);
    request_.store(-1, std::memory_order_relaxed);
    generation_.fetch_add(1, std::memory_order_release);  // the consumer re-parks on its next pop
}

// --- Consumer -------------------------------------------------------------------------------------------------------

void ClipSource::park(int64_t frame, int64_t neededAt) {
    parked_ = true;
    requestNeededAt_.store(neededAt, std::memory_order_relaxed);
    request_.store(frame, std::memory_order_release);
}

bool ClipSource::pop(int64_t frame, uint32_t frames, float* out) {
    const size_t samples = static_cast<size_t>(frames) * channels_;
    const uint32_t generation = generation_.load(std::memory_order_acquire);
    if (generation != seenGeneration_) {
        seenGeneration_ = generation;
        const int64_t primed = primedFrom_.load(std::memory_order_relaxed);
        readFrame_ = primed;
        parked_ = primed < 0;
    }
    if (parked_) {
        std::memset(out, 0, samples * sizeof(float));
        starved_.fetch_add(1, std::memory_order_relaxed);
        return false;
    }
    // eof before the head: when the end is already known, the frames up to it are already in the fifo.
    const bool eof = eof_.load(std::memory_order_acquire);
    size_t availableFrames = fifo_.available() / channels_;
    if (frame != readFrame_) {
        const bool ahead = frame > readFrame_;
        const int64_t gap = ahead ? frame - readFrame_ : 0;
        if (ahead && gap <= static_cast<int64_t>(availableFrames)) {
            fifo_.skip(static_cast<size_t>(gap) * channels_);
            readFrame_ = frame;
            availableFrames -= static_cast<size_t>(gap);
        } else if (ahead && eof) {
            // Beyond everything the source holds: silence, not a miss.
            fifo_.skip(availableFrames * channels_);
            readFrame_ = frame;
            availableFrames = 0;
        } else {
            park(frame, 0);
            std::memset(out, 0, samples * sizeof(float));
            starved_.fetch_add(1, std::memory_order_relaxed);
            return false;
        }
    }
    const size_t got = std::min<size_t>(frames, availableFrames);
    if (got > 0) {
        fifo_.read(out, got * channels_);
        readFrame_ += static_cast<int64_t>(got);
    }
    if (got < frames) {
        std::memset(out + got * channels_, 0, (samples - got * channels_) * sizeof(float));
        if (!eof) {
            starved_.fetch_add(1, std::memory_order_relaxed);
            return false;
        }
    }
    return true;
}

}  // namespace tembra
