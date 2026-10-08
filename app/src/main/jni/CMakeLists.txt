cmake_minimum_required(VERSION 3.22.1)
project(ncmdump)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(ncmdump SHARED
    ncmcrypt.cpp
    aes.cpp
    cJSON.cpp
    ncm_jni.cpp
)

target_include_directories(ncmdump PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)
target_link_libraries(ncmdump ${log-lib})
