// Test-only WAV writer (dr_wav) so the decoder, resampler and loop path can be exercised without bundled recordings.
#ifndef TEMBRA_TESTS_WAV_FIXTURES_H
#define TEMBRA_TESTS_WAV_FIXTURES_H

#include <cstdint>
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
