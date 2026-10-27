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

set(SOURCES
    whisper_jni.cpp
    whisper.cpp
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
    ggml/src/ggml-cpu/ggml-cpu.c
    ggml/src/ggml-cpu/ggml-cpu.cpp
    ggml/src/ggml-cpu/binary-ops.cpp
    ggml/src/ggml-cpu/unary-ops.cpp
    ggml/src/ggml-cpu/ops.cpp
    ggml/src/ggml-cpu/quants.c
    ggml/src/ggml-cpu/vec.cpp
)

add_library(whisper_jni SHARED ${SOURCES})

target_link_libraries(whisper_jni
    android
    log
)
