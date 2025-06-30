cmake_minimum_required(VERSION 3.22.1)

project("v4l2camera")

# Find required packages
find_library(log-lib log)

# Add source files
add_library(${CMAKE_PROJECT_NAME} SHARED
    v4l2_native.cpp
    frame_generator.cpp
)

# Include directories
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CMAKE_SOURCE_DIR}
)

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
    android
)

# Compiler options
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    -std=c++17
    -fPIC
    -Wall
    -Wextra
)