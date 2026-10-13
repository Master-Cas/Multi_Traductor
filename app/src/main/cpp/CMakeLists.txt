cmake_minimum_required(VERSION 3.22)
project(multi_whisper LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
# AGP debug toolchain does not add optimizer flags for native libraries.
# Apply optimization to whisper.cpp and ggml subdirectory targets explicitly.
add_compile_options(-O3 -DNDEBUG)
# Android Debug APK must benchmark optimized native inference, not unoptimized -g code.
# This applies to whisper and ggml subdirectory targets too.
set(CMAKE_C_FLAGS_DEBUG "-O3 -DNDEBUG" CACHE STRING "optimized native debug" FORCE)
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG" CACHE STRING "optimized native debug" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp" "${CMAKE_CURRENT_BINARY_DIR}/whisper")
add_library(multi_whisper SHARED whisper_jni.cpp)
target_include_directories(multi_whisper PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp/include")
target_link_libraries(multi_whisper PRIVATE whisper log android)
