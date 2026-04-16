cmake_minimum_required(VERSION 3.22.1)

project("roadguard_native" C CXX)

# ── Compiler settings ────────────────────────────────────────────────────
set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Optimisation: -O2 for release builds (set via Android Gradle plugin)
# -ffast-math: safe for our scalar Kalman operations (no NaN/Inf edge cases)
add_compile_options(-Wall -Wextra -ffast-math)

# ── Native shared library ────────────────────────────────────────────────
# Compiles kalman_filter.c + jni_bridge.cpp into libroadguard_native.so
add_library(
    roadguard_native   # Library name → libroadguard_native.so
    SHARED             # Shared library loaded at runtime by System.loadLibrary()
    kalman_filter.c
    jni_bridge.cpp
)

# ── Include paths ────────────────────────────────────────────────────────
target_include_directories(roadguard_native PRIVATE
    ${CMAKE_SOURCE_DIR}   # Allows #include "kalman_filter.h" in jni_bridge.cpp
)

# ── Link Android system libraries ────────────────────────────────────────
# log: Android logging (android/log.h) — available in NDK without extra deps
# m:   libm math library (sqrtf etc.) - already used in kalman_filter.c
find_library(log-lib log)
find_library(m-lib m)

target_link_libraries(
    roadguard_native
    ${log-lib}
    ${m-lib}
)
