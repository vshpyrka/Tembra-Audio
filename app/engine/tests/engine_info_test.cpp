// Catch2 tests for the engine's C ABI descriptor functions (specs/001-project-scaffold/contracts/engine-c-abi.md).
#include <catch2/catch_test_macros.hpp>

#include "tembra_engine.h"

TEST_CASE("abi version matches the header constant", "[abi]") {
    REQUIRE(tembra_engine_abi_version() == TEMBRA_ENGINE_ABI_VERSION);
    REQUIRE(TEMBRA_ENGINE_ABI_VERSION == 1u);
}
