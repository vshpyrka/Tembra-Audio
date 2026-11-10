// A short history of per-block peaks (feature 012, research R6): the capture meter's 64 ms rule generalised. The
// published peak is the largest over the blocks that fit the window, so a reader at the display rate never misses a
// transient that fell between two of its frames. Sixteen entries cover the window down to 4 ms blocks; shorter blocks
// shorten the window, never the correctness. Header-only, no allocation (used from TEMBRA_RT_SOURCES).
#ifndef TEMBRA_MIXER_RECENT_PEAK_H
#define TEMBRA_MIXER_RECENT_PEAK_H

#include <array>
#include <cstdint>

namespace tembra {

class RecentPeak {
public:
    static constexpr uint32_t kEntries = 16;

    /** The window in frames (64 ms at the stream rate). */
    void setWindowFrames(uint32_t frames) { windowFrames_ = frames; }

    void push(float peak, uint32_t frames) {
        entries_[next_] = Entry{peak, frames};
        next_ = (next_ + 1) % kEntries;
    }

    /** The newest entry always counts; older ones while the frames before them stay within the window. */
    float max() const {
        float peak = 0.0f;
        uint32_t covered = 0;
        for (uint32_t k = 0; k < kEntries; ++k) {
            const Entry& entry = entries_[(next_ + kEntries - 1 - k) % kEntries];
            if (entry.frames == 0) break;
            if (k > 0 && covered >= windowFrames_) break;
            if (entry.peak > peak) peak = entry.peak;
            covered += entry.frames;
        }
        return peak;
    }

    void reset() {
        for (Entry& entry : entries_) entry = Entry{};
        next_ = 0;
    }

private:
    struct Entry {
        float peak = 0.0f;
        uint32_t frames = 0;
    };

    std::array<Entry, kEntries> entries_{};
    uint32_t next_ = 0;
    uint32_t windowFrames_ = 3072;
};

}  // namespace tembra

#endif  // TEMBRA_MIXER_RECENT_PEAK_H
