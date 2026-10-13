// Catch2 tests for the engine's C ABI descriptor functions (specs/001-project-scaffold/contracts/engine-c-abi.md;
// revision 6 and version 0.5.0 from specs/009-timeline-arranger/contracts/engine-c-abi-v6.md).
#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_string.hpp>

#include <regex>
#include <string>

#include "tembra_engine.h"

using Catch::Matchers::ContainsSubstring;
using Catch::Matchers::EndsWith;
using Catch::Matchers::StartsWith;

TEST_CASE("abi version matches the header constant", "[abi]") {
    REQUIRE(tembra_engine_abi_version() == TEMBRA_ENGINE_ABI_VERSION);
    REQUIRE(TEMBRA_ENGINE_ABI_VERSION == 6u);
}

TEST_CASE("name is Tembra Engine", "[descriptor]") {
    REQUIRE(std::string(tembra_engine_name()) == "Tembra Engine");
}

TEST_CASE("version is a semantic version and equals 0.5.0", "[descriptor]") {
    const std::string version = tembra_engine_version();
    REQUIRE(std::regex_match(version, std::regex(R"(\d+\.\d+\.\d+)")));
    REQUIRE(version == "0.5.0");
}

TEST_CASE("describe lists name, version and every compiled library", "[descriptor]") {
    const std::string describe = tembra_engine_describe();
    REQUIRE_THAT(describe, StartsWith("Tembra Engine 0.5.0 ("));
    REQUIRE_THAT(describe, EndsWith(")"));
    REQUIRE_THAT(describe, ContainsSubstring("miniaudio 0.11.25"));
    REQUIRE_THAT(describe, ContainsSubstring("dr_wav 0.14.5"));
    REQUIRE_THAT(describe, ContainsSubstring("signalsmith-stretch 1.1.0"));
#ifdef __ANDROID__
    REQUIRE_THAT(describe, ContainsSubstring("oboe 1.11.0"));
#else
    REQUIRE_THAT(describe, !ContainsSubstring("oboe"));
#endif
}

TEST_CASE("descriptor pointers are stable across calls", "[descriptor]") {
    REQUIRE(tembra_engine_describe() == tembra_engine_describe());
    REQUIRE(tembra_engine_name() == tembra_engine_name());
}
