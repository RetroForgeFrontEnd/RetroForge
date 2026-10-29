// Vulkan hardware rendering for cores (see vulkan_render.cpp): the core draws with Vulkan, and each finished frame is
// copied into an RGBA picture the player's OpenGL drawing shows like any other frame.
#pragma once

#include <cstdint>
#include <string>

#include "libretro.h"

namespace vkr {

/** Keeps the core's context negotiation interface (it may create the instance and device itself). */
bool setNegotiation(const retro_hw_render_context_negotiation_interface *iface);
/** Answers GET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_SUPPORT. */
bool negotiationSupport(retro_hw_render_context_negotiation_interface *iface);

/** Creates the instance and device (on the core thread). False with [error] when Vulkan isn't usable. */
bool init(std::string &error);
/** The interface handed to the core through GET_HW_RENDER_INTERFACE; null before init. */
const retro_hw_render_interface *interface();

/**
 * Copies the core's last image (set_image) into [rgba] (width * height * 4 bytes, top row first). False when there's
 * no image yet.
 */
bool readFrame(unsigned width, unsigned height, uint8_t *rgba);

void destroy();

}  // namespace vkr
