// Netplay (see netplay.h): TCP connections from each guest to the host, a reader thread per connection that files what
// arrives, and lockstep frames on the core thread. Messages are a type byte, a little-endian length and the payload.

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
constexpr uint16_t kVersion = 2;         // 2: up to four players, with the host relaying everyone's buttons
enum Msg : uint8_t { HELLO = 1, STATE = 2, INPUT = 3, CRC = 4, RESYNC = 5, BYE = 6, FRAME = 7 };
// How often the guests compare a checksum of the game with the host's (frames), and the biggest state worth
// checksumming that often.
constexpr uint32_t kCrcEvery = 180;
constexpr size_t kMaxCrcState = 16u << 20;
// Frames of buttons kept, for a resync that goes back a little.
constexpr uint32_t kHistory = 900;
// A frame waits this long for buttons before the loop is told to try again.
constexpr auto kWaitSlice = std::chrono::milliseconds(8);
// A connecting guest has this long to say hello.
constexpr int kHelloSeconds = 5;

struct Input {
    uint32_t buttons = 0;
    int16_t analog[4]{};
};

using clock = std::chrono::steady_clock;

std::mutex m;  // everything below that more than one thread touches
std::condition_variable arrived;
std::atomic<State> current{State::Off};
std::string statusLine;
std::atomic<bool> stopping{false};
std::mutex sendMutex;  // guards every fd below and writes to them
int listenFd = -1;

bool isHost = false;
int slot = 0;  // this device's player: 0 the host
int delay = 3;
std::string game;

// The host's guests, by player (slot 0 is the host itself and unused). A guest is "in" once it has been sent the
// game's state: from then on the host waits for its buttons every frame.
struct Peer {
    int fd = -1;
    std::thread reader;
    bool in = false;          // guarded by m
    bool needsState = false;  // guarded by m: just joined, or asked for a resync
    std::map<uint32_t, Input> inputs;  // guarded by m
};
Peer peers[kMaxPlayers];
std::thread acceptThread;

// A guest's connection to the host.
int hostFd = -1;
std::thread guestThread;

// Core thread, except where marked.
uint32_t frame = 0;
int64_t sentUpTo = -1;
std::map<uint32_t, Input> local;
bool started = false;                          // guarded by m (host: the first guest is in and the game runs)
std::map<uint32_t, FrameInput> frames;         // guarded by m (guest: everyone's buttons, from the host)
uint8_t playersIn = 1;                         // guarded by m: bit per player in the game
std::vector<uint8_t> incomingState;            // guarded by m (guest)
uint32_t incomingFrame = 0;                    // guarded by m
bool hasIncomingState = false;                 // guarded by m
bool initialSync = true;                       // guarded by m (guest)
std::map<uint32_t, uint32_t> ourCrc, theirCrc; // guarded by m (guest)
bool resyncAsked = false;                      // guarded by m
clock::time_point waitingSince{};

void setStatus(State s, const std::string &line) {
    std::lock_guard<std::mutex> lock(m);
    current = s;
    statusLine = line;
    arrived.notify_all();
}

int countOf(uint8_t mask) {
    int n = 0;
    for (int i = 0; i < kMaxPlayers; i++) n += (mask >> i) & 1;
    return n;
}

// Called with m held.
std::string playingLine() {
    int n = countOf(playersIn);
    return "Netplay: you're player " + std::to_string(slot + 1) + " · " + std::to_string(n) + " player" + (n == 1 ? "" : "s") +
           " · " + std::to_string(delay) + " frame" + (delay == 1 ? "" : "s") + " delay";
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

// Sends to whichever connection [fd] names; false when it's gone or the send failed.
bool sendTo(const int &fd, Msg type, const std::vector<uint8_t> &payload) {
    std::lock_guard<std::mutex> lock(sendMutex);
    if (fd < 0) return false;
    std::vector<uint8_t> out;
    out.reserve(5 + payload.size());
    out.push_back(type);
    put32(out, uint32_t(payload.size()));
    out.insert(out.end(), payload.begin(), payload.end());
    size_t done = 0;
    while (done < out.size()) {
        ssize_t n = ::send(fd, out.data() + done, out.size() - done, MSG_NOSIGNAL);
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

// magic, version, delay, the player this hello is for (the host's answer), the game.
std::vector<uint8_t> hello(int forSlot) {
    std::vector<uint8_t> b;
    put32(b, kMagic);
    put16(b, kVersion);
    put16(b, uint16_t(delay));
    put16(b, uint16_t(forSlot));
    b.insert(b.end(), game.begin(), game.end());
    return b;
}

void putInput(std::vector<uint8_t> &b, const Input &in) {
    put32(b, in.buttons);
    for (int16_t a : in.analog) put16(b, uint16_t(a));
}

Input getInput(const uint8_t *p) {
    Input in;
    in.buttons = get32(p);
    for (int i = 0; i < 4; i++) in.analog[i] = int16_t(get16(p + 4 + 2 * i));
    return in;
}

constexpr size_t kInputBytes = 12;

void tune(int fd) {
    int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof one);
}

void closeFd(int &fd) {
    std::lock_guard<std::mutex> lock(sendMutex);
    if (fd >= 0) {
        ::shutdown(fd, SHUT_RDWR);
        ::close(fd);
    }
    fd = -1;
}

// ---- The host ----

// A guest's reader: its buttons and resync requests, until it leaves.
void peerLoop(int who) {
    Peer &peer = peers[who];
    Msg type;
    std::vector<uint8_t> p;
    while (!stopping && recvMsg(peer.fd, type, p)) {
        std::lock_guard<std::mutex> lock(m);
        if (type == INPUT && p.size() >= 4 + kInputBytes) {
            peer.inputs[get32(p.data())] = getInput(p.data() + 4);
        } else if (type == RESYNC) {
            peer.needsState = true;
        } else if (type == BYE) {
            break;
        }
        arrived.notify_all();
    }
    if (stopping) return;
    // Gone: the game carries on without them, their buttons up from now on.
    closeFd(peer.fd);
    std::lock_guard<std::mutex> lock(m);
    peer.in = false;
    peer.needsState = false;
    peer.inputs.clear();
    playersIn &= uint8_t(~(1u << who));
    if (current == State::Syncing && !started) current = State::Hosting;  // left before the game began
    statusLine = "Player " + std::to_string(who + 1) + " left. " + playingLine();
    LOGI("player %d left", who + 1);
    arrived.notify_all();
}

void acceptLoop() {
    while (!stopping) {
        pollfd waiting{listenFd, POLLIN, 0};
        if (poll(&waiting, 1, 200) <= 0) continue;
        int fd = accept(listenFd, nullptr, nullptr);
        if (fd < 0) continue;
        tune(fd);
        timeval wait{kHelloSeconds, 0}, forever{0, 0};
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &wait, sizeof wait);
        Msg type;
        std::vector<uint8_t> p;
        if (!recvMsg(fd, type, p) || type != HELLO || p.size() < 10 || get32(p.data()) != kMagic) {
            ::close(fd);
            continue;
        }
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &forever, sizeof forever);
        std::string theirGame(p.begin() + 10, p.end());
        std::string why;
        if (get16(p.data() + 4) != kVersion) why = "a different RetroForge version";
        else if (theirGame != game) why = "a different game";
        int who = 0;
        if (why.empty()) {
            std::lock_guard<std::mutex> lock(sendMutex);
            for (int i = 1; i < kMaxPlayers && !who; i++) if (peers[i].fd < 0) who = i;
            if (!who) why = "a full game (4 players)";
        }
        if (!why.empty()) {
            int turnedAway = fd;
            sendTo(turnedAway, BYE, std::vector<uint8_t>(why.begin(), why.end()));
            LOGW("turned away a player: %s (%s)", why.c_str(), theirGame.c_str());
            ::close(fd);
            continue;
        }
        Peer &peer = peers[who];
        if (peer.reader.joinable()) peer.reader.join();  // the last player in this place has gone
        {
            std::lock_guard<std::mutex> lock(sendMutex);
            peer.fd = fd;
        }
        sendTo(peer.fd, HELLO, hello(who));
        {
            std::lock_guard<std::mutex> lock(m);
            peer.inputs.clear();
            peer.needsState = true;
            peer.in = false;
            if (current == State::Hosting) {
                current = State::Syncing;
                statusLine = "Player " + std::to_string(who + 1) + " joined. Starting…";
            }
        }
        LOGI("player %d joined", who + 1);
        peer.reader = std::thread(peerLoop, who);
    }
}

// The host's frame: sends waiting guests the state, then gathers everyone's buttons for this frame and passes them on.
bool hostFrame(std::unique_lock<std::mutex> &lock, const Input &mine, FrameInput &out, const Hooks &hooks, bool *sentLocal) {
    bool anyNeed = false;
    for (int i = 1; i < kMaxPlayers; i++) anyNeed |= peers[i].needsState;
    if (anyNeed) {
        lock.unlock();
        std::vector<uint8_t> state = hooks.save();
        lock.lock();
        if (!started) {
            // The game starts with the first guest: no one has pressed anything for the first [delay] frames.
            started = true;
            frame = 0;
            local.clear();
            for (uint32_t f = 0; f < uint32_t(delay); f++) local[f] = Input{};
            sentUpTo = delay - 1;
        }
        std::vector<uint8_t> msg;
        put32(msg, frame);
        msg.insert(msg.end(), state.begin(), state.end());
        for (int i = 1; i < kMaxPlayers; i++) {
            Peer &peer = peers[i];
            if (!peer.needsState) continue;
            peer.needsState = false;
            if (!peer.in) {
                // A new guest's buttons start [delay] frames on; until then it presses nothing.
                peer.in = true;
                playersIn |= uint8_t(1u << i);
                peer.inputs.erase(peer.inputs.begin(), peer.inputs.lower_bound(frame));
                for (uint32_t f = frame; f < frame + uint32_t(delay); f++) peer.inputs.emplace(f, Input{});
            }
            lock.unlock();
            sendTo(peer.fd, STATE, msg);
            lock.lock();
            LOGI("sent player %d the game state (%zu bytes) at frame %u", i + 1, state.size(), frame);
        }
        current = State::Playing;
        statusLine = playingLine();
    }
    if (current != State::Playing) return false;

    uint32_t ahead = frame + uint32_t(delay);
    if (int64_t(ahead) > sentUpTo) {
        local[ahead] = mine;
        sentUpTo = ahead;
        if (sentLocal) *sentLocal = true;
    }
    int missing = 0;
    bool ready = arrived.wait_for(lock, kWaitSlice, [&] {
        missing = 0;
        for (int i = 1; i < kMaxPlayers; i++) {
            if (peers[i].in && !peers[i].inputs.count(frame)) { missing = i; return false; }
        }
        return true;
    });
    if (!ready || stopping) {
        if (waitingSince == clock::time_point{}) waitingSince = clock::now();
        if (missing && clock::now() - waitingSince > std::chrono::seconds(1)) {
            statusLine = "Waiting for player " + std::to_string(missing + 1) + "…";
        }
        return false;
    }
    if (waitingSince != clock::time_point{}) {
        waitingSince = {};
        statusLine = playingLine();
    }
    out = FrameInput{};
    Input all[kMaxPlayers];
    all[0] = local.count(frame) ? local[frame] : Input{};
    for (int i = 1; i < kMaxPlayers; i++) {
        if (peers[i].in) all[i] = peers[i].inputs[frame];
    }
    std::vector<uint8_t> msg;
    put32(msg, frame);
    msg.push_back(playersIn);
    for (const Input &in : all) putInput(msg, in);
    for (int i = 0; i < kMaxPlayers; i++) {
        out.buttons[i] = all[i].buttons;
        std::memcpy(out.analog[i], all[i].analog, sizeof out.analog[i]);
    }
    bool to[kMaxPlayers]{};
    for (int i = 1; i < kMaxPlayers; i++) to[i] = peers[i].in;
    lock.unlock();
    for (int i = 1; i < kMaxPlayers; i++) {
        if (to[i]) sendTo(peers[i].fd, FRAME, msg);
    }
    lock.lock();
    return true;
}

// ---- A guest ----

void guestLoop(std::string address, int port) {
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
        hostFd = fd;
    }
    sendTo(hostFd, HELLO, hello(0));
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
    if (type != HELLO || p.size() < 10 || get32(p.data()) != kMagic || get16(p.data() + 4) != kVersion) {
        setStatus(State::Failed, "That isn't a RetroForge game of this version.");
        return;
    }
    {
        std::lock_guard<std::mutex> lock(m);
        delay = get16(p.data() + 6);  // the host's delay, so everyone applies buttons on the same frame
        slot = get16(p.data() + 8);
        initialSync = true;
        current = State::Syncing;
        statusLine = "Connected as player " + std::to_string(slot + 1) + ". Getting the host's game…";
    }
    LOGI("joined %s as player %d, delay %d", address.c_str(), slot + 1, delay);
    while (!stopping && recvMsg(fd, type, p)) {
        std::lock_guard<std::mutex> lock(m);
        switch (type) {
            case STATE:
                if (p.size() >= 4) {
                    incomingFrame = get32(p.data());
                    incomingState.assign(p.begin() + 4, p.end());
                    hasIncomingState = true;
                }
                break;
            case FRAME:
                if (p.size() >= 5 + kMaxPlayers * kInputBytes) {
                    FrameInput in;
                    for (int i = 0; i < kMaxPlayers; i++) {
                        Input one = getInput(p.data() + 5 + i * kInputBytes);
                        in.buttons[i] = one.buttons;
                        std::memcpy(in.analog[i], one.analog, sizeof in.analog[i]);
                    }
                    frames[get32(p.data())] = in;
                    playersIn = p[4];
                }
                break;
            case CRC:
                if (p.size() >= 8) theirCrc[get32(p.data())] = get32(p.data() + 4);
                break;
            case BYE:
                current = State::Failed;
                statusLine = "The host left.";
                arrived.notify_all();
                return;
            default:
                break;
        }
        arrived.notify_all();
    }
    if (!stopping) setStatus(State::Failed, "Lost the connection to the host.");
}

// Starts this guest's frames at [start], where the host's state was taken.
void resetFrames(uint32_t start) {
    frame = start;
    local.clear();
    for (uint32_t f = start; f < start + uint32_t(delay); f++) local[f] = Input{};
    sentUpTo = int64_t(start) + delay - 1;
    std::lock_guard<std::mutex> lock(m);
    frames.erase(frames.begin(), frames.lower_bound(start));
    ourCrc.clear();
    theirCrc.clear();
}

bool guestFrame(std::unique_lock<std::mutex> &lock, const Input &mine, FrameInput &out, const Hooks &hooks, bool *sentLocal) {
    if (hasIncomingState) {
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
                std::vector<uint8_t> msg;
                put32(msg, uint32_t(f));
                putInput(msg, last);
                sendTo(hostFd, INPUT, msg);
                sentUpTo = f;
            }
            std::lock_guard<std::mutex> again(m);
            frames.erase(frames.begin(), frames.lower_bound(at));
            ourCrc.clear();
            theirCrc.clear();
        }
        LOGI("loaded the host's state (%zu bytes) at frame %u", state.size(), at);
        lock.lock();
        current = State::Playing;
        statusLine = playingLine();
    }
    if (current != State::Playing) return false;

    // This device's buttons now are played [delay] frames from now, on every device.
    uint32_t ahead = frame + uint32_t(delay);
    if (int64_t(ahead) > sentUpTo) {
        local[ahead] = mine;
        sentUpTo = ahead;
        if (sentLocal) *sentLocal = true;
        std::vector<uint8_t> msg;
        put32(msg, ahead);
        putInput(msg, mine);
        lock.unlock();
        sendTo(hostFd, INPUT, msg);
        lock.lock();
    }
    if (!arrived.wait_for(lock, kWaitSlice, [] { return frames.count(frame) > 0 || current != State::Playing; }) ||
        current != State::Playing) {
        if (waitingSince == clock::time_point{}) waitingSince = clock::now();
        if (clock::now() - waitingSince > std::chrono::seconds(1)) statusLine = "Waiting for the other players…";
        return false;
    }
    if (waitingSince != clock::time_point{}) {
        waitingSince = {};
        statusLine = playingLine();
    }
    out = frames[frame];
    return true;
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
    if (fd < 0 || bind(fd, reinterpret_cast<sockaddr *>(&addr), sizeof addr) != 0 || listen(fd, kMaxPlayers) != 0) {
        if (fd >= 0) ::close(fd);
        error = "Couldn't open port " + std::to_string(port) + " for netplay.";
        return false;
    }
    stopping = false;
    isHost = true;
    slot = 0;
    delay = delayFrames;
    game = gameId;
    listenFd = fd;
    {
        std::lock_guard<std::mutex> lock(m);
        started = false;
        playersIn = 1;
    }
    setStatus(State::Hosting, "Waiting for players to join (up to 4)…");
    acceptThread = std::thread(acceptLoop);
    return true;
}

bool join(const std::string &address, int port, const std::string &gameId, int delayFrames, std::string &error) {
    leave();
    stopping = false;
    isHost = false;
    slot = 1;
    delay = delayFrames;
    game = gameId;
    {
        std::lock_guard<std::mutex> lock(m);
        playersIn = 1;
    }
    setStatus(State::Joining, "Connecting to " + address + "…");
    guestThread = std::thread(guestLoop, address, port);
    return true;
}

void leave() {
    stopping = true;
    sendTo(hostFd, BYE, {});
    for (Peer &peer : peers) sendTo(peer.fd, BYE, {});
    closeFd(hostFd);
    for (Peer &peer : peers) closeFd(peer.fd);
    closeFd(listenFd);
    arrived.notify_all();
    if (acceptThread.joinable()) acceptThread.join();
    if (guestThread.joinable()) guestThread.join();
    for (Peer &peer : peers) if (peer.reader.joinable()) peer.reader.join();
    std::lock_guard<std::mutex> lock(m);
    current = State::Off;
    statusLine.clear();
    for (Peer &peer : peers) {
        peer.in = peer.needsState = false;
        peer.inputs.clear();
    }
    frames.clear();
    local.clear();
    started = hasIncomingState = resyncAsked = false;
    playersIn = 1;
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

int localPlayer() { return slot; }

int players() {
    std::lock_guard<std::mutex> lock(m);
    return countOf(playersIn);
}

bool beginFrame(uint32_t localButtons, const int16_t localAnalog[4], FrameInput &out, const Hooks &hooks, bool *sentLocal) {
    Input mine;
    mine.buttons = localButtons;
    std::memcpy(mine.analog, localAnalog, sizeof mine.analog);
    std::unique_lock<std::mutex> lock(m);
    bool ran = isHost ? hostFrame(lock, mine, out, hooks, sentLocal) : guestFrame(lock, mine, out, hooks, sentLocal);
    if (!ran && current != State::Playing) {
        lock.unlock();
        std::this_thread::sleep_for(kWaitSlice);
    }
    return ran;
}

void endFrame(const Hooks &hooks) {
    if (current != State::Playing) return;
    if (frame > 0 && frame % kCrcEvery == 0) {
        // The game's RAM where the core offers it: some cores' save states differ byte for byte even straight after
        // loading one (Genesis Plus GX), which would look like drifting apart every time.
        uint32_t sum = 0;
        bool have = hooks.checksum && hooks.checksum(sum);
        if (!have) {
            std::vector<uint8_t> state = hooks.save();
            have = !state.empty() && state.size() <= kMaxCrcState;
            if (have) sum = uint32_t(crc32(0L, state.data(), uInt(state.size())));
        }
        if (have) {
            if (isHost) {
                std::vector<uint8_t> msg;
                put32(msg, frame);
                put32(msg, sum);
                bool to[kMaxPlayers]{};
                {
                    std::lock_guard<std::mutex> lock(m);
                    for (int i = 1; i < kMaxPlayers; i++) to[i] = peers[i].in;
                }
                for (int i = 1; i < kMaxPlayers; i++) if (to[i]) sendTo(peers[i].fd, CRC, msg);
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
        if (ask) sendTo(hostFd, RESYNC, {});
    }
    frame++;
    std::lock_guard<std::mutex> lock(m);
    if (frame > kHistory) {
        uint32_t oldest = frame - kHistory;
        local.erase(local.begin(), local.lower_bound(oldest));
        frames.erase(frames.begin(), frames.lower_bound(oldest));
        for (Peer &peer : peers) peer.inputs.erase(peer.inputs.begin(), peer.inputs.lower_bound(oldest));
    }
}

void stateChanged() {
    std::lock_guard<std::mutex> lock(m);
    if (!isHost || current != State::Playing) return;
    for (int i = 1; i < kMaxPlayers; i++) if (peers[i].in) peers[i].needsState = true;
}

}  // namespace netplay
