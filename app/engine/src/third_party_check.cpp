// Compiles the vendored, permissively licensed audio libraries into the engine and exposes their versions.
// This unit carries dr_wav's implementation (research R5); miniaudio's lives in core/miniaudio_impl.cpp (feature 004,
// research R16) and is included here for its version string only.
#include "third_party_versions.h"

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
