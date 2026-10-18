#include "timeline/overview_builder.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <memory>
#include <vector>

#include "tembra_engine.h"
#include "timeline/audio_decoder.h"

namespace tembra {

namespace {

constexpr size_t kChunkFrames = 65536;

int8_t quantise(float x) { return static_cast<int8_t>(std::clamp<long>(std::lround(x * 127.0f), -127, 127)); }

/** One pyramid level: the finished buckets, the bucket being accumulated and where the level lives in the file. */
struct Level {
    uint64_t buckets = 0;          // expected from the header's frame count
    std::vector<int8_t> data;      // (min, max) per channel per finished bucket
    std::vector<int8_t> lo;        // running minimum per channel of the open bucket
    std::vector<int8_t> hi;
    uint32_t children = 0;         // finished children (base: frames) in the open bucket
    uint64_t writtenBytes = 0;     // bytes of data already in the file
    long offset = 0;               // file offset of the level's region

    void open(uint32_t channels) {
        lo.assign(channels, 127);
        hi.assign(channels, -127);
        children = 0;
    }
};

class Writer {
public:
    Writer(FILE* file, uint32_t channels, int64_t frames) : file_(file), channels_(channels), frames_(frames) {
        long offset = static_cast<long>(OverviewBuilder::kHeaderBytes);
        for (uint32_t l = 0; l < OverviewBuilder::kLevels; ++l) {
            levels_[l].buckets = OverviewBuilder::bucketsOf(frames, l);
            levels_[l].data.reserve(static_cast<size_t>(levels_[l].buckets) * channels * 2);
            levels_[l].offset = offset;
            levels_[l].open(channels);
            offset += static_cast<long>(levels_[l].buckets * channels * 2);
        }
        baseLo_.assign(channels, 1.0f);
        baseHi_.assign(channels, -1.0f);
    }

    /** Folds [frames] interleaved frames into the open base bucket, closing buckets as they fill. */
    void push(const float* samples, size_t frames) {
        for (size_t i = 0; i < frames; ++i) {
            const float* frame = samples + i * channels_;
            for (uint32_t c = 0; c < channels_; ++c) {
                baseLo_[c] = std::min(baseLo_[c], frame[c]);
                baseHi_[c] = std::max(baseHi_[c], frame[c]);
            }
            if (++baseCount_ == OverviewBuilder::kBaseBucket) closeBase();
        }
    }

    /** Closes the partial buckets at every level (the end of the file). */
    void finish() {
        if (baseCount_ > 0) closeBase();
        for (uint32_t l = 1; l < OverviewBuilder::kLevels; ++l) {
            if (levels_[l].children > 0) closeLevel(l);
        }
    }

    /** Appends the new data of every level and rewrites the header; false when the file refuses. */
    bool flush() {
        for (Level& level : levels_) {
            const size_t have = level.data.size();
            if (have > level.writtenBytes) {
                if (std::fseek(file_, level.offset + static_cast<long>(level.writtenBytes), SEEK_SET) != 0) return false;
                if (std::fwrite(level.data.data() + level.writtenBytes, 1, have - level.writtenBytes, file_) != have - level.writtenBytes) return false;
                level.writtenBytes = have;
            }
        }
        if (!writeHeader(levels_[0].data.size() / (static_cast<size_t>(channels_) * 2))) return false;
        return std::fflush(file_) == 0;
    }

    /** The decoded length differed from the header's promise: rebuild the whole file from memory with the real count. */
    bool rewriteFor(int64_t actualFrames) {
        frames_ = actualFrames;
        if (std::fseek(file_, 0, SEEK_SET) != 0) return false;
        if (!writeHeader(levels_[0].data.size() / (static_cast<size_t>(channels_) * 2))) return false;
        for (Level& level : levels_) {
            if (std::fwrite(level.data.data(), 1, level.data.size(), file_) != level.data.size()) return false;
        }
        return std::fflush(file_) == 0;
    }

    uint64_t baseBuckets() const { return levels_[0].data.size() / (static_cast<size_t>(channels_) * 2); }

private:
    bool writeHeader(uint64_t completed) {
        unsigned char header[OverviewBuilder::kHeaderBytes];
        std::memcpy(header, "TPK1", 4);
        const uint16_t version = OverviewBuilder::kVersion;
        const uint16_t channels = static_cast<uint16_t>(channels_);
        const uint32_t levels = OverviewBuilder::kLevels;
        const uint32_t base = OverviewBuilder::kBaseBucket;
        std::memcpy(header + 4, &version, 2);
        std::memcpy(header + 6, &channels, 2);
        std::memcpy(header + 8, &levels, 4);
        std::memcpy(header + 12, &base, 4);
        std::memcpy(header + 16, &frames_, 8);
        std::memcpy(header + 24, &completed, 8);
        if (std::fseek(file_, 0, SEEK_SET) != 0) return false;
        return std::fwrite(header, 1, sizeof(header), file_) == sizeof(header);
    }

    void closeBase() {
        Level& base = levels_[0];
        for (uint32_t c = 0; c < channels_; ++c) {
            base.lo[c] = quantise(baseLo_[c]);
            base.hi[c] = quantise(baseHi_[c]);
            baseLo_[c] = 1.0f;
            baseHi_[c] = -1.0f;
        }
        baseCount_ = 0;
        closeLevel(0);
    }

    /** Appends level [l]'s open bucket, feeds it to the parent, and closes the parent when it has four children. */
    void closeLevel(uint32_t l) {
        Level& level = levels_[l];
        for (uint32_t c = 0; c < channels_; ++c) {
            level.data.push_back(level.lo[c]);
            level.data.push_back(level.hi[c]);
        }
        if (l + 1 < OverviewBuilder::kLevels) {
            Level& parent = levels_[l + 1];
            for (uint32_t c = 0; c < channels_; ++c) {
                parent.lo[c] = std::min(parent.lo[c], level.lo[c]);
                parent.hi[c] = std::max(parent.hi[c], level.hi[c]);
            }
            if (++parent.children == 4) closeLevel(l + 1);
        }
        level.open(channels_);
    }

    FILE* file_;
    uint32_t channels_;
    int64_t frames_;
    Level levels_[OverviewBuilder::kLevels];
    std::vector<float> baseLo_;
    std::vector<float> baseHi_;
    uint32_t baseCount_ = 0;
};

}  // namespace

int32_t OverviewBuilder::build(const char* path, const char* outPath, const std::function<void()>& onFlush) {
    if (path == nullptr || outPath == nullptr) return TEMBRA_ERR_ARGUMENT;
    int32_t error = TEMBRA_OK;
    tembra_clip_info info{};
    std::unique_ptr<AudioDecoder> decoder = AudioDecoder::open(path, 0, &info, &error);  // the source's own rate
    if (!decoder) return TEMBRA_ERR_DECODE;
    FILE* file = std::fopen(outPath, "wb");
    if (file == nullptr) return TEMBRA_ERR_ARGUMENT;

    const uint32_t channels = decoder->channels();
    const int64_t expectedFrames = decoder->outputFrames();
    const int64_t flushEvery = static_cast<int64_t>(kFlushSeconds * decoder->outputRate());
    Writer writer(file, channels, expectedFrames);
    std::vector<float> chunk(kChunkFrames * channels);
    int64_t decoded = 0;
    int64_t nextFlushAt = flushEvery;
    bool ok = writer.flush();  // the header with nothing completed, so a reader finds a valid file at once
    while (ok) {
        const size_t got = decoder->read(chunk.data(), kChunkFrames);
        if (got == 0) break;
        // A chunk that crosses a ten-second mark is split there, so the header is never later than promised.
        size_t pushed = 0;
        while (ok && pushed < got) {
            const size_t until = static_cast<size_t>(std::min<int64_t>(static_cast<int64_t>(got - pushed), nextFlushAt - decoded));
            writer.push(chunk.data() + pushed * channels, until);
            pushed += until;
            decoded += static_cast<int64_t>(until);
            if (decoded >= nextFlushAt) {
                nextFlushAt += flushEvery;
                ok = writer.flush();
                if (ok && onFlush) onFlush();
            }
        }
    }
    if (ok) {
        writer.finish();
        ok = decoded == expectedFrames ? writer.flush() : writer.rewriteFor(decoded);
        if (ok && onFlush) onFlush();
    }
    const bool closed = std::fclose(file) == 0;
    if (!ok || !closed) {
        std::remove(outPath);
        return TEMBRA_ERR_ARGUMENT;
    }
    return TEMBRA_OK;
}

}  // namespace tembra
