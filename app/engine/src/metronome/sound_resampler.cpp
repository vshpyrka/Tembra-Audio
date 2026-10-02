#include "metronome/sound_resampler.h"

#include <cmath>

// miniaudio's implementation lives in core/miniaudio_impl.cpp; this unit only uses its API.
#include "miniaudio.h"

namespace tembra {

std::vector<float> SoundResampler::resample(const std::vector<float>& mono, uint32_t from, uint32_t to) {
    if (from == to || mono.empty() || from == 0 || to == 0) return mono;
    ma_resampler_config config = ma_resampler_config_init(ma_format_f32, 1, from, to, ma_resample_algorithm_linear);
    config.linear.lpfOrder = kLowPassOrder;
    ma_resampler resampler;
    if (ma_resampler_init(&config, nullptr, &resampler) != MA_SUCCESS) return std::vector<float>();

    const size_t wanted = static_cast<size_t>(std::llround(static_cast<double>(mono.size()) * to / from));
    const size_t delay = static_cast<size_t>(ma_resampler_get_output_latency(&resampler));
    // Feed the input followed by enough silence to flush the filter's delay, then drop that delay from the front.
    std::vector<float> input(mono);
    input.resize(mono.size() + static_cast<size_t>(ma_resampler_get_input_latency(&resampler)) + static_cast<size_t>(from / to) * 4 + 16, 0.0f);
    std::vector<float> output(wanted + delay + 16, 0.0f);
    ma_uint64 inFrames = input.size();
    ma_uint64 outFrames = output.size();
    ma_resampler_process_pcm_frames(&resampler, input.data(), &inFrames, output.data(), &outFrames);
    ma_resampler_uninit(&resampler, nullptr);

    std::vector<float> out(wanted, 0.0f);
    for (size_t i = 0; i < wanted && i + delay < static_cast<size_t>(outFrames); ++i) out[i] = output[i + delay];
    return out;
}

}  // namespace tembra
