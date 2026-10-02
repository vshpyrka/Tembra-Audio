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
    tone_generator_test.cpp
    sample_player_test.cpp
    recordings_on_pitch_test.cpp
    triple_buffer_test.cpp
    click_synth_test.cpp
    click_sample_builder_test.cpp
    custom_sound_loader_test.cpp
    metronome_renderer_test.cpp
)
target_include_directories(tembra_engine_tests PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../src
    ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/signalsmith-stretch
    ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/dr_libs
)
target_compile_definitions(tembra_engine_tests PRIVATE TEMBRA_BUILD_TESTS)
if(TEMBRA_TUNER_RECORDINGS_DIR)
    target_compile_definitions(tembra_engine_tests PRIVATE TEMBRA_TUNER_RECORDINGS_DIR="${TEMBRA_TUNER_RECORDINGS_DIR}")
endif()
target_link_libraries(tembra_engine_tests PRIVATE tembra_engine_core Catch2::Catch2WithMain)

list(APPEND CMAKE_MODULE_PATH ${catch2_SOURCE_DIR}/extras)
include(Catch)
catch_discover_tests(tembra_engine_tests)
