// Audio file decoding for the timeline (feature 009, research R5, R11): the Decoder interface the clip sources and
// the tests program against, and AudioDecoder over miniaudio's ma_decoder (its bundled dr_wav, dr_flac and dr_mp3
// backends) producing 32-bit float at the stream rate. Never used on the audio callback: the streamer thread reads,
// the callback only pops the fifo a clip source fills.
#ifndef TEMBRA_TIMELINE_AUDIO_DECODER_H
#define TEMBRA_TIMELINE_AUDIO_DECODER_H

#include <cstddef>
#include <cstdint>
#include <memory>

#include "tembra_engine.h"

namespace tembra {

/** A seekable stream of interleaved float frames at one output rate. */
class Decoder {
public:
    virtual ~Decoder() = default;

    virtual uint32_t channels() const = 0;
    virtual uint32_t outputRate() const = 0;
    /** Length at the output rate (the source's frames scaled when the decoder resamples). */
    virtual int64_t outputFrames() const = 0;
    /** Positions the next read at output frame [frame]; false when the frame is outside the stream. */
    virtual bool seek(int64_t frame) = 0;
    /** Decodes up to [frames] frames into [interleaved]; fewer only at the end. */
    virtual size_t read(float* interleaved, size_t frames) = 0;
};

/**
 * miniaudio decoder on a file. open() reports the source's native channels, rate, frames and format in a
 * tembra_clip_info and converts to [outputRate] (0 keeps the native rate); MP3 decoders are created with a seek table
 * so seeks never scan the file. inspect() answers the offline tembra_audio_inspect: a short-lived native-rate decoder
 * plus a read of the last frame on WAV and FLAC, which is how a file truncated behind an intact header is refused.
 */
class AudioDecoder final : public Decoder {
public:
    static constexpr uint32_t kMp3SeekPoints = 1000;
    static constexpr uint32_t kMaxChannels = 8;

    static int32_t inspect(const char* path, tembra_audio_info& info);
    static std::unique_ptr<AudioDecoder> open(const char* path, uint32_t outputRate, tembra_clip_info* info, int32_t* error);

    ~AudioDecoder() override;
    AudioDecoder(const AudioDecoder&) = delete;
    AudioDecoder& operator=(const AudioDecoder&) = delete;

    uint32_t channels() const override;
    uint32_t outputRate() const override;
    int64_t outputFrames() const override;
    bool seek(int64_t frame) override;
    size_t read(float* interleaved, size_t frames) override;

    uint32_t sourceRate() const;
    int64_t sourceFrames() const;
    uint32_t format() const;

    struct Impl;  // the ma_decoder and what was learnt about the file; defined in audio_decoder.cpp only

private:
    explicit AudioDecoder(std::unique_ptr<Impl> impl);
    std::unique_ptr<Impl> impl_;
};

}  // namespace tembra

#endif  // TEMBRA_TIMELINE_AUDIO_DECODER_H
