// RetroForge PC player: preloaded (LD_PRELOAD) into every Wine process.
//
// Android doesn't let an app that targets a current SDK execute files in its own storage, but the system
// linker can still load them: "/system/bin/linker64 /path/program args" runs a program from app storage.
// RetroForge starts Wine that way, and this library makes every program Wine starts (wineserver, the next
// wine process) go the same way. It also answers "/proc/self/exe" with Wine's own path, because under the
// linker that link points at the linker, and Wine finds its files from it.
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define LINKER "/system/bin/linker64"
#define MAX_ARGS 4096

extern char **environ;

typedef int (*execve_fn)(const char *, char *const[], char *const[]);
typedef int (*spawn_fn)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *,
                        char *const[], char *const[]);

static int is_system_path(const char *path) {
    static const char *prefixes[] = {"/system/", "/apex/", "/vendor/", "/product/", "/odm/", "/system_ext/", NULL};
    for (int i = 0; prefixes[i]; i++)
        if (strncmp(path, prefixes[i], strlen(prefixes[i])) == 0) return 1;
    return 0;
}

static int ends_with(const char *s, const char *tail) {
    size_t n = strlen(s), m = strlen(tail);
    return n >= m && strcmp(s + n - m, tail) == 0;
}

// Reads the start of a file: 1 = 64-bit ELF, 2 = "#!" script (interpreter copied out), 0 = other / unreadable.
static int probe(const char *path, char *interp, size_t interp_len, char *interp_arg, size_t arg_len) {
    char head[256];
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    ssize_t n = read(fd, head, sizeof(head) - 1);
    close(fd);
    if (n < 5) return 0;
    head[n] = 0;
    if (head[0] == 0x7f && head[1] == 'E' && head[2] == 'L' && head[3] == 'F') return head[4] == 2 ? 1 : 0;
    if (head[0] != '#' || head[1] != '!') return 0;
    char *p = head + 2, *end;
    while (*p == ' ' || *p == '\t') p++;
    end = p;
    while (*end && *end != ' ' && *end != '\t' && *end != '\n') end++;
    snprintf(interp, interp_len, "%.*s", (int)(end - p), p);
    interp_arg[0] = 0;
    p = end;
    while (*p == ' ' || *p == '\t') p++;
    end = p;
    while (*end && *end != '\n') end++;
    while (end > p && (end[-1] == ' ' || end[-1] == '\t' || end[-1] == '\r')) end--;
    if (end > p) snprintf(interp_arg, arg_len, "%.*s", (int)(end - p), p);
    return interp[0] ? 2 : 0;
}

// Works out what to really run for (path, argv): returns the program and fills out_argv, or NULL to run it as
// it is. Returns "" to refuse: Wine's preloader is a static program the linker can't load, and Wine carries on
// without it when it can't be started.
static const char *rewrite(const char *path, char *const argv[], const char **out_argv) {
    static __thread char interp[PATH_MAX], interp_arg[256];
    if (!path || is_system_path(path)) return NULL;
    if (ends_with(path, "/wine-preloader")) return "";
    int kind = probe(path, interp, sizeof(interp), interp_arg, sizeof(interp_arg));
    int argc = 0;
    while (argv && argv[argc] && argc < MAX_ARGS - 8) argc++;
    int o = 0;
    if (kind == 1) {
        out_argv[o++] = LINKER;
        out_argv[o++] = path;
        for (int i = 1; i < argc; i++) out_argv[o++] = argv[i];
        out_argv[o] = NULL;
        return LINKER;
    }
    if (kind == 2) {
        int wrap = !is_system_path(interp);
        if (wrap) out_argv[o++] = LINKER;
        out_argv[o++] = interp;
        if (interp_arg[0]) out_argv[o++] = interp_arg;
        out_argv[o++] = path;
        for (int i = 1; i < argc; i++) out_argv[o++] = argv[i];
        out_argv[o] = NULL;
        return wrap ? LINKER : interp;
    }
    return NULL;
}

int execve(const char *path, char *const argv[], char *const envp[]) {
    static execve_fn real;
    if (!real) real = (execve_fn)dlsym(RTLD_NEXT, "execve");
    const char *out_argv[MAX_ARGS];
    const char *target = rewrite(path, argv, out_argv);
    if (target && !target[0]) { errno = ENOENT; return -1; }
    if (target) return real(target, (char *const *)out_argv, envp);
    return real(path, argv, envp);
}

int execv(const char *path, char *const argv[]) { return execve(path, argv, environ); }

// Finds a program on PATH the way execvp does, so the rewrite above applies to it too.
static const char *search_path(const char *file, char *buf, size_t len) {
    if (strchr(file, '/')) return file;
    const char *path = getenv("PATH");
    if (!path) path = "/system/bin";
    while (*path) {
        const char *colon = strchr(path, ':');
        size_t dir_len = colon ? (size_t)(colon - path) : strlen(path);
        snprintf(buf, len, "%.*s/%s", (int)dir_len, path, file);
        if (access(buf, R_OK) == 0) return buf;
        if (!colon) break;
        path = colon + 1;
    }
    return file;
}

int execvpe(const char *file, char *const argv[], char *const envp[]) {
    char buf[PATH_MAX];
    return execve(search_path(file, buf, sizeof(buf)), argv, envp);
}

int execvp(const char *file, char *const argv[]) { return execvpe(file, argv, environ); }

static int spawn(const char *real_name, pid_t *pid, const char *path, const posix_spawn_file_actions_t *actions,
                 const posix_spawnattr_t *attr, char *const argv[], char *const envp[]) {
    spawn_fn real = (spawn_fn)dlsym(RTLD_NEXT, real_name);
    const char *out_argv[MAX_ARGS];
    const char *target = rewrite(path, argv, out_argv);
    if (target && !target[0]) return ENOENT;
    if (target) return real(pid, target, actions, attr, (char *const *)out_argv, envp);
    return real(pid, path, actions, attr, argv, envp);
}

int posix_spawn(pid_t *pid, const char *path, const posix_spawn_file_actions_t *actions,
                const posix_spawnattr_t *attr, char *const argv[], char *const envp[]) {
    return spawn("posix_spawn", pid, path, actions, attr, argv, envp);
}

int posix_spawnp(pid_t *pid, const char *file, const posix_spawn_file_actions_t *actions,
                 const posix_spawnattr_t *attr, char *const argv[], char *const envp[]) {
    char buf[PATH_MAX];
    const char *path = search_path(file, buf, sizeof(buf));
    return spawn(strchr(path, '/') ? "posix_spawn" : "posix_spawnp", pid, path, actions, attr, argv, envp);
}

// Wine's own program path, set by RetroForge, stands in for "/proc/self/exe" (the linker, under linker64).
static const char *self_exe(const char *path) {
    if (!path || strcmp(path, "/proc/self/exe") != 0) return NULL;
    const char *exe = getenv("PCPLAYER_SELF_EXE");
    return exe && exe[0] ? exe : NULL;
}

char *realpath(const char *path, char *resolved) {
    typedef char *(*fn)(const char *, char *);
    static fn real;
    if (!real) real = (fn)dlsym(RTLD_NEXT, "realpath");
    const char *exe = self_exe(path);
    return real(exe ? exe : path, resolved);
}

ssize_t readlink(const char *path, char *buf, size_t len) {
    typedef ssize_t (*fn)(const char *, char *, size_t);
    static fn real;
    if (!real) real = (fn)dlsym(RTLD_NEXT, "readlink");
    const char *exe = self_exe(path);
    if (!exe) return real(path, buf, len);
    size_t n = strlen(exe);
    if (n > len) n = len;
    memcpy(buf, exe, n);
    return (ssize_t)n;
}
