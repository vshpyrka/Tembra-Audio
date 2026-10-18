#include "timeline/arrangement_snapshot.h"

#include <algorithm>
#include <cmath>

namespace tembra {

namespace {

int64_t scaleFrames(int64_t frames, uint32_t sourceRate, uint32_t outputRate) {
    if (sourceRate == outputRate || sourceRate == 0) return frames;
    return std::llround(static_cast<double>(frames) * static_cast<double>(outputRate) / static_cast<double>(sourceRate));
}

}  // namespace

int32_t ArrangementSnapshot::validate(const tembra_arrangement& in, ClipSource* const* sources, size_t sourceCount) {
    if (in.track_count > TEMBRA_MAX_TRACKS || in.clip_count > TEMBRA_MAX_CLIPS) return TEMBRA_ERR_ARGUMENT;
    for (uint32_t t = 0; t < in.track_count; ++t) {
        if (!std::isfinite(in.tracks[t].gain_linear)) return TEMBRA_ERR_ARGUMENT;
    }
    for (uint32_t i = 0; i < in.clip_count; ++i) {
        const tembra_clip_state& clip = in.clips[i];
        if (clip.track >= in.track_count) return TEMBRA_ERR_ARGUMENT;
        if (clip.source < 0 || static_cast<size_t>(clip.source) >= sourceCount || sources[clip.source] == nullptr) return TEMBRA_ERR_ARGUMENT;
        if (!std::isfinite(clip.start_beats) || clip.start_beats < 0.0) return TEMBRA_ERR_ARGUMENT;
        if (clip.offset_frames < 0 || clip.length_frames <= 0) return TEMBRA_ERR_ARGUMENT;
        if (clip.offset_frames + clip.length_frames > sources[clip.source]->info().frames) return TEMBRA_ERR_ARGUMENT;
    }
    return TEMBRA_OK;
}

void ArrangementSnapshot::fill(const tembra_arrangement& in, ClipSource* const* sources, size_t sourceCount, uint32_t outputRate, uint64_t newVersion) {
    version = newVersion;
    trackCount = std::min<uint32_t>(in.track_count, TEMBRA_MAX_TRACKS);
    clipCount = std::min<uint32_t>(in.clip_count, TEMBRA_MAX_CLIPS);
    for (uint32_t t = 0; t < trackCount; ++t) {
        tracks[t].gain = in.tracks[t].gain_linear;
        tracks[t].audible = in.tracks[t].audible != 0 ? 1u : 0u;
    }
    for (uint32_t i = 0; i < clipCount; ++i) {
        const tembra_clip_state& clip = in.clips[i];
        ClipState& out = clips[i];
        out.track = clip.track;
        out.sourceHandle = clip.source;
        out.source = clip.source >= 0 && static_cast<size_t>(clip.source) < sourceCount ? sources[clip.source] : nullptr;
        out.startBeats = clip.start_beats;
        out.offsetFrames = clip.offset_frames;
        out.lengthFrames = clip.length_frames;
        out.sourceRate = out.source != nullptr ? out.source->info().sample_rate : outputRate;
        out.outOffset = scaleFrames(clip.offset_frames, out.sourceRate, outputRate);
        out.outLength = std::max<int64_t>(1, scaleFrames(clip.length_frames, out.sourceRate, outputRate));
    }
}

void ArrangementSnapshot::sortClipsByTrackAndStart() {
    std::stable_sort(clips, clips + clipCount, [](const ClipState& a, const ClipState& b) {
        if (a.track != b.track) return a.track < b.track;
        return a.startBeats < b.startBeats;
    });
}

bool ArrangementSnapshot::references(const ClipSource* source) const {
    for (uint32_t i = 0; i < clipCount; ++i) {
        if (clips[i].source == source) return true;
    }
    return false;
}

}  // namespace tembra
