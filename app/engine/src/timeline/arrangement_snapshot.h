// The engine-side arrangement (feature 009, research R3; data-model.md §6): a POD mirror of tembra_arrangement with
// the clip sources resolved and the clip offsets converted to the stream rate, validated and sorted on the control
// thread, published through a TripleBuffer and only read on the callback. Clip starts stay in beats (Constitution V).
#ifndef TEMBRA_TIMELINE_ARRANGEMENT_SNAPSHOT_H
#define TEMBRA_TIMELINE_ARRANGEMENT_SNAPSHOT_H

#include <cstddef>
#include <cstdint>

#include "tembra_engine.h"
#include "timeline/clip_source.h"

namespace tembra {

struct TrackState {
    float gain = 1.0f;
    uint32_t audible = 1;
};

/** The fast path of tembra_transport_set_track: the whole table travels so two quick changes never lose one. */
struct TrackTable {
    uint64_t version = 0;
    uint32_t count = 0;
    TrackState tracks[TEMBRA_MAX_TRACKS];
};

struct ClipState {
    uint32_t track = 0;
    int32_t sourceHandle = -1;
    ClipSource* source = nullptr;
    double startBeats = 0.0;
    int64_t offsetFrames = 0;  // at the source rate, as the document stores it
    int64_t lengthFrames = 0;  // at the source rate
    uint32_t sourceRate = 0;
    int64_t outOffset = 0;     // at the stream rate (the decoder resamples)
    int64_t outLength = 0;     // at the stream rate
};

struct ArrangementSnapshot {
    uint64_t version = 0;
    uint32_t trackCount = 0;
    uint32_t clipCount = 0;
    TrackState tracks[TEMBRA_MAX_TRACKS];
    ClipState clips[TEMBRA_MAX_CLIPS];

    /**
     * The contract's validation (ERR_ARGUMENT): counts within bounds, every track < track_count, every source an open
     * handle of [sources] (nullptr = closed), offset + length within the source's frames, start_beats ≥ 0, gains
     * finite. [sources] is indexed by handle.
     */
    static int32_t validate(const tembra_arrangement& in, ClipSource* const* sources, size_t sourceCount);

    /** Copies a validated [in] at [outputRate] with [version]; clips keep their order until sortClipsByTrackAndStart(). */
    void fill(const tembra_arrangement& in, ClipSource* const* sources, size_t sourceCount, uint32_t outputRate, uint64_t version);

    /** Orders the clips by track, then start; the renderer cuts an earlier clip at the next one's start (FR-027). */
    void sortClipsByTrackAndStart();

    bool references(const ClipSource* source) const;
};

}  // namespace tembra

#endif  // TEMBRA_TIMELINE_ARRANGEMENT_SNAPSHOT_H
