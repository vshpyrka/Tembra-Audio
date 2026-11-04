// The engine's own WAV writer (feature 010, research R6): a 44-byte RIFF/PCM header with 24-bit little-endian samples,
// whose sizes the take recorder rewrites every second so an abrupt end leaves a readable file. dr_wav's writer cannot
// rewrite sizes mid-stream and miniaudio is built without encoding, hence this one. Recorder thread only, never the
// callback; a failure is reported through the return values and failed(), never thrown.
#ifndef TEMBRA_CAPTURE_WAV_WRITER_H
#define TEMBRA_CAPTURE_WAV_WRITER_H

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <vector>

namespace tembra {

class WavWriter {
public:
    static constexpr uint32_t kHeaderBytes = 44;
    static constexpr uint32_t kBitsPerSample = 24;
    static constexpr uint32_t kBytesPerSample = 3;

    WavWriter() = default;
    ~WavWriter();
    WavWriter(const WavWriter&) = delete;
    WavWriter& operator=(const WavWriter&) = delete;

    /** Creates or truncates [path] and writes the header with zero sizes; false when the file cannot be created. */
    bool open(const char* path, uint32_t channels, uint32_t sampleRate);

    /** Appends [frames] interleaved float frames as int24 (clamped at ±1.0); false, sticky, on a write failure. */
    bool write(const float* interleaved, size_t frames);

    /** Rewrites the RIFF and data sizes for the frames so far, flushes, and returns to the end; false on failure. */
    bool refreshSizes();

    /** refreshSizes() then close; false when any write failed. */
    bool finish();

    /** Closes without refreshing (an abandoned file the caller deletes). */
    void close();

    bool isOpen() const { return file_ != nullptr; }
    bool failed() const { return failed_; }
    int64_t framesWritten() const { return frames_; }
    uint32_t channels() const { return channels_; }
    uint32_t sampleRate() const { return rate_; }

    /** The 44-byte RIFF/WAVE/fmt/data header for [dataBytes] of 24-bit PCM. */
    static void encodeHeader(uint8_t out[kHeaderBytes], uint32_t channels, uint32_t sampleRate, uint64_t dataBytes);

    /** [samples] floats to little-endian int24 (3 bytes each), rounded and clamped to the int24 range. */
    static void encodeInt24(const float* in, size_t samples, uint8_t* out);

#ifdef TEMBRA_BUILD_TESTS
    /** Closes the FILE* beneath the writer so every later write and refresh fails (the write-failure path). */
    void loseFileForTest();
#endif

private:
    bool writeHeader();

    FILE* file_ = nullptr;
    uint32_t channels_ = 0;
    uint32_t rate_ = 0;
    int64_t frames_ = 0;
    bool failed_ = false;
    bool lost_ = false;
    std::vector<uint8_t> staging_;
};

}  // namespace tembra

#endif  // TEMBRA_CAPTURE_WAV_WRITER_H
