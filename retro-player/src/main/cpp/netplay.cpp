// Netplay (see netplay.h): a TCP connection between two players, with a reader thread that files what arrives, and
// lockstep frames on the core thread. Messages are a type byte, a little-endian length and the payload.

#include "netplay.h"

#include <android/log.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>
#include <zlib.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <map>
#include <mutex>
#include <thread>

#define LOG_TAG "RetroForgeNetplay"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace netplay {
namespace {

constexpr uint32_t kMagic = 0x504E4652;  // "RFNP"
constexpr uint16_t kVersion = 1;
enum Msg : uint8_t { HELLO = 1, STATE = 2, INPUT = 3, CRC = 4, RESYNC = 5, BYE = 6 };
// How often the two compare a checksum of the game (frames), and the biggest state worth checksumming that often.
constexpr uint32_t kCrcEvery = 180;
constexpr size_t kMaxCrcState = 16u << 20;
// Frames of buttons kept, for a resync that goes back a little.
constexpr uint32_t kHistory = 900;
// A frame waits this long for the other player's buttons before the loop is told to try again.
constexpr auto kWaitSlice = std::chrono::milliseconds(8);

struct Input {
    uint32_t buttons = 0;
    int16_t analog[4]{};
};

using clock = std::chrono::steady_clock;

std::mutex m;  // everything below that both threads touch
std::condition_variable arrived;
std::atomic<State> current{State::Off};
std::string statusLine;
std::thread network;
std::atomic<bool> stopping{false};
int listenFd = -1, peerFd = -1;
std::mutex sendMutex;

bool isHost = false;
int delay = 3;
std::string game;

// Core thread, except where marked.
uint32_t frame = 0;
int64_t sentUpTo = -1;
std::map<uint32_t, Input> local;
std::map<uint32_t, Input> remote;              // guarded by m
bool sendState = false;                        // guarded by m (host: the guest needs the state)
bool initialSync = true;                       // guarded by m
std::vector<uint8_t> incomingState;            // guarded by m (guest)
uint32_t incomingFrame = 0;                    // guarded by m
bool hasIncomingState = false;                 // guarded by m
std::map<uint32_t, uint32_t> ourCrc, theirCrc; // guarded by m
bool resyncAsked = false;                      // guarded by m
clock::time_point waitingSince{};

void setStatus(State s, const std::string &line) {
    std::lock_guard<std::mutex> lock(m);
    current = s;
    statusLine = line;
    arrived.notify_all();
}

// ---- Sending and receiving ----

void put32(std::vector<uint8_t> &b, uint32_t v) {
    for (int i = 0; i < 4; i++) b.push_back(uint8_t(v >> (8 * i)));
}
void put16(std::vector<uint8_t> &b, uint16_t v) {
    b.push_back(uint8_t(v));
    b.push_back(uint8_t(v >> 8));
}
uint32_t get32(const uint8_t *p) { return uint32_t(p[0]) | uint32_t(p[1]) << 8 | uint32_t(p[2]) << 16 | uint32_t(p[3]) << 24; }
uint16_t get16(const uint8_t *p) { return uint16_t(p[0] | p[1] << 8); }

bool sendMsg(Msg type, const std::vector<uint8_t> &payload) {
    std::lock_guard<std::mutex> lock(sendMutex);
    if (peerFd < 0) return false;
    std::vector<uint8_t> out;
    out.reserve(5 + payload.size());
    out.push_back(type);
    put32(out, uint32_t(payload.size()));
    out.insert(out.end(), payload.begin(), payload.end());
    size_t done = 0;
    while (done < out.size()) {
        ssize_t n = ::send(peerFd, out.data() + done, out.size() - done, MSG_NOSIGNAL);
        if (n <= 0) return false;
        done += size_t(n);
    }
    return true;
}

bool recvAll(int fd, uint8_t *buf, size_t size) {
    size_t done = 0;
    while (done < size && !stopping) {
        ssize_t n = ::recv(fd, buf + done, size - done, 0);
        if (n <= 0) return false;
        done += size_t(n);
    }
    return done == size;
}

bool recvMsg(int fd, Msg &type, std::vector<uint8_t> &payload) {
    uint8_t head[5];
    if (!recvAll(fd, head, 5)) return false;
    type = Msg(head[0]);
    uint32_t size = get32(head + 1);
    if (size > (512u << 20)) return false;
    payload.resize(size);
    return size == 0 || recvAll(fd, payload.data(), size);
}

std::vector<uint8_t> hello() {
    std::vector<uint8_t> b;
    put32(b, kMagic);
    put16(b, kVersion);
    put16(b, uint16_t(delay));
    b.insert(b.end(), game.begin(), game.end());
    return b;
}

std::vector<uint8_t> inputMsg(uint32_t f, const Input &in) {
    std::vector<uint8_t> b;
    put32(b, f);
    put32(b, in.buttons);
    for (int16_t a : in.analog) put16(b, uint16_t(a));
    return b;
}

void tune(int fd) {
    int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof one);
}

// ---- The reader thread ----

void readLoop() {
    Msg type;
    std::vector<uint8_t> p;
    while (!stopping && recvMsg(peerFd, type, p)) {
        std::lock_guard<std::mutex> lock(m);
        switch (type) {
            case STATE:
                if (p.size() >= 4) {
                    incomingFrame = get32(p.data());
                    incomingState.assign(p.begin() + 4, p.end());
                    hasIncomingState = true;
                }
                break;
            case INPUT:
                if (p.size() >= 16) {
                    Input in;
                    in.buttons = get32(p.data() + 4);
                    for (int i = 0; i < 4; i++) in.analog[i] = int16_t(get16(p.data() + 8 + 2 * i));
                    remote[get32(p.data())] = in;
                }
                break;
            case CRC:
                if (p.size() >= 8) theirCrc[get32(p.data())] = get32(p.data() + 4);
                break;
            case RESYNC:
                if (isHost) sendState = true;
                break;
            case BYE:
                current = State::Failed;
                statusLine = "The other player left.";
                arrived.notify_all();
                return;
            default:
                break;
        }
        arrived.notify_all();
    }
    if (!stopping) setStatus(State::Failed, "Lost the connection to the other player.");
}

void hostLoop() {
    while (!stopping) {
        pollfd waiting{listenFd, POLLIN, 0};
        if (poll(&waiting, 1, 200) <= 0) continue;
        int fd = accept(listenFd, nullptr, nullptr);
        if (fd < 0) continue;
        tune(fd);
        Msg type;
        std::vector<uint8_t> p;
        if (!recvMsg(fd, type, p) || type != HELLO || p.size() < 8 || get32(p.data()) != kMagic) {
            ::close(fd);
            continue;
        }
        std::string theirGame(p.begin() + 8, p.end());
        {
            std::lock_guard<std::mutex> lock(sendMutex);
            peerFd = fd;
        }
        if (get16(p.data() + 4) != kVersion || theirGame != game) {
            std::string why = get16(p.data() + 4) != kVersion ? "a different RetroForge version" : "a different game";
            sendMsg(BYE, std::vector<uint8_t>(why.begin(), why.end()));
            LOGW("turned away a player with %s (%s)", why.c_str(), theirGame.c_str());
            std::lock_guard<std::mutex> lock(sendMutex);
            ::close(fd);
            peerFd = -1;
            continue;
        }
        sendMsg(HELLO, hello());
        {
            std::lock_guard<std::mutex> lock(m);
            sendState = true;
            initialSync = true;
            current = State::Syncing;
            statusLine = "Player 2 joined. Starting…";
        }
        LOGI("player 2 joined");
        readLoop();
        return;
    }
}

void joinLoop(std::string address, int port) {
    addrinfo hints{}, *found = nullptr;
    hints.ai_socktype = SOCK_STREAM;
    if (getaddrinfo(address.c_str(), std::to_string(port).c_str(), &hints, &found) != 0 || !found) {
        setStatus(State::Failed, "Couldn't find " + address + ".");
        return;
    }
    int fd = socket(found->ai_family, SOCK_STREAM, 0);
    fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
    int rc = ::connect(fd, found->ai_addr, found->ai_addrlen);
    freeaddrinfo(found);
    bool connected = rc == 0;
    if (!connected) {
        pollfd w{fd, POLLOUT, 0};
        int err = 0;
        socklen_t len = sizeof err;
        connected = poll(&w, 1, 6000) > 0 && getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &len) == 0 && err == 0;
    }
    if (!connected || stopping) {
        ::close(fd);
        setStatus(State::Failed, "Couldn't reach " + address + ". Is it hosting, on the same network?");
        return;
    }
    fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) & ~O_NONBLOCK);
    tune(fd);
    {
        std::lock_guard<std::mutex> lock(sendMutex);
        peerFd = fd;
    }
    sendMsg(HELLO, hello());
    Msg type;
    std::vector<uint8_t> p;
    if (!recvMsg(fd, type, p)) {
        setStatus(State::Failed, "The host didn't answer.");
        return;
    }
    if (type == BYE) {
        setStatus(State::Failed, "The host is playing " + std::string(p.empty() ? "something else" : std::string(p.begin(), p.end())) + ".");
        return;
    }
    if (type != HELLO || p.size() < 8 || get32(p.data()) != kMagic) {
        setStatus(State::Failed, "That isn't a RetroForge game.");
        return;
    }
    {
        std::lock_guard<std::mutex> lock(m);
        delay = get16(p.data() + 6);  // the host's delay, so both apply buttons on the same frame
        initialSync = true;
        current = State::Syncing;
        statusLine = "Connected. Getting the host's game…";
    }
    LOGI("joined %s, delay %d", address.c_str(), delay);
    readLoop();
}

void resetFrames(uint32_t start) {
    frame = start;
    local.clear();
    for (uint32_t f = start; f < start + uint32_t(delay); f++) local[f] = Input{};
    sentUpTo = int64_t(start) + delay - 1;
    std::lock_guard<std::mutex> lock(m);
    // Buttons the other player already sent for later frames stay: the host starts playing as soon as it has sent
    // its state, while the guest may load it a while later.
    remote.erase(remote.begin(), remote.lower_bound(start));
    ourCrc.clear();
    theirCrc.clear();
    // The first [delay] frames have no buttons from anyone yet: both sides play them with nothing pressed.
    for (uint32_t f = start; f < start + uint32_t(delay); f++) remote.emplace(f, Input{});
}

std::string playingLine() {
    return std::string(isHost ? "Netplay: you're player 1" : "Netplay: you're player 2") + " · " + std::to_string(delay) +
           " frame" + (delay == 1 ? "" : "s") + " delay";
}

}  // namespace

// ---- Public ----

bool host(int port, const std::string &gameId, int delayFrames, std::string &error) {
    leave();
    int fd = socket(AF_INET6, SOCK_STREAM, 0);
    int zero = 0, one = 1;
    setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, &zero, sizeof zero);  // IPv4 too
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof one);
    sockaddr_in6 addr{};
    addr.sin6_family = AF_INET6;
    addr.sin6_port = htons(uint16_t(port));
    addr.sin6_addr = in6addr_any;
    if (fd < 0 || bind(fd, reinterpret_cast<sockaddr *>(&addr), sizeof addr) != 0 || listen(fd, 1) != 0) {
        if (fd >= 0) ::close(fd);
        error = "Couldn't open port " + std::to_string(port) + " for netplay.";
        return false;
    }
    stopping = false;
    isHost = true;
    delay = delayFrames;
    game = gameId;
    listenFd = fd;
    setStatus(State::Hosting, "Waiting for player 2 to join…");
    network = std::thread(hostLoop);
    return true;
}

bool join(const std::string &address, int port, const std::string &gameId, int delayFrames, std::string &error) {
    leave();
    stopping = false;
    isHost = false;
    delay = delayFrames;
    game = gameId;
    setStatus(State::Joining, "Connecting to " + address + "…");
    network = std::thread(joinLoop, address, port);
    return true;
}

void leave() {
    stopping = true;
    if (peerFd >= 0) sendMsg(BYE, {});
    {
        std::lock_guard<std::mutex> lock(sendMutex);
        if (peerFd >= 0) {
            ::shutdown(peerFd, SHUT_RDWR);
            ::close(peerFd);
        }
        peerFd = -1;
    }
    if (listenFd >= 0) {
        ::shutdown(listenFd, SHUT_RDWR);
        ::close(listenFd);
        listenFd = -1;
    }
    arrived.notify_all();
    if (network.joinable()) network.join();
    std::lock_guard<std::mutex> lock(m);
    current = State::Off;
    statusLine.clear();
    remote.clear();
    local.clear();
    sendState = hasIncomingState = resyncAsked = false;
    incomingState.clear();
}

State state() { return current; }

std::string status() {
    std::lock_guard<std::mutex> lock(m);
    return statusLine;
}

bool active() {
    State s = current;
    return s == State::Syncing || s == State::Playing;
}

int localPlayer() { return isHost ? 0 : 1; }

bool beginFrame(uint32_t localButtons, const int16_t localAnalog[4], FrameInput &out, const Hooks &hooks, bool *sentLocal) {
    std::unique_lock<std::mutex> lock(m);
    if (isHost && sendState) {
        // The guest gets the game as it is now: at the start, and whenever it asks after a mismatch.
        sendState = false;
        bool first = initialSync;
        initialSync = false;
        lock.unlock();
        std::vector<uint8_t> state = hooks.save();
        if (first) resetFrames(0);
        std::vector<uint8_t> msg;
        put32(msg, frame);
        msg.insert(msg.end(), state.begin(), state.end());
        sendMsg(STATE, msg);
        LOGI("sent the game state (%zu bytes) at frame %u", state.size(), frame);
        setStatus(State::Playing, playingLine());
        lock.lock();
    }
    if (!isHost && hasIncomingState) {
        hasIncomingState = false;
        std::vector<uint8_t> state;
        state.swap(incomingState);
        uint32_t at = incomingFrame;
        bool first = initialSync;
        initialSync = false;
        resyncAsked = false;
        lock.unlock();
        if (!hooks.load(state)) LOGW("couldn't load the host's state");
        if (first) {
            resetFrames(at);
        } else {
            // A resync: carry on from the host's frame, sending our buttons for any frames the host still needs.
            frame = at;
            Input last = local.empty() ? Input{} : local.rbegin()->second;
            for (int64_t f = sentUpTo + 1; f < int64_t(at) + delay; f++) {
                local[uint32_t(f)] = last;
                sendMsg(INPUT, inputMsg(uint32_t(f), last));
                sentUpTo = f;
            }
            std::lock_guard<std::mutex> again(m);
            ourCrc.clear();
            theirCrc.clear();
        }
        LOGI("loaded the host's state (%zu bytes) at frame %u", state.size(), at);
        setStatus(State::Playing, playingLine());
        lock.lock();
    }
    if (current != State::Playing) {
        lock.unlock();
        std::this_thread::sleep_for(kWaitSlice);
        return false;
    }

    // This device's buttons now are played [delay] frames from now, on both devices.
    uint32_t ahead = frame + uint32_t(delay);
    if (int64_t(ahead) > sentUpTo) {
        Input in;
        in.buttons = localButtons;
        std::memcpy(in.analog, localAnalog, sizeof in.analog);
        local[ahead] = in;
        sentUpTo = ahead;
        if (sentLocal) *sentLocal = true;
        lock.unlock();
        sendMsg(INPUT, inputMsg(ahead, in));
        lock.lock();
    }
    if (!arrived.wait_for(lock, kWaitSlice, [] { return remote.count(frame) > 0 || current != State::Playing; }) ||
        current != State::Playing) {
        if (waitingSince == clock::time_point{}) waitingSince = clock::now();
        if (clock::now() - waitingSince > std::chrono::seconds(1)) {
            statusLine = std::string("Waiting for player ") + (isHost ? "2" : "1") + "…";
        }
        return false;
    }
    if (waitingSince != clock::time_point{}) {
        waitingSince = {};
        statusLine = playingLine();
    }
    Input mine = local.count(frame) ? local[frame] : Input{};
    Input theirs = remote[frame];
    const Input &hostIn = isHost ? mine : theirs;
    const Input &guestIn = isHost ? theirs : mine;
    out.buttons[0] = hostIn.buttons;
    out.buttons[1] = guestIn.buttons;
    std::memcpy(out.analog[0], hostIn.analog, sizeof out.analog[0]);
    std::memcpy(out.analog[1], guestIn.analog, sizeof out.analog[1]);
    return true;
}

void endFrame(const Hooks &hooks) {
    if (current != State::Playing) return;
    if (frame > 0 && frame % kCrcEvery == 0) {
        std::vector<uint8_t> state = hooks.save();
        if (!state.empty() && state.size() <= kMaxCrcState) {
            uint32_t sum = uint32_t(crc32(0L, state.data(), uInt(state.size())));
            if (isHost) {
                std::vector<uint8_t> msg;
                put32(msg, frame);
                put32(msg, sum);
                sendMsg(CRC, msg);
            } else {
                std::lock_guard<std::mutex> lock(m);
                ourCrc[frame] = sum;
            }
        }
    }
    if (!isHost) {
        // Compare what both sides have; a mismatch asks the host for its state.
        bool ask = false;
        {
            std::lock_guard<std::mutex> lock(m);
            for (auto it = theirCrc.begin(); it != theirCrc.end();) {
                auto ours = ourCrc.find(it->first);
                if (ours == ourCrc.end()) {
                    if (it->first + kCrcEvery * 4 < frame) it = theirCrc.erase(it); else ++it;
                    continue;
                }
                if (ours->second != it->second && !resyncAsked) {
                    resyncAsked = true;
                    ask = true;
                    LOGW("out of step at frame %u: asking the host for its state", it->first);
                }
                ourCrc.erase(ours);
                it = theirCrc.erase(it);
            }
        }
        if (ask) sendMsg(RESYNC, {});
    }
    frame++;
    std::lock_guard<std::mutex> lock(m);
    if (frame > kHistory) {
        uint32_t oldest = frame - kHistory;
        local.erase(local.begin(), local.lower_bound(oldest));
        remote.erase(remote.begin(), remote.lower_bound(oldest));
    }
}

void stateChanged() {
    std::lock_guard<std::mutex> lock(m);
    if (isHost && current == State::Playing) sendState = true;
}

}  // namespace netplay
