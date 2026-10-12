#include "tuner/wav_decoder.h"

// dr_wav's implementation lives in third_party_check.cpp; this unit only uses its API.
#include "dr_wav.h"
// The engine's non-real-time loader path carries the catalogue's engine.loadClip section (feature 007); this unit
// never runs on the audio thread, so the facade may be used here.
#include "tembra_trace.h"
#include "tembra_trace_names.h"

namespace tembra {

std::optional<DecodedSample> WavDecoder::decode(const uint8_t* bytes, size_t length) {
    TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_ENGINE_LOAD_CLIP);
    if (bytes == nullptr || length == 0) return std::nullopt;
    drwav wav;
    if (!drwav_init_memory(&wav, bytes, length, nullptr)) return std::nullopt;
    if (wav.channels == 0 || wav.sampleRate == 0 || wav.totalPCMFrameCount == 0) {
        drwav_uninit(&wav);
        return std::nullopt;
    }
    const size_t frames = static_cast<size_t>(wav.totalPCMFrameCount);
    const size_t channels = wav.channels;
    std::vector<float> interleaved(frames * channels);
    const drwav_uint64 read = drwav_read_pcm_frames_f32(&wav, frames, interleaved.data());
    const uint32_t sampleRate = wav.sampleRate;
    drwav_uninit(&wav);
    if (read == 0) return std::nullopt;
    DecodedSample sample;
    sample.sampleRate = sampleRate;
    sample.mono.resize(static_cast<size_t>(read));
    for (size_t i = 0; i < sample.mono.size(); ++i) {
        float sum = 0.0f;
        for (size_t c = 0; c < channels; ++c) sum += interleaved[i * channels + c];
        sample.mono[i] = sum / static_cast<float>(channels);
    }
    return sample;
}

}  // namespace tembra
