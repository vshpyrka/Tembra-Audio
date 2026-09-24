cmake_minimum_required(VERSION 3.22)
project(tembra_engine_android LANGUAGES C CXX)

# The shared engine core (static). It links Oboe itself under if(ANDROID).
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../.. tembra_engine_core_build)

# The shared library Kotlin loads with System.loadLibrary("tembra_engine"): core + JNI shim.
add_library(tembra_engine SHARED tembra_jni.cpp)
target_link_libraries(tembra_engine PRIVATE tembra_engine_core log)
