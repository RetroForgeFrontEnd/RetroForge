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

/**
 * A custom Vulkan driver (Turnip) to use instead of the device's: the folder holding adrenotools' hook libraries, the
 * driver's folder and its library's file name. Empty names use the device's own driver.
 */
void setDriver(const std::string &hookDir, const std::string &driverDir, const std::string &driverName);

/** Creates the instance and device (on the core thread). False with [error] when Vulkan isn't usable. */
bool init(std::string &error);
/** The interface handed to the core through GET_HW_RENDER_INTERFACE; null before init. */
const retro_hw_render_interface *interface();

/**
 * Copies the core's last image (set_image) into [rgba] (width * height * 4 bytes, bottom row first like an OpenGL
 * framebuffer). False when there's no image yet.
 */
bool readFrame(unsigned width, unsigned height, uint8_t *rgba);

void destroy();

}  // namespace vkr
