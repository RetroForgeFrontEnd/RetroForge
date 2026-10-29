cmake_minimum_required(VERSION 3.22.1)
project(retroforge_player CXX)

add_library(retroforge_player SHARED player.cpp vfs.cpp vulkan_render.cpp netplay.cpp)
target_compile_features(retroforge_player PRIVATE cxx_std_17)
target_compile_options(retroforge_player PRIVATE -Wall -Wno-unused-parameter)
# 16 KB pages (Android 15+ devices that use them).
target_link_options(retroforge_player PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(retroforge_player android log EGL GLESv3 aaudio z dl)
