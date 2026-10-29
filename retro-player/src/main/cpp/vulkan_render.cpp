// Vulkan hardware rendering for cores. The core draws with Vulkan into its own image and hands it over with set_image;
// each frame that image is blitted into an RGBA picture, copied to memory the CPU can read, and given to the player's
// OpenGL drawing, which shows it like any other frame (so screen shapes, filters and VR modes all still apply).
// Instance and device: made by the core itself when it negotiates (Dolphin, Citra, PCSX2 …), otherwise here.

#include "vulkan_render.h"

#include <dlfcn.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <mutex>
#include <vector>

#include "libretro_vulkan.h"

#define LOG_TAG "RetroForgePlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

void *library = nullptr;
PFN_vkGetInstanceProcAddr getInstanceProc = nullptr;
PFN_vkGetDeviceProcAddr getDeviceProc = nullptr;

#define VK_FN(name) PFN_##name name = nullptr
// Instance level.
VK_FN(vkCreateInstance);
VK_FN(vkEnumerateInstanceExtensionProperties);
VK_FN(vkDestroyInstance);
VK_FN(vkEnumeratePhysicalDevices);
VK_FN(vkGetPhysicalDeviceProperties);
VK_FN(vkGetPhysicalDeviceQueueFamilyProperties);
VK_FN(vkGetPhysicalDeviceFeatures);
VK_FN(vkGetPhysicalDeviceMemoryProperties);
VK_FN(vkEnumerateDeviceExtensionProperties);
VK_FN(vkCreateDevice);
// Device level.
VK_FN(vkDestroyDevice);
VK_FN(vkGetDeviceQueue);
VK_FN(vkDeviceWaitIdle);
VK_FN(vkCreateCommandPool);
VK_FN(vkDestroyCommandPool);
VK_FN(vkAllocateCommandBuffers);
VK_FN(vkBeginCommandBuffer);
VK_FN(vkEndCommandBuffer);
VK_FN(vkResetCommandBuffer);
VK_FN(vkCmdPipelineBarrier);
VK_FN(vkCmdBlitImage);
VK_FN(vkCmdCopyImageToBuffer);
VK_FN(vkQueueSubmit);
VK_FN(vkCreateFence);
VK_FN(vkDestroyFence);
VK_FN(vkWaitForFences);
VK_FN(vkResetFences);
VK_FN(vkCreateImage);
VK_FN(vkDestroyImage);
VK_FN(vkGetImageMemoryRequirements);
VK_FN(vkBindImageMemory);
VK_FN(vkCreateBuffer);
VK_FN(vkDestroyBuffer);
VK_FN(vkGetBufferMemoryRequirements);
VK_FN(vkBindBufferMemory);
VK_FN(vkAllocateMemory);
VK_FN(vkFreeMemory);
VK_FN(vkMapMemory);
VK_FN(vkUnmapMemory);
VK_FN(vkInvalidateMappedMemoryRanges);
#undef VK_FN

const retro_hw_render_context_negotiation_interface_vulkan *negotiation = nullptr;

VkInstance instance = VK_NULL_HANDLE;
VkPhysicalDevice gpu = VK_NULL_HANDLE;
VkDevice device = VK_NULL_HANDLE;
VkQueue queue = VK_NULL_HANDLE;
uint32_t queueFamily = 0;
VkPhysicalDeviceMemoryProperties memory{};
std::mutex queueMutex;
retro_hw_render_interface_vulkan iface{};
bool ready = false;

// Cores that build their swapchain on the frontend's surface (Dolphin) only draw when they're given one. They wrap the
// swapchain and present themselves (frames come back through set_image), so a stand-in handle does: the few surface
// queries their wrappers pass on are answered by surfaceProc. Their instance has no surface extensions to make a real one.
const VkSurfaceKHR standInSurface = reinterpret_cast<VkSurfaceKHR>(uint64_t(0x52464F52));

// What the core handed over for this frame.
retro_vulkan_image image{};
bool haveImage = false;
uint32_t imageFamily = VK_QUEUE_FAMILY_IGNORED;
std::vector<VkSemaphore> waitSemaphores;
std::vector<VkCommandBuffer> coreCommands;
VkSemaphore signalSemaphore = VK_NULL_HANDLE;
uint32_t syncIndex = 0;

// Ours: the copy-out path.
VkCommandPool pool = VK_NULL_HANDLE;
VkCommandBuffer commands = VK_NULL_HANDLE;
VkFence fence = VK_NULL_HANDLE;
VkImage rgba = VK_NULL_HANDLE;
VkDeviceMemory rgbaMemory = VK_NULL_HANDLE;
VkBuffer readback = VK_NULL_HANDLE;
VkDeviceMemory readbackMemory = VK_NULL_HANDLE;
void *mapped = nullptr;
bool readbackCoherent = true;
unsigned copyW = 0, copyH = 0;

template <typename T>
bool instanceFn(T &fn, const char *name) {
    fn = reinterpret_cast<T>(getInstanceProc(instance, name));
    return fn != nullptr;
}

template <typename T>
bool deviceFn(T &fn, const char *name) {
    fn = reinterpret_cast<T>(getDeviceProc(device, name));
    return fn != nullptr;
}

int memoryType(uint32_t bits, VkMemoryPropertyFlags want) {
    for (uint32_t i = 0; i < memory.memoryTypeCount; i++)
        if ((bits & (1u << i)) && (memory.memoryTypes[i].propertyFlags & want) == want) return int(i);
    return -1;
}

// ---- The interface the core calls ----

void setImage(void *, const retro_vulkan_image *img, uint32_t count, const VkSemaphore *semaphores, uint32_t family) {
    if (img) {
        image = *img;
        haveImage = true;
    }
    waitSemaphores.assign(semaphores, semaphores + count);
    imageFamily = family;
}

uint32_t getSyncIndex(void *) { return syncIndex; }
uint32_t getSyncIndexMask(void *) { return 0x3; }

void setCommandBuffers(void *, uint32_t count, const VkCommandBuffer *buffers) { coreCommands.assign(buffers, buffers + count); }

// Each frame waits for its own copy to finish, so whatever the core used for a sync index is already free.
void waitSyncIndex(void *) {}

void lockQueue(void *) { queueMutex.lock(); }
void unlockQueue(void *) { queueMutex.unlock(); }
void setSignalSemaphore(void *, VkSemaphore semaphore) { signalSemaphore = semaphore; }

// ---- Instance and device ----

VKAPI_ATTR VkResult VKAPI_CALL surfaceSupport(VkPhysicalDevice, uint32_t, VkSurfaceKHR, VkBool32 *supported) {
    *supported = VK_TRUE;
    return VK_SUCCESS;
}

VKAPI_ATTR VkResult VKAPI_CALL surfaceFormats(VkPhysicalDevice, VkSurfaceKHR, uint32_t *count, VkSurfaceFormatKHR *formats) {
    const VkSurfaceFormatKHR offered[] = {{VK_FORMAT_R8G8B8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR},
                                          {VK_FORMAT_B8G8R8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR}};
    if (!formats) return *count = 2, VK_SUCCESS;
    uint32_t n = std::min<uint32_t>(*count, 2);
    for (uint32_t i = 0; i < n; i++) formats[i] = offered[i];
    *count = n;
    return n < 2 ? VK_INCOMPLETE : VK_SUCCESS;
}

VKAPI_ATTR VkResult VKAPI_CALL surfacePresentModes(VkPhysicalDevice, VkSurfaceKHR, uint32_t *count, VkPresentModeKHR *modes) {
    if (!modes) return *count = 1, VK_SUCCESS;
    if (*count < 1) return VK_INCOMPLETE;
    modes[0] = VK_PRESENT_MODE_FIFO_KHR;
    *count = 1;
    return VK_SUCCESS;
}

VKAPI_ATTR VkResult VKAPI_CALL surfaceCapabilities(VkPhysicalDevice, VkSurfaceKHR, VkSurfaceCapabilitiesKHR *caps) {
    *caps = {};
    caps->minImageCount = 2;
    caps->maxImageCount = 4;
    caps->currentExtent = {0xFFFFFFFFu, 0xFFFFFFFFu};
    caps->minImageExtent = {1, 1};
    caps->maxImageExtent = {8192, 8192};
    caps->maxImageArrayLayers = 1;
    caps->supportedTransforms = caps->currentTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
    caps->supportedCompositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    caps->supportedUsageFlags = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    return VK_SUCCESS;
}

VKAPI_ATTR void VKAPI_CALL destroySurface(VkInstance, VkSurfaceKHR, const VkAllocationCallbacks *) {}

// The instance-level lookup handed to negotiating cores: the driver's, plus answers for the stand-in surface. The
// instance has no surface extensions, so the driver gives nothing for these; Dolphin only puts its own wrappers in
// place of functions the frontend's lookup does give.
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL surfaceProc(VkInstance inst, const char *name) {
    if (!strcmp(name, "vkGetPhysicalDeviceSurfaceSupportKHR")) return reinterpret_cast<PFN_vkVoidFunction>(surfaceSupport);
    if (!strcmp(name, "vkGetPhysicalDeviceSurfaceFormatsKHR")) return reinterpret_cast<PFN_vkVoidFunction>(surfaceFormats);
    if (!strcmp(name, "vkGetPhysicalDeviceSurfacePresentModesKHR")) return reinterpret_cast<PFN_vkVoidFunction>(surfacePresentModes);
    if (!strcmp(name, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR")) return reinterpret_cast<PFN_vkVoidFunction>(surfaceCapabilities);
    if (!strcmp(name, "vkDestroySurfaceKHR")) return reinterpret_cast<PFN_vkVoidFunction>(destroySurface);
    return getInstanceProc(inst, name);
}

VkInstance createInstanceWrapper(void *, const VkInstanceCreateInfo *info) {
    VkInstance made = VK_NULL_HANDLE;
    return vkCreateInstance(info, nullptr, &made) == VK_SUCCESS ? made : VK_NULL_HANDLE;
}

VkDevice createDeviceWrapper(VkPhysicalDevice physical, void *, const VkDeviceCreateInfo *info) {
    VkDevice made = VK_NULL_HANDLE;
    return vkCreateDevice(physical, info, nullptr, &made) == VK_SUCCESS ? made : VK_NULL_HANDLE;
}

bool ownInstance(const VkApplicationInfo *app) {
    uint32_t count = 0;
    vkEnumerateInstanceExtensionProperties(nullptr, &count, nullptr);
    std::vector<VkExtensionProperties> available(count);
    vkEnumerateInstanceExtensionProperties(nullptr, &count, available.data());
    std::vector<const char *> extensions;
    for (auto &e : available)
        if (!strcmp(e.extensionName, VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME)) extensions.push_back(e.extensionName);
    VkInstanceCreateInfo info{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    info.pApplicationInfo = app;
    info.enabledExtensionCount = uint32_t(extensions.size());
    info.ppEnabledExtensionNames = extensions.data();
    return vkCreateInstance(&info, nullptr, &instance) == VK_SUCCESS;
}

// A device of our own for cores that don't make theirs: one graphics+compute queue, every feature the GPU has, and
// the common extensions cores expect.
bool ownDevice() {
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, nullptr);
    std::vector<VkQueueFamilyProperties> families(count);
    vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, families.data());
    bool found = false;
    for (uint32_t i = 0; i < count; i++) {
        if ((families[i].queueFlags & (VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT)) == (VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT)) {
            queueFamily = i;
            found = true;
            break;
        }
    }
    if (!found) return false;
    VkPhysicalDeviceFeatures features{};
    vkGetPhysicalDeviceFeatures(gpu, &features);
    vkEnumerateDeviceExtensionProperties(gpu, nullptr, &count, nullptr);
    std::vector<VkExtensionProperties> available(count);
    vkEnumerateDeviceExtensionProperties(gpu, nullptr, &count, available.data());
    const char *wanted[] = {
        "VK_KHR_maintenance1", "VK_KHR_maintenance2", "VK_KHR_maintenance3", "VK_KHR_dedicated_allocation",
        "VK_KHR_get_memory_requirements2", "VK_KHR_bind_memory2", "VK_KHR_image_format_list", "VK_KHR_create_renderpass2",
        "VK_KHR_sampler_mirror_clamp_to_edge", "VK_KHR_shader_draw_parameters", "VK_KHR_storage_buffer_storage_class",
        "VK_KHR_8bit_storage", "VK_KHR_16bit_storage", "VK_KHR_shader_float16_int8", "VK_EXT_shader_subgroup_ballot",
        "VK_KHR_driver_properties", "VK_KHR_timeline_semaphore", "VK_KHR_synchronization2",
    };
    std::vector<const char *> extensions;
    for (auto *name : wanted)
        for (auto &e : available)
            if (!strcmp(e.extensionName, name)) extensions.push_back(name);
    float priority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    queueInfo.queueFamilyIndex = queueFamily;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &priority;
    VkDeviceCreateInfo info{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    info.queueCreateInfoCount = 1;
    info.pQueueCreateInfos = &queueInfo;
    info.enabledExtensionCount = uint32_t(extensions.size());
    info.ppEnabledExtensionNames = extensions.data();
    info.pEnabledFeatures = &features;
    return vkCreateDevice(gpu, &info, nullptr, &device) == VK_SUCCESS;
}

bool loadDeviceFunctions() {
    return deviceFn(vkDestroyDevice, "vkDestroyDevice") && deviceFn(vkGetDeviceQueue, "vkGetDeviceQueue") &&
           deviceFn(vkDeviceWaitIdle, "vkDeviceWaitIdle") && deviceFn(vkCreateCommandPool, "vkCreateCommandPool") &&
           deviceFn(vkDestroyCommandPool, "vkDestroyCommandPool") && deviceFn(vkAllocateCommandBuffers, "vkAllocateCommandBuffers") &&
           deviceFn(vkBeginCommandBuffer, "vkBeginCommandBuffer") && deviceFn(vkEndCommandBuffer, "vkEndCommandBuffer") &&
           deviceFn(vkResetCommandBuffer, "vkResetCommandBuffer") && deviceFn(vkCmdPipelineBarrier, "vkCmdPipelineBarrier") &&
           deviceFn(vkCmdBlitImage, "vkCmdBlitImage") && deviceFn(vkCmdCopyImageToBuffer, "vkCmdCopyImageToBuffer") &&
           deviceFn(vkQueueSubmit, "vkQueueSubmit") && deviceFn(vkCreateFence, "vkCreateFence") &&
           deviceFn(vkDestroyFence, "vkDestroyFence") && deviceFn(vkWaitForFences, "vkWaitForFences") &&
           deviceFn(vkResetFences, "vkResetFences") && deviceFn(vkCreateImage, "vkCreateImage") &&
           deviceFn(vkDestroyImage, "vkDestroyImage") && deviceFn(vkGetImageMemoryRequirements, "vkGetImageMemoryRequirements") &&
           deviceFn(vkBindImageMemory, "vkBindImageMemory") && deviceFn(vkCreateBuffer, "vkCreateBuffer") &&
           deviceFn(vkDestroyBuffer, "vkDestroyBuffer") && deviceFn(vkGetBufferMemoryRequirements, "vkGetBufferMemoryRequirements") &&
           deviceFn(vkBindBufferMemory, "vkBindBufferMemory") && deviceFn(vkAllocateMemory, "vkAllocateMemory") &&
           deviceFn(vkFreeMemory, "vkFreeMemory") && deviceFn(vkMapMemory, "vkMapMemory") && deviceFn(vkUnmapMemory, "vkUnmapMemory") &&
           deviceFn(vkInvalidateMappedMemoryRanges, "vkInvalidateMappedMemoryRanges");
}

// ---- The copy-out path ----

void freeCopy() {
    if (mapped) vkUnmapMemory(device, readbackMemory);
    mapped = nullptr;
    if (readback) vkDestroyBuffer(device, readback, nullptr);
    if (readbackMemory) vkFreeMemory(device, readbackMemory, nullptr);
    if (rgba) vkDestroyImage(device, rgba, nullptr);
    if (rgbaMemory) vkFreeMemory(device, rgbaMemory, nullptr);
    readback = VK_NULL_HANDLE;
    readbackMemory = rgbaMemory = VK_NULL_HANDLE;
    rgba = VK_NULL_HANDLE;
    copyW = copyH = 0;
}

bool makeCopy(unsigned w, unsigned h) {
    freeCopy();
    VkImageCreateInfo imageInfo{VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imageInfo.extent = {w, h, 1};
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    if (vkCreateImage(device, &imageInfo, nullptr, &rgba) != VK_SUCCESS) return false;
    VkMemoryRequirements req{};
    vkGetImageMemoryRequirements(device, rgba, &req);
    int type = memoryType(req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    if (type < 0) type = memoryType(req.memoryTypeBits, 0);
    VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    alloc.allocationSize = req.size;
    alloc.memoryTypeIndex = uint32_t(type);
    if (type < 0 || vkAllocateMemory(device, &alloc, nullptr, &rgbaMemory) != VK_SUCCESS) return false;
    vkBindImageMemory(device, rgba, rgbaMemory, 0);

    VkBufferCreateInfo bufferInfo{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
    bufferInfo.size = VkDeviceSize(w) * h * 4;
    bufferInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    if (vkCreateBuffer(device, &bufferInfo, nullptr, &readback) != VK_SUCCESS) return false;
    vkGetBufferMemoryRequirements(device, readback, &req);
    // Cached memory reads back fastest; coherent saves an invalidate.
    readbackCoherent = true;
    type = memoryType(req.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT | VK_MEMORY_PROPERTY_HOST_CACHED_BIT);
    if (type < 0) type = memoryType(req.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_CACHED_BIT), readbackCoherent = false;
    if (type < 0) type = memoryType(req.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT), readbackCoherent = true;
    alloc.allocationSize = req.size;
    alloc.memoryTypeIndex = uint32_t(type);
    if (type < 0 || vkAllocateMemory(device, &alloc, nullptr, &readbackMemory) != VK_SUCCESS) return false;
    vkBindBufferMemory(device, readback, readbackMemory, 0);
    if (vkMapMemory(device, readbackMemory, 0, VK_WHOLE_SIZE, 0, &mapped) != VK_SUCCESS) return false;
    copyW = w;
    copyH = h;
    return true;
}

void imageBarrier(VkImage target, VkImageLayout from, VkImageLayout to, VkAccessFlags srcAccess, VkAccessFlags dstAccess,
                  uint32_t srcFamily = VK_QUEUE_FAMILY_IGNORED, uint32_t dstFamily = VK_QUEUE_FAMILY_IGNORED) {
    VkImageMemoryBarrier barrier{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
    barrier.srcAccessMask = srcAccess;
    barrier.dstAccessMask = dstAccess;
    barrier.oldLayout = from;
    barrier.newLayout = to;
    barrier.srcQueueFamilyIndex = srcFamily;
    barrier.dstQueueFamilyIndex = dstFamily;
    barrier.image = target;
    barrier.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr, 1, &barrier);
}

}  // namespace

namespace vkr {

bool setNegotiation(const retro_hw_render_context_negotiation_interface *base) {
    auto *n = reinterpret_cast<const retro_hw_render_context_negotiation_interface_vulkan *>(base);
    if (!n || n->interface_type != RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN) return false;
    negotiation = n;
    LOGI("Vulkan: the core negotiates its device (interface version %u)", n->interface_version);
    return true;
}

bool negotiationSupport(retro_hw_render_context_negotiation_interface *support) {
    if (!support || support->interface_type != RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN) return false;
    support->interface_version = RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN_VERSION;
    return true;
}

bool init(std::string &error) {
    if (ready) return true;
    library = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) return error = "This device has no Vulkan driver", false;
    getInstanceProc = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(library, "vkGetInstanceProcAddr"));
    if (!getInstanceProc) return error = "Vulkan driver is incomplete", false;
    instance = VK_NULL_HANDLE;
    instanceFn(vkCreateInstance, "vkCreateInstance");
    instanceFn(vkEnumerateInstanceExtensionProperties, "vkEnumerateInstanceExtensionProperties");

    VkApplicationInfo ownApp{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    ownApp.pApplicationName = "RetroForge";
    ownApp.pEngineName = "RetroForge";
    ownApp.apiVersion = VK_API_VERSION_1_1;
    const VkApplicationInfo *app = negotiation && negotiation->get_application_info ? negotiation->get_application_info() : nullptr;
    if (!app) app = &ownApp;

    if (negotiation && negotiation->interface_version >= 2 && negotiation->create_instance)
        instance = negotiation->create_instance(surfaceProc, app, createInstanceWrapper, nullptr);
    else
        ownInstance(app);
    if (!instance) return error = "Couldn't start Vulkan (instance)", false;

    bool ok = instanceFn(vkDestroyInstance, "vkDestroyInstance") && instanceFn(vkEnumeratePhysicalDevices, "vkEnumeratePhysicalDevices") &&
              instanceFn(vkGetPhysicalDeviceProperties, "vkGetPhysicalDeviceProperties") &&
              instanceFn(vkGetPhysicalDeviceQueueFamilyProperties, "vkGetPhysicalDeviceQueueFamilyProperties") &&
              instanceFn(vkGetPhysicalDeviceFeatures, "vkGetPhysicalDeviceFeatures") &&
              instanceFn(vkGetPhysicalDeviceMemoryProperties, "vkGetPhysicalDeviceMemoryProperties") &&
              instanceFn(vkEnumerateDeviceExtensionProperties, "vkEnumerateDeviceExtensionProperties") &&
              instanceFn(vkCreateDevice, "vkCreateDevice") && instanceFn(getDeviceProc, "vkGetDeviceProcAddr");
    if (!ok) return error = "Vulkan driver is incomplete", false;

    uint32_t count = 1;
    VkPhysicalDevice first = VK_NULL_HANDLE;
    vkEnumeratePhysicalDevices(instance, &count, &first);
    if (!first) return error = "No Vulkan GPU found", false;
    gpu = first;

    retro_vulkan_context context{};
    bool made = false;
    if (negotiation && negotiation->interface_version >= 2 && negotiation->create_device2)
        made = negotiation->create_device2(&context, instance, gpu, standInSurface, surfaceProc, createDeviceWrapper, nullptr);
    else if (negotiation && negotiation->create_device)
        made = negotiation->create_device(&context, instance, gpu, standInSurface, surfaceProc, nullptr, 0, nullptr, 0, nullptr);
    if (made) {
        device = context.device;
        queue = context.queue;
        queueFamily = context.queue_family_index;
        if (context.gpu) gpu = context.gpu;
    } else if (!ownDevice()) {
        return error = "Couldn't start Vulkan (device)", false;
    }
    if (!loadDeviceFunctions()) return error = "Vulkan driver is incomplete", false;
    if (!queue) vkGetDeviceQueue(device, queueFamily, 0, &queue);
    vkGetPhysicalDeviceMemoryProperties(gpu, &memory);

    VkCommandPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = queueFamily;
    vkCreateCommandPool(device, &poolInfo, nullptr, &pool);
    VkCommandBufferAllocateInfo bufferInfo{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    bufferInfo.commandPool = pool;
    bufferInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    bufferInfo.commandBufferCount = 1;
    vkAllocateCommandBuffers(device, &bufferInfo, &commands);
    VkFenceCreateInfo fenceInfo{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    vkCreateFence(device, &fenceInfo, nullptr, &fence);

    iface = {};
    iface.interface_type = RETRO_HW_RENDER_INTERFACE_VULKAN;
    iface.interface_version = RETRO_HW_RENDER_INTERFACE_VULKAN_VERSION;
    iface.instance = instance;
    iface.gpu = gpu;
    iface.device = device;
    iface.get_device_proc_addr = getDeviceProc;
    iface.get_instance_proc_addr = getInstanceProc;
    iface.queue = queue;
    iface.queue_index = queueFamily;
    iface.set_image = setImage;
    iface.get_sync_index = getSyncIndex;
    iface.get_sync_index_mask = getSyncIndexMask;
    iface.set_command_buffers = setCommandBuffers;
    iface.wait_sync_index = waitSyncIndex;
    iface.lock_queue = lockQueue;
    iface.unlock_queue = unlockQueue;
    iface.set_signal_semaphore = setSignalSemaphore;

    VkPhysicalDeviceProperties props{};
    vkGetPhysicalDeviceProperties(gpu, &props);
    LOGI("Vulkan ready: %s, API %u.%u, device made by %s", props.deviceName, VK_VERSION_MAJOR(props.apiVersion),
         VK_VERSION_MINOR(props.apiVersion), made ? "the core" : "the player");
    ready = true;
    return true;
}

const retro_hw_render_interface *interface() {
    return ready ? reinterpret_cast<const retro_hw_render_interface *>(&iface) : nullptr;
}

bool readFrame(unsigned w, unsigned h, uint8_t *out) {
    if (!ready || !haveImage || w == 0 || h == 0) return false;
    if ((w != copyW || h != copyH) && !makeCopy(w, h)) {
        LOGE("Vulkan: couldn't make a %ux%u copy target", w, h);
        return false;
    }
    VkImage source = image.create_info.image;
    VkImageLayout layout = image.image_layout;
    bool transfer = imageFamily != VK_QUEUE_FAMILY_IGNORED && imageFamily != queueFamily;

    vkResetCommandBuffer(commands, 0);
    VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
    begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vkBeginCommandBuffer(commands, &begin);
    const VkAccessFlags written = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_SHADER_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT;
    imageBarrier(source, layout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, written, VK_ACCESS_TRANSFER_READ_BIT,
                 transfer ? imageFamily : VK_QUEUE_FAMILY_IGNORED, transfer ? queueFamily : VK_QUEUE_FAMILY_IGNORED);
    imageBarrier(rgba, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 0, VK_ACCESS_TRANSFER_WRITE_BIT);
    // A blit, so whatever format the core draws in (BGRA, RGBA, 10-bit) comes out as RGBA bytes.
    VkImageBlit blit{};
    blit.srcSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    blit.srcOffsets[1] = {int32_t(w), int32_t(h), 1};
    blit.dstSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    blit.dstOffsets[1] = {int32_t(w), int32_t(h), 1};
    vkCmdBlitImage(commands, source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, rgba, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit, VK_FILTER_NEAREST);
    imageBarrier(rgba, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT);
    VkBufferImageCopy copy{};
    copy.imageSubresource = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1};
    copy.imageExtent = {w, h, 1};
    vkCmdCopyImageToBuffer(commands, rgba, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readback, 1, &copy);
    // The core's image goes back as it gave it.
    imageBarrier(source, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, layout, VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT,
                 transfer ? queueFamily : VK_QUEUE_FAMILY_IGNORED, transfer ? imageFamily : VK_QUEUE_FAMILY_IGNORED);
    VkBufferMemoryBarrier host{VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER};
    host.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    host.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
    host.srcQueueFamilyIndex = host.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    host.buffer = readback;
    host.size = VK_WHOLE_SIZE;
    vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, 0, nullptr, 1, &host, 0, nullptr);
    vkEndCommandBuffer(commands);

    // The core's own command buffers (if it handed any over) run first, then the copy.
    std::vector<VkCommandBuffer> all(coreCommands);
    all.push_back(commands);
    std::vector<VkPipelineStageFlags> stages(waitSemaphores.size(), VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
    VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
    submit.waitSemaphoreCount = uint32_t(waitSemaphores.size());
    submit.pWaitSemaphores = waitSemaphores.data();
    submit.pWaitDstStageMask = stages.data();
    submit.commandBufferCount = uint32_t(all.size());
    submit.pCommandBuffers = all.data();
    if (signalSemaphore) {
        submit.signalSemaphoreCount = 1;
        submit.pSignalSemaphores = &signalSemaphore;
    }
    vkResetFences(device, 1, &fence);
    VkResult result;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        result = vkQueueSubmit(queue, 1, &submit, fence);
    }
    waitSemaphores.clear();
    coreCommands.clear();
    signalSemaphore = VK_NULL_HANDLE;
    syncIndex = (syncIndex + 1) & 1;
    if (result != VK_SUCCESS) {
        LOGE("Vulkan: frame copy submit failed (%d)", result);
        return false;
    }
    vkWaitForFences(device, 1, &fence, VK_TRUE, UINT64_MAX);
    if (!readbackCoherent) {
        VkMappedMemoryRange range{VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE};
        range.memory = readbackMemory;
        range.size = VK_WHOLE_SIZE;
        vkInvalidateMappedMemoryRanges(device, 1, &range);
    }
    // Bottom row first, as an OpenGL framebuffer is: the player draws hardware frames that way up.
    const size_t row = size_t(w) * 4;
    for (unsigned y = 0; y < h; y++) std::memcpy(out + y * row, static_cast<const uint8_t *>(mapped) + (h - 1 - y) * row, row);
    return true;
}

void destroy() {
    if (!ready) return;
    vkDeviceWaitIdle(device);
    freeCopy();
    if (fence) vkDestroyFence(device, fence, nullptr);
    if (pool) vkDestroyCommandPool(device, pool, nullptr);
    fence = VK_NULL_HANDLE;
    pool = VK_NULL_HANDLE;
    commands = VK_NULL_HANDLE;
    if (negotiation && negotiation->destroy_device) negotiation->destroy_device();
    vkDestroyDevice(device, nullptr);
    vkDestroyInstance(instance, nullptr);
    device = VK_NULL_HANDLE;
    instance = VK_NULL_HANDLE;
    queue = VK_NULL_HANDLE;
    gpu = VK_NULL_HANDLE;
    negotiation = nullptr;
    haveImage = false;
    ready = false;
}

}  // namespace vkr
