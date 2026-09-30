// The five built-in click sounds, synthesised at the stream rate when the output opens (research R3;
// contracts/click-sounds.md).
#ifndef TEMBRA_METRONOME_CLICK_SYNTH_H
#define TEMBRA_METRONOME_CLICK_SYNTH_H

#include <cstdint>

#include "metronome/click_sample.h"
#include "tembra_engine.h"

namespace tembra {

/**
 * Renders one level of one built-in sound: Click peaks at −9 dBFS, Accent is the recipe with its tonal partials a
 * fifth up (× 1.5) at −3 dBFS, Sub is the Click recipe at −17 dBFS. Every sample has a raised-cosine attack, an
 * exponential decay and a last 2 ms ramp to exactly zero, and lasts at most 120 ms. Host thread (allocates).
 */
class ClickSynth {
public:
    static ClickSample render(tembra_click_sound sound, tembra_click_level level, uint32_t rate);

    static constexpr double kSubDbfs = -17.0;
    static constexpr double kClickDbfs = -9.0;
    static constexpr double kAccentDbfs = -3.0;
    static constexpr double kAccentRatio = 1.5;
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_CLICK_SYNTH_H
