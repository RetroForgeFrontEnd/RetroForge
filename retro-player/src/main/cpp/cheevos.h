#pragma once

#include <cstddef>
#include "libretro.h"

// RetroAchievements through rcheevos (MIT, github.com/RetroAchievements/rcheevos; commit in third_party/rcheevos.commit).
// The core thread calls frame() after each retro_run with the core lock held; Kotlin does the web requests
// (NativePlayer.cheevos*), so no HTTP library is needed here.
namespace cheevos {

using MemoryData = void *(*)(unsigned);
using MemorySize = size_t (*)(unsigned);

// The core's memory map (RETRO_ENVIRONMENT_SET_MEMORY_MAPS), copied.
void setMemoryMap(const retro_memory_map *map);
// The core's memory functions, once a game is loaded.
void setCoreMemory(MemoryData data, MemorySize size);
// After retro_run, with the core lock held.
void frame();
// The game was reset or a state loaded: progress towards achievements starts again.
void reset();
// The core is closing.
void shutdown();

}  // namespace cheevos
