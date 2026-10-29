// RetroForge's own libretro frontend: loads a core, draws its frames with OpenGL ES, plays its sound with AAudio
// and feeds it the controls Kotlin passes in. One game at a time; the player runs in its own process.
// Only libretro.h (MIT) comes from the libretro project; cores are separate downloads loaded at run time.

#include <jni.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <aaudio/AAudio.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <algorithm>
#include <initializer_list>
#include <utility>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <map>
#include <mutex>
#include <set>
#include <string>
#include <thread>
#include <vector>

#include "libretro.h"
#include "netplay.h"
#include "vfs.h"
#include "vulkan_render.h"

#define TAG "RetroForgePlayer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct CoreApi {
    void *handle = nullptr;
    void (*init)() = nullptr;
    void (*deinit)() = nullptr;
    void (*get_system_info)(retro_system_info *) = nullptr;
    void (*get_system_av_info)(retro_system_av_info *) = nullptr;
    void (*set_environment)(retro_environment_t) = nullptr;
    void (*set_video_refresh)(retro_video_refresh_t) = nullptr;
    void (*set_audio_sample)(retro_audio_sample_t) = nullptr;
    void (*set_audio_sample_batch)(retro_audio_sample_batch_t) = nullptr;
    void (*set_input_poll)(retro_input_poll_t) = nullptr;
    void (*set_input_state)(retro_input_state_t) = nullptr;
    void (*set_controller_port_device)(unsigned, unsigned) = nullptr;
    void (*reset)() = nullptr;
    void (*run)() = nullptr;
    size_t (*serialize_size)() = nullptr;
    bool (*serialize)(void *, size_t) = nullptr;
    bool (*unserialize)(const void *, size_t) = nullptr;
    bool (*load_game)(const retro_game_info *) = nullptr;
    void (*unload_game)() = nullptr;
    void *(*get_memory_data)(unsigned) = nullptr;
    size_t (*get_memory_size)(unsigned) = nullptr;
    void (*cheat_reset)() = nullptr;
    void (*cheat_set)(unsigned, bool, const char *) = nullptr;
};

// Single-producer (core thread) single-consumer (AAudio callback) ring of stereo frames.
class AudioRing {
public:
    void reset(size_t frames) {
        buf_.assign(frames * 2, 0);
        cap_ = frames;
        read_ = 0;
        write_ = 0;
    }
    size_t fill() const { return write_.load(std::memory_order_acquire) - read_.load(std::memory_order_acquire); }
    void write(const int16_t *data, size_t frames) {
        size_t w = write_.load(std::memory_order_relaxed);
        size_t space = cap_ - (w - read_.load(std::memory_order_acquire));
        if (frames > space) frames = space; // overflow: drop the newest
        for (size_t i = 0; i < frames; i++) {
            size_t at = ((w + i) % cap_) * 2;
            buf_[at] = data[i * 2];
            buf_[at + 1] = data[i * 2 + 1];
        }
        write_.store(w + frames, std::memory_order_release);
    }
    void read(int16_t *out, size_t frames) {
        size_t r = read_.load(std::memory_order_relaxed);
        size_t avail = write_.load(std::memory_order_acquire) - r;
        size_t n = frames < avail ? frames : avail;
        for (size_t i = 0; i < n; i++) {
            size_t at = ((r + i) % cap_) * 2;
            out[i * 2] = buf_[at];
            out[i * 2 + 1] = buf_[at + 1];
        }
        if (n < frames) memset(out + n * 2, 0, (frames - n) * 2 * sizeof(int16_t)); // underrun: silence
        read_.store(r + n, std::memory_order_release);
    }
private:
    std::vector<int16_t> buf_;
    size_t cap_ = 1;
    std::atomic<size_t> read_{0}, write_{0};
};

struct Player {
    CoreApi core;
    std::string corePath, systemDir, saveDir, contentPath;
    retro_system_info info{};
    retro_system_av_info av{};
    retro_pixel_format format = RETRO_PIXEL_FORMAT_0RGB1555;
    bool gameLoaded = false;
    std::vector<uint8_t> content;

    // Core options: values chosen (defaults unless Kotlin set one), kept alive for GET_VARIABLE.
    std::map<std::string, std::string> variables, overrides;
    std::vector<std::pair<std::string, std::string>> definitions; // key, "Description; a|b|c", in the core's order
    std::atomic<bool> variablesChanged{false};

    std::mutex coreMutex; // every call into the core
    std::thread loop;
    std::atomic<bool> running{false}, paused{false}, fastForward{false};
    // Fast-forward the core asked for itself (VICE while a disk loads), on top of the user's own.
    std::atomic<bool> coreFastForward{false};
    std::atomic<float> volume{1.0f};
    retro_disk_control_ext_callback disks{}; // multi-disc games (the core's eject/insert); zeroed when not offered // boost for quiet systems; samples are clamped, not wrapped
    std::atomic<int> viewX{0}, viewY{0}, viewW{0}, viewH{0}; // picture rectangle in surface pixels; 0 = fit

    // Window hand-over between the UI thread and the core thread.
    std::mutex windowMutex;
    std::condition_variable windowCv;
    ANativeWindow *pendingWindow = nullptr;
    bool windowPending = false;
    // Second display (dual-screen handhelds): with split on, the top half of the picture goes to the main surface
    // and the bottom half (the DS touch screen) to this one.
    ANativeWindow *pendingWindow2 = nullptr;
    bool window2Pending = false;
    std::atomic<bool> split{false};
    // Cardboard VR: the picture once per eye, side by side. With a side-by-side source (Virtual Boy in 3D) each eye
    // gets its own half of the frame; outside VR only the left eye is shown, as a normal picture.
    std::atomic<bool> vr{false}, sideBySideSource{false};

    // GL state, owned by the core thread.
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLConfig config = nullptr;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    ANativeWindow *window = nullptr;
    EGLSurface surface2 = EGL_NO_SURFACE;
    ANativeWindow *window2 = nullptr;
    // Keyboard (computers: C64, Amiga, DOS …): keys held, and press/release events for the core's callback, which is
    // called on the core thread just before it runs a frame.
    std::atomic<bool> keys[RETROK_LAST] = {};
    // Mouse (the touchscreen as a trackpad): movement adds up between frames, and each frame reads what came since.
    std::atomic<int> mouseDx{0}, mouseDy{0}, mouseButtons{0};
    int frameMouseDx = 0, frameMouseDy = 0;
    retro_keyboard_event_t keyboardCallback = nullptr;
    struct KeyEvent { bool down; unsigned keycode; uint32_t character; uint16_t modifiers; };
    std::mutex keyMutex;
    std::vector<KeyEvent> keyEvents;
    // A key is held at least kMinKeyHold, so an emulator that reads the keyboard once a frame (a C64 every 20 ms)
    // sees even a quick tap: releases that come sooner wait here until their time.
    std::chrono::steady_clock::time_point keyDownAt[RETROK_LAST];
    struct PendingUp { unsigned keycode; uint16_t modifiers; std::chrono::steady_clock::time_point at; };
    std::vector<PendingUp> pendingUps;
    GLuint program = 0, texture = 0;
    // The frame before, for LCD ghosting (filter 5): the two textures swap places each frame.
    GLuint prevTexture = 0;
    unsigned prevW = 0, prevH = 0;
    GLint uTexSize = -1, uOutSize = -1, uSrcSize = -1, uFilter = -1, uRegion = -1, uPrev = -1;
    std::atomic<int> filter{0};
    unsigned texW = 0, texH = 0;
    bool hasFrame = false;
    std::vector<uint8_t> staging;

    // Hardware rendering: cores that draw with OpenGL ES (N64, PSP, Dreamcast …) draw into hwFbo, which is then
    // drawn to the screen like a software frame. The context is GLES 3 when such a core asked for it.
    retro_hw_render_callback hw{};
    bool hwRequested = false, hwReady = false, hwFrame = false;
    // Vulkan cores (Dolphin, Citra, PCSX2 …): their frames are copied into hwTexture (vulkan_render.cpp). preferVulkan
    // is the user's choice for this system ("retroforge_graphics" = "vulkan"), told to cores that ask what's preferred.
    bool vulkan = false, preferVulkan = false;
    // Whether this core is offered the player's VFS (streaming): "retroforge_vfs" = "on" from the app.
    bool offerVfs = false;
    // RETRO_ENVIRONMENT_SET_ROTATION: the picture turned this many quarter turns anticlockwise.
    std::atomic<unsigned> rotation{0};
    // Frames put on screen, for the FPS counter (read once a second by the app).
    std::atomic<uint64_t> framesShown{0};
    GLuint hwFbo = 0, hwTexture = 0, hwDepth = 0;
    unsigned hwW = 0, hwH = 0;       // framebuffer size (the core's largest picture)
    unsigned frameW = 0, frameH = 0; // the part of it the last frame used
    int glesVersion = 2;

    // Audio.
    AAudioStream *stream = nullptr;
    std::atomic<bool> audioLost{false};
    AudioRing ring;
    int deviceRate = 48000;
    double step = 1.0, rsPos = 0.0;
    float prevL = 0, prevR = 0;
    std::vector<int16_t> resampled;

    // Controls: libretro joypad bitmask and analog sticks per port.
    std::atomic<uint32_t> buttons[4]{};
    // Every button pressed since the frame last used them, so a tap shorter than a frame still counts (slow games,
    // netplay waiting for the other player): latched by setButtons, carried until used, read once per frame.
    std::atomic<uint32_t> latched[4]{};
    uint32_t carried[4]{}, frameButtons[4]{};
    // What the core says player 1's joypad buttons do (id → its name for it), for the on-screen buttons.
    std::map<unsigned, std::string> buttonNames;
    std::atomic<int16_t> analog[4][2][2]{};
    // Touchscreen (DS bottom screen and similar), in libretro's -0x7fff..0x7fff over the drawn game.
    std::atomic<int16_t> pointerX{0}, pointerY{0};
    std::atomic<bool> pointerDown{false};
    // Netplay: this frame's buttons for players 1 and 2 come from both devices (netplay.h), not from this one alone.
    bool netInput = false;
    netplay::FrameInput netFrame;
};

Player *P = nullptr;

// ---------- core callbacks ----------

void coreLog(enum retro_log_level level, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(level >= RETRO_LOG_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, "RetroForgeCore", fmt, args);
    va_end(args);
}

retro_time_t perfTimeUsec() {
    return std::chrono::duration_cast<std::chrono::microseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}
retro_perf_tick_t perfCounter() {
    return std::chrono::steady_clock::now().time_since_epoch().count();
}
uint64_t perfCpuFeatures() { return 0; }
void perfLog() {}
void perfRegister(retro_perf_counter *) {}
void perfStart(retro_perf_counter *) {}
void perfStop(retro_perf_counter *) {}

// "Description; first|second|third" -> "first"
std::string defaultOf(const char *value) {
    std::string v = value ? value : "";
    size_t semi = v.find("; ");
    std::string options = semi == std::string::npos ? v : v.substr(semi + 2);
    size_t bar = options.find('|');
    return bar == std::string::npos ? options : options.substr(0, bar);
}

uintptr_t hwFramebuffer() { return P ? P->hwFbo : 0; }

retro_proc_address_t hwProcAddress(const char *sym) {
    // Core GL ES functions from the library itself (some drivers' eglGetProcAddress only knows extensions).
    static void *gles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
    void *f = gles ? dlsym(gles, sym) : nullptr;
    if (!f) f = reinterpret_cast<void *>(eglGetProcAddress(sym));
    return reinterpret_cast<retro_proc_address_t>(f);
}

bool environment(unsigned cmd, void *data) {
    switch (cmd) {
        // Files through the player, so games RetroForge streams (network drives, Android folders) need no copy.
        case RETRO_ENVIRONMENT_GET_VFS_INTERFACE:
            // Only for cores the app streams games to; the rest keep their own file access, as before streaming.
            return P->offerVfs && vfs::provide(static_cast<retro_vfs_interface_info *>(data));
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            static_cast<retro_log_callback *>(data)->log = coreLog;
            return true;
        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE: {
            auto *perf = static_cast<retro_perf_callback *>(data);
            perf->get_time_usec = perfTimeUsec;
            perf->get_cpu_features = perfCpuFeatures;
            perf->get_perf_counter = perfCounter;
            perf->perf_register = perfRegister;
            perf->perf_start = perfStart;
            perf->perf_stop = perfStop;
            perf->perf_log = perfLog;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            auto format = *static_cast<const retro_pixel_format *>(data);
            if (format > RETRO_PIXEL_FORMAT_RGB565) return false;
            P->format = format;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *static_cast<const char **>(data) = P->systemDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *static_cast<const char **>(data) = P->saveDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY:
            *static_cast<const char **>(data) = P->systemDir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_LIBRETRO_PATH:
            *static_cast<const char **>(data) = P->corePath.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *static_cast<bool *>(data) = true;
            return true;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *static_cast<unsigned *>(data) = RETRO_LANGUAGE_ENGLISH;
            return true;
        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            if (data) *static_cast<int *>(data) = 3;
            return true;
        case RETRO_ENVIRONMENT_SET_FASTFORWARDING_OVERRIDE: {
            // The core turns fast-forward on and off itself (e.g. VICE's warp while a disk loads).
            if (data) P->coreFastForward = static_cast<const retro_fastforwarding_override *>(data)->fastforward;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_FASTFORWARDING:
            if (data) *static_cast<bool *>(data) = P->fastForward || P->coreFastForward;
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            // 0: cores fall back to the plain SET_VARIABLES list, which is all the player needs.
            *static_cast<unsigned *>(data) = 0;
            return true;
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            P->definitions.clear();
            for (auto *v = static_cast<const retro_variable *>(data); v && v->key; v++) {
                P->definitions.emplace_back(v->key, v->value ? v->value : "");
                auto chosen = P->overrides.find(v->key);
                P->variables[v->key] = chosen != P->overrides.end() ? chosen->second : defaultOf(v->value);
            }
            return true;
        }
        case RETRO_ENVIRONMENT_SET_VARIABLE: {
            auto *v = static_cast<const retro_variable *>(data);
            if (!v || !v->key) return true;
            if (v->value) P->variables[v->key] = v->value;
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto *v = static_cast<retro_variable *>(data);
            auto found = P->variables.find(v->key ? v->key : "");
            if (found == P->variables.end()) {
                v->value = nullptr;
                return false;
            }
            v->value = found->second.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *static_cast<bool *>(data) = P->variablesChanged.exchange(false);
            return true;
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            P->av.geometry = *static_cast<const retro_game_geometry *>(data);
            return true;
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
            P->av = *static_cast<const retro_system_av_info *>(data);
            if (P->av.timing.sample_rate > 0) P->step = P->av.timing.sample_rate / P->deviceRate;
            return true;
        case RETRO_ENVIRONMENT_GET_TARGET_REFRESH_RATE:
            *static_cast<float *>(data) = 60.0f;
            return true;
        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            auto *m = static_cast<const retro_message *>(data);
            if (m && m->msg) LOGI("core message: %s", m->msg);
            return true;
        }
        case RETRO_ENVIRONMENT_GET_DISK_CONTROL_INTERFACE_VERSION:
            *static_cast<unsigned *>(data) = 1;
            return true;
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE:
            P->disks = data ? *static_cast<const retro_disk_control_ext_callback *>(data) : retro_disk_control_ext_callback{};
            return true;
        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: {
            P->disks = {};
            if (data) std::memcpy(&P->disks, data, sizeof(retro_disk_control_callback));
            return true;
        }
        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            // OpenGL ES 2 or 3, or Vulkan (desktop OpenGL doesn't exist on Android).
            auto *hw = static_cast<retro_hw_render_callback *>(data);
            if (hw->context_type == RETRO_HW_CONTEXT_VULKAN) {
                void *vk = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
                LOGI("core asked for GPU rendering, Vulkan: %s", vk ? "offered" : "not offered (no driver)");
                if (!vk) return false;
                dlclose(vk);
                P->hw = *hw;
                P->hwRequested = true;
                P->vulkan = true;
                return true;
            }
            bool gles = hw->context_type == RETRO_HW_CONTEXT_OPENGLES2 || hw->context_type == RETRO_HW_CONTEXT_OPENGLES3 ||
                        (hw->context_type == RETRO_HW_CONTEXT_OPENGLES_VERSION && hw->version_major <= 3);
            LOGI("core asked for GPU rendering, type %d %u.%u: %s", hw->context_type, hw->version_major, hw->version_minor,
                 gles ? "offered" : "not offered");
            if (!gles) return false;
            hw->get_current_framebuffer = hwFramebuffer;
            hw->get_proc_address = hwProcAddress;
            P->hw = *hw;
            P->hwRequested = true;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_KEYBOARD_CALLBACK: {
            auto *cb = static_cast<const retro_keyboard_callback *>(data);
            P->keyboardCallback = cb ? cb->callback : nullptr;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_ROTATION:
            P->rotation = *static_cast<const unsigned *>(data) & 3;
            LOGI("core rotates the picture %u x 90 degrees", P->rotation.load());
            return true;
        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER:
            *static_cast<unsigned *>(data) = P->preferVulkan ? RETRO_HW_CONTEXT_VULKAN : RETRO_HW_CONTEXT_OPENGLES3;
            return true;
        case RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE:
            return vkr::setNegotiation(static_cast<const retro_hw_render_context_negotiation_interface *>(data));
        case RETRO_ENVIRONMENT_GET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_SUPPORT:
            return vkr::negotiationSupport(static_cast<retro_hw_render_context_negotiation_interface *>(data));
        case RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE: {
            auto *iface = P->vulkan ? vkr::interface() : nullptr;
            if (!iface) return false;
            *static_cast<const retro_hw_render_interface **>(data) = iface;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT:
            return true;
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS: {
            if (!P || !data) return true;
            P->buttonNames.clear();
            for (auto *d = static_cast<const retro_input_descriptor *>(data); d->description; d++)
                if (d->port == 0 && d->device == RETRO_DEVICE_JOYPAD && d->id <= RETRO_DEVICE_ID_JOYPAD_R3) P->buttonNames[d->id] = d->description;
            return true;
        }
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_MINIMUM_AUDIO_LATENCY:
            return true;
        default: {
            // Hardware rendering, rumble, VFS and the rest aren't offered yet; cores fall back or say so.
            // Logged once per command, to see what a new core asks for.
            static std::set<unsigned> logged;
            if (logged.insert(cmd).second) LOGI("core asked for environment %u (not offered)", cmd);
            return false;
        }
    }
}

// ---------- video ----------

const char *kVertex = R"(
attribute vec2 aPos;
attribute vec2 aUv;
varying vec2 vUv;
void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); })";

// uFilter 4, curved CRT: CRT with the picture bent like a tube and darker corners.
// uFilter 0, sharp: whole-pixel scaling with a one-pixel blend at the edges, so pixel art stays crisp at any size
// without the uneven pixels of nearest-neighbour. 1, smooth: plain bilinear. 2, CRT: sharp plus darkened gaps between
// the console's scan lines. 3, LCD: sharp plus a faint grid between pixels, like a handheld's screen.
// 5, LCD with ghosting: the LCD grid, with each frame blended into the one before, as on a Game Boy's slow screen
// (games that flicker sprites for transparency look as intended). 6, smooth pixel art: Scale2x, the classic upscaler.
const char *kFragment = R"(
precision mediump float;
uniform sampler2D uTex;
uniform sampler2D uPrev;
uniform vec2 uTexSize;
uniform vec2 uOutSize;
uniform vec2 uSrcSize;
uniform int uFilter;
uniform vec4 uRegion; // the drawn part of the texture: left, top, right, bottom
varying vec2 vUv;
void main() {
    vec2 uv = vUv;
    if (uFilter == 4) {
        // Curved CRT: bend the picture like a tube's glass, black beyond its edges.
        vec2 local = (vUv - uRegion.xy) / (uRegion.zw - uRegion.xy) * 2.0 - 1.0;
        local *= 1.0 + 0.06 * dot(local.yx, local.yx);
        if (abs(local.x) > 1.0 || abs(local.y) > 1.0) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        uv = uRegion.xy + (local * 0.5 + 0.5) * (uRegion.zw - uRegion.xy);
    }
    if (uFilter == 1) {
        gl_FragColor = vec4(texture2D(uTex, uv).rgb, 1.0);
        return;
    }
    vec2 texel = uv * uTexSize;
    if (uFilter == 6) {
        // Scale2x: each pixel becomes four, rounding off staircase diagonals where neighbours agree.
        vec2 base = floor(texel) + 0.5;
        vec2 quarter = step(0.5, fract(texel)); // 0 or 1 across, 0 or 1 down
        vec3 p = texture2D(uTex, base / uTexSize).rgb;
        vec3 a = texture2D(uTex, (base + vec2(0.0, -1.0)) / uTexSize).rgb; // above
        vec3 b = texture2D(uTex, (base + vec2(1.0, 0.0)) / uTexSize).rgb;  // right
        vec3 c = texture2D(uTex, (base + vec2(-1.0, 0.0)) / uTexSize).rgb; // left
        vec3 d = texture2D(uTex, (base + vec2(0.0, 1.0)) / uTexSize).rgb;  // below
        // The two neighbours meeting at this quarter's corner, and the two away from it.
        vec3 v = quarter.y < 0.5 ? a : d;
        vec3 h = quarter.x < 0.5 ? c : b;
        vec3 vFar = quarter.y < 0.5 ? d : a;
        vec3 hFar = quarter.x < 0.5 ? b : c;
        float same = step(distance(v, h), 0.02);
        float notV = step(0.02, distance(h, vFar));
        float notH = step(0.02, distance(v, hFar));
        gl_FragColor = vec4(mix(p, v, same * notV * notH), 1.0);
        return;
    }
    vec2 scale = max(floor(uOutSize / uSrcSize), vec2(1.0));
    vec2 region = 0.5 - 0.5 / scale;
    vec2 centre = fract(texel) - 0.5;
    vec2 f = (centre - clamp(centre, -region, region)) * scale + 0.5;
    vec2 at = (floor(texel) + f) / uTexSize;
    vec3 colour = texture2D(uTex, at).rgb;
    if (uFilter == 5) colour = mix(texture2D(uPrev, at).rgb, colour, 0.6);
    if (uFilter == 2) {
        float d = abs(fract(texel.y) - 0.5) * 2.0;
        colour *= (1.0 - 0.5 * d * d) * 1.15;
    } else if (uFilter == 4) {
        float d = abs(fract(texel.y) - 0.5) * 2.0;
        vec2 local = (uv - uRegion.xy) / (uRegion.zw - uRegion.xy) * 2.0 - 1.0;
        colour *= (1.0 - 0.45 * d * d) * 1.18 * (1.0 - 0.25 * dot(local, local) * 0.5);
    } else if (uFilter == 3 || uFilter == 5) {
        vec2 d = abs(fract(texel) - 0.5) * 2.0;
        colour *= 1.0 - 0.35 * smoothstep(0.7, 1.0, max(d.x, d.y));
    }
    gl_FragColor = vec4(colour, 1.0);
})";

GLuint compile(GLenum type, const char *source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(shader, sizeof log, nullptr, log);
        LOGE("shader: %s", log);
    }
    return shader;
}

bool eglStart() {
    Player &p = *P;
    p.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (p.display == EGL_NO_DISPLAY || !eglInitialize(p.display, nullptr, nullptr)) return false;
    // GPU cores get OpenGL ES 3 (which also runs their ES 2 code); software cores only need ES 2.
    for (int version : {p.hwRequested ? 3 : 2, 2}) {
        const EGLint attrs[] = {EGL_RENDERABLE_TYPE, version == 3 ? 0x40 /* EGL_OPENGL_ES3_BIT_KHR */ : EGL_OPENGL_ES2_BIT,
                                EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE};
        EGLint count = 0;
        if (!eglChooseConfig(p.display, attrs, &p.config, 1, &count) || count == 0) continue;
        const EGLint ctx[] = {EGL_CONTEXT_CLIENT_VERSION, version, EGL_NONE};
        p.context = eglCreateContext(p.display, p.config, EGL_NO_CONTEXT, ctx);
        if (p.context != EGL_NO_CONTEXT) {
            p.glesVersion = version;
            return true;
        }
    }
    return false;
}

void glSetup() {
    Player &p = *P;
    p.program = glCreateProgram();
    glAttachShader(p.program, compile(GL_VERTEX_SHADER, kVertex));
    glAttachShader(p.program, compile(GL_FRAGMENT_SHADER, kFragment));
    glBindAttribLocation(p.program, 0, "aPos");
    glBindAttribLocation(p.program, 1, "aUv");
    glLinkProgram(p.program);
    p.uTexSize = glGetUniformLocation(p.program, "uTexSize");
    p.uOutSize = glGetUniformLocation(p.program, "uOutSize");
    p.uSrcSize = glGetUniformLocation(p.program, "uSrcSize");
    p.uRegion = glGetUniformLocation(p.program, "uRegion");
    p.uFilter = glGetUniformLocation(p.program, "uFilter");
    p.uPrev = glGetUniformLocation(p.program, "uPrev");
    glUseProgram(p.program);
    glUniform1i(glGetUniformLocation(p.program, "uTex"), 0);
    glUniform1i(p.uPrev, 1);
    for (GLuint *texture : {&p.texture, &p.prevTexture}) {
        glGenTextures(1, texture);
        glBindTexture(GL_TEXTURE_2D, *texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    p.texW = p.texH = p.prevW = p.prevH = 0;
}

// (Re)sizes the GPU core's framebuffer to the core's largest picture.
void hwResize() {
    Player &p = *P;
    if (p.vulkan && p.hwW && p.hwH) return; // Vulkan frames size the texture themselves (vulkanFrame)
    unsigned w = std::max(1u, std::max(p.av.geometry.max_width, p.av.geometry.base_width));
    unsigned h = std::max(1u, std::max(p.av.geometry.max_height, p.av.geometry.base_height));
    if (w == p.hwW && h == p.hwH) return;
    glBindTexture(GL_TEXTURE_2D, p.hwTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindFramebuffer(GL_FRAMEBUFFER, p.hwFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, p.hwTexture, 0);
    if (p.hw.depth) {
        glBindRenderbuffer(GL_RENDERBUFFER, p.hwDepth);
        glRenderbufferStorage(GL_RENDERBUFFER, p.hw.stencil ? GL_DEPTH24_STENCIL8 : GL_DEPTH_COMPONENT24, w, h);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, p.hw.stencil ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, p.hwDepth);
    }
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) LOGE("GPU framebuffer %ux%u incomplete: 0x%x", w, h, status);
    glViewport(0, 0, w, h);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    p.hwW = w;
    p.hwH = h;
}

// Creates the framebuffer and tells the core its GL context is ready (on the core thread, context current).
void hwSetup() {
    Player &p = *P;
    glGenFramebuffers(1, &p.hwFbo);
    glGenTextures(1, &p.hwTexture);
    glBindTexture(GL_TEXTURE_2D, p.hwTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (p.hw.depth && !p.vulkan) glGenRenderbuffers(1, &p.hwDepth);
    if (p.vulkan) {
        std::string error;
        if (!vkr::init(error)) LOGE("Vulkan: %s", error.c_str());
        p.hw.depth = false;
    }
    hwResize();
    LOGI("GPU rendering ready: OpenGL ES %d, framebuffer %ux%u%s", p.glesVersion, p.hwW, p.hwH, p.hw.depth ? " with depth" : "");
    if (p.hw.context_reset) {
        std::lock_guard<std::mutex> lock(p.coreMutex);
        p.hw.context_reset();
    }
    p.hwReady = true;
}

// Puts back the GL state the player's own drawing relies on, whatever the core left behind.
void restoreGlState() {
    Player &p = *P;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (p.glesVersion >= 3) {
        glBindVertexArray(0);
        glBindSampler(0, 0);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_DITHER);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glActiveTexture(GL_TEXTURE0);
    GLint attribs = 0;
    glGetIntegerv(GL_MAX_VERTEX_ATTRIBS, &attribs);
    for (GLint i = 2; i < attribs; i++) glDisableVertexAttribArray(i);
}

void releaseSurface() {
    Player &p = *P;
    if (p.surface != EGL_NO_SURFACE) {
        eglMakeCurrent(p.display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(p.display, p.surface);
        p.surface = EGL_NO_SURFACE;
    }
    if (p.window) {
        ANativeWindow_release(p.window);
        p.window = nullptr;
    }
}

// Picks up a new or removed window from the UI thread and acknowledges it, so the UI never destroys a window in use.
void applyWindowChange() {
    Player &p = *P;
    std::unique_lock<std::mutex> lock(p.windowMutex);
    if (!p.windowPending) return;
    releaseSurface();
    if (p.pendingWindow) {
        p.window = p.pendingWindow;
        p.pendingWindow = nullptr;
        if (p.context == EGL_NO_CONTEXT && !eglStart()) LOGE("EGL failed to start");
        if (p.context != EGL_NO_CONTEXT) {
            p.surface = eglCreateWindowSurface(p.display, p.config, p.window, nullptr);
            if (p.surface != EGL_NO_SURFACE && eglMakeCurrent(p.display, p.surface, p.surface, p.context)) {
                eglSwapInterval(p.display, 1);
                if (p.program == 0) glSetup();
                if (p.hwRequested && !p.hwReady) hwSetup();
                p.hasFrame = false;
            } else {
                LOGE("EGL surface failed");
            }
        }
    }
    p.windowPending = false;
    if (p.window2Pending && p.context != EGL_NO_CONTEXT) {
        if (p.surface2 != EGL_NO_SURFACE) {
            eglDestroySurface(p.display, p.surface2);
            p.surface2 = EGL_NO_SURFACE;
        }
        if (p.window2) ANativeWindow_release(p.window2);
        p.window2 = p.pendingWindow2;
        p.pendingWindow2 = nullptr;
        if (p.window2) {
            p.surface2 = eglCreateWindowSurface(p.display, p.config, p.window2, nullptr);
            if (p.surface2 == EGL_NO_SURFACE) LOGE("EGL second surface failed");
        }
        p.window2Pending = false;
    }
    p.windowCv.notify_all();
}

void videoRefresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    Player &p = *P;
    static int framesLogged = 0;
    if (framesLogged < 3) {
        framesLogged++;
        LOGI("frame %ux%u pitch %zu format %d%s", width, height, pitch, static_cast<int>(p.format), data ? "" : " (no data)");
    }
    if (data == RETRO_HW_FRAME_BUFFER_VALID && p.vulkan) {
        // Drawn by the core with Vulkan: its picture is copied into hwTexture.
        p.staging.resize(size_t(width) * height * 4);
        if (!vkr::readFrame(width, height, p.staging.data())) return;
        glBindTexture(GL_TEXTURE_2D, p.hwTexture);
        if (width > p.hwW || height > p.hwH) {
            p.hwW = std::max(width, p.hwW);
            p.hwH = std::max(height, p.hwH);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, p.hwW, p.hwH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        }
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, p.staging.data());
        p.frameW = width;
        p.frameH = height;
        p.hwFrame = true;
        p.hasFrame = true;
        return;
    }
    if (data == RETRO_HW_FRAME_BUFFER_VALID) {
        // Drawn by the core into hwFbo: only remember how much of it this frame used.
        p.frameW = width;
        p.frameH = height;
        p.hwFrame = true;
        p.hasFrame = true;
        return;
    }
    if (!data || p.surface == EGL_NO_SURFACE || width == 0 || height == 0) return; // duplicate frame: keep the last
    p.hwFrame = false;
    GLenum glFormat, glType;
    if (p.format == RETRO_PIXEL_FORMAT_XRGB8888) {
        p.staging.resize(width * height * 4);
        auto *out = reinterpret_cast<uint32_t *>(p.staging.data());
        for (unsigned y = 0; y < height; y++) {
            auto *row = reinterpret_cast<const uint32_t *>(static_cast<const uint8_t *>(data) + y * pitch);
            for (unsigned x = 0; x < width; x++) {
                uint32_t c = row[x]; // 0x00RRGGBB -> bytes R,G,B,A
                out[y * width + x] = 0xFF000000u | ((c & 0xFFu) << 16) | (c & 0xFF00u) | ((c >> 16) & 0xFFu);
            }
        }
        glFormat = GL_RGBA;
        glType = GL_UNSIGNED_BYTE;
    } else {
        p.staging.resize(width * height * 2);
        auto *out = reinterpret_cast<uint16_t *>(p.staging.data());
        bool from1555 = p.format == RETRO_PIXEL_FORMAT_0RGB1555;
        for (unsigned y = 0; y < height; y++) {
            auto *row = reinterpret_cast<const uint16_t *>(static_cast<const uint8_t *>(data) + y * pitch);
            if (!from1555) {
                memcpy(out + y * width, row, width * 2);
                continue;
            }
            for (unsigned x = 0; x < width; x++) {
                uint16_t c = row[x];
                out[y * width + x] = static_cast<uint16_t>(((c & 0x7FE0u) << 1) | ((c >> 4) & 0x20u) | (c & 0x1Fu));
            }
        }
        glFormat = GL_RGB;
        glType = GL_UNSIGNED_SHORT_5_6_5;
    }
    glPixelStorei(GL_UNPACK_ALIGNMENT, 2);
    if (p.filter == 5) {
        // Keep the last frame for ghosting: upload into the older texture, which becomes the current one.
        std::swap(p.texture, p.prevTexture);
        std::swap(p.texW, p.prevW);
        std::swap(p.texH, p.prevH);
    }
    glBindTexture(GL_TEXTURE_2D, p.texture);
    if (width != p.texW || height != p.texH) {
        glTexImage2D(GL_TEXTURE_2D, 0, glFormat, width, height, 0, glFormat, glType, p.staging.data());
        p.texW = width;
        p.texH = height;
    } else {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, glFormat, glType, p.staging.data());
    }
    p.hasFrame = true;
}

// Draws the part x0..x1, y0..y1 (fractions, left to right and top to bottom) of the current picture into the area
// ax, ay, aw, ah of the current surface (GL coordinates), fitted and centred, or into the rectangle Kotlin chose when
// [useChosen].
void drawPicture(int ax, int ay, int aw, int ah, int sh, float x0, float x1, float y0, float y1, bool useChosen) {
    Player &p = *P;
    unsigned fw = p.hwFrame ? p.frameW : p.texW, fh = p.hwFrame ? p.frameH : p.texH;
    if (fw == 0 || fh == 0 || aw <= 0 || ah <= 0) return;
    int vx, vy, vw, vh;
    if (useChosen && p.viewW > 0 && p.viewH > 0) {
        // Chosen by Kotlin (screen shape setting); top-left origin there, bottom-left in GL.
        vx = p.viewX;
        vw = p.viewW;
        vh = p.viewH;
        vy = sh - (p.viewY + vh);
    } else {
        // A side-by-side frame holds two pictures: its own shape, not the core's (one eye's), is the reference.
        float full = p.av.geometry.aspect_ratio > 0 && !p.sideBySideSource ? p.av.geometry.aspect_ratio : float(fw) / float(fh);
        float aspect = full * (x1 - x0) / (y1 - y0);
        vw = aw;
        vh = int(aw / aspect);
        if (vh > ah) {
            vh = ah;
            vw = int(ah * aspect);
        }
        vx = ax + (aw - vw) / 2;
        vy = ay + (ah - vh) / 2;
    }
    glViewport(vx, vy, vw, vh);
    // Software frames are stored top row first; a GPU core's framebuffer is bottom row first (unless it says
    // otherwise) and only partly used, so the corners pick out that part.
    GLfloat left = x0, right = x1, top = y0, bottom = y1;
    if (p.hwFrame) {
        float u = float(p.frameW) / float(p.hwW), v = float(p.frameH) / float(p.hwH);
        left = x0 * u;
        right = x1 * u;
        top = p.hw.bottom_left_origin ? (1 - y0) * v : y0 * v;
        bottom = p.hw.bottom_left_origin ? (1 - y1) * v : y1 * v;
    }
    // The core's rotation (vertical WonderSwan and arcade games): each screen corner shows the image corner r steps
    // clockwise from it, which turns the picture r x 90 degrees anticlockwise. Corners: top left, top right,
    // bottom right, bottom left.
    const GLfloat corners[4][2] = {{left, top}, {right, top}, {right, bottom}, {left, bottom}};
    unsigned r = p.rotation & 3;
    auto uv = [&](unsigned corner) { return corners[(corner + r) & 3]; };
    const GLfloat quad[] = {-1, -1, uv(3)[0], uv(3)[1], 1, -1, uv(2)[0], uv(2)[1],
                            -1, 1, uv(0)[0], uv(0)[1], 1, 1, uv(1)[0], uv(1)[1]};
    glUseProgram(p.program);
    if (p.hwFrame) {
        glUniform2f(p.uTexSize, float(p.hwW), float(p.hwH));
    } else {
        glUniform2f(p.uTexSize, float(p.texW), float(p.texH));
    }
    glUniform2f(p.uSrcSize, float(fw) * (x1 - x0), float(fh) * (y1 - y0));
    glUniform2f(p.uOutSize, float(vw), float(vh));
    glUniform1i(p.uFilter, p.filter);
    glUniform4f(p.uRegion, left, std::min(top, bottom), right, std::max(top, bottom));
    // The frame before (filter 5), or this one again until there is one of the same size.
    bool havePrev = !p.hwFrame && p.prevW == p.texW && p.prevH == p.texH;
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, p.hwFrame ? p.hwTexture : havePrev ? p.prevTexture : p.texture);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, p.hwFrame ? p.hwTexture : p.texture);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), quad);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), quad + 2);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void present() {
    Player &p = *P;
    if (p.surface == EGL_NO_SURFACE) return;
    if (p.hwRequested) restoreGlState();
    bool split = p.split && p.surface2 != EGL_NO_SURFACE;
    EGLint sw = 0, sh = 0;
    eglQuerySurface(p.display, p.surface, EGL_WIDTH, &sw);
    eglQuerySurface(p.display, p.surface, EGL_HEIGHT, &sh);
    glViewport(0, 0, sw, sh);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    if (p.hasFrame && sw > 0 && sh > 0) {
        bool sbs = p.sideBySideSource;
        if (p.vr) {
            // One picture per eye, inside each half with a margin so it sits comfortably in a viewer's lens.
            int eyeW = sw / 2, margin = eyeW / 12;
            for (int eye = 0; eye < 2; eye++) {
                float x0 = sbs ? 0.5f * eye : 0, x1 = sbs ? x0 + 0.5f : 1;
                drawPicture(eye * eyeW + margin, margin, eyeW - 2 * margin, sh - 2 * margin, sh, x0, x1, 0, 1, false);
            }
        } else {
            drawPicture(0, 0, sw, sh, sh, 0, sbs ? 0.5f : 1, 0, split ? 0.5f : 1, true);
        }
    }
    eglSwapBuffers(p.display, p.surface);
    p.framesShown++;
    if (split && eglMakeCurrent(p.display, p.surface2, p.surface2, p.context)) {
        EGLint w2 = 0, h2 = 0;
        eglQuerySurface(p.display, p.surface2, EGL_WIDTH, &w2);
        eglQuerySurface(p.display, p.surface2, EGL_HEIGHT, &h2);
        glViewport(0, 0, w2, h2);
        glClear(GL_COLOR_BUFFER_BIT);
        if (p.hasFrame && w2 > 0 && h2 > 0) drawPicture(0, 0, w2, h2, h2, 0, 1, 0.5f, 1, false);
        eglSwapBuffers(p.display, p.surface2);
        eglMakeCurrent(p.display, p.surface, p.surface, p.context);
    }
}

// ---------- audio ----------

aaudio_data_callback_result_t audioCallback(AAudioStream *, void *, void *audioData, int32_t frames) {
    P->ring.read(static_cast<int16_t *>(audioData), static_cast<size_t>(frames));
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void audioError(AAudioStream *, void *, aaudio_result_t error) {
    // Headphones plugged in or out: the core thread reopens the stream (it can't be done from this callback).
    if (error == AAUDIO_ERROR_DISCONNECTED) P->audioLost = true;
}

void audioClose() {
    if (P->stream) {
        AAudioStream_requestStop(P->stream);
        AAudioStream_close(P->stream);
        P->stream = nullptr;
    }
}

void audioOpen() {
    Player &p = *P;
    audioClose();
    AAudioStreamBuilder *builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return;
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setDataCallback(builder, audioCallback, nullptr);
    AAudioStreamBuilder_setErrorCallback(builder, audioError, nullptr);
    if (AAudioStreamBuilder_openStream(builder, &p.stream) != AAUDIO_OK) p.stream = nullptr;
    AAudioStreamBuilder_delete(builder);
    if (!p.stream) {
        LOGE("audio stream failed to open");
        return;
    }
    // The device's own rate; the core's rate is converted to it here, so speed never depends on the device.
    p.deviceRate = AAudioStream_getSampleRate(p.stream);
    double coreRate = p.av.timing.sample_rate > 0 ? p.av.timing.sample_rate : 44100.0;
    p.step = coreRate / p.deviceRate;
    p.ring.reset(static_cast<size_t>(p.deviceRate / 4)); // 250 ms
    p.audioLost = false;
    AAudioStream_requestStart(p.stream);
}

void pushAudio(const int16_t *data, size_t frames) {
    Player &p = *P;
    if (!p.stream || p.fastForward || p.coreFastForward) return; // fast-forward plays silently rather than chipmunk-fast
    p.resampled.clear();
    const float gain = p.volume;
    for (size_t i = 0; i < frames; i++) {
        float l = std::clamp(data[i * 2] * gain, -32768.0f, 32767.0f);
        float r = std::clamp(data[i * 2 + 1] * gain, -32768.0f, 32767.0f);
        while (p.rsPos < 1.0) {
            float t = static_cast<float>(p.rsPos);
            p.resampled.push_back(static_cast<int16_t>(p.prevL + (l - p.prevL) * t));
            p.resampled.push_back(static_cast<int16_t>(p.prevR + (r - p.prevR) * t));
            p.rsPos += p.step;
        }
        p.rsPos -= 1.0;
        p.prevL = l;
        p.prevR = r;
    }
    p.ring.write(p.resampled.data(), p.resampled.size() / 2);
}

size_t audioBatch(const int16_t *data, size_t frames) {
    pushAudio(data, frames);
    return frames;
}

void audioSample(int16_t left, int16_t right) {
    int16_t frame[2] = {left, right};
    pushAudio(frame, 1);
}

// ---------- input ----------

constexpr auto kMinKeyHold = std::chrono::milliseconds(80);

void inputPoll() {}

int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4) return 0;
    switch (device & RETRO_DEVICE_MASK) {
        case RETRO_DEVICE_JOYPAD: {
            if (P->netInput && port >= 2) return 0;
            uint32_t mask = P->netInput ? P->netFrame.buttons[port] : P->frameButtons[port];
            if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return static_cast<int16_t>(mask);
            return id < 16 ? static_cast<int16_t>((mask >> id) & 1u) : 0;
        }
        case RETRO_DEVICE_ANALOG:
            if (index >= 2 || id >= 2) return 0;
            if (P->netInput) return port < 2 ? P->netFrame.analog[port][index * 2 + id] : 0;
            return P->analog[port][index][id];
        case RETRO_DEVICE_KEYBOARD:
            return id < RETROK_LAST && P->keys[id].load() ? 1 : 0;
        case RETRO_DEVICE_MOUSE:
            if (port != 0) return 0;
            switch (id) {
                case RETRO_DEVICE_ID_MOUSE_X: return static_cast<int16_t>(P->frameMouseDx);
                case RETRO_DEVICE_ID_MOUSE_Y: return static_cast<int16_t>(P->frameMouseDy);
                case RETRO_DEVICE_ID_MOUSE_LEFT: return P->mouseButtons & 1 ? 1 : 0;
                case RETRO_DEVICE_ID_MOUSE_RIGHT: return P->mouseButtons & 2 ? 1 : 0;
                case RETRO_DEVICE_ID_MOUSE_MIDDLE: return P->mouseButtons & 4 ? 1 : 0;
                default: return 0;
            }
        case RETRO_DEVICE_POINTER:
            if (port != 0 || index != 0) return 0;
            if (id == RETRO_DEVICE_ID_POINTER_X) return P->pointerX;
            if (id == RETRO_DEVICE_ID_POINTER_Y) return P->pointerY;
            if (id == RETRO_DEVICE_ID_POINTER_PRESSED) return P->pointerDown ? 1 : 0;
            if (id == RETRO_DEVICE_ID_POINTER_COUNT) return P->pointerDown ? 1 : 0;
            return 0;
        default:
            return 0;
    }
}

// ---------- main loop ----------

void runLoop() {
    Player &p = *P;
    using clock = std::chrono::steady_clock;
    auto next = clock::now();
    while (p.running) {
        applyWindowChange();
        if (p.audioLost) audioOpen();
        if (p.paused || p.surface == EGL_NO_SURFACE || (p.hwRequested && !p.hwReady)) {
            std::this_thread::sleep_for(std::chrono::milliseconds(16));
            next = clock::now();
            continue;
        }
        if (p.hwRequested && !p.vulkan) {
            hwResize(); // a no-op unless the core's picture grew
            glBindFramebuffer(GL_FRAMEBUFFER, p.hwFbo);
            glViewport(0, 0, p.hwW, p.hwH);
        }
        {
            std::vector<Player::KeyEvent> events;
            {
                std::lock_guard<std::mutex> keyLock(p.keyMutex);
                // Releases held back for kMinKeyHold whose time has come.
                auto now = clock::now();
                for (auto it = p.pendingUps.begin(); it != p.pendingUps.end();) {
                    if (it->at > now) { ++it; continue; }
                    p.keys[it->keycode] = false;
                    p.keyEvents.push_back({false, it->keycode, 0, it->modifiers});
                    it = p.pendingUps.erase(it);
                }
                events.swap(p.keyEvents);
            }
            // This frame's mouse movement: everything since the last frame.
            p.frameMouseDx = p.mouseDx.exchange(0);
            p.frameMouseDy = p.mouseDy.exchange(0);
            std::lock_guard<std::mutex> lock(p.coreMutex);
            if (p.keyboardCallback) {
                for (auto &e : events) p.keyboardCallback(e.down, e.keycode, e.character, e.modifiers);
            }
            // Netplay: both players' buttons for this frame, or wait for the other player's (no frame this time).
            netplay::Hooks hooks{
                [&p] {
                    std::vector<uint8_t> state(p.core.serialize_size());
                    if (state.empty() || !p.core.serialize(state.data(), state.size())) state.clear();
                    return state;
                },
                [&p](const std::vector<uint8_t> &state) { return p.core.unserialize(state.data(), state.size()); },
            };
            for (int i = 0; i < 4; i++) {
                p.carried[i] |= p.latched[i].exchange(0);
                p.frameButtons[i] = p.buttons[i] | p.carried[i];
            }
            p.netInput = netplay::active();
            if (p.netInput) {
                int16_t sticks[4] = {p.analog[0][0][0], p.analog[0][0][1], p.analog[0][1][0], p.analog[0][1][1]};
                bool sent = false;
                bool ready = netplay::beginFrame(p.frameButtons[0], sticks, p.netFrame, hooks, &sent);
                if (sent) p.carried[0] = 0;  // the other player has them now
                if (!ready) continue;
            } else {
                for (uint32_t &c : p.carried) c = 0;  // this frame uses them
            }
            p.core.run();
            if (p.netInput) netplay::endFrame(hooks);
        }
        present();

        double fps = p.av.timing.fps > 0 ? p.av.timing.fps : 60.0;
        auto frame = std::chrono::duration_cast<clock::duration>(std::chrono::duration<double>(1.0 / fps));
        if ((p.fastForward || p.coreFastForward) && !p.netInput) {
            next = clock::now(); // as fast as the device goes (vsync still limits drawing)
        } else if (p.stream) {
            // Audio is the clock: wait while more than ~64 ms of sound is queued. Keeps sound and speed exact.
            size_t target = static_cast<size_t>(p.deviceRate * 0.064);
            auto limit = clock::now() + std::chrono::milliseconds(100);
            while (p.running && p.ring.fill() > target && clock::now() < limit) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
            }
        } else {
            next += frame;
            auto now = clock::now();
            if (next < now - std::chrono::milliseconds(100)) next = now;
            std::this_thread::sleep_until(next);
        }
    }
    if (p.hwReady && (p.vulkan || p.surface != EGL_NO_SURFACE) && p.hw.context_destroy) {
        std::lock_guard<std::mutex> lock(p.coreMutex);
        p.hw.context_destroy();
    }
    if (p.surface2 != EGL_NO_SURFACE) {
        eglDestroySurface(p.display, p.surface2);
        p.surface2 = EGL_NO_SURFACE;
    }
    if (p.window2) {
        ANativeWindow_release(p.window2);
        p.window2 = nullptr;
    }
    releaseSurface();
    if (P->context != EGL_NO_CONTEXT) {
        eglDestroyContext(P->display, P->context);
        P->context = EGL_NO_CONTEXT;
        P->program = 0;
    }
    if (P->display != EGL_NO_DISPLAY) {
        eglTerminate(P->display);
        P->display = EGL_NO_DISPLAY;
    }
    // Anyone still waiting to hand over a window is released.
    std::lock_guard<std::mutex> lock(p.windowMutex);
    p.windowPending = false;
    p.windowCv.notify_all();
}

template <typename T>
bool bind(void *handle, T &fn, const char *name) {
    fn = reinterpret_cast<T>(dlsym(handle, name));
    if (!fn) LOGE("core is missing %s", name);
    return fn != nullptr;
}

std::string str(JNIEnv *env, jstring s) {
    if (!s) return "";
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

jbyteArray bytes(JNIEnv *env, const void *data, size_t size) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(size));
    if (array) env->SetByteArrayRegion(array, 0, static_cast<jsize>(size), static_cast<const jbyte *>(data));
    return array;
}

bool readAll(int fd, std::vector<uint8_t> &out) {
    struct stat st{};
    if (fstat(fd, &st) != 0) return false;
    out.resize(static_cast<size_t>(st.st_size));
    size_t done = 0;
    while (done < out.size()) {
        ssize_t n = read(fd, out.data() + done, out.size() - done);
        if (n <= 0) return false;
        done += static_cast<size_t>(n);
    }
    return true;
}

} // namespace

// ---------- JNI ----------

extern "C" {

#define JNI(name) JNICALL Java_com_retroforge_player_NativePlayer_##name

// Loads the core. Returns null on success or what went wrong. Afterwards [systemInfo] describes it.
JNIEXPORT jstring JNI(openCore)(JNIEnv *env, jobject, jstring corePath, jstring systemDir, jstring saveDir,
                                jobjectArray optionKeys, jobjectArray optionValues) {
    if (P) return env->NewStringUTF("A game is already open");
    vfs::resetRequested();
    P = new Player();
    Player &p = *P;
    p.corePath = str(env, corePath);
    p.systemDir = str(env, systemDir);
    p.saveDir = str(env, saveDir);
    for (jsize i = 0; optionKeys && i < env->GetArrayLength(optionKeys); i++) {
        p.overrides[str(env, static_cast<jstring>(env->GetObjectArrayElement(optionKeys, i)))] =
            str(env, static_cast<jstring>(env->GetObjectArrayElement(optionValues, i)));
    }
    // RetroForge's own setting, not a core option: Vulkan or OpenGL for cores that let the frontend choose.
    if (auto it = p.overrides.find("retroforge_vfs"); it != p.overrides.end()) {
        p.offerVfs = it->second == "on";
        p.overrides.erase(it);
    }
    if (auto it = p.overrides.find("retroforge_graphics"); it != p.overrides.end()) {
        p.preferVulkan = it->second == "vulkan";
        p.overrides.erase(it);
    }
    void *h = dlopen(p.corePath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!h) {
        std::string error = std::string("Couldn't load the emulator core: ") + dlerror();
        delete P;
        P = nullptr;
        return env->NewStringUTF(error.c_str());
    }
    CoreApi &c = p.core;
    c.handle = h;
    bool ok = bind(h, c.init, "retro_init") && bind(h, c.deinit, "retro_deinit") &&
              bind(h, c.get_system_info, "retro_get_system_info") && bind(h, c.get_system_av_info, "retro_get_system_av_info") &&
              bind(h, c.set_environment, "retro_set_environment") && bind(h, c.set_video_refresh, "retro_set_video_refresh") &&
              bind(h, c.set_audio_sample, "retro_set_audio_sample") && bind(h, c.set_audio_sample_batch, "retro_set_audio_sample_batch") &&
              bind(h, c.set_input_poll, "retro_set_input_poll") && bind(h, c.set_input_state, "retro_set_input_state") &&
              bind(h, c.set_controller_port_device, "retro_set_controller_port_device") && bind(h, c.reset, "retro_reset") &&
              bind(h, c.run, "retro_run") && bind(h, c.serialize_size, "retro_serialize_size") &&
              bind(h, c.serialize, "retro_serialize") && bind(h, c.unserialize, "retro_unserialize") &&
              bind(h, c.load_game, "retro_load_game") && bind(h, c.unload_game, "retro_unload_game") &&
              bind(h, c.get_memory_data, "retro_get_memory_data") && bind(h, c.get_memory_size, "retro_get_memory_size");
    if (!ok) {
        delete P;
        P = nullptr;
        return env->NewStringUTF("The emulator core is damaged or not a libretro core");
    }
    // Cheats are optional: a core without them simply has no Cheats page.
    c.cheat_reset = reinterpret_cast<void (*)()>(dlsym(h, "retro_cheat_reset"));
    c.cheat_set = reinterpret_cast<void (*)(unsigned, bool, const char *)>(dlsym(h, "retro_cheat_set"));
    c.set_environment(environment);
    c.init();
    c.set_video_refresh(videoRefresh);
    c.set_audio_sample(audioSample);
    c.set_audio_sample_batch(audioBatch);
    c.set_input_poll(inputPoll);
    c.set_input_state(inputState);
    c.get_system_info(&p.info);
    return nullptr;
}

// [needFullpath, blockExtract] then library name, version and valid extensions.
JNIEXPORT jobjectArray JNI(systemInfo)(JNIEnv *env, jobject) {
    jobjectArray out = env->NewObjectArray(5, env->FindClass("java/lang/String"), nullptr);
    if (!P) return out;
    const retro_system_info &i = P->info;
    env->SetObjectArrayElement(out, 0, env->NewStringUTF(i.need_fullpath ? "1" : "0"));
    env->SetObjectArrayElement(out, 1, env->NewStringUTF(i.block_extract ? "1" : "0"));
    env->SetObjectArrayElement(out, 2, env->NewStringUTF(i.library_name ? i.library_name : ""));
    env->SetObjectArrayElement(out, 3, env->NewStringUTF(i.library_version ? i.library_version : ""));
    env->SetObjectArrayElement(out, 4, env->NewStringUTF(i.valid_extensions ? i.valid_extensions : ""));
    return out;
}

// Loads the game from [path] (a real file) or, when [fd] >= 0, from that open file read into memory.
// [path] is still passed to the core so it can tell the type from the extension.
JNIEXPORT jboolean JNI(loadGame)(JNIEnv *env, jobject, jstring path, jint fd, jboolean fromFileIntoMemory) {
    if (!P) return JNI_FALSE;
    Player &p = *P;
    p.contentPath = str(env, path);
    retro_game_info game{};
    game.path = p.contentPath.c_str();
    if (fd >= 0) {
        if (!readAll(fd, p.content)) return JNI_FALSE;
    } else if (fromFileIntoMemory) {
        int file = open(p.contentPath.c_str(), O_RDONLY | O_CLOEXEC);
        if (file < 0) return JNI_FALSE;
        bool ok = readAll(file, p.content);
        close(file);
        if (!ok) return JNI_FALSE;
    }
    if (!p.content.empty()) {
        game.data = p.content.data();
        game.size = p.content.size();
    }
    std::lock_guard<std::mutex> lock(p.coreMutex);
    if (!p.core.load_game(&game)) return JNI_FALSE;
    p.gameLoaded = true;
    p.core.get_system_av_info(&p.av);
    // Every port, as RetroArch does: one controller, the rest empty. Flycast attaches memory cards here.
    p.core.set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
    for (unsigned port = 1; port < 4; port++) p.core.set_controller_port_device(port, RETRO_DEVICE_NONE);
    LOGI("loaded %s: %ux%u %.2f fps %.0f Hz", p.contentPath.c_str(), p.av.geometry.base_width, p.av.geometry.base_height,
         p.av.timing.fps, p.av.timing.sample_rate);
    return JNI_TRUE;
}

// fps, sample rate, base width, base height, aspect ratio.
JNIEXPORT jfloatArray JNI(avInfo)(JNIEnv *env, jobject) {
    jfloatArray out = env->NewFloatArray(5);
    if (!P) return out;
    const retro_system_av_info &a = P->av;
    float aspect = a.geometry.aspect_ratio > 0 ? a.geometry.aspect_ratio
                   : a.geometry.base_height > 0 ? float(a.geometry.base_width) / float(a.geometry.base_height) : 4.0f / 3.0f;
    // A quarter-turned picture swaps its width and height, so the app fits it the right way round. Some cores (Beetle
    // Cygne) already give the turned shape as their aspect ratio (upright, while the image itself is wide): that one
    // is kept as it is.
    bool turned = P->rotation & 1;
    bool wideImage = a.geometry.base_width >= a.geometry.base_height;
    bool alreadyTurned = turned && wideImage && aspect < 1.0f;
    float shown = turned && !alreadyTurned ? 1.0f / aspect : aspect;
    float values[5] = {float(a.timing.fps), float(a.timing.sample_rate), float(turned ? a.geometry.base_height : a.geometry.base_width),
                       float(turned ? a.geometry.base_width : a.geometry.base_height), shown};
    LOGI("picture %ux%u, aspect %.3f, rotation %u -> shown aspect %.3f", a.geometry.base_width, a.geometry.base_height, aspect, P->rotation.load(), shown);
    env->SetFloatArrayRegion(out, 0, 5, values);
    return out;
}

JNIEXPORT void JNI(start)(JNIEnv *, jobject) {
    if (!P || !P->gameLoaded || P->running) return;
    audioOpen();
    P->running = true;
    P->loop = std::thread(runLoop);
}

JNIEXPORT void JNI(setPaused)(JNIEnv *, jobject, jboolean paused) {
    if (!P) return;
    P->paused = paused;
    if (P->stream) {
        if (paused) AAudioStream_requestPause(P->stream);
        else AAudioStream_requestStart(P->stream);
    }
}

JNIEXPORT void JNI(setFastForward)(JNIEnv *, jobject, jboolean on) {
    if (P) P->fastForward = on;
}

// Hands the drawing surface to the core thread (null when it goes away) and waits until it's been taken.
JNIEXPORT void JNI(setSurface)(JNIEnv *env, jobject, jobject surface) {
    if (!P) return;
    Player &p = *P;
    std::unique_lock<std::mutex> lock(p.windowMutex);
    if (p.pendingWindow) ANativeWindow_release(p.pendingWindow);
    p.pendingWindow = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    p.windowPending = true;
    if (!p.running) return; // picked up when the loop starts
    p.windowCv.wait_for(lock, std::chrono::seconds(2), [&] { return !p.windowPending; });
}

JNIEXPORT void JNI(setVr)(JNIEnv *, jobject, jboolean on) {
    if (P) P->vr = on;
}

JNIEXPORT void JNI(setSideBySideSource)(JNIEnv *, jobject, jboolean on) {
    if (P) P->sideBySideSource = on;
}

// The second display's surface (null when it goes away). Split mode is on while there is one.
JNIEXPORT void JNI(setSecondSurface)(JNIEnv *env, jobject, jobject surface) {
    if (!P) return;
    Player &p = *P;
    std::unique_lock<std::mutex> lock(p.windowMutex);
    if (p.pendingWindow2) ANativeWindow_release(p.pendingWindow2);
    p.pendingWindow2 = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    p.window2Pending = true;
    p.split = surface != nullptr;
    if (!p.running) return;
    p.windowCv.wait_for(lock, std::chrono::seconds(2), [&] { return !p.window2Pending; });
}

JNIEXPORT void JNI(setButtons)(JNIEnv *, jobject, jint port, jint mask) {
    if (P && port >= 0 && port < 4) {
        P->buttons[port] = static_cast<uint32_t>(mask);
        P->latched[port] |= static_cast<uint32_t>(mask);
    }
}

JNIEXPORT void JNI(setAnalog)(JNIEnv *, jobject, jint port, jint stick, jint x, jint y) {
    if (!P || port < 0 || port >= 4 || stick < 0 || stick > 1) return;
    P->analog[port][stick][0] = static_cast<int16_t>(x);
    P->analog[port][stick][1] = static_cast<int16_t>(y);
}

// The core's options for the menu: key, description, choices ("a|b|c") and current value, four per option.
JNIEXPORT jobjectArray JNI(coreOptions)(JNIEnv *env, jobject) {
    std::vector<std::string> out;
    if (P) {
        std::lock_guard<std::mutex> lock(P->coreMutex);
        for (auto &[key, raw] : P->definitions) {
            size_t semi = raw.find("; ");
            out.push_back(key);
            out.push_back(semi == std::string::npos ? key : raw.substr(0, semi));
            out.push_back(semi == std::string::npos ? raw : raw.substr(semi + 2));
            auto current = P->variables.find(key);
            out.push_back(current != P->variables.end() ? current->second : "");
        }
    }
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(out.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < out.size(); i++) env->SetObjectArrayElement(array, static_cast<jsize>(i), env->NewStringUTF(out[i].c_str()));
    return array;
}

// What the core calls each of player 1's buttons: id and name, two per button (empty when the core doesn't say).
JNIEXPORT jobjectArray JNI(buttonNames)(JNIEnv *env, jobject) {
    std::vector<std::string> out;
    if (P) {
        std::lock_guard<std::mutex> lock(P->coreMutex);
        for (auto &[id, name] : P->buttonNames) { out.push_back(std::to_string(id)); out.push_back(name); }
    }
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(out.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < out.size(); i++) env->SetObjectArrayElement(array, static_cast<jsize>(i), env->NewStringUTF(out[i].c_str()));
    return array;
}

// Tells the core what's plugged into a port (e.g. a DualShock for PCSX ReARMed, so it reads the analog sticks).
JNIEXPORT void JNI(setPortDevice)(JNIEnv *, jobject, jint port, jint device) {
    if (!P || !P->gameLoaded) return;
    std::lock_guard<std::mutex> lock(P->coreMutex);
    P->core.set_controller_port_device(static_cast<unsigned>(port), static_cast<unsigned>(device));
}

// Changes a core option while the game runs (e.g. the DS screen layout on rotation); the core picks it up
// through GET_VARIABLE_UPDATE on its next frame.
JNIEXPORT void JNI(setOption)(JNIEnv *env, jobject, jstring key, jstring value) {
    if (!P) return;
    std::string k = str(env, key), v = str(env, value);
    std::lock_guard<std::mutex> lock(P->coreMutex);
    P->overrides[k] = v;
    if (P->variables.count(k)) P->variables[k] = v;
    P->variablesChanged = true;
}

JNIEXPORT jlong JNI(framesShown)(JNIEnv *, jobject) { return P ? jlong(P->framesShown.load()) : 0; }

JNIEXPORT void JNI(addStream)(JNIEnv *env, jobject, jstring path, jint fd) { vfs::addStream(str(env, path), fd); }

JNIEXPORT jboolean JNI(vfsRequested)(JNIEnv *, jobject) { return vfs::wasRequested() ? JNI_TRUE : JNI_FALSE; }

JNIEXPORT void JNI(setMouse)(JNIEnv *, jobject, jint dx, jint dy, jint buttons) {
    if (!P) return;
    P->mouseDx += dx;
    P->mouseDy += dy;
    P->mouseButtons = buttons;
}

JNIEXPORT void JNI(setKey)(JNIEnv *, jobject, jint keycode, jboolean down, jint character, jint modifiers) {
    if (!P || keycode <= 0 || keycode >= RETROK_LAST) return;
    auto now = std::chrono::steady_clock::now();
    std::lock_guard<std::mutex> lock(P->keyMutex);
    if (down) {
        P->keyDownAt[keycode] = now;
        P->keys[keycode] = true;
        P->keyEvents.push_back({true, static_cast<unsigned>(keycode), static_cast<uint32_t>(character), static_cast<uint16_t>(modifiers)});
        return;
    }
    auto releaseAt = P->keyDownAt[keycode] + kMinKeyHold;
    if (now < releaseAt) {
        P->pendingUps.push_back({static_cast<unsigned>(keycode), static_cast<uint16_t>(modifiers), releaseAt});
        return;
    }
    P->keys[keycode] = false;
    P->keyEvents.push_back({false, static_cast<unsigned>(keycode), 0, static_cast<uint16_t>(modifiers)});
}

JNIEXPORT void JNI(setFilter)(JNIEnv *, jobject, jint filter) {
    if (P) P->filter = filter;
}

JNIEXPORT void JNI(setViewport)(JNIEnv *, jobject, jint x, jint y, jint w, jint h) {
    if (!P) return;
    P->viewX = x;
    P->viewY = y;
    P->viewW = w;
    P->viewH = h;
}

// Replaces the active cheats with [codes] (each a core-format code, parts joined with '+'). False when the core has none.
JNIEXPORT jboolean JNI(applyCheats)(JNIEnv *env, jobject, jobjectArray codes) {
    if (!P || !P->gameLoaded || !P->core.cheat_reset || !P->core.cheat_set) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(P->coreMutex);
    P->core.cheat_reset();
    jsize n = codes ? env->GetArrayLength(codes) : 0;
    for (jsize i = 0; i < n; i++) {
        std::string code = str(env, static_cast<jstring>(env->GetObjectArrayElement(codes, i)));
        P->core.cheat_set(static_cast<unsigned>(i), true, code.c_str());
    }
    return JNI_TRUE;
}

// Multi-disc games: [number of discs, current disc (0-based)], or [0, 0] when the core has no disc control.
JNIEXPORT jintArray JNI(discs)(JNIEnv *env, jobject) {
    jintArray out = env->NewIntArray(2);
    if (!P || !P->gameLoaded || !P->disks.get_num_images || !P->disks.get_image_index) return out;
    std::lock_guard<std::mutex> lock(P->coreMutex);
    jint values[2] = {static_cast<jint>(P->disks.get_num_images()), static_cast<jint>(P->disks.get_image_index())};
    env->SetIntArrayRegion(out, 0, 2, values);
    return out;
}

// Changes disc the way a console would: open the tray, swap, close it.
JNIEXPORT jboolean JNI(insertDisc)(JNIEnv *, jobject, jint index) {
    if (!P || !P->gameLoaded || !P->disks.set_eject_state || !P->disks.set_image_index) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(P->coreMutex);
    if (!P->disks.set_eject_state(true)) return JNI_FALSE;
    bool ok = P->disks.set_image_index(static_cast<unsigned>(index));
    P->disks.set_eject_state(false);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// Game volume: 1.0 as the core made it, up to 2.0 for quiet systems.
JNIEXPORT void JNI(setVolume)(JNIEnv *, jobject, jfloat volume) {
    if (P) P->volume = std::clamp(volume, 0.0f, 3.0f);
}

// [x], [y]: 0..1 across the drawn game picture.
JNIEXPORT void JNI(setPointer)(JNIEnv *, jobject, jfloat x, jfloat y, jboolean down) {
    if (!P) return;
    auto scale = [](float v) { return static_cast<int16_t>(std::clamp(v * 2.0f - 1.0f, -1.0f, 1.0f) * 0x7fff); };
    P->pointerX = scale(x);
    P->pointerY = scale(y);
    P->pointerDown = down;
}

JNIEXPORT void JNI(reset)(JNIEnv *, jobject) {
    if (!P || !P->gameLoaded) return;
    std::lock_guard<std::mutex> lock(P->coreMutex);
    P->core.reset();
}

JNIEXPORT jbyteArray JNI(saveRam)(JNIEnv *env, jobject) {
    if (!P || !P->gameLoaded) return nullptr;
    std::lock_guard<std::mutex> lock(P->coreMutex);
    size_t size = P->core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = P->core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    return size && data ? bytes(env, data, size) : nullptr;
}

JNIEXPORT void JNI(restoreSaveRam)(JNIEnv *env, jobject, jbyteArray saved) {
    if (!P || !P->gameLoaded || !saved) return;
    std::lock_guard<std::mutex> lock(P->coreMutex);
    size_t size = P->core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    void *data = P->core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    jsize length = env->GetArrayLength(saved);
    if (!size || !data) return;
    env->GetByteArrayRegion(saved, 0, static_cast<jsize>(std::min<size_t>(size, static_cast<size_t>(length))), static_cast<jbyte *>(data));
}

JNIEXPORT jbyteArray JNI(saveState)(JNIEnv *env, jobject) {
    if (!P || !P->gameLoaded) return nullptr;
    std::lock_guard<std::mutex> lock(P->coreMutex);
    size_t size = P->core.serialize_size();
    if (!size) return nullptr;
    std::vector<uint8_t> state(size);
    if (!P->core.serialize(state.data(), size)) return nullptr;
    return bytes(env, state.data(), size);
}

JNIEXPORT jboolean JNI(loadState)(JNIEnv *env, jobject, jbyteArray state) {
    if (!P || !P->gameLoaded || !state) return JNI_FALSE;
    std::vector<uint8_t> data(static_cast<size_t>(env->GetArrayLength(state)));
    env->GetByteArrayRegion(state, 0, static_cast<jsize>(data.size()), reinterpret_cast<jbyte *>(data.data()));
    std::lock_guard<std::mutex> lock(P->coreMutex);
    bool loaded = P->core.unserialize(data.data(), data.size());
    if (loaded) netplay::stateChanged();  // the host loaded a state: player 2 gets it too
    return loaded ? JNI_TRUE : JNI_FALSE;
}

// ---------- netplay ----------

JNIEXPORT jstring JNI(netplayHost)(JNIEnv *env, jobject, jint port, jstring gameId, jint delay) {
    std::string error;
    return netplay::host(port, str(env, gameId), delay, error) ? nullptr : env->NewStringUTF(error.c_str());
}

JNIEXPORT jstring JNI(netplayJoin)(JNIEnv *env, jobject, jstring address, jint port, jstring gameId, jint delay) {
    std::string error;
    return netplay::join(str(env, address), port, str(env, gameId), delay, error) ? nullptr : env->NewStringUTF(error.c_str());
}

JNIEXPORT void JNI(netplayLeave)(JNIEnv *, jobject) { netplay::leave(); }

/** 0 off, 1 hosting (waiting), 2 joining, 3 starting, 4 playing, 5 failed. */
JNIEXPORT jint JNI(netplayState)(JNIEnv *, jobject) { return static_cast<jint>(netplay::state()); }

JNIEXPORT jstring JNI(netplayStatus)(JNIEnv *env, jobject) { return env->NewStringUTF(netplay::status().c_str()); }

JNIEXPORT jint JNI(netplayPlayer)(JNIEnv *, jobject) { return netplay::localPlayer(); }

// Stops the loop and sound. The game stays loaded so saves can still be read.
JNIEXPORT void JNI(stop)(JNIEnv *, jobject) {
    if (!P) return;
    P->running = false;
    if (P->loop.joinable()) P->loop.join();
    audioClose();
}

// Unloads the game and the core. The library itself stays mapped: the player process ends with the game.
JNIEXPORT void JNI(close)(JNIEnv *, jobject) {
    if (!P) return;
    netplay::leave();
    P->running = false;
    if (P->loop.joinable()) P->loop.join();
    audioClose();
    {
        std::lock_guard<std::mutex> lock(P->coreMutex);
        if (P->gameLoaded) P->core.unload_game();
        P->core.deinit();
    }
    if (P->vulkan) vkr::destroy();
    delete P;
    P = nullptr;
    vfs::clearStreams();
}

} // extern "C"
