// Netplay: two players, one on each device, play the same game together over the network. The host is player 1, the
// guest player 2. Both run the same emulator on the same game: the host sends its save state when the guest joins,
// then only each frame's buttons travel, applied a few frames later on both sides (input delay) so they arrive in
// time. Both devices then run identical frames. Every few seconds the two compare a checksum of the game; if they've
// drifted apart, the host sends its state again.
#pragma once

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace netplay {

enum class State { Off, Hosting, Joining, Syncing, Playing, Failed };

struct FrameInput {
    uint32_t buttons[2]{};   // libretro joypad masks: [0] the host (port 1), [1] the guest (port 2)
    int16_t analog[2][4]{};  // left x, left y, right x, right y per player
};

/** The core's save and load, called on the core thread from beginFrame/endFrame. */
struct Hooks {
    std::function<std::vector<uint8_t>()> save;
    std::function<bool(const std::vector<uint8_t> &)> load;
};

/** Listens on [port] for one guest playing [gameId]. False when the port can't be opened. */
bool host(int port, const std::string &gameId, int delayFrames, std::string &error);
/** Connects to a host at [address]:[port] in the background; [state] then says how it went. */
bool join(const std::string &address, int port, const std::string &gameId, int delayFrames, std::string &error);
/** Ends the session (the other side is told). */
void leave();

State state();
/** One line for the player: who it's connected to, the delay, or what went wrong. */
std::string status();
bool active();
/** Which player this device is: 0 host, 1 guest. */
int localPlayer();

/**
 * Before each frame, on the core thread. [sentLocal] is set when this call sent [localButtons] to the other player. Sends this device's buttons (for a frame [delay] ahead), loads a state the
 * host sent, and fills [out] with both players' buttons for this frame. False when the frame can't run yet (the other
 * player's buttons haven't come): the caller tries again shortly without running the core.
 */
bool beginFrame(uint32_t localButtons, const int16_t localAnalog[4], FrameInput &out, const Hooks &hooks, bool *sentLocal = nullptr);
/** After the frame ran: moves on, and now and then compares checksums. */
void endFrame(const Hooks &hooks);
/** The host loaded a save state itself: the guest is sent the new state. */
void stateChanged();

}  // namespace netplay
