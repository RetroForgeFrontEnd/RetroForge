// Netplay: up to four players, one on each device, play the same game together over the network. The host is player
// 1; guests are players 2 to 4, in the order they join, and can join while the game is running. Everyone runs the same
// emulator on the same game: the host sends a guest its save state when it joins, then only buttons travel. Each guest
// sends the host its buttons for a frame a few frames ahead (input delay); the host gathers everyone's and sends every
// guest all of them for that frame, so every device runs identical frames. Every few seconds the guests compare a
// checksum of the game with the host's; one that has drifted apart is sent the host's state again.
#pragma once

#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace netplay {

constexpr int kMaxPlayers = 4;

enum class State { Off, Hosting, Joining, Syncing, Playing, Failed };

struct FrameInput {
    uint32_t buttons[kMaxPlayers]{};   // libretro joypad masks, by player (port)
    int16_t analog[kMaxPlayers][4]{};  // left x, left y, right x, right y per player
};

/** The core's save and load, called on the core thread from beginFrame/endFrame. */
struct Hooks {
    std::function<std::vector<uint8_t>()> save;
    std::function<bool(const std::vector<uint8_t> &)> load;
    /** A checksum of the game's RAM, compared between devices; false when the core doesn't offer its RAM. */
    std::function<bool(uint32_t &)> checksum;
};

/** Listens on [port] for up to three guests playing [gameId]. False when the port can't be opened. */
bool host(int port, const std::string &gameId, int delayFrames, std::string &error);
/** Connects to a host at [address]:[port] in the background; [state] then says how it went. */
bool join(const std::string &address, int port, const std::string &gameId, int delayFrames, std::string &error);
/** Ends the session (the others are told). */
void leave();

State state();
/** One line for the player: who's playing, the delay, or what went wrong. */
std::string status();
bool active();
/** Which player this device is: 0 the host, 1 to 3 the guests. */
int localPlayer();
/** How many players are in the game, this device included. */
int players();

/**
 * Before each frame, on the core thread. [sentLocal] is set when this call took [localButtons] for a frame ahead
 * (they're on their way to the others). Loads a state the host sent, and fills [out] with every player's buttons for
 * this frame. False when the frame can't run yet (someone's buttons haven't come): the caller tries again shortly
 * without running the core.
 */
bool beginFrame(uint32_t localButtons, const int16_t localAnalog[4], FrameInput &out, const Hooks &hooks, bool *sentLocal = nullptr);
/** After the frame ran: moves on, and now and then compares checksums. */
void endFrame(const Hooks &hooks);
/** The host loaded a save state itself: every guest is sent the new state. */
void stateChanged();

}  // namespace netplay
