// libretro's Vulkan hardware-rendering interface (the public API from libretro-common's libretro_vulkan.h, version 5,
// with context negotiation version 2). Vulkan is used through function pointers only (VK_NO_PROTOTYPES).
#pragma once

#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>

#include "libretro.h"

#define RETRO_HW_RENDER_INTERFACE_VULKAN_VERSION 5
#define RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN_VERSION 2

struct retro_vulkan_image {
    VkImageView image_view;
    VkImageLayout image_layout;
    VkImageViewCreateInfo create_info;
};

typedef void (*retro_vulkan_set_image_t)(void *handle, const struct retro_vulkan_image *image, uint32_t num_semaphores,
                                         const VkSemaphore *semaphores, uint32_t src_queue_family);
typedef uint32_t (*retro_vulkan_get_sync_index_t)(void *handle);
typedef uint32_t (*retro_vulkan_get_sync_index_mask_t)(void *handle);
typedef void (*retro_vulkan_set_command_buffers_t)(void *handle, uint32_t num_cmd, const VkCommandBuffer *cmd);
typedef void (*retro_vulkan_wait_sync_index_t)(void *handle);
typedef void (*retro_vulkan_lock_queue_t)(void *handle);
typedef void (*retro_vulkan_unlock_queue_t)(void *handle);
typedef void (*retro_vulkan_set_signal_semaphore_t)(void *handle, VkSemaphore semaphore);

typedef const VkApplicationInfo *(*retro_vulkan_get_application_info_t)(void);

struct retro_vulkan_context {
    VkPhysicalDevice gpu;
    VkDevice device;
    VkQueue queue;
    uint32_t queue_family_index;
    VkQueue presentation_queue;
    uint32_t presentation_queue_family_index;
};

typedef bool (*retro_vulkan_create_device_t)(struct retro_vulkan_context *context, VkInstance instance, VkPhysicalDevice gpu,
                                             VkSurfaceKHR surface, PFN_vkGetInstanceProcAddr get_instance_proc_addr,
                                             const char **required_device_extensions, unsigned num_required_device_extensions,
                                             const char **required_device_layers, unsigned num_required_device_layers,
                                             const VkPhysicalDeviceFeatures *required_features);
typedef void (*retro_vulkan_destroy_device_t)(void);

// Version 2.
typedef VkInstance (*retro_vulkan_create_instance_wrapper_t)(void *opaque, const VkInstanceCreateInfo *create_info);
typedef VkInstance (*retro_vulkan_create_instance_t)(PFN_vkGetInstanceProcAddr get_instance_proc_addr, const VkApplicationInfo *app,
                                                     retro_vulkan_create_instance_wrapper_t create_instance_wrapper, void *opaque);
typedef VkDevice (*retro_vulkan_create_device_wrapper_t)(VkPhysicalDevice gpu, void *opaque, const VkDeviceCreateInfo *create_info);
typedef bool (*retro_vulkan_create_device2_t)(struct retro_vulkan_context *context, VkInstance instance, VkPhysicalDevice gpu,
                                              VkSurfaceKHR surface, PFN_vkGetInstanceProcAddr get_instance_proc_addr,
                                              retro_vulkan_create_device_wrapper_t create_device_wrapper, void *opaque);

struct retro_hw_render_context_negotiation_interface_vulkan {
    enum retro_hw_render_context_negotiation_interface_type interface_type;
    unsigned interface_version;
    retro_vulkan_get_application_info_t get_application_info;
    retro_vulkan_create_device_t create_device;
    retro_vulkan_destroy_device_t destroy_device;
    retro_vulkan_create_instance_t create_instance;
    retro_vulkan_create_device2_t create_device2;
};

struct retro_hw_render_interface_vulkan {
    enum retro_hw_render_interface_type interface_type;
    unsigned interface_version;
    void *handle;
    VkInstance instance;
    VkPhysicalDevice gpu;
    VkDevice device;
    PFN_vkGetDeviceProcAddr get_device_proc_addr;
    PFN_vkGetInstanceProcAddr get_instance_proc_addr;
    VkQueue queue;
    unsigned queue_index;
    retro_vulkan_set_image_t set_image;
    retro_vulkan_get_sync_index_t get_sync_index;
    retro_vulkan_get_sync_index_mask_t get_sync_index_mask;
    retro_vulkan_set_command_buffers_t set_command_buffers;
    retro_vulkan_wait_sync_index_t wait_sync_index;
    retro_vulkan_lock_queue_t lock_queue;
    retro_vulkan_unlock_queue_t unlock_queue;
    retro_vulkan_set_signal_semaphore_t set_signal_semaphore;
};
