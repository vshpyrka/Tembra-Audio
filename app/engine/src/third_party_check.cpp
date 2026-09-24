// Compiles the vendored, permissively licensed audio libraries into the engine and exposes their versions.
// This is the single translation unit that carries their implementations (research R5).
// No audio device is touched: miniaudio is built without device I/O and without its bundled decoders
// (dr_wav is vendored separately at the version we pin).
#include "third_party_versions.h"

#define MA_NO_DEVICE_IO
#define MA_NO_WAV
#define MA_NO_FLAC
#define MA_NO_MP3
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio.h"

#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

#include "signalsmith-stretch.h"

#include <string>

namespace tembra {

namespace {

std::string stretchVersion() {
    const auto& v = signalsmith::stretch::SignalsmithStretch<float>::version;
    return std::to_string(v[0]) + "." + std::to_string(v[1]) + "." + std::to_string(v[2]);
}

}  // namespace

const char* thirdPartyVersions() {
    static const std::string versions =
        std::string("miniaudio ") + MA_VERSION_STRING +
        "; dr_wav " + DRWAV_VERSION_STRING +
        "; signalsmith-stretch " + stretchVersion();
    return versions.c_str();
}

}  // namespace tembra
