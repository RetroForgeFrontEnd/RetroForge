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
#include <GLES2/gl2.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "libretro.h"

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
    std::atomic<bool> variablesChanged{false};

    std::mutex coreMutex; // every call into the core
    std::thread loop;
    std::atomic<bool> running{false}, paused{false}, fastForward{false};
    std::atomic<int> viewX{0}, viewY{0}, viewW{0}, viewH{0}; // picture rectangle in surface pixels; 0 = fit

    // Window hand-over between the UI thread and the core thread.
    std::mutex windowMutex;
    std::condition_variable windowCv;
    ANativeWindow *pendingWindow = nullptr;
    bool windowPending = false;

    // GL state, owned by the core thread.
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLConfig config = nullptr;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    ANativeWindow *window = nullptr;
    GLuint program = 0, texture = 0;
    GLint uTexSize = -1, uOutSize = -1;
    unsigned texW = 0, texH = 0;
    bool hasFrame = false;
    std::vector<uint8_t> staging;

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
    std::atomic<int16_t> analog[4][2][2]{};
    // Touchscreen (DS bottom screen and similar), in libretro's -0x7fff..0x7fff over the drawn game.
    std::atomic<int16_t> pointerX{0}, pointerY{0};
    std::atomic<bool> pointerDown{false};
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

bool environment(unsigned cmd, void *data) {
    switch (cmd) {
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
        case RETRO_ENVIRONMENT_GET_FASTFORWARDING:
            if (data) *static_cast<bool *>(data) = P->fastForward;
            return true;
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            // 0: cores fall back to the plain SET_VARIABLES list, which is all the player needs.
            *static_cast<unsigned *>(data) = 0;
            return true;
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            for (auto *v = static_cast<const retro_variable *>(data); v && v->key; v++) {
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
        case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_MINIMUM_AUDIO_LATENCY:
            return true;
        default:
            // Hardware rendering, disc control, rumble, VFS and the rest aren't offered yet; cores fall back or say so.
            return false;
    }
}

// ---------- video ----------

const char *kVertex = R"(
attribute vec2 aPos;
attribute vec2 aUv;
varying vec2 vUv;
void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); })";

// Sharp bilinear: whole-pixel scaling with a one-pixel blend at the edges, so pixel art stays crisp at any size
// without the uneven pixels of nearest-neighbour.
const char *kFragment = R"(
precision mediump float;
uniform sampler2D uTex;
uniform vec2 uTexSize;
uniform vec2 uOutSize;
varying vec2 vUv;
void main() {
    vec2 texel = vUv * uTexSize;
    vec2 scale = max(floor(uOutSize / uTexSize), vec2(1.0));
    vec2 region = 0.5 - 0.5 / scale;
    vec2 centre = fract(texel) - 0.5;
    vec2 f = (centre - clamp(centre, -region, region)) * scale + 0.5;
    gl_FragColor = vec4(texture2D(uTex, (floor(texel) + f) / uTexSize).rgb, 1.0);
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
    const EGLint attrs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8, EGL_NONE};
    EGLint count = 0;
    if (!eglChooseConfig(p.display, attrs, &p.config, 1, &count) || count == 0) return false;
    const EGLint ctx[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    p.context = eglCreateContext(p.display, p.config, EGL_NO_CONTEXT, ctx);
    return p.context != EGL_NO_CONTEXT;
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
    glGenTextures(1, &p.texture);
    glBindTexture(GL_TEXTURE_2D, p.texture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    p.texW = p.texH = 0;
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
                p.hasFrame = false;
            } else {
                LOGE("EGL surface failed");
            }
        }
    }
    p.windowPending = false;
    p.windowCv.notify_all();
}

void videoRefresh(const void *data, unsigned width, unsigned height, size_t pitch) {
    Player &p = *P;
    if (!data || p.surface == EGL_NO_SURFACE || width == 0 || height == 0) return; // duplicate frame: keep the last
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

void present() {
    Player &p = *P;
    if (p.surface == EGL_NO_SURFACE) return;
    EGLint sw = 0, sh = 0;
    eglQuerySurface(p.display, p.surface, EGL_WIDTH, &sw);
    eglQuerySurface(p.display, p.surface, EGL_HEIGHT, &sh);
    glViewport(0, 0, sw, sh);
    glClearColor(0, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    if (p.hasFrame && sw > 0 && sh > 0) {
        int vx, vy, vw, vh;
        if (p.viewW > 0 && p.viewH > 0) {
            // Chosen by Kotlin (screen shape setting); top-left origin there, bottom-left in GL.
            vx = p.viewX;
            vw = p.viewW;
            vh = p.viewH;
            vy = sh - (p.viewY + vh);
        } else {
            float aspect = p.av.geometry.aspect_ratio > 0 ? p.av.geometry.aspect_ratio : float(p.texW) / float(p.texH);
            vw = sw;
            vh = int(sw / aspect);
            if (vh > sh) {
                vh = sh;
                vw = int(sh * aspect);
            }
            vx = (sw - vw) / 2;
            vy = (sh - vh) / 2;
        }
        glViewport(vx, vy, vw, vh);
        static const GLfloat quad[] = {-1, -1, 0, 1, 1, -1, 1, 1, -1, 1, 0, 0, 1, 1, 1, 0};
        glUseProgram(p.program);
        glUniform2f(p.uTexSize, float(p.texW), float(p.texH));
        glUniform2f(p.uOutSize, float(vw), float(vh));
        glBindTexture(GL_TEXTURE_2D, p.texture);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), quad);
        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), quad + 2);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }
    eglSwapBuffers(p.display, p.surface);
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
    if (!p.stream || p.fastForward) return; // fast-forward plays silently rather than chipmunk-fast
    p.resampled.clear();
    for (size_t i = 0; i < frames; i++) {
        float l = data[i * 2], r = data[i * 2 + 1];
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

void inputPoll() {}

int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4) return 0;
    switch (device & RETRO_DEVICE_MASK) {
        case RETRO_DEVICE_JOYPAD: {
            uint32_t mask = P->buttons[port];
            if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return static_cast<int16_t>(mask);
            return id < 16 ? static_cast<int16_t>((mask >> id) & 1u) : 0;
        }
        case RETRO_DEVICE_ANALOG:
            if (index < 2 && id < 2) return P->analog[port][index][id];
            return 0;
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
        if (p.paused || p.surface == EGL_NO_SURFACE) {
            std::this_thread::sleep_for(std::chrono::milliseconds(16));
            next = clock::now();
            continue;
        }
        {
            std::lock_guard<std::mutex> lock(p.coreMutex);
            p.core.run();
        }
        present();

        double fps = p.av.timing.fps > 0 ? p.av.timing.fps : 60.0;
        auto frame = std::chrono::duration_cast<clock::duration>(std::chrono::duration<double>(1.0 / fps));
        if (p.fastForward) {
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
    P = new Player();
    Player &p = *P;
    p.corePath = str(env, corePath);
    p.systemDir = str(env, systemDir);
    p.saveDir = str(env, saveDir);
    for (jsize i = 0; optionKeys && i < env->GetArrayLength(optionKeys); i++) {
        p.overrides[str(env, static_cast<jstring>(env->GetObjectArrayElement(optionKeys, i)))] =
            str(env, static_cast<jstring>(env->GetObjectArrayElement(optionValues, i)));
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
    p.core.set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
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
    float values[5] = {float(a.timing.fps), float(a.timing.sample_rate), float(a.geometry.base_width),
                       float(a.geometry.base_height), aspect};
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

JNIEXPORT void JNI(setButtons)(JNIEnv *, jobject, jint port, jint mask) {
    if (P && port >= 0 && port < 4) P->buttons[port] = static_cast<uint32_t>(mask);
}

JNIEXPORT void JNI(setAnalog)(JNIEnv *, jobject, jint port, jint stick, jint x, jint y) {
    if (!P || port < 0 || port >= 4 || stick < 0 || stick > 1) return;
    P->analog[port][stick][0] = static_cast<int16_t>(x);
    P->analog[port][stick][1] = static_cast<int16_t>(y);
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

JNIEXPORT void JNI(setViewport)(JNIEnv *, jobject, jint x, jint y, jint w, jint h) {
    if (!P) return;
    P->viewX = x;
    P->viewY = y;
    P->viewW = w;
    P->viewH = h;
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
    return P->core.unserialize(data.data(), data.size()) ? JNI_TRUE : JNI_FALSE;
}

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
    P->running = false;
    if (P->loop.joinable()) P->loop.join();
    audioClose();
    {
        std::lock_guard<std::mutex> lock(P->coreMutex);
        if (P->gameLoaded) P->core.unload_game();
        P->core.deinit();
    }
    delete P;
    P = nullptr;
}

} // extern "C"
