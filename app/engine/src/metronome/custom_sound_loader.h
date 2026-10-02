// Loads the app's copy of a custom click (research R4): dr_wav decode of the first 30 s, downmix to mono, then
// ClickSampleBuilder. Host thread only (file I/O, allocation).
#ifndef TEMBRA_METRONOME_CUSTOM_SOUND_LOADER_H
#define TEMBRA_METRONOME_CUSTOM_SOUND_LOADER_H

#include <cstdint>
#include <vector>

#include "metronome/click_set.h"
#include "tembra_engine.h"

namespace tembra {

class CustomSoundLoader {
public:
    /**
     * Decodes [path] and prepares it for [targetRate] into [out]. TEMBRA_ERR_ARGUMENT when the path is null or cannot
     * be opened, TEMBRA_ERR_DECODE when it is not an integer (8/16/24/32-bit) or float (32-bit) PCM WAV,
     * TEMBRA_ERR_SILENT when nothing is above −60 dBFS. [info] may be null.
     */
    static int32_t load(const char* path, uint32_t targetRate, tembra_custom_sound_info* info, ClickSet* out);

    /** The decode step alone: the first 30 s of [path], averaged to mono, and the source rate, channels and bits. */
    static int32_t decode(const char* path, std::vector<float>* mono, tembra_custom_sound_info* info);

    static constexpr uint32_t kMaxSeconds = 30;
};

}  // namespace tembra

#endif  // TEMBRA_METRONOME_CUSTOM_SOUND_LOADER_H
