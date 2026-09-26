// WAV bytes to mono float frames with dr_wav (research R5); runs on the caller's thread, never on a callback.
#ifndef TEMBRA_TUNER_WAV_DECODER_H
#define TEMBRA_TUNER_WAV_DECODER_H

#include <cstddef>
#include <cstdint>
#include <optional>
#include <vector>

namespace tembra {

struct DecodedSample {
    std::vector<float> mono;
    uint32_t sampleRate = 0;
};

class WavDecoder {
public:
    /** Decodes PCM 16/24/32-bit or float WAV, averaging channels to mono; empty on anything dr_wav rejects. */
    static std::optional<DecodedSample> decode(const uint8_t* bytes, size_t length);
};

}  // namespace tembra

#endif  // TEMBRA_TUNER_WAV_DECODER_H
