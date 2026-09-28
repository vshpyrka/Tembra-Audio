cmake_minimum_required(VERSION 3.22)
project(tembra_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(TEMBRA_BUILD_TESTS "Build the Catch2 test target" OFF)
# Directory of drop-in reference recordings checked on pitch by recordings_on_pitch_test (feature 003, research R5).
set(TEMBRA_TUNER_RECORDINGS_DIR "" CACHE PATH "Directory scanned for <instrument>-<note>.wav recordings by the tests")

# The engine core is always a static library. Its archive is named libtembra_engine.a on every
# platform so the Kotlin/Native cinterop definition (staticLibraries = libtembra_engine.a) matches;
# on Android the :engine:android module wraps it in a shared library of the same name.
add_library(tembra_engine_core STATIC
    src/engine_info.cpp
    src/third_party_check.cpp
    src/tuner/band_pass.cpp
    src/tuner/mcleod_pitch_detector.cpp
    src/tuner/pitch_smoother.cpp
    src/tuner/tuner_analyzer.cpp
    src/tuner/tone_generator.cpp
    src/tuner/wav_decoder.cpp
    src/tuner/sample_player.cpp
    src/tuner/tuner_engine.cpp
    src/tuner/tuner_abi.cpp
    src/platform/null_audio_device.cpp
    src/platform/synthetic_audio_device.cpp
)
set_target_properties(tembra_engine_core PROPERTIES OUTPUT_NAME tembra_engine)
target_include_directories(tembra_engine_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_include_directories(tembra_engine_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/miniaudio
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/dr_libs
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/signalsmith-stretch
)
# The test hook that swaps the platform device exists only in test builds.
target_compile_definitions(tembra_engine_core PRIVATE $<$<BOOL:${TEMBRA_BUILD_TESTS}>:TEMBRA_BUILD_TESTS>)
# The analysis thread and the synthetic device use std::thread.
find_package(Threads REQUIRED)
target_link_libraries(tembra_engine_core PUBLIC Threads::Threads)

# Oboe (Apache-2.0) is provided by Prefab from the :engine:android Gradle module on Android only.
if(ANDROID)
    find_package(oboe REQUIRED CONFIG)
    target_link_libraries(tembra_engine_core PUBLIC oboe::oboe)
    target_sources(tembra_engine_core PRIVATE src/platform/android/oboe_audio_device.cpp)
endif()

if(IOS)
    # RemoteIO through the Core Audio C API (research R3); the frameworks propagate to the Kotlin/Native link.
    target_sources(tembra_engine_core PRIVATE src/platform/apple/remote_io_audio_device.cpp)
    target_link_libraries(tembra_engine_core PUBLIC "-framework AudioToolbox" "-framework CoreAudio")
endif()

if(TEMBRA_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
