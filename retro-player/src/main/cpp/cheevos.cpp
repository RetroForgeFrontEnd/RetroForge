#include "cheevos.h"

#include <jni.h>
#include <android/log.h>

#include <deque>
#include <map>
#include <mutex>
#include <string>
#include <vector>

extern "C" {
#include "rc_client.h"
#include "rc_libretro.h"
}

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "RetroForgeCheevos", __VA_ARGS__)

// The player's lock on the core (player.cpp); always taken before this file's own lock.
std::mutex &coreLock();
// The loaded game: its path and, when it was read into memory, its bytes (player.cpp).
const std::string &contentPath();
const std::vector<uint8_t> &contentBytes();

namespace {

// Fields of a message to Kotlin are split by this (titles can hold any ordinary character).
constexpr char SEP = '\x1f';

std::mutex lock;  // everything below
rc_client_t *client = nullptr;
rc_libretro_memory_regions_t regions{};
bool regionsReady = false;
uint32_t consoleId = 0;
int framesSinceRegions = 0;
cheevos::MemoryData memoryData = nullptr;
cheevos::MemorySize memorySize = nullptr;

// The core's memory map, copied (the core's own array may not live on).
std::vector<retro_memory_descriptor> descriptors;
std::vector<std::string> addressSpaces;
bool haveMap = false;

// Web requests waiting for Kotlin, and their callbacks.
struct Request { std::string url, post, type; rc_client_server_callback_t callback; void *data; };
std::map<int, Request> requests;
std::deque<int> unsent;
int nextRequest = 1;

std::deque<std::string> messages;  // for Kotlin: events, login and load results

std::string join(std::initializer_list<std::string> parts) {
    std::string out;
    for (auto &p : parts) { if (!out.empty()) out += SEP; out += p; }
    return out;
}

void say(std::string line) {
    messages.push_back(std::move(line));
    if (messages.size() > 200) messages.pop_front();
}

std::string badge(const rc_client_achievement_t *a, bool unlocked) {
    const char *url = unlocked ? a->badge_url : a->badge_locked_url;
    return url ? url : "";
}

void coreMemoryInfo(uint32_t id, rc_libretro_core_memory_info_t *info) {
    info->data = memoryData ? static_cast<uint8_t *>(memoryData(id)) : nullptr;
    info->size = memorySize ? memorySize(id) : 0;
}

void initRegions() {
    if (regionsReady) rc_libretro_memory_destroy(&regions);
    retro_memory_map map{descriptors.data(), static_cast<unsigned>(descriptors.size())};
    regionsReady = rc_libretro_memory_init(&regions, haveMap ? &map : nullptr, coreMemoryInfo, consoleId) != 0;
    framesSinceRegions = 0;
}

uint32_t RC_CCONV readMemory(uint32_t address, uint8_t *buffer, uint32_t bytes, rc_client_t *) {
    if (!regionsReady) return 0;
    return rc_libretro_memory_read(&regions, address, buffer, bytes);
}

void RC_CCONV serverCall(const rc_api_request_t *request, rc_client_server_callback_t callback, void *data, rc_client_t *) {
    int id = nextRequest++;
    requests[id] = {request->url ? request->url : "", request->post_data ? request->post_data : "",
                    request->content_type ? request->content_type : "", callback, data};
    unsent.push_back(id);
}

void RC_CCONV onEvent(const rc_client_event_t *event, rc_client_t *) {
    const rc_client_achievement_t *a = event->achievement;
    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED:
            say(join({"unlocked", std::to_string(a->id), a->title ? a->title : "", a->description ? a->description : "",
                      std::to_string(a->points), badge(a, true)}));
            break;
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_SHOW:
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_UPDATE:
            say(join({"progress", std::to_string(a->id), a->title ? a->title : "", a->measured_progress, badge(a, false)}));
            break;
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW:
            say(join({"challenge", std::to_string(a->id), a->title ? a->title : "", badge(a, false)}));
            break;
        case RC_CLIENT_EVENT_GAME_COMPLETED: {
            const rc_client_game_t *g = rc_client_get_game_info(client);
            say(join({"completed", g && g->title ? g->title : ""}));
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED:
            say(join({"leaderboard", event->leaderboard->title ? event->leaderboard->title : "",
                      event->leaderboard->tracker_value ? event->leaderboard->tracker_value : ""}));
            break;
        case RC_CLIENT_EVENT_SERVER_ERROR:
            say(join({"error", event->server_error->error_message ? event->server_error->error_message : ""}));
            break;
        case RC_CLIENT_EVENT_DISCONNECTED: say("disconnected"); break;
        case RC_CLIENT_EVENT_RECONNECTED: say("reconnected"); break;
        case RC_CLIENT_EVENT_RESET: say("reset"); break;
        default: break;
    }
}

void RC_CCONV onLogin(int result, const char *error, rc_client_t *c, void *) {
    const rc_client_user_t *user = rc_client_get_user_info(c);
    if (result == RC_OK && user) {
        say(join({"login", "1", "", user->username ? user->username : "", user->token ? user->token : "",
                  user->display_name ? user->display_name : "", std::to_string(user->score),
                  std::to_string(user->score_softcore), user->avatar_url ? user->avatar_url : ""}));
    } else {
        say(join({"login", "0", error ? error : rc_error_str(result)}));
    }
}

void RC_CCONV onGameLoaded(int result, const char *error, rc_client_t *c, void *) {
    const rc_client_game_t *game = rc_client_get_game_info(c);
    if (result != RC_OK || !game || game->id == 0) {
        say(join({"game", "0", error ? error : rc_error_str(result)}));
        return;
    }
    rc_client_user_game_summary_t summary{};
    rc_client_get_user_game_summary(c, &summary);
    say(join({"game", "1", "", std::to_string(game->id), game->title ? game->title : "", game->badge_url ? game->badge_url : "",
              std::to_string(summary.num_unlocked_achievements), std::to_string(summary.num_core_achievements),
              std::to_string(summary.points_unlocked), std::to_string(summary.points_core)}));
}

std::string str(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

jobjectArray strings(JNIEnv *env, const std::vector<std::string> &list) {
    jobjectArray out = env->NewObjectArray(static_cast<jsize>(list.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < list.size(); i++) {
        jstring s = env->NewStringUTF(list[i].c_str());
        env->SetObjectArrayElement(out, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return out;
}

}  // namespace

namespace cheevos {

void setMemoryMap(const retro_memory_map *map) {
    std::lock_guard<std::mutex> l(lock);
    descriptors.clear();
    addressSpaces.clear();
    haveMap = map && map->num_descriptors;
    if (!haveMap) return;
    addressSpaces.reserve(map->num_descriptors);
    for (unsigned i = 0; i < map->num_descriptors; i++) {
        retro_memory_descriptor d = map->descriptors[i];
        addressSpaces.emplace_back(d.addrspace ? d.addrspace : "");
        descriptors.push_back(d);
    }
    for (size_t i = 0; i < descriptors.size(); i++) descriptors[i].addrspace = map->descriptors[i].addrspace ? addressSpaces[i].c_str() : nullptr;
}

void setCoreMemory(MemoryData data, MemorySize size) {
    std::lock_guard<std::mutex> l(lock);
    memoryData = data;
    memorySize = size;
}

void frame() {
    std::lock_guard<std::mutex> l(lock);
    if (!client || !rc_client_is_game_loaded(client)) return;
    // Some cores only hand over their memory after a few frames.
    if ((!regionsReady || regions.total_size == 0) && ++framesSinceRegions >= 60) initRegions();
    rc_client_do_frame(client);
}

void reset() {
    std::lock_guard<std::mutex> l(lock);
    if (client) rc_client_reset(client);
}

void shutdown() {
    std::lock_guard<std::mutex> l(lock);
    if (client) rc_client_unload_game(client);
    if (regionsReady) rc_libretro_memory_destroy(&regions);
    regionsReady = false;
    memoryData = nullptr;
    memorySize = nullptr;
    haveMap = false;
    descriptors.clear();
}

}  // namespace cheevos

#define JNI(name) JNICALL Java_com_retroforge_player_NativePlayer_##name

extern "C" {

// Creates the RetroAchievements client (once); [hardcore] can't be changed while a game is loaded.
JNIEXPORT void JNI(cheevosStart)(JNIEnv *, jobject, jboolean hardcore) {
    std::lock_guard<std::mutex> l(lock);
    if (!client) {
        client = rc_client_create(readMemory, serverCall);
        rc_client_set_event_handler(client, onEvent);
        rc_client_enable_logging(client, RC_CLIENT_LOG_LEVEL_WARN, [](const char *m, const rc_client_t *) { LOGI("%s", m); });
    }
    rc_client_set_hardcore_enabled(client, hardcore ? 1 : 0);
}

// The part of the user agent rcheevos adds ("rcheevos/12.5.0").
JNIEXPORT jstring JNI(cheevosUserAgentClause)(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> l(lock);
    char buffer[64] = {0};
    if (client) rc_client_get_user_agent_clause(client, buffer, sizeof buffer);
    return env->NewStringUTF(buffer);
}

// Signs in with a token from an earlier sign-in, else with the password (which is not kept).
JNIEXPORT void JNI(cheevosLogin)(JNIEnv *env, jobject, jstring user, jstring password, jstring token) {
    std::string u = str(env, user), p = str(env, password), t = str(env, token);
    std::lock_guard<std::mutex> l(lock);
    if (!client) return;
    if (!t.empty()) rc_client_begin_login_with_token(client, u.c_str(), t.c_str(), onLogin, nullptr);
    else rc_client_begin_login_with_password(client, u.c_str(), p.c_str(), onLogin, nullptr);
}

JNIEXPORT void JNI(cheevosLogout)(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> l(lock);
    if (client) rc_client_logout(client);
}

// Identifies the loaded game for [console] (rcheevos' console id) and starts its achievements.
JNIEXPORT void JNI(cheevosLoadGame)(JNIEnv *, jobject, jint console) {
    std::lock_guard<std::mutex> core(coreLock());
    std::lock_guard<std::mutex> l(lock);
    if (!client) return;
    consoleId = static_cast<uint32_t>(console);
    initRegions();
    const std::vector<uint8_t> &bytes = contentBytes();
    rc_client_begin_identify_and_load_game(client, consoleId, contentPath().c_str(),
                                           bytes.empty() ? nullptr : bytes.data(), bytes.size(), onGameLoaded, nullptr);
}

// Web requests to make: "id, url, content type, post data" each, fields split by \x1f. Also lets rcheevos
// retry and time out while the game is paused.
JNIEXPORT jobjectArray JNI(cheevosRequests)(JNIEnv *env, jobject) {
    std::vector<std::string> out;
    {
        std::lock_guard<std::mutex> l(lock);
        if (client) rc_client_idle(client);
        for (int id : unsent) {
            const Request &r = requests[id];
            out.push_back(join({std::to_string(id), r.url, r.type, r.post}));
        }
        unsent.clear();
    }
    return strings(env, out);
}

// The answer to a request; [status] is the HTTP status, or -1 when there was no answer (network down).
JNIEXPORT void JNI(cheevosResponse)(JNIEnv *env, jobject, jint id, jint status, jbyteArray body) {
    std::string text;
    if (body) {
        text.resize(static_cast<size_t>(env->GetArrayLength(body)));
        env->GetByteArrayRegion(body, 0, static_cast<jsize>(text.size()), reinterpret_cast<jbyte *>(&text[0]));
    }
    std::lock_guard<std::mutex> core(coreLock());
    std::lock_guard<std::mutex> l(lock);
    auto it = requests.find(id);
    if (it == requests.end()) return;
    Request r = it->second;
    requests.erase(it);
    rc_api_server_response_t response{text.c_str(), text.size(), status < 0 ? RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR : status};
    r.callback(&response, r.data);
}

// Events since the last call: "unlocked", "progress", "challenge", "completed", "leaderboard", "login", "game",
// "error", "disconnected", "reconnected", "reset", each with its fields split by \x1f.
JNIEXPORT jobjectArray JNI(cheevosEvents)(JNIEnv *env, jobject) {
    std::vector<std::string> out;
    {
        std::lock_guard<std::mutex> l(lock);
        out.assign(messages.begin(), messages.end());
        messages.clear();
    }
    return strings(env, out);
}

// The game's achievements: "id, title, description, points, unlocked (0/1), badge, progress, percent, rarity".
JNIEXPORT jobjectArray JNI(cheevosList)(JNIEnv *env, jobject) {
    std::vector<std::string> out;
    {
        std::lock_guard<std::mutex> l(lock);
        if (client && rc_client_is_game_loaded(client)) {
            rc_client_achievement_list_t *list = rc_client_create_achievement_list(client, RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE,
                                                                                  RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_PROGRESS);
            for (uint32_t b = 0; list && b < list->num_buckets; b++) {
                const rc_client_achievement_bucket_t &bucket = list->buckets[b];
                for (uint32_t i = 0; i < bucket.num_achievements; i++) {
                    const rc_client_achievement_t *a = bucket.achievements[i];
                    bool unlocked = a->unlocked != RC_CLIENT_ACHIEVEMENT_UNLOCKED_NONE;
                    out.push_back(join({std::to_string(a->id), a->title ? a->title : "", a->description ? a->description : "",
                                        std::to_string(a->points), unlocked ? "1" : "0", badge(a, unlocked), a->measured_progress,
                                        std::to_string(a->measured_percent), std::to_string(a->rarity)}));
                }
            }
            if (list) rc_client_destroy_achievement_list(list);
        }
    }
    return strings(env, out);
}

// "Title is on level 3" and the like, when the game has it.
JNIEXPORT jstring JNI(cheevosRichPresence)(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> l(lock);
    char buffer[256] = {0};
    if (client && rc_client_is_game_loaded(client)) rc_client_get_rich_presence_message(client, buffer, sizeof buffer);
    return env->NewStringUTF(buffer);
}

JNIEXPORT void JNI(cheevosUnloadGame)(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> l(lock);
    if (client) rc_client_unload_game(client);
}

}  // extern "C"
