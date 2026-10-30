// RetroForge PC player: networking for Windows games under Wine on Android. Part of libpcexec.so (preloaded into
// every Wine process).
//
// 1. Network interfaces. Android doesn't let apps make the netlink RTM_GETLINK query behind bionic's
//    if_nameindex(), so it returns NULL, and Wine's nsiproxy (GetAdaptersAddresses and everything built on it)
//    walks that NULL and its service dies: callers hang (gbe_fork's SteamAPI_Init did). /proc/net/route can't be
//    read either (no routes, so gethostbyname(own name) fails), and SIOCGIFHWADDR is refused (no MAC, so Wi-Fi
//    isn't seen as Ethernet). Each is answered from getifaddrs(), which still works, when the real call fails.
//
// 2. Play together over the internet: a virtual LAN. When the file named by $PCNET_FILE says so, Windows sees one
//    more network adapter ("rf0", 10.77.0.x/24, the others hidden), and game traffic to that network goes to
//    RetroForge's relay on 127.0.0.1 (PcLan.kt), which carries it to the other devices. On the way back the relay's
//    packets carry who really sent them, so the game sees 10.77.0.y as the sender:
//      UDP to 10.77.0.y / 10.77.0.255 / 255.255.255.255  ->  sent to the relay with a 12-byte header (where to);
//      UDP from the relay                                  ->  header removed, sender rewritten;
//      TCP connect to 10.77.0.y:port                       ->  relay told "local port L wants 10.77.0.y:port",
//                                                              then connected to the relay, which links it through;
//      accept/getpeername/getsockname of relayed sockets   ->  the relay says which virtual address stands behind
//                                                              each 127.0.0.1 port.
//    Wine's sockets go through these libc calls in two places: wineserver (bind, connect, accept, names) and each
//    Wine process (sendmsg/recvmsg), both of which have this library preloaded.
#define _GNU_SOURCE
#include <arpa/inet.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <net/if_arp.h>
#include <netinet/in.h>
#include <poll.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define VLAN_IFNAME "rf0"
#define VLAN_IFINDEX 4077
#define MAGIC "RFVL"
#define HDR 12

// The libc function this library stands in front of, as real_<name>.
#define REAL(name) \
    static __typeof__(&name) real_##name; \
    if (!real_##name) real_##name = (__typeof__(&name))dlsym(RTLD_NEXT, #name)

// ---- Virtual LAN settings ($PCNET_FILE: "vip=10.77.0.2", "port=47700", "hide=1"), re-read when they change ----

static struct {
    int on;
    uint32_t vip;    // this device's virtual address (network order)
    uint32_t net;    // 10.77.0.0
    uint32_t mask;   // 255.255.255.0
    uint16_t port;   // the relay's port on 127.0.0.1 (host order)
    int hide;        // leave the real adapters out of Windows' list
} vlan;
static pthread_mutex_t vlan_lock = PTHREAD_MUTEX_INITIALIZER;

static void vlan_refresh(void) {
    static time_t checked;
    static struct timespec mtime;
    static int had_file;
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    if (checked && now.tv_sec - checked < 1) return;
    pthread_mutex_lock(&vlan_lock);
    checked = now.tv_sec ? now.tv_sec : 1;
    const char *path = getenv("PCNET_FILE");
    struct stat st;
    if (!path || stat(path, &st) != 0) {
        if (had_file) memset(&vlan, 0, sizeof(vlan));
        had_file = 0;
        pthread_mutex_unlock(&vlan_lock);
        return;
    }
    if (had_file && st.st_mtim.tv_sec == mtime.tv_sec && st.st_mtim.tv_nsec == mtime.tv_nsec) {
        pthread_mutex_unlock(&vlan_lock);
        return;
    }
    had_file = 1;
    mtime = st.st_mtim;
    memset(&vlan, 0, sizeof(vlan));
    FILE *fp = fdopen(open(path, O_RDONLY | O_CLOEXEC), "r");
    if (fp) {
        char line[128], value[64];
        while (fgets(line, sizeof(line), fp)) {
            struct in_addr a;
            if (sscanf(line, "vip=%63s", value) == 1 && inet_pton(AF_INET, value, &a) == 1) vlan.vip = a.s_addr;
            else if (sscanf(line, "port=%63s", value) == 1) vlan.port = (uint16_t)atoi(value);
            else if (sscanf(line, "hide=%63s", value) == 1) vlan.hide = atoi(value);
        }
        fclose(fp);
    }
    vlan.mask = htonl(0xFFFFFF00u);
    vlan.net = vlan.vip & vlan.mask;
    vlan.on = vlan.vip && vlan.port;
    pthread_mutex_unlock(&vlan_lock);
}

static int vlan_active(void) {
    vlan_refresh();
    return vlan.on;
}

static uint32_t vlan_broadcast(void) { return vlan.net | ~vlan.mask; }

// Addresses that go through the relay: the virtual network (but not this device) and the all-networks broadcast.
static int vlan_routed(uint32_t addr) {
    if (addr == vlan.vip) return 0;
    return (addr & vlan.mask) == vlan.net || addr == htonl(INADDR_BROADCAST);
}

static int is_loopback_port(const struct sockaddr *sa, uint16_t port) {
    const struct sockaddr_in *in = (const struct sockaddr_in *)sa;
    return sa && sa->sa_family == AF_INET && in->sin_addr.s_addr == htonl(INADDR_LOOPBACK) &&
           (!port || ntohs(in->sin_port) == port);
}

// ---- Network interfaces ----

// One getifaddrs() entry in a single allocation, so bionic's freeifaddrs() (one free() per entry) frees it.
static struct ifaddrs *make_ifaddr(const char *name, uint32_t addr, uint32_t mask, uint32_t brd, unsigned flags) {
    struct block {
        struct ifaddrs ifa;
        struct sockaddr_in addr, mask, brd;
        char name[IFNAMSIZ];
    } *b = calloc(1, sizeof(*b));
    if (!b) return NULL;
    snprintf(b->name, sizeof(b->name), "%s", name);
    b->addr.sin_family = b->mask.sin_family = b->brd.sin_family = AF_INET;
    b->addr.sin_addr.s_addr = addr;
    b->mask.sin_addr.s_addr = mask;
    b->brd.sin_addr.s_addr = brd;
    b->ifa.ifa_name = b->name;
    b->ifa.ifa_flags = flags;
    b->ifa.ifa_addr = (struct sockaddr *)&b->addr;
    b->ifa.ifa_netmask = (struct sockaddr *)&b->mask;
    b->ifa.ifa_broadaddr = (struct sockaddr *)&b->brd;
    return &b->ifa;
}

int getifaddrs(struct ifaddrs **out) {
    REAL(getifaddrs);
    REAL(freeifaddrs);
    int ret = real_getifaddrs(out);
    if (ret != 0 || !vlan_active()) return ret;
    if (vlan.hide) {
        // Only the loopback adapter and the virtual one, so games advertise the virtual address.
        struct ifaddrs **link = out;
        while (*link) {
            struct ifaddrs *ifa = *link;
            if (!(ifa->ifa_flags & IFF_LOOPBACK)) {
                *link = ifa->ifa_next;
                ifa->ifa_next = NULL;
                real_freeifaddrs(ifa);
            } else {
                link = &ifa->ifa_next;
            }
        }
    }
    struct ifaddrs *rf = make_ifaddr(VLAN_IFNAME, vlan.vip, vlan.mask, vlan_broadcast(),
                                     IFF_UP | IFF_BROADCAST | IFF_RUNNING | IFF_MULTICAST);
    if (rf) {
        rf->ifa_next = *out;
        *out = rf;
    }
    return 0;
}

unsigned if_nametoindex(const char *name) {
    REAL(if_nametoindex);
    if (name && strcmp(name, VLAN_IFNAME) == 0 && vlan_active()) return VLAN_IFINDEX;
    return real_if_nametoindex(name);
}

char *if_indextoname(unsigned index, char *name) {
    REAL(if_indextoname);
    if (index == VLAN_IFINDEX && vlan_active()) return strcpy(name, VLAN_IFNAME);
    return real_if_indextoname(index, name);
}

// Marks a list made below: its end entry's name (bionic's is NULL).
static char our_list[] = "";

// The interface list built from getifaddrs() (one entry per name), for when bionic's netlink query is refused.
struct if_nameindex *if_nameindex(void) {
    REAL(if_nameindex);
    struct if_nameindex *real = vlan_active() ? NULL : real_if_nameindex();
    if (real) return real;
    struct ifaddrs *addrs, *ifa;
    if (getifaddrs(&addrs) != 0) addrs = NULL;
    int count = 0;
    for (ifa = addrs; ifa; ifa = ifa->ifa_next) count++;
    // Entries and names in one block, freed by if_freenameindex() below.
    struct if_nameindex *list = calloc(count + 1, sizeof(*list) + IFNAMSIZ);
    if (!list) {
        freeifaddrs(addrs);
        return NULL;
    }
    char *names = (char *)(list + count + 1);
    int n = 0;
    for (ifa = addrs; ifa; ifa = ifa->ifa_next) {
        int seen = 0;
        for (int i = 0; i < n && !seen; i++) seen = strcmp(list[i].if_name, ifa->ifa_name) == 0;
        unsigned index = seen ? 0 : if_nametoindex(ifa->ifa_name);
        if (!index) continue;
        list[n].if_index = index;
        list[n].if_name = names + n * IFNAMSIZ;
        snprintf(list[n].if_name, IFNAMSIZ, "%s", ifa->ifa_name);
        n++;
    }
    list[n].if_name = our_list;
    freeifaddrs(addrs);
    return list;
}

void if_freenameindex(struct if_nameindex *list) {
    REAL(if_freenameindex);
    // Ours is one block (names included); bionic's names are freed one by one.
    if (!list) return;
    int count = 0;
    while (list[count].if_index) count++;
    if (list[count].if_name == our_list) free(list);
    else real_if_freenameindex(list);
}

// A made-up MAC address for an adapter whose real one Android won't give: locally administered, stable for the
// adapter's name and the device ($PCNET_MACSEED, from the app), different between devices.
static void fake_mac(const char *name, unsigned char mac[6]) {
    uint32_t h = 2166136261u;
    const char *seed = getenv("PCNET_MACSEED");
    for (const char *p = seed ? seed : ""; *p; p++) h = (h ^ (unsigned char)*p) * 16777619u;
    for (const char *p = name; *p; p++) h = (h ^ (unsigned char)*p) * 16777619u;
    mac[0] = 0x02;
    mac[1] = 0x52;
    mac[2] = h >> 24;
    mac[3] = h >> 16;
    mac[4] = h >> 8;
    mac[5] = h;
}

static void set_in(struct sockaddr *sa, uint32_t addr) {
    struct sockaddr_in *in = (struct sockaddr_in *)sa;
    memset(in, 0, sizeof(*in));
    in->sin_family = AF_INET;
    in->sin_addr.s_addr = addr;
}

int ioctl(int fd, int request, ...) {
    // (bionic's ioctl is overloaded, so it's looked up by hand.)
    static int (*real_ioctl)(int, int, ...);
    if (!real_ioctl) real_ioctl = (int (*)(int, int, ...))dlsym(RTLD_NEXT, "ioctl");
    va_list ap;
    va_start(ap, request);
    void *arg = va_arg(ap, void *);
    va_end(ap);
    struct ifreq *ifr = arg;
    unsigned req = (unsigned)request;
    int ifreq_call = req == SIOCGIFHWADDR || req == SIOCGIFFLAGS || req == SIOCGIFMTU || req == SIOCGIFINDEX ||
                     req == SIOCGIFADDR || req == SIOCGIFNETMASK || req == SIOCGIFBRDADDR;
    if (ifreq_call && ifr && strncmp(ifr->ifr_name, VLAN_IFNAME, IFNAMSIZ) == 0 && vlan_active()) {
        switch (req) {
        case SIOCGIFHWADDR:
            memset(&ifr->ifr_hwaddr, 0, sizeof(ifr->ifr_hwaddr));
            ifr->ifr_hwaddr.sa_family = ARPHRD_ETHER;
            fake_mac(VLAN_IFNAME, (unsigned char *)ifr->ifr_hwaddr.sa_data);
            return 0;
        case SIOCGIFFLAGS: ifr->ifr_flags = IFF_UP | IFF_BROADCAST | IFF_RUNNING | IFF_MULTICAST; return 0;
        case SIOCGIFMTU: ifr->ifr_mtu = 1400; return 0;
        case SIOCGIFINDEX: ifr->ifr_ifindex = VLAN_IFINDEX; return 0;
        case SIOCGIFADDR: set_in(&ifr->ifr_addr, vlan.vip); return 0;
        case SIOCGIFNETMASK: set_in(&ifr->ifr_netmask, vlan.mask); return 0;
        case SIOCGIFBRDADDR: set_in(&ifr->ifr_broadaddr, vlan_broadcast()); return 0;
        }
    }
    int ret = real_ioctl(fd, request, arg);
    if (ret != 0 && req == SIOCGIFHWADDR && ifr) {
        memset(&ifr->ifr_hwaddr, 0, sizeof(ifr->ifr_hwaddr));
        if (strcmp(ifr->ifr_name, "lo") == 0) {
            ifr->ifr_hwaddr.sa_family = ARPHRD_LOOPBACK;
        } else {
            ifr->ifr_hwaddr.sa_family = ARPHRD_ETHER;
            fake_mac(ifr->ifr_name, (unsigned char *)ifr->ifr_hwaddr.sa_data);
        }
        errno = 0;
        return 0;
    }
    return ret;
}

// /proc/net/route as Linux writes it, made from getifaddrs(): a route to each adapter's own network, and the default
// route on the first adapter that isn't loopback (Android hides the gateway; Wine only needs the adapter).
static FILE *fake_route_table(void) {
    char *text = NULL;
    size_t size = 0;
    FILE *out = open_memstream(&text, &size);
    if (!out) return NULL;
    fprintf(out, "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n");
    struct ifaddrs *addrs, *ifa;
    int have_default = 0;
    if (getifaddrs(&addrs) == 0) {
        for (ifa = addrs; ifa; ifa = ifa->ifa_next) {
            if (!ifa->ifa_addr || ifa->ifa_addr->sa_family != AF_INET || (ifa->ifa_flags & IFF_LOOPBACK)) continue;
            uint32_t addr = ((struct sockaddr_in *)ifa->ifa_addr)->sin_addr.s_addr;
            uint32_t mask = ifa->ifa_netmask ? ((struct sockaddr_in *)ifa->ifa_netmask)->sin_addr.s_addr : 0xFFFFFFFFu;
            if (!have_default) {
                fprintf(out, "%s\t00000000\t00000000\t0003\t0\t0\t0\t00000000\t0\t0\t0\n", ifa->ifa_name);
                have_default = 1;
            }
            fprintf(out, "%s\t%08X\t00000000\t0001\t0\t0\t0\t%08X\t0\t0\t0\n", ifa->ifa_name, addr & mask, mask);
        }
        freeifaddrs(addrs);
    }
    fclose(out);
    FILE *fp = fmemopen(NULL, size + 1, "w+");
    if (fp) {
        fwrite(text, 1, size, fp);
        rewind(fp);
    }
    free(text);
    return fp;
}

static FILE *open_file(const char *real_name, const char *path, const char *mode) {
    typedef FILE *(*fn)(const char *, const char *);
    fn real = (fn)dlsym(RTLD_NEXT, real_name);
    int route = path && strcmp(path, "/proc/net/route") == 0;
    // With the virtual LAN on, the routes must match the adapters Windows is shown.
    if (route && vlan_active()) return fake_route_table();
    FILE *fp = real(path, mode);
    if (!fp && route) fp = fake_route_table();
    return fp;
}

FILE *fopen(const char *path, const char *mode) { return open_file("fopen", path, mode); }
FILE *fopen64(const char *path, const char *mode) { return open_file("fopen64", path, mode); }

// ---- The relay: questions over a private UDP socket ----

// Asks the relay one question; fills `answer` (up to `len`) and returns its length, or -1 when it doesn't answer.
static int ask_relay(const unsigned char *question, int qlen, unsigned char *answer, int len) {
    REAL(socket);
    REAL(sendto);
    REAL(recvfrom);
    int fd = real_socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;
    struct sockaddr_in relay = {.sin_family = AF_INET, .sin_port = htons(vlan.port),
                                .sin_addr.s_addr = htonl(INADDR_LOOPBACK)};
    int got = -1;
    for (int attempt = 0; attempt < 3 && got < 0; attempt++) {
        if (real_sendto(fd, question, qlen, 0, (struct sockaddr *)&relay, sizeof(relay)) != qlen) break;
        struct pollfd p = {.fd = fd, .events = POLLIN};
        if (poll(&p, 1, 150) == 1) got = (int)real_recvfrom(fd, answer, len, 0, NULL, NULL);
    }
    close(fd);
    return got;
}

static void put16(unsigned char *p, uint16_t v) { p[0] = v >> 8; p[1] = v; }
static uint16_t get16(const unsigned char *p) { return (uint16_t)(p[0] << 8 | p[1]); }

static int socket_type(int fd) {
    int type = 0;
    socklen_t len = sizeof(type);
    return getsockopt(fd, SOL_SOCKET, SO_TYPE, &type, &len) == 0 ? type : 0;
}

static uint16_t local_port(int fd) {
    REAL(getsockname);
    struct sockaddr_in in;
    socklen_t len = sizeof(in);
    if (real_getsockname(fd, (struct sockaddr *)&in, &len) != 0 || in.sin_family != AF_INET) return 0;
    return ntohs(in.sin_port);
}

// "Local port `port` (TCP or UDP) is linked to dst:dport": the relay's answer to 'O' (our own socket that was
// connected through it) or 'I' (a connection the relay made to us on behalf of another device).
static int relay_lookup(char kind, int tcp, uint16_t port, struct sockaddr_in *out) {
    unsigned char q[8] = {'R', 'F', 'V', 'L', (unsigned char)kind, tcp ? 'T' : 'U'}, a[16];
    put16(q + 6, port);
    int n = ask_relay(q, sizeof(q), a, sizeof(a));
    if (n < 12 || memcmp(a, MAGIC, 4) != 0 || a[4] != 'A') return 0;
    memset(out, 0, sizeof(*out));
    out->sin_family = AF_INET;
    memcpy(&out->sin_addr.s_addr, a + 6, 4);
    out->sin_port = htons(get16(a + 10));
    return 1;
}

// Rewrites a 127.0.0.1 address the game is about to see into the virtual address behind it, if there is one.
static void unrelay_peer(int fd, struct sockaddr *addr, socklen_t *len) {
    if (!addr || !len || *len < sizeof(struct sockaddr_in) || !is_loopback_port(addr, 0) || !vlan_active()) return;
    struct sockaddr_in *in = (struct sockaddr_in *)addr, found;
    int tcp = socket_type(fd) == SOCK_STREAM;
    int ok = ntohs(in->sin_port) == vlan.port ? relay_lookup('O', tcp, local_port(fd), &found)
                                               : tcp && relay_lookup('I', tcp, ntohs(in->sin_port), &found);
    if (ok) {
        in->sin_addr = found.sin_addr;
        in->sin_port = found.sin_port;
    }
}

// ---- Socket calls ----

int bind(int fd, const struct sockaddr *addr, socklen_t len) {
    REAL(bind);
    if (addr && addr->sa_family == AF_INET && len >= sizeof(struct sockaddr_in) && vlan_active()) {
        struct sockaddr_in in = *(const struct sockaddr_in *)addr;
        // The virtual address (or its broadcast) isn't a real one here: listen on every address instead.
        if (in.sin_addr.s_addr == vlan.vip || in.sin_addr.s_addr == vlan_broadcast()) {
            in.sin_addr.s_addr = htonl(INADDR_ANY);
            return real_bind(fd, (struct sockaddr *)&in, sizeof(in));
        }
    }
    return real_bind(fd, addr, len);
}

int connect(int fd, const struct sockaddr *addr, socklen_t len) {
    REAL(connect);
    REAL(bind);
    if (!addr || addr->sa_family != AF_INET || len < sizeof(struct sockaddr_in) || !vlan_active())
        return real_connect(fd, addr, len);
    const struct sockaddr_in *dst = (const struct sockaddr_in *)addr;
    struct sockaddr_in to = *dst;
    if (dst->sin_addr.s_addr == vlan.vip) {
        to.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        return real_connect(fd, (struct sockaddr *)&to, sizeof(to));
    }
    if (!vlan_routed(dst->sin_addr.s_addr) || dst->sin_addr.s_addr == htonl(INADDR_BROADCAST))
        return real_connect(fd, addr, len);
    int type = socket_type(fd);
    if (type != SOCK_STREAM && type != SOCK_DGRAM) return real_connect(fd, addr, len);
    uint16_t port = local_port(fd);
    if (!port) {
        struct sockaddr_in any = {.sin_family = AF_INET};
        real_bind(fd, (struct sockaddr *)&any, sizeof(any));
        port = local_port(fd);
    }
    // 'C': "my local port links to dst" — then the connection goes to the relay.
    unsigned char q[16] = {'R', 'F', 'V', 'L', 'C', type == SOCK_STREAM ? 'T' : 'U'}, a[16];
    memcpy(q + 6, &dst->sin_addr.s_addr, 4);
    put16(q + 10, ntohs(dst->sin_port));
    put16(q + 12, port);
    if (!port || ask_relay(q, 14, a, sizeof(a)) < 5 || a[4] != 'A') {
        errno = ENETUNREACH;
        return -1;
    }
    to.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    to.sin_port = htons(vlan.port);
    return real_connect(fd, (struct sockaddr *)&to, sizeof(to));
}

ssize_t sendmsg(int fd, const struct msghdr *msg, int flags) {
    REAL(sendmsg);
    const struct sockaddr_in *dst = msg ? msg->msg_name : NULL;
    if (!dst || msg->msg_namelen < sizeof(*dst) || dst->sin_family != AF_INET || !vlan_active())
        return real_sendmsg(fd, msg, flags);
    struct msghdr m = *msg;
    struct sockaddr_in to = *dst;
    if (dst->sin_addr.s_addr == vlan.vip) {
        to.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        m.msg_name = &to;
        m.msg_namelen = sizeof(to);
        return real_sendmsg(fd, &m, flags);
    }
    if (!vlan_routed(dst->sin_addr.s_addr) || socket_type(fd) != SOCK_DGRAM) return real_sendmsg(fd, msg, flags);
    // To the relay, with a header saying where it was going.
    unsigned char hdr[HDR] = {'R', 'F', 'V', 'L', 'D', 0};
    memcpy(hdr + 6, &dst->sin_addr.s_addr, 4);
    put16(hdr + 10, ntohs(dst->sin_port));
    struct iovec iov[64];
    if (m.msg_iovlen + 1 > 64) {
        errno = EMSGSIZE;
        return -1;
    }
    iov[0].iov_base = hdr;
    iov[0].iov_len = HDR;
    memcpy(iov + 1, m.msg_iov, m.msg_iovlen * sizeof(struct iovec));
    m.msg_iov = iov;
    m.msg_iovlen++;
    to.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    to.sin_port = htons(vlan.port);
    m.msg_name = &to;
    m.msg_namelen = sizeof(to);
    ssize_t n = real_sendmsg(fd, &m, flags);
    return n >= HDR ? n - HDR : (n < 0 ? n : 0);
}

ssize_t sendto(int fd, const void *buf, size_t len, int flags, const struct sockaddr *addr, socklen_t alen) {
    REAL(sendto);
    if (!addr || !vlan_active()) return real_sendto(fd, buf, len, flags, addr, alen);
    struct iovec iov = {.iov_base = (void *)buf, .iov_len = len};
    struct msghdr m = {.msg_name = (void *)addr, .msg_namelen = alen, .msg_iov = &iov, .msg_iovlen = 1};
    return sendmsg(fd, &m, flags);
}

ssize_t recvmsg(int fd, struct msghdr *msg, int flags) {
    REAL(recvmsg);
    REAL(recvfrom);
    if (!msg || !vlan_active() || socket_type(fd) != SOCK_DGRAM) return real_recvmsg(fd, msg, flags);
    // A look at the next packet: is it from the relay?
    unsigned char hdr[HDR];
    struct sockaddr_in from;
    socklen_t flen = sizeof(from);
    ssize_t peek = real_recvfrom(fd, hdr, HDR, (flags & MSG_DONTWAIT) | MSG_PEEK, (struct sockaddr *)&from, &flen);
    if (peek < HDR || !is_loopback_port((struct sockaddr *)&from, vlan.port) || memcmp(hdr, MAGIC, 4) != 0 ||
        hdr[4] != 'D')
        return real_recvmsg(fd, msg, flags);
    struct msghdr m = *msg;
    struct iovec iov[64];
    if (m.msg_iovlen + 1 > 64) return real_recvmsg(fd, msg, flags);
    iov[0].iov_base = hdr;
    iov[0].iov_len = HDR;
    memcpy(iov + 1, m.msg_iov, m.msg_iovlen * sizeof(struct iovec));
    m.msg_iov = iov;
    m.msg_iovlen++;
    m.msg_name = &from;
    m.msg_namelen = sizeof(from);
    ssize_t n = real_recvmsg(fd, &m, flags);
    if (n < 0) return n;
    msg->msg_flags = m.msg_flags;
    msg->msg_controllen = m.msg_controllen;
    if (n < HDR || memcmp(hdr, MAGIC, 4) != 0) return 0;  // raced with another reader; drop it
    // Who really sent it.
    if (msg->msg_name && msg->msg_namelen >= sizeof(struct sockaddr_in)) {
        struct sockaddr_in *src = msg->msg_name;
        memset(src, 0, sizeof(*src));
        src->sin_family = AF_INET;
        memcpy(&src->sin_addr.s_addr, hdr + 6, 4);
        src->sin_port = htons(get16(hdr + 10));
        msg->msg_namelen = sizeof(*src);
    }
    return n - HDR;
}

ssize_t recvfrom(int fd, void *buf, size_t len, int flags, struct sockaddr *addr, socklen_t *alen) {
    REAL(recvfrom);
    if (!vlan_active()) return real_recvfrom(fd, buf, len, flags, addr, alen);
    struct sockaddr_in from;
    struct iovec iov = {.iov_base = buf, .iov_len = len};
    struct msghdr m = {.msg_name = addr ? addr : (struct sockaddr *)&from,
                       .msg_namelen = addr && alen ? *alen : sizeof(from), .msg_iov = &iov, .msg_iovlen = 1};
    ssize_t n = recvmsg(fd, &m, flags);
    if (n >= 0 && addr && alen) *alen = m.msg_namelen;
    return n;
}

ssize_t send(int fd, const void *buf, size_t len, int flags) { return sendto(fd, buf, len, flags, NULL, 0); }
ssize_t recv(int fd, void *buf, size_t len, int flags) { return recvfrom(fd, buf, len, flags, NULL, NULL); }

int accept4(int fd, struct sockaddr *addr, socklen_t *len, int flags) {
    REAL(accept4);
    int s = real_accept4(fd, addr, len, flags);
    if (s >= 0) unrelay_peer(s, addr, len);
    return s;
}

int accept(int fd, struct sockaddr *addr, socklen_t *len) { return accept4(fd, addr, len, 0); }

int getpeername(int fd, struct sockaddr *addr, socklen_t *len) {
    REAL(getpeername);
    int ret = real_getpeername(fd, addr, len);
    if (ret == 0) unrelay_peer(fd, addr, len);
    return ret;
}

// A socket linked through the relay is on the virtual network: its own address is this device's virtual one.
int getsockname(int fd, struct sockaddr *addr, socklen_t *len) {
    REAL(getsockname);
    REAL(getpeername);
    int ret = real_getsockname(fd, addr, len);
    if (ret != 0 || !addr || !len || !is_loopback_port(addr, 0) || !vlan_active()) return ret;
    struct sockaddr_in peer, found;
    socklen_t plen = sizeof(peer);
    if (real_getpeername(fd, (struct sockaddr *)&peer, &plen) != 0 || !is_loopback_port((struct sockaddr *)&peer, 0))
        return ret;
    int tcp = socket_type(fd) == SOCK_STREAM;
    if (ntohs(peer.sin_port) == vlan.port || (tcp && relay_lookup('I', tcp, ntohs(peer.sin_port), &found)))
        ((struct sockaddr_in *)addr)->sin_addr.s_addr = vlan.vip;
    return ret;
}
