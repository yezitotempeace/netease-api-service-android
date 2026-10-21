cmake_minimum_required(VERSION 3.22.1)
project(ncmnode)

add_library(ncmnode SHARED node-start.cpp)

# Node 18 头文件
include_directories(${CMAKE_SOURCE_DIR}/node18/include/node)

# 预编译的 Node 运行时（arm64-v8a）
add_library(libnode SHARED IMPORTED)
set_target_properties(libnode PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libnode/${ANDROID_ABI}/libnode.so)

find_library(log-lib log)

target_link_libraries(ncmnode libnode ${log-lib})
