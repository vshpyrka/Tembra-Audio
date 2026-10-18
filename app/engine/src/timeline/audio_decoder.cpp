#include "timeline/audio_decoder.h"

#include <cstdio>
#include <cstring>

#include "miniaudio.h"
#include "tembra_trace.h"
#include "tembra_trace_names.h"

namespace tembra {

struct AudioDecoder::Impl {
    ma_decoder decoder{};
    bool initialised = false;
    uint32_t channels = 0;
    uint32_t outputRate = 0;
    uint32_t sourceRate = 0;
    int64_t sourceFrames = 0;
    int64_t outputFrames = 0;
    uint32_t format = TEMBRA_FORMAT_UNKNOWN;

    ~Impl() {
        if (initialised) ma_decoder_uninit(&decoder);
    }
};

namespace {

struct Probe {
    ma_encoding_format encoding;
    uint32_t format;
};

// Each backend is tried on its own so the format is known without reaching into miniaudio's private vtables.
constexpr Probe kProbes[] = {
    {ma_encoding_format_wav, TEMBRA_FORMAT_WAV},
    {ma_encoding_format_flac, TEMBRA_FORMAT_FLAC},
    {ma_encoding_format_mp3, TEMBRA_FORMAT_MP3},
};

/** Opens [path] with the first backend that accepts it; false when none does or the stream is unusable. */
bool initDecoder(const char* path, uint32_t outputRate, uint32_t mp3SeekPoints, AudioDecoder::Impl& impl) {
    for (const Probe& probe : kProbes) {
        ma_decoder_config config = ma_decoder_config_init(ma_format_f32, 0, outputRate);
        config.encodingFormat = probe.encoding;
        config.seekPointCount = probe.encoding == ma_encoding_format_mp3 ? mp3SeekPoints : 0;
        if (ma_decoder_init_file(path, &config, &impl.decoder) != MA_SUCCESS) continue;
        impl.initialised = true;
        impl.format = probe.format;
        ma_uint32 channels = 0;
        ma_uint32 sourceRate = 0;
        ma_uint64 sourceLength = 0;
        ma_uint64 outputLength = 0;
        if (ma_data_source_get_data_format(impl.decoder.pBackend, nullptr, &channels, &sourceRate, nullptr, 0) != MA_SUCCESS ||
            ma_data_source_get_length_in_pcm_frames(impl.decoder.pBackend, &sourceLength) != MA_SUCCESS ||
            ma_decoder_get_length_in_pcm_frames(&impl.decoder, &outputLength) != MA_SUCCESS || channels == 0 ||
            channels > AudioDecoder::kMaxChannels || sourceRate == 0 || sourceLength == 0) {
            ma_decoder_uninit(&impl.decoder);
            impl.initialised = false;
            return false;
        }
        impl.channels = channels;
        impl.sourceRate = sourceRate;
        impl.outputRate = impl.decoder.outputSampleRate;
        impl.sourceFrames = static_cast<int64_t>(sourceLength);
        impl.outputFrames = static_cast<int64_t>(outputLength);
        return true;
    }
    return false;
}

uint32_t readLe32(const unsigned char* p) {
    return static_cast<uint32_t>(p[0]) | (static_cast<uint32_t>(p[1]) << 8) | (static_cast<uint32_t>(p[2]) << 16) | (static_cast<uint32_t>(p[3]) << 24);
}

/**
 * A RIFF WAV cut behind an intact header still opens (dr_wav clamps the data chunk to the file), so the header's own
 * promise is checked here: the data chunk it declares must fit in the file. Other containers (RF64, W64) and a
 * streaming 0xFFFFFFFF size are accepted as they are; FLAC and MP3 count their frames from the stream itself.
 */
bool wavDataChunkFits(const char* path) {
    FILE* file = std::fopen(path, "rb");
    if (file == nullptr) return false;
    bool fits = true;
    unsigned char header[12];
    if (std::fread(header, 1, sizeof(header), file) == sizeof(header) && std::memcmp(header, "RIFF", 4) == 0 && std::memcmp(header + 8, "WAVE", 4) == 0) {
        std::fseek(file, 0, SEEK_END);
        const long fileSize = std::ftell(file);
        long offset = 12;
        for (int chunk = 0; chunk < 64 && offset + 8 <= fileSize; ++chunk) {  // bounded walk over the chunk list
            unsigned char chunkHeader[8];
            std::fseek(file, offset, SEEK_SET);
            if (std::fread(chunkHeader, 1, sizeof(chunkHeader), file) != sizeof(chunkHeader)) break;
            const uint32_t size = readLe32(chunkHeader + 4);
            if (std::memcmp(chunkHeader, "data", 4) == 0) {
                if (size != 0xFFFFFFFFu && static_cast<int64_t>(offset) + 8 + static_cast<int64_t>(size) > static_cast<int64_t>(fileSize)) fits = false;
                break;
            }
            offset += 8 + static_cast<long>(size) + static_cast<long>(size & 1u);
        }
    }
    std::fclose(file);
    return fits;
}

}  // namespace

int32_t AudioDecoder::inspect(const char* path, tembra_audio_info& info) {
    if (path == nullptr) return TEMBRA_ERR_ARGUMENT;
    Impl impl;
    if (!initDecoder(path, 0, 0, impl)) return TEMBRA_ERR_DECODE;
    if (impl.format == TEMBRA_FORMAT_WAV && !wavDataChunkFits(path)) return TEMBRA_ERR_DECODE;
    info.format = impl.format;
    info.channels = impl.channels;
    info.sample_rate = impl.sourceRate;
    info.frames = impl.sourceFrames;
    return TEMBRA_OK;
}

std::unique_ptr<AudioDecoder> AudioDecoder::open(const char* path, uint32_t outputRate, tembra_clip_info* info, int32_t* error) {
    TEMBRA_TRACE_SCOPE(TEMBRA_TRACE_ENGINE_LOAD_CLIP);
    if (path == nullptr) {
        if (error != nullptr) *error = TEMBRA_ERR_ARGUMENT;
        return nullptr;
    }
    auto impl = std::make_unique<Impl>();
    if (!initDecoder(path, outputRate, kMp3SeekPoints, *impl)) {
        if (error != nullptr) *error = TEMBRA_ERR_DECODE;
        return nullptr;
    }
    if (info != nullptr) {
        info->channels = impl->channels;
        info->sample_rate = impl->sourceRate;
        info->frames = impl->sourceFrames;
        info->format = impl->format;
    }
    if (error != nullptr) *error = TEMBRA_OK;
    return std::unique_ptr<AudioDecoder>(new AudioDecoder(std::move(impl)));
}

AudioDecoder::AudioDecoder(std::unique_ptr<Impl> impl) : impl_(std::move(impl)) {}

AudioDecoder::~AudioDecoder() = default;

uint32_t AudioDecoder::channels() const { return impl_->channels; }

uint32_t AudioDecoder::outputRate() const { return impl_->outputRate; }

int64_t AudioDecoder::outputFrames() const { return impl_->outputFrames; }

uint32_t AudioDecoder::sourceRate() const { return impl_->sourceRate; }

int64_t AudioDecoder::sourceFrames() const { return impl_->sourceFrames; }

uint32_t AudioDecoder::format() const { return impl_->format; }

bool AudioDecoder::seek(int64_t frame) {
    if (frame < 0 || frame > impl_->outputFrames) return false;
    return ma_decoder_seek_to_pcm_frame(&impl_->decoder, static_cast<ma_uint64>(frame)) == MA_SUCCESS;
}

size_t AudioDecoder::read(float* interleaved, size_t frames) {
    ma_uint64 read = 0;
    const ma_result result = ma_decoder_read_pcm_frames(&impl_->decoder, interleaved, frames, &read);
    if (result != MA_SUCCESS && result != MA_AT_END) return 0;
    return static_cast<size_t>(read);
}

}  // namespace tembra
