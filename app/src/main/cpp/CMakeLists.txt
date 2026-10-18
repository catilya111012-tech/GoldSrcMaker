cmake_minimum_required(VERSION 3.22.1)

project(goldsrc_native CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(goldsrc_native SHARED
        native_bridge.cpp
        vulkan_renderer.cpp)

find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

target_link_libraries(goldsrc_native
        ${log-lib}
        ${android-lib}
        ${vulkan-lib})

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/compiler_tools/CMakeLists.txt")
    add_subdirectory(compiler_tools)
endif()
