#include "capture/wav_writer.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace tembra {

namespace {

void putU32(uint8_t* at, uint32_t value) {
    at[0] = static_cast<uint8_t>(value & 0xFF);
    at[1] = static_cast<uint8_t>((value >> 8) & 0xFF);
    at[2] = static_cast<uint8_t>((value >> 16) & 0xFF);
    at[3] = static_cast<uint8_t>((value >> 24) & 0xFF);
}

void putU16(uint8_t* at, uint16_t value) {
    at[0] = static_cast<uint8_t>(value & 0xFF);
    at[1] = static_cast<uint8_t>((value >> 8) & 0xFF);
}

constexpr float kInt24Full = 8388608.0f;  // 2^23

}  // namespace

WavWriter::~WavWriter() { close(); }

void WavWriter::encodeHeader(uint8_t out[kHeaderBytes], uint32_t channels, uint32_t sampleRate, uint64_t dataBytes) {
    const uint32_t data = dataBytes > 0xFFFFFFFFull - 36u ? 0xFFFFFFFFu - 36u : static_cast<uint32_t>(dataBytes);
    const uint16_t blockAlign = static_cast<uint16_t>(channels * kBytesPerSample);
    std::memcpy(out, "RIFF", 4);
    putU32(out + 4, 36u + data);
    std::memcpy(out + 8, "WAVE", 4);
    std::memcpy(out + 12, "fmt ", 4);
    putU32(out + 16, 16);
    putU16(out + 20, 1);  // PCM
    putU16(out + 22, static_cast<uint16_t>(channels));
    putU32(out + 24, sampleRate);
    putU32(out + 28, sampleRate * blockAlign);
    putU16(out + 32, blockAlign);
    putU16(out + 34, kBitsPerSample);
    std::memcpy(out + 36, "data", 4);
    putU32(out + 40, data);
}

void WavWriter::encodeInt24(const float* in, size_t samples, uint8_t* out) {
    for (size_t i = 0; i < samples; ++i) {
        const float clamped = std::clamp(in[i], -1.0f, 1.0f);
        // Rounded to the nearest code; +1.0 lands on the top code, -1.0 on the bottom one.
        const int32_t value = static_cast<int32_t>(std::clamp<float>(std::nearbyint(clamped * kInt24Full), -8388608.0f, 8388607.0f));
        const uint32_t bits = static_cast<uint32_t>(value);
        out[i * 3] = static_cast<uint8_t>(bits & 0xFF);
        out[i * 3 + 1] = static_cast<uint8_t>((bits >> 8) & 0xFF);
        out[i * 3 + 2] = static_cast<uint8_t>((bits >> 16) & 0xFF);
    }
}

bool WavWriter::open(const char* path, uint32_t channels, uint32_t sampleRate) {
    close();
    if (path == nullptr || channels == 0 || channels > 2 || sampleRate == 0) return false;
    file_ = std::fopen(path, "wb");
    if (file_ == nullptr) return false;
    channels_ = channels;
    rate_ = sampleRate;
    frames_ = 0;
    failed_ = false;
    lost_ = false;
    if (!writeHeader()) {
        failed_ = true;
        close();
        return false;
    }
    return true;
}

bool WavWriter::writeHeader() {
    uint8_t header[kHeaderBytes];
    encodeHeader(header, channels_, rate_, static_cast<uint64_t>(frames_) * channels_ * kBytesPerSample);
    return std::fwrite(header, 1, kHeaderBytes, file_) == kHeaderBytes;
}

bool WavWriter::write(const float* interleaved, size_t frames) {
    if (file_ == nullptr || lost_ || failed_) {
        failed_ = true;
        return false;
    }
    const size_t samples = frames * channels_;
    if (staging_.size() < samples * kBytesPerSample) staging_.resize(samples * kBytesPerSample);
    encodeInt24(interleaved, samples, staging_.data());
    if (std::fwrite(staging_.data(), 1, samples * kBytesPerSample, file_) != samples * kBytesPerSample) {
        failed_ = true;
        return false;
    }
    frames_ += static_cast<int64_t>(frames);
    return true;
}

bool WavWriter::refreshSizes() {
    if (file_ == nullptr || lost_ || failed_) {
        failed_ = true;
        return false;
    }
    if (std::fflush(file_) != 0 || std::fseek(file_, 0, SEEK_SET) != 0 || !writeHeader() || std::fflush(file_) != 0 || std::fseek(file_, 0, SEEK_END) != 0) {
        failed_ = true;
        return false;
    }
    return true;
}

bool WavWriter::finish() {
    const bool refreshed = refreshSizes();
    bool closed = true;
    if (file_ != nullptr && !lost_) closed = std::fclose(file_) == 0;
    file_ = nullptr;
    if (!closed) failed_ = true;
    return refreshed && closed && !failed_;
}

void WavWriter::close() {
    if (file_ != nullptr && !lost_) std::fclose(file_);
    file_ = nullptr;
}

#ifdef TEMBRA_BUILD_TESTS
void WavWriter::loseFileForTest() {
    if (file_ != nullptr && !lost_) std::fclose(file_);
    lost_ = true;  // file_ stays non-null so isOpen() still reports open, as a file lost under a live writer would
}
#endif

}  // namespace tembra
