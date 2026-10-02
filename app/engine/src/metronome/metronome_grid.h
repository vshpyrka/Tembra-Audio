// The validated engine-side copy of tembra_metronome_grid (research R2; data-model.md §6).
#ifndef TEMBRA_METRONOME_METRONOME_GRID_H
#define TEMBRA_METRONOME_METRONOME_GRID_H

#include <cmath>
#include <cstdint>
#include <cstring>

#include "tembra_engine.h"

namespace tembra {

/** POD mirror of the ABI struct. The default is 120 BPM, four beats, Accent then three Clicks. */
struct MetronomeGrid {
    static constexpr uint32_t kMaxTicks = 32;

    double bpm = 120.0;
    uint32_t ticksPerBeat = 1;
    uint32_t ticksPerBar = 4;
    uint8_t levels[kMaxTicks] = {TEMBRA_CLICK_ACCENT, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK, TEMBRA_CLICK_CLICK};

    /** bpm 40–240, ticks per beat 1–4, ticks per bar a whole number of beats up to 32, every used level ≤ Accent. */
    static bool validate(const tembra_metronome_grid& grid) {
        if (!std::isfinite(grid.bpm) || grid.bpm < 40.0 || grid.bpm > 240.0) return false;
        if (grid.ticks_per_beat < 1 || grid.ticks_per_beat > 4) return false;
        if (grid.ticks_per_bar < 1 || grid.ticks_per_bar > kMaxTicks) return false;
        if (grid.ticks_per_bar % grid.ticks_per_beat != 0) return false;
        for (uint32_t i = 0; i < grid.ticks_per_bar; ++i) {
            if (grid.levels[i] > TEMBRA_CLICK_ACCENT) return false;
        }
        return true;
    }

    /** Copies a grid that passed validate(). */
    static MetronomeGrid from(const tembra_metronome_grid& grid) {
        MetronomeGrid out;
        out.bpm = grid.bpm;
        out.ticksPerBeat = grid.ticks_per_beat;
        out.ticksPerBar = grid.ticks_per_bar;
        std::memcpy(out.levels, grid.levels, sizeof(out.levels));
        return out;
    }

    /** 60 / bpm × rate / ticksPerBeat: the tempo counts the signature's lower-number note (research R1). */
    double framesPerTick(uint32_t rate) const { return 60.0 / bpm * static_cast<double>(rate) / static_cast<double>(ticksPerBeat); }

    double framesPerBeat(uint32_t rate) const { return 60.0 / bpm * static_cast<double>(rate); }

    uint32_t beats() const { return ticksPerBar / ticksPerBeat; }

    bool sameLevels(const MetronomeGrid& other) const { return std::memcmp(levels, other.levels, sizeof(levels)) == 0; }

    bool operator==(const MetronomeGrid& other) const {
        return bpm == other.bpm && ticksPerBeat == other.ticksPerBeat && ticksPerBar == other.ticksPerBar && sameLevels(other);
    }
    bool operator!=(const MetronomeGrid& other) const { return !(*this == other); }
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_METRONOME_GRID_H
