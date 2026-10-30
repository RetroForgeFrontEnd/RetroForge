// RetroForge's virtual gamepad for Windows games.
//
// Preloaded into Wine's processes. In the one that runs Wine's controller driver (winedevice.exe, whose winebus
// reads controllers through SDL) it adds an SDL virtual "Xbox 360 Controller", which Windows games see as an XInput
// pad. The app writes the pad's state into a small shared file (PCPAD_FILE); SDL asks for fresh state each time it
// updates its joysticks, and this reads the file then.
//
// File layout (little-endian), written by com.retroforge.pcplayer.VirtualPad:
//   0  int32    connected (0 or 1)
//   4  int16[6] left x, left y, right x, right y, left trigger, right trigger (-32768..32767)
//  16  uint8[15] buttons in SDL game controller order:
//               A B X Y Back Guide Start LeftStick RightStick LB RB Up Down Left Right
#include <dlfcn.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "pcpad"
#define PAD_FILE_SIZE 64
#define BUTTONS 15
#define AXES 6

// The parts of SDL2's API used here (SDL 2.24+), declared locally so no SDL headers are needed.
typedef struct {
    uint16_t version, type, naxes, nbuttons, nhats, vendor_id, product_id, padding;
    uint32_t button_mask, axis_mask;
    const char *name;
    void *userdata;
    void (*Update)(void *userdata);
    void (*SetPlayerIndex)(void *userdata, int player_index);
    int (*Rumble)(void *userdata, uint16_t low, uint16_t high);
    int (*RumbleTriggers)(void *userdata, uint16_t left, uint16_t right);
    int (*SetLED)(void *userdata, uint8_t r, uint8_t g, uint8_t b);
    int (*SendEffect)(void *userdata, const void *data, int size);
} VirtualJoystickDesc;

#define SDL_INIT_JOYSTICK 0x200u
#define SDL_JOYSTICK_TYPE_GAMECONTROLLER 1
#define SDL_VIRTUAL_JOYSTICK_DESC_VERSION 1

static int (*p_SDL_Init)(uint32_t);
static int (*p_SDL_JoystickAttachVirtualEx)(const VirtualJoystickDesc *);
static void *(*p_SDL_JoystickOpen)(int);
static int (*p_SDL_JoystickSetVirtualAxis)(void *, int, int16_t);
static int (*p_SDL_JoystickSetVirtualButton)(void *, int, uint8_t);
static const char *(*p_SDL_GetError)(void);

static volatile uint8_t *pad;
static void *joystick;
static int debug;  // PCPAD_DEBUG=1: log button changes as SDL reads them

static void update(void *userdata) {
    (void)userdata;
    if (!pad || !joystick) return;
    int connected = *(volatile int32_t *)pad != 0;
    for (int i = 0; i < AXES; i++) {
        int16_t value = connected ? *(volatile int16_t *)(pad + 4 + i * 2) : 0;
        // Triggers rest at -32768 (released) in SDL's virtual joystick.
        if (!connected && i >= 4) value = -32768;
        p_SDL_JoystickSetVirtualAxis(joystick, i, value);
    }
    static uint8_t last[BUTTONS];
    for (int i = 0; i < BUTTONS; i++) {
        uint8_t value = connected ? pad[16 + i] : 0;
        p_SDL_JoystickSetVirtualButton(joystick, i, value);
        if (debug && value != last[i]) __android_log_print(ANDROID_LOG_DEBUG, TAG, "button %d %s", i, value ? "down" : "up");
        last[i] = value;
    }
}

static int is_controller_process(void) {
    char cmdline[512];
    int fd = open("/proc/self/cmdline", O_RDONLY);
    if (fd < 0) return 0;
    ssize_t n = read(fd, cmdline, sizeof(cmdline) - 1);
    close(fd);
    if (n <= 0) return 0;
    for (ssize_t i = 0; i < n; i++) if (cmdline[i] == 0) cmdline[i] = ' ';
    cmdline[n] = 0;
    return strstr(cmdline, "winedevice.exe") != NULL;
}

#define BIND(name) p_##name = dlsym(sdl, #name); if (!p_##name) goto missing;

__attribute__((constructor))
static void pcpad_init(void) {
    const char *path = getenv("PCPAD_FILE");
    if (!path || !*path || !is_controller_process()) return;
    debug = getenv("PCPAD_DEBUG") != NULL;

    int fd = open(path, O_RDONLY);
    if (fd < 0) return;
    void *map = mmap(NULL, PAD_FILE_SIZE, PROT_READ, MAP_SHARED, fd, 0);
    close(fd);
    if (map == MAP_FAILED) return;
    pad = map;

    // The same SDL winebus loads, so the virtual pad is in its list of joysticks.
    void *sdl = dlopen("libSDL2-2.0.so.0", RTLD_NOW | RTLD_GLOBAL);
    if (!sdl) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "SDL2 not found: %s", dlerror());
        return;
    }
    BIND(SDL_Init) BIND(SDL_JoystickAttachVirtualEx) BIND(SDL_JoystickOpen)
    BIND(SDL_JoystickSetVirtualAxis) BIND(SDL_JoystickSetVirtualButton) BIND(SDL_GetError)

    p_SDL_Init(SDL_INIT_JOYSTICK);
    VirtualJoystickDesc desc;
    memset(&desc, 0, sizeof(desc));
    desc.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
    desc.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
    desc.naxes = AXES;
    desc.nbuttons = BUTTONS;
    desc.vendor_id = 0x045E;   // Microsoft
    desc.product_id = 0x028E;  // Xbox 360 Controller
    desc.button_mask = (1u << BUTTONS) - 1;
    desc.axis_mask = (1u << AXES) - 1;
    desc.name = "Xbox 360 Controller";
    desc.Update = update;
    int index = p_SDL_JoystickAttachVirtualEx(&desc);
    if (index < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "virtual pad not added: %s", p_SDL_GetError());
        return;
    }
    joystick = p_SDL_JoystickOpen(index);
    __android_log_print(ANDROID_LOG_INFO, TAG, "virtual Xbox 360 pad added (pid %d)", getpid());
    return;

missing:
    __android_log_print(ANDROID_LOG_ERROR, TAG, "this SDL2 has no virtual joysticks");
}
