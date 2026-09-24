// Descriptor functions of the Tembra engine C ABI.
// Only tembra_engine_abi_version() is implemented by the foundational phase; name, version and
// describe are added by the "native engine link" story (tasks T045–T049).
#include "tembra_engine.h"

extern "C" uint32_t tembra_engine_abi_version(void) {
    return TEMBRA_ENGINE_ABI_VERSION;
}
