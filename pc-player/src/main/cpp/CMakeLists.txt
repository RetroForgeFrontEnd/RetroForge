cmake_minimum_required(VERSION 3.22.1)
project(retroforge_pc_player C)

# Winlator Cmod's X server natives (MIT): drawing, shared memory, sockets, GPU buffers.
add_library(winlator SHARED
            winlator/drawable.c
            winlator/gpu_image.c
            winlator/sysvshared_memory.c
            winlator/xconnector_epoll.c)
target_compile_options(winlator PRIVATE -O2 -Wno-unused-function)
target_link_options(winlator PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(winlator log android jnigraphics EGL GLESv2)

# Preloaded into Wine's processes so the programs Wine starts go through the system linker (see pcexec.c).
add_library(pcexec SHARED pcexec.c)
target_compile_options(pcexec PRIVATE -O2 -Wall)
target_link_options(pcexec PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(pcexec dl log)
