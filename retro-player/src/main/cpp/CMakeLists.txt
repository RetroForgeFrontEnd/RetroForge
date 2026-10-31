cmake_minimum_required(VERSION 3.22.1)
project(retroforge_player C CXX)

# RetroAchievements (rcheevos, MIT, github.com/RetroAchievements/rcheevos; commit in third_party/rcheevos.commit).
file(GLOB_RECURSE RCHEEVOS_SOURCES third_party/rcheevos/src/*.c)
list(FILTER RCHEEVOS_SOURCES EXCLUDE REGEX "rc_client_raintegration\\.c$")

# CHD reading for hashing disc games (libchdr, BSD-3-Clause, github.com/rtissera/libchdr, with its bundled LZMA SDK,
# miniz and zstd; commit in third_party/libchdr.commit), built as one file with its symbols kept hidden from cores.
set(LIBCHDR third_party/libchdr)
set_source_files_properties(${LIBCHDR}/unity.c PROPERTIES
    COMPILE_OPTIONS "-fvisibility=hidden;-w"
    COMPILE_DEFINITIONS "WANT_RAW_DATA_SECTOR=1;WANT_SUBCODE=1;VERIFY_BLOCK_CRC=1;Z7_ST")
set_source_files_properties(${LIBCHDR}/unity.c PROPERTIES INCLUDE_DIRECTORIES
    "${CMAKE_CURRENT_SOURCE_DIR}/${LIBCHDR}/include;${CMAKE_CURRENT_SOURCE_DIR}/${LIBCHDR}/deps/lzma-26.02/include;${CMAKE_CURRENT_SOURCE_DIR}/${LIBCHDR}/deps/miniz-3.1.2;${CMAKE_CURRENT_SOURCE_DIR}/${LIBCHDR}/deps/zstd-1.5.7")

add_library(retroforge_player SHARED player.cpp vfs.cpp vulkan_render.cpp netplay.cpp cheevos.cpp cheevos_chd.cpp ${RCHEEVOS_SOURCES} ${LIBCHDR}/unity.c)
target_compile_features(retroforge_player PRIVATE cxx_std_17)
target_compile_options(retroforge_player PRIVATE $<$<COMPILE_LANGUAGE:CXX>:-Wall -Wno-unused-parameter>)
target_include_directories(retroforge_player PRIVATE . third_party/rcheevos/include third_party/rcheevos/src ${LIBCHDR}/include)
target_compile_definitions(retroforge_player PRIVATE RC_CLIENT_SUPPORTS_HASH=1 RC_DISABLE_LUA=1)
# 16 KB pages (Android 15+ devices that use them).
target_link_options(retroforge_player PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(retroforge_player android log EGL GLESv3 aaudio z dl)

# Custom Adreno Vulkan drivers (Turnip) through libadrenotools (BSD-2-Clause, github.com/bylaws/libadrenotools,
# vendored with its linkernsbypass submodule; commits in third_party/*.commit). 64-bit ARM only; its hook libraries
# are packaged in the APK and copied out by the app (see NativePlayer.setGpuDriver).
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_subdirectory(third_party/libadrenotools)
    target_link_libraries(retroforge_player adrenotools)
    target_compile_definitions(retroforge_player PRIVATE RF_ADRENOTOOLS=1)
endif()
