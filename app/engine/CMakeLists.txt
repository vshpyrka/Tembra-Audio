cmake_minimum_required(VERSION 3.22)
project(tembra_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(TEMBRA_BUILD_TESTS "Build the Catch2 test target" OFF)
# Tracing facade (feature 007): compiled only into measurement builds (-Ptembra.perf=true passes
# -DTEMBRA_TRACING=ON from both the Android and the iOS engine builds); off, the macros in
# include/tembra_trace.h expand to nothing and no signpost name exists in the binary (FR-014).
option(TEMBRA_TRACING "Compile the tracing facade" OFF)
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
    src/tuner/level_gate.cpp
    src/tuner/tuner_analyzer.cpp
    src/tuner/tone_generator.cpp
    src/tuner/wav_decoder.cpp
    src/tuner/sample_player.cpp
    src/tuner/tuner_abi.cpp
    src/platform/null_audio_device.cpp
    src/platform/synthetic_audio_device.cpp
)
# Core, metronome, transport, timeline and mixer sources are globbed (feature 004, research R16; feature 009, R2);
# CONFIGURE_DEPENDS re-runs CMake when files appear.
file(GLOB TEMBRA_CORE_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/core/*.cpp)
file(GLOB TEMBRA_METRONOME_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/metronome/*.cpp)
file(GLOB TEMBRA_TRANSPORT_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/transport/*.cpp)
file(GLOB TEMBRA_TIMELINE_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/timeline/*.cpp)
file(GLOB TEMBRA_MIXER_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/src/mixer/*.cpp)
target_sources(tembra_engine_core PRIVATE
    ${TEMBRA_CORE_SOURCES} ${TEMBRA_METRONOME_SOURCES}
    ${TEMBRA_TRANSPORT_SOURCES} ${TEMBRA_TIMELINE_SOURCES} ${TEMBRA_MIXER_SOURCES}
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
# PUBLIC: the cinterop and JNI consumers see the same TEMBRA_TRACING value as the engine.
target_compile_definitions(tembra_engine_core PUBLIC $<$<BOOL:${TEMBRA_TRACING}>:TEMBRA_TRACING=1>)
# miniaudio contributes its resampler and, since feature 009 (research R5), its WAV, FLAC and MP3 decoders to the
# engine: no device I/O, encoding or generation.
target_compile_definitions(tembra_engine_core PRIVATE MA_NO_DEVICE_IO MA_NO_ENCODING MA_NO_GENERATION)
# The analysis thread and the synthetic device use std::thread.
find_package(Threads REQUIRED)
target_link_libraries(tembra_engine_core PUBLIC Threads::Threads)

# Oboe (Apache-2.0) is provided by Prefab from the :engine:android Gradle module on Android only.
if(ANDROID)
    find_package(oboe REQUIRED CONFIG)
    target_link_libraries(tembra_engine_core PUBLIC oboe::oboe)
    target_sources(tembra_engine_core PRIVATE src/platform/android/oboe_audio_device.cpp)
    # The tracing facade's ATrace binding (feature 007): inert stubs unless TEMBRA_TRACING is on.
    target_sources(tembra_engine_core PRIVATE src/platform/trace_android.cpp)
    if(TEMBRA_TRACING)
        target_link_libraries(tembra_engine_core PUBLIC android)
    endif()
else()
    # The os_signpost binding (iOS and the macOS host test build); inert stubs unless TEMBRA_TRACING is on.
    target_sources(tembra_engine_core PRIVATE src/platform/trace_apple.c)
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
