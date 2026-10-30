cmake_minimum_required(VERSION 3.22.1)
project(retroforge_pc_player C)

# Winlator Cmod's X server and audio natives (MIT): drawing, shared memory, sockets, GPU buffers, AAudio playback.
add_library(winlator SHARED
            winlator/drawable.c
            winlator/gpu_image.c
            winlator/sysvshared_memory.c
            winlator/xconnector_epoll.c
            winlator/alsa_client.c)
target_compile_options(winlator PRIVATE -O2 -Wno-unused-function)
target_link_options(winlator PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(winlator log android jnigraphics EGL GLESv2 aaudio)

# Preloaded into Wine's processes so the programs Wine starts go through the system linker (see pcexec.c).
add_library(pcexec SHARED pcexec.c)
target_compile_options(pcexec PRIVATE -O2 -Wall)
target_link_options(pcexec PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(pcexec dl log)

# Preloaded too: the on-screen gamepad as an SDL virtual Xbox 360 controller for Wine (see pcpad.c).
add_library(pcpad SHARED pcpad.c)
target_compile_options(pcpad PRIVATE -O2 -Wall)
target_link_options(pcpad PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(pcpad dl log)
