// Descriptor functions of the Tembra engine C ABI (contracts/engine-c-abi.md).
#include "tembra_engine.h"

#include <string>

#include "third_party_versions.h"

#ifdef __ANDROID__
#include <oboe/Oboe.h>
#endif

#define TEMBRA_ENGINE_NAME "Tembra Engine"
#define TEMBRA_ENGINE_VERSION "0.1.0"

namespace {

std::string platformLibraries() {
#ifdef __ANDROID__
    return std::string("oboe ") + oboe::Version::Text + "; ";
#else
    return std::string();
#endif
}

}  // namespace

extern "C" {

uint32_t tembra_engine_abi_version(void) {
    return TEMBRA_ENGINE_ABI_VERSION;
}

const char* tembra_engine_name(void) {
    return TEMBRA_ENGINE_NAME;
}

const char* tembra_engine_version(void) {
    return TEMBRA_ENGINE_VERSION;
}

const char* tembra_engine_describe(void) {
    static const std::string description =
        std::string(TEMBRA_ENGINE_NAME " " TEMBRA_ENGINE_VERSION " (") + platformLibraries() +
        tembra::thirdPartyVersions() + ")";
    return description.c_str();
}

}  // extern "C"
