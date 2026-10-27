// The waveform overview builder (feature 009, research R10; contracts/engine-c-abi-v6.md ".peaks file format"):
// decodes an asset once on the calling (IO) thread and writes a peak-mip file of int8 min/max pairs per channel at
// 256, 1024, 4096 and 16 384 frames per bucket, flushing the header every ten seconds of audio with the count of
// completed base buckets so the UI can draw a partial overview while a long file builds. Not traced here: the Kotlin
// caller's IO section traces the whole import.
#ifndef TEMBRA_TIMELINE_OVERVIEW_BUILDER_H
#define TEMBRA_TIMELINE_OVERVIEW_BUILDER_H

#include <cstdint>
#include <functional>

namespace tembra {

/**
 * File layout (little-endian): "TPK1", uint16 version (1), uint16 channels, uint32 levels (4), uint32 base_bucket
 * (256), int64 frames (at the source rate), uint64 completed_base_buckets; then per level ceil(frames / bucket)
 * buckets of (int8 min, int8 max) per channel, levels consecutive. A sample maps to round(x × 127) clamped to ±127.
 */
class OverviewBuilder {
public:
    static constexpr uint32_t kVersion = 1;
    static constexpr uint32_t kLevels = 4;
    static constexpr uint32_t kBaseBucket = 256;
    static constexpr uint32_t kHeaderBytes = 32;
    static constexpr double kFlushSeconds = 10.0;

    /**
     * Writes the overview of [path] to [outPath]. TEMBRA_ERR_DECODE when the file cannot be read, TEMBRA_ERR_ARGUMENT
     * when the output cannot be written. [onFlush] (tests) runs after every periodic header flush and after the last.
     */
    static int32_t build(const char* path, const char* outPath, const std::function<void()>& onFlush = {});

    static uint32_t bucketFrames(uint32_t level) { return kBaseBucket << (2 * level); }
    static uint64_t bucketsOf(int64_t frames, uint32_t level) {
        const uint64_t bucket = bucketFrames(level);
        return (static_cast<uint64_t>(frames) + bucket - 1) / bucket;
    }
};

}  // namespace tembra

#endif  // TEMBRA_TIMELINE_OVERVIEW_BUILDER_H
