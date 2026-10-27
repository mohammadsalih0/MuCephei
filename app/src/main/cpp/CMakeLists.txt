cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/src
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/src/ggml-cpu
)

add_definitions(
    -D_GNU_SOURCE
    -DGGML_USE_CPU
    -DWHISPER_VERSION="1.5.0"
)

# Core GGML and Whisper source files
set(SOURCES
    whisper_jni.cpp
    whisper.cpp
    aneforge/whisper-aneforge.cpp
    ggml/src/ggml.c
    ggml/src/ggml.cpp
    ggml/src/ggml-alloc.c
    ggml/src/ggml-backend.cpp
    ggml/src/ggml-backend-reg.cpp
    ggml/src/ggml-backend-meta.cpp
    ggml/src/ggml-backend-dl.cpp
    ggml/src/ggml-opt.cpp
    ggml/src/ggml-quants.c
    ggml/src/gguf.cpp
    ggml/src/ggml-threading.cpp

    # GGML CPU backend core
    ggml/src/ggml-cpu/ggml-cpu.c
    ggml/src/ggml-cpu/ggml-cpu.cpp
    ggml/src/ggml-cpu/binary-ops.cpp
    ggml/src/ggml-cpu/unary-ops.cpp
    ggml/src/ggml-cpu/ops.cpp
    ggml/src/ggml-cpu/quants.c
    ggml/src/ggml-cpu/repack.cpp
    ggml/src/ggml-cpu/traits.cpp
    ggml/src/ggml-cpu/hbm.cpp
    ggml/src/ggml-cpu/vec.cpp
    ggml/src/ggml-cpu/tiled/tiled.cpp
    ggml/src/ggml-cpu/tiled/tiled-kernel.cpp
)

# Architecture-specific CPU implementations
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    list(APPEND SOURCES
        ggml/src/ggml-cpu/llamafile/sgemm.cpp
        ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp
        ggml/src/ggml-cpu/arch/arm/quants.c
        ggml/src/ggml-cpu/arch/arm/repack.cpp
    )
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    list(APPEND SOURCES
        ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp
        ggml/src/ggml-cpu/arch/arm/quants.c
        ggml/src/ggml-cpu/arch/arm/repack.cpp
    )
elseif (${ANDROID_ABI} STREQUAL "x86_64" OR ${ANDROID_ABI} STREQUAL "x86")
    list(APPEND SOURCES
        ggml/src/ggml-cpu/llamafile/sgemm.cpp
        ggml/src/ggml-cpu/arch/x86/cpu-feats.cpp
        ggml/src/ggml-cpu/arch/x86/quants.c
        ggml/src/ggml-cpu/arch/x86/repack.cpp
    )
endif()

add_library(whisper_jni SHARED ${SOURCES})

target_link_libraries(whisper_jni
    android
    log
)
