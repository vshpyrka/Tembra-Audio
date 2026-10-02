#include "metronome/click_sample_builder.h"

#include <algorithm>
#include <cmath>

#include "metronome/sound_resampler.h"

namespace tembra {

namespace {

constexpr double kPi = 3.14159265358979323846;

double fromDb(double db) { return std::pow(10.0, db / 20.0); }

size_t framesFor(double ms, uint32_t rate) { return static_cast<size_t>(std::llround(ms * static_cast<double>(rate) / 1000.0)); }

float peakOf(const std::vector<float>& pcm) {
    float peak = 0.0f;
    for (float s : pcm) peak = std::max(peak, std::abs(s));
    return peak;
}

ClickSample scaled(const std::vector<float>& pcm, double gain, uint32_t rate) {
    ClickSample sample;
    sample.rate = rate;
    sample.pcm.resize(pcm.size());
    for (size_t i = 0; i < pcm.size(); ++i) sample.pcm[i] = static_cast<float>(static_cast<double>(pcm[i]) * gain);
    return sample;
}

}  // namespace

int32_t ClickSampleBuilder::prepare(const std::vector<float>& mono, uint32_t sourceRate, uint32_t targetRate, tembra_custom_sound_info* info,
                                    ClickSet* out) {
    if (out == nullptr || sourceRate == 0 || targetRate == 0) return TEMBRA_ERR_ARGUMENT;
    const float peak = peakOf(mono);
    if (mono.empty() || static_cast<double>(peak) < fromDb(kSilentDbfs)) return TEMBRA_ERR_SILENT;

    // Onset: the trailing 2 ms RMS reaches −40 dB relative to the peak.
    const size_t window = std::max<size_t>(1, framesFor(kRmsWindowMs, sourceRate));
    const double threshold = static_cast<double>(peak) * fromDb(kOnsetDbRePeak);
    const double thresholdSum = threshold * threshold * static_cast<double>(window);
    double sum = 0.0;
    size_t onset = 0;
    for (size_t i = 0; i < mono.size(); ++i) {
        sum += static_cast<double>(mono[i]) * mono[i];
        if (i >= window) sum -= static_cast<double>(mono[i - window]) * mono[i - window];
        if (sum >= thresholdSum) {
            onset = i;
            break;
        }
    }
    const size_t backUp = framesFor(kBackUpMs, sourceRate);
    const size_t start = onset > backUp ? onset - backUp : 0;

    // End: the last frame above −60 dB re peak, capped 2 s after the onset.
    const float endThreshold = static_cast<float>(static_cast<double>(peak) * fromDb(kEndDbRePeak));
    size_t soundEnd = mono.size();
    while (soundEnd > onset + 1 && std::abs(mono[soundEnd - 1]) < endThreshold) --soundEnd;
    const size_t end = std::min(soundEnd, onset + framesFor(kMaxKeptMs, sourceRate));

    std::vector<float> kept(mono.begin() + static_cast<std::ptrdiff_t>(start), mono.begin() + static_cast<std::ptrdiff_t>(end));
    const size_t fadeIn = std::min(kept.size(), framesFor(kFadeInMs, sourceRate));
    for (size_t n = 0; n < fadeIn; ++n) {
        kept[n] = static_cast<float>(kept[n] * (0.5 - 0.5 * std::cos(kPi * static_cast<double>(n) / static_cast<double>(fadeIn))));
    }
    const size_t sound = end - onset;
    const size_t fullFade = framesFor(kFadeOutMs, sourceRate);
    const size_t fadeOut = std::min(kept.size(), sound < fullFade ? std::max<size_t>(1, sound / 2) : fullFade);
    const size_t fadeStart = kept.size() - fadeOut;
    for (size_t j = 0; j < fadeOut; ++j) {
        const double gain = 0.5 + 0.5 * std::cos(kPi * static_cast<double>(j + 1) / static_cast<double>(fadeOut));
        kept[fadeStart + j] = static_cast<float>(kept[fadeStart + j] * gain);
    }
    kept.back() = 0.0f;

    if (info != nullptr) {
        info->source_rate = sourceRate;
        info->kept_frames = static_cast<uint32_t>(kept.size());
        info->trimmed_ms = static_cast<float>(static_cast<double>(start) * 1000.0 / sourceRate);
        info->kept_ms = static_cast<float>(static_cast<double>(kept.size()) * 1000.0 / sourceRate);
    }

    std::vector<float> resampled = SoundResampler::resample(kept, sourceRate, targetRate);
    if (resampled.empty()) return TEMBRA_ERR_SILENT;
    resampled.back() = 0.0f;
    const float resampledPeak = peakOf(resampled);
    if (resampledPeak <= 0.0f) return TEMBRA_ERR_SILENT;

    const double clickGain = fromDb(kClickDbfs) / resampledPeak;
    const double accentGain = std::min(clickGain * fromDb(kAccentBoostDb), fromDb(kAccentLimitDbfs) / resampledPeak);
    const double subGain = clickGain * fromDb(kSubCutDb);
    out->click = scaled(resampled, clickGain, targetRate);
    out->accent = scaled(resampled, accentGain, targetRate);
    out->sub = scaled(resampled, subGain, targetRate);
    return TEMBRA_OK;
}

}  // namespace tembra
