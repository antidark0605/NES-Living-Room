cmake_minimum_required(VERSION 3.22.1)
project(nesbridge LANGUAGES CXX)
add_library(nesbridge SHARED nesbridge.cpp)
target_link_libraries(nesbridge PRIVATE log dl)
