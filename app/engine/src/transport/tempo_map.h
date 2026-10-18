// The project's one tempo and signature, converting beats to frames at render time (feature 009, research R3;
// Constitution V: everything musical is stored in beats). POD so it travels through a TripleBuffer.
#ifndef TEMBRA_TRANSPORT_TEMPO_MAP_H
#define TEMBRA_TRANSPORT_TEMPO_MAP_H

#include <cmath>
#include <cstdint>

namespace tembra {

/**
 * The tempo counts the signature's unit note (a quarter in x/4, an eighth in x/8), exactly as the metronome grid does
 * (feature 004, FR-007), so the click's framesPerTick and the transport's framesPerBeat agree to the frame. The beat
 * unit therefore only labels the ruler; it never scales the frames per beat.
 */
struct TempoMap {
    double bpm = 120.0;
    uint32_t beatsPerBar = 4;
    uint32_t beatUnit = 4;  // 4 or 8
    uint32_t rate = 48000;

    double framesPerBeat() const { return 60.0 / bpm * static_cast<double>(rate); }

    /** The nearest frame of [beats]; rounding happens once, here, so a clip starts on one exact sample. */
    int64_t beatsToFrame(double beats) const { return std::llround(beats * framesPerBeat()); }

    double frameToBeats(int64_t frame) const { return static_cast<double>(frame) / framesPerBeat(); }

    /** 0-based bar index of [beats]; never negative. */
    uint32_t barOf(double beats) const {
        if (!(beats > 0.0)) return 0;
        return static_cast<uint32_t>(std::floor(beats / static_cast<double>(beatsPerBar)));
    }

    /** 0-based position inside the bar, fractional. */
    double beatInBar(double beats) const {
        if (!(beats > 0.0)) return 0.0;
        return beats - static_cast<double>(barOf(beats)) * static_cast<double>(beatsPerBar);
    }

    bool operator==(const TempoMap& other) const {
        return bpm == other.bpm && beatsPerBar == other.beatsPerBar && beatUnit == other.beatUnit && rate == other.rate;
    }
    bool operator!=(const TempoMap& other) const { return !(*this == other); }
};

}  // namespace tembra

#endif  // TEMBRA_TRANSPORT_TEMPO_MAP_H
