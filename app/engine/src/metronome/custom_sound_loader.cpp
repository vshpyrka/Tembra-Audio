#include "metronome/custom_sound_loader.h"

#include <cstdio>

#include "metronome/click_sample_builder.h"

// dr_wav's implementation lives in third_party_check.cpp; this unit only uses its API.
#include "dr_wav.h"

namespace tembra {

namespace {

bool acceptedEncoding(const drwav& wav) {
    if (wav.translatedFormatTag == DR_WAVE_FORMAT_IEEE_FLOAT) return wav.bitsPerSample == 32;
    if (wav.translatedFormatTag != DR_WAVE_FORMAT_PCM) return false;
    return wav.bitsPerSample == 8 || wav.bitsPerSample == 16 || wav.bitsPerSample == 24 || wav.bitsPerSample == 32;
}

}  // namespace

int32_t CustomSoundLoader::decode(const char* path, std::vector<float>* mono, tembra_custom_sound_info* info) {
    if (path == nullptr || mono == nullptr) return TEMBRA_ERR_ARGUMENT;
    std::FILE* probe = std::fopen(path, "rb");
    if (probe == nullptr) return TEMBRA_ERR_ARGUMENT;
    std::fclose(probe);

    drwav wav;
    if (!drwav_init_file(&wav, path, nullptr)) return TEMBRA_ERR_DECODE;
    if (!acceptedEncoding(wav) || wav.channels == 0 || wav.sampleRate == 0 || wav.totalPCMFrameCount == 0) {
        drwav_uninit(&wav);
        return TEMBRA_ERR_DECODE;
    }
    const drwav_uint64 cap = static_cast<drwav_uint64>(kMaxSeconds) * wav.sampleRate;
    const size_t frames = static_cast<size_t>(wav.totalPCMFrameCount < cap ? wav.totalPCMFrameCount : cap);
    const size_t channels = wav.channels;
    std::vector<float> interleaved(frames * channels);
    const size_t read = static_cast<size_t>(drwav_read_pcm_frames_f32(&wav, frames, interleaved.data()));
    if (info != nullptr) {
        info->source_rate = wav.sampleRate;
        info->source_channels = wav.channels;
        info->source_bits = wav.bitsPerSample;
    }
    drwav_uninit(&wav);
    if (read == 0) return TEMBRA_ERR_DECODE;

    mono->assign(read, 0.0f);
    for (size_t i = 0; i < read; ++i) {
        float sum = 0.0f;
        for (size_t c = 0; c < channels; ++c) sum += interleaved[i * channels + c];
        (*mono)[i] = sum / static_cast<float>(channels);
    }
    return TEMBRA_OK;
}

int32_t CustomSoundLoader::load(const char* path, uint32_t targetRate, tembra_custom_sound_info* info, ClickSet* out) {
    if (out == nullptr) return TEMBRA_ERR_ARGUMENT;
    tembra_custom_sound_info local{};
    std::vector<float> mono;
    const int32_t decoded = decode(path, &mono, &local);
    if (decoded != TEMBRA_OK) return decoded;
    const int32_t prepared = ClickSampleBuilder::prepare(mono, local.source_rate, targetRate, &local, out);
    if (prepared != TEMBRA_OK) return prepared;
    if (info != nullptr) *info = local;
    return TEMBRA_OK;
}

}  // namespace tembra
