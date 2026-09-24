cmake_minimum_required(VERSION 3.22)
project(tembra_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(TEMBRA_BUILD_TESTS "Build the Catch2 test target" OFF)

# The engine core is always a static library. Its archive is named libtembra_engine.a on every
# platform so the Kotlin/Native cinterop definition (staticLibraries = libtembra_engine.a) matches;
# on Android the :engine:android module wraps it in a shared library of the same name.
add_library(tembra_engine_core STATIC
    src/engine_info.cpp
    src/third_party_check.cpp
)
set_target_properties(tembra_engine_core PROPERTIES OUTPUT_NAME tembra_engine)
target_include_directories(tembra_engine_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_include_directories(tembra_engine_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/miniaudio
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/dr_libs
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/signalsmith-stretch
)

# Oboe (Apache-2.0) is provided by Prefab from the :engine:android Gradle module on Android only.
if(ANDROID)
    find_package(oboe REQUIRED CONFIG)
    target_link_libraries(tembra_engine_core PUBLIC oboe::oboe)
endif()

if(TEMBRA_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
