// The player's libretro VFS (see vfs.cpp): ordinary files, plus games streamed from a file descriptor RetroForge holds.
#pragma once

#include <string>

#include "libretro.h"

namespace vfs {

/** Answers RETRO_ENVIRONMENT_GET_VFS_INTERFACE (up to version 4). */
bool provide(retro_vfs_interface_info *info);

/** Whether the loaded core asked for the VFS, so streamed games can reach it. */
bool wasRequested();
void resetRequested();

/** Makes [path] read from [fd] (duplicated, so the caller keeps its own). */
void addStream(const std::string &path, int fd);
void clearStreams();

}  // namespace vfs
