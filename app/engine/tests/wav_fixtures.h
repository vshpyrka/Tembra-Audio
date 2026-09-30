// Test-only WAV writer (dr_wav) so the decoder, resampler and loop path can be exercised without bundled recordings.
#ifndef TEMBRA_TESTS_WAV_FIXTURES_H
#define TEMBRA_TESTS_WAV_FIXTURES_H

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstdio>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>

#include "dr_wav.h"

namespace tembra::fixtures {

/** Writes [samples] (interleaved, [channels]) as a PCM WAV of [bits] bits at [sampleRate]; returns false on failure. */
inline bool writeTempWav(const std::string& path, uint32_t sampleRate, uint32_t channels, uint32_t bits, const std::vector<float>& samples) {
    drwav_data_format format{};
    format.container = drwav_container_riff;
    format.format = bits == 32 ? DR_WAVE_FORMAT_IEEE_FLOAT : DR_WAVE_FORMAT_PCM;
    format.channels = channels;
    format.sampleRate = sampleRate;
    format.bitsPerSample = bits;
    drwav wav;
    if (!drwav_init_file_write(&wav, path.c_str(), &format, nullptr)) return false;
    const drwav_uint64 frames = samples.size() / channels;
    drwav_uint64 written = 0;
    if (bits == 16) {
        std::vector<drwav_int16> pcm(samples.size());
        drwav_f32_to_s16(pcm.data(), samples.data(), samples.size());
        written = drwav_write_pcm_frames(&wav, frames, pcm.data());
    } else if (bits == 24) {
        std::vector<drwav_uint8> pcm(samples.size() * 3);
        for (size_t i = 0; i < samples.size(); ++i) {
            const double clamped = samples[i] > 1.0f ? 1.0 : (samples[i] < -1.0f ? -1.0 : samples[i]);
            const int32_t value = static_cast<int32_t>(clamped * 8388607.0);
            pcm[i * 3] = static_cast<drwav_uint8>(value & 0xFF);
            pcm[i * 3 + 1] = static_cast<drwav_uint8>((value >> 8) & 0xFF);
            pcm[i * 3 + 2] = static_cast<drwav_uint8>((value >> 16) & 0xFF);
        }
        written = drwav_write_pcm_frames(&wav, frames, pcm.data());
    } else {
        written = drwav_write_pcm_frames(&wav, frames, samples.data());
    }
    drwav_uninit(&wav);
    return written == frames;
}

/** Sample encodings the custom-sound loader accepts (spec FR-023): 8/16/24/32-bit integer PCM and 32-bit float. */
enum class WavEncoding { Pcm8, Pcm16, Pcm24, Pcm32, Float32 };

inline uint32_t bitsOf(WavEncoding encoding) {
    switch (encoding) {
        case WavEncoding::Pcm8: return 8;
        case WavEncoding::Pcm16: return 16;
        case WavEncoding::Pcm24: return 24;
        case WavEncoding::Pcm32: return 32;
        case WavEncoding::Float32: return 32;
    }
    return 16;
}

/**
 * Writes [samples] (interleaved, [channels]) at [sampleRate] in [encoding]; integers are rounded to the nearest code
 * (8-bit is unsigned with 128 as zero, as WAV requires). Returns false on failure.
 */
inline bool writeWav(const std::string& path, uint32_t sampleRate, uint32_t channels, WavEncoding encoding, const std::vector<float>& samples) {
    drwav_data_format format{};
    format.container = drwav_container_riff;
    format.format = encoding == WavEncoding::Float32 ? DR_WAVE_FORMAT_IEEE_FLOAT : DR_WAVE_FORMAT_PCM;
    format.channels = channels;
    format.sampleRate = sampleRate;
    format.bitsPerSample = bitsOf(encoding);
    drwav wav;
    if (!drwav_init_file_write(&wav, path.c_str(), &format, nullptr)) return false;
    const drwav_uint64 frames = samples.size() / channels;
    const auto clamp = [](float x) { return x > 1.0f ? 1.0 : (x < -1.0f ? -1.0 : static_cast<double>(x)); };
    std::vector<drwav_uint8> bytes;
    const uint32_t width = bitsOf(encoding) / 8;
    bytes.reserve(samples.size() * width);
    for (float sample : samples) {
        const double x = clamp(sample);
        switch (encoding) {
            case WavEncoding::Pcm8:
                bytes.push_back(static_cast<drwav_uint8>(std::lround((x + 1.0) * 127.5)));
                break;
            case WavEncoding::Pcm16:
            case WavEncoding::Pcm24:
            case WavEncoding::Pcm32: {
                const double full = std::ldexp(1.0, static_cast<int>(bitsOf(encoding)) - 1);
                const double scaled = std::min(full - 1.0, std::round(x * full));
                const int64_t value = static_cast<int64_t>(scaled);
                for (uint32_t b = 0; b < width; ++b) bytes.push_back(static_cast<drwav_uint8>((static_cast<uint64_t>(value) >> (8 * b)) & 0xFF));
                break;
            }
            case WavEncoding::Float32: {
                const float f = static_cast<float>(x);
                const auto* raw = reinterpret_cast<const drwav_uint8*>(&f);
                bytes.insert(bytes.end(), raw, raw + 4);
                break;
            }
        }
    }
    const drwav_uint64 written = drwav_write_pcm_frames(&wav, frames, bytes.data());
    drwav_uninit(&wav);
    return written == frames;
}

/** Writes a minimal RIFF/WAVE header whose fmt chunk carries [formatTag] (2 = Microsoft ADPCM) and [dataBytes] of zeros. */
inline bool writeTaggedWav(const std::string& path, uint16_t formatTag, uint32_t sampleRate, uint16_t channels, uint16_t bits, uint32_t dataBytes) {
    std::vector<uint8_t> out;
    const auto u32 = [&out](uint32_t v) { for (int b = 0; b < 4; ++b) out.push_back(static_cast<uint8_t>((v >> (8 * b)) & 0xFF)); };
    const auto u16 = [&out](uint16_t v) { out.push_back(static_cast<uint8_t>(v & 0xFF)); out.push_back(static_cast<uint8_t>(v >> 8)); };
    const auto tag = [&out](const char* t) { out.insert(out.end(), t, t + 4); };
    const uint16_t blockAlign = static_cast<uint16_t>(std::max(1, channels * bits / 8));
    tag("RIFF");
    u32(4 + 8 + 16 + 8 + dataBytes);
    tag("WAVE");
    tag("fmt ");
    u32(16);
    u16(formatTag);
    u16(channels);
    u32(sampleRate);
    u32(sampleRate * blockAlign);
    u16(blockAlign);
    u16(bits);
    tag("data");
    u32(dataBytes);
    out.insert(out.end(), dataBytes, 0);
    std::ofstream file(path, std::ios::binary);
    file.write(reinterpret_cast<const char*>(out.data()), static_cast<std::streamsize>(out.size()));
    return static_cast<bool>(file);
}

inline std::vector<uint8_t> readFile(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    return std::vector<uint8_t>((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
}

inline std::string tempPath(const std::string& name) {
    const char* dir = std::getenv("TMPDIR");
    return std::string(dir != nullptr ? dir : "/tmp") + "/" + name;
}

}  // namespace tembra::fixtures

#endif  // TEMBRA_TESTS_WAV_FIXTURES_H
