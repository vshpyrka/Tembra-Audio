include(FetchContent)
FetchContent_Declare(
    Catch2
    GIT_REPOSITORY https://github.com/catchorg/Catch2.git
    GIT_TAG v3.16.0
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(Catch2)

add_executable(tembra_engine_tests
    engine_info_test.cpp
    seqlock_test.cpp
    ring_buffer_test.cpp
    engine_core_state_test.cpp
    mpm_test.cpp
    band_pass_test.cpp
    tuner_analyzer_test.cpp
    level_gate_test.cpp
    tone_generator_test.cpp
    sample_player_test.cpp
    recordings_on_pitch_test.cpp
    triple_buffer_test.cpp
    click_synth_test.cpp
    click_sample_builder_test.cpp
    custom_sound_loader_test.cpp
    metronome_renderer_test.cpp
    audio_health_test.cpp
    trace_poison_test.cpp
    trace_shim_test.cpp
    tempo_map_test.cpp
    transport_clock_test.cpp
    spsc_fifo_test.cpp
    audio_decoder_test.cpp
    clip_source_test.cpp
    disk_streamer_test.cpp
    track_strip_test.cpp
    master_bus_test.cpp
    transport_renderer_test.cpp
)
target_include_directories(tembra_engine_tests PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../src
    ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/signalsmith-stretch
    ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/dr_libs
)
target_compile_definitions(tembra_engine_tests PRIVATE TEMBRA_BUILD_TESTS)
# The committed audio fixtures of feature 009 (specs/009-timeline-arranger/fixtures/README.md).
target_compile_definitions(tembra_engine_tests PRIVATE
    TEMBRA_FIXTURES_DIR="${CMAKE_CURRENT_SOURCE_DIR}/../../../specs/009-timeline-arranger/fixtures")
if(TEMBRA_TUNER_RECORDINGS_DIR)
    target_compile_definitions(tembra_engine_tests PRIVATE TEMBRA_TUNER_RECORDINGS_DIR="${TEMBRA_TUNER_RECORDINGS_DIR}")
endif()
target_link_libraries(tembra_engine_tests PRIVATE tembra_engine_core Catch2::Catch2WithMain)

list(APPEND CMAKE_MODULE_PATH ${catch2_SOURCE_DIR}/extras)
include(Catch)
catch_discover_tests(tembra_engine_tests)

# --- Real-time guard (feature 007, contracts/tracing-facade.md) ---------------------------------------------------
# The real-time sources (the callback dispatcher, the renderers the callback drives, the capture feed, the devices)
# compile a second time with the tracing facade poisoned: any use of it in these units fails this build.
set(TEMBRA_RT_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/core/engine_core.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/core/audio_health.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/metronome/metronome_renderer.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/metronome/click_synth.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/tuner/tone_generator.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/tuner/sample_player.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/tuner/tuner_analyzer.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/platform/null_audio_device.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/platform/synthetic_audio_device.cpp
    # Feature 009: the clip source the callback pops, the mixer strip and bus, and the transport renderer.
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/timeline/clip_source.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/mixer/track_strip.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/mixer/master_bus.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/transport/transport_renderer.cpp
)
add_library(tembra_rt_poison OBJECT ${TEMBRA_RT_SOURCES})
target_include_directories(tembra_rt_poison PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../include
    ${CMAKE_CURRENT_SOURCE_DIR}/../src
    ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/miniaudio
    ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/dr_libs
    ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/signalsmith-stretch
)
target_compile_definitions(tembra_rt_poison PRIVATE TEMBRA_BUILD_TESTS TEMBRA_TRACING=1 TEMBRA_TRACE_POISON=1
    MA_NO_DEVICE_IO MA_NO_ENCODING MA_NO_GENERATION)
target_link_libraries(tembra_rt_poison PRIVATE Threads::Threads)
add_dependencies(tembra_engine_tests tembra_rt_poison)

# The negative check: a unit that traces under the poison must NOT compile.
try_compile(TEMBRA_POISON_NEGATIVE_COMPILED
    ${CMAKE_CURRENT_BINARY_DIR}/poison_negative
    ${CMAKE_CURRENT_SOURCE_DIR}/trace_poison_negative.cpp
    CMAKE_FLAGS "-DINCLUDE_DIRECTORIES=${CMAKE_CURRENT_SOURCE_DIR}/../include"
    COMPILE_DEFINITIONS -DTEMBRA_TRACING=1 -DTEMBRA_TRACE_POISON=1
    CXX_STANDARD 17
)
if(TEMBRA_POISON_NEGATIVE_COMPILED)
    set(TEMBRA_POISON_NEGATIVE_FAILED 0)
else()
    set(TEMBRA_POISON_NEGATIVE_FAILED 1)
endif()
target_compile_definitions(tembra_engine_tests PRIVATE TEMBRA_POISON_NEGATIVE_FAILED=${TEMBRA_POISON_NEGATIVE_FAILED})
