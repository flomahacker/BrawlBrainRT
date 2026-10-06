cmake_minimum_required(VERSION 3.22.1)

project(brawlcapture LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    brawlcapture
    SHARED
    native_detector.cpp
)

target_compile_options(
    brawlcapture
    PRIVATE
    -O3
    -ffast-math
    -fno-exceptions
    -fno-rtti
)

find_library(log-lib log)

target_link_libraries(
    brawlcapture
    ${log-lib}
)
