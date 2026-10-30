// lantest: what a Windows game's LAN mode does, for testing RetroForge's PC player networking.
// Lists the network adapters (GetAdaptersAddresses, GetAdaptersInfo, gethostbyname of this PC's name), then for a
// while broadcasts "hello" on UDP 47777 every second, prints every hello it hears, connects over TCP to each new
// sender's port 47778 and swaps a line, printing the addresses each side sees.
// Build: zig cc -target x86_64-windows-gnu -O2 lantest.c -o lantest.exe -lws2_32 -liphlpapi
// Run:   lantest.exe [seconds]   (default 30)
#include <winsock2.h>
#include <ws2tcpip.h>
#include <iphlpapi.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define UDP_PORT 47777
#define TCP_PORT 47778

static char name[256];
static ULONG broadcasts[16];
static int nbroadcasts;

static void list_adapters(void) {
    ULONG size = 0;
    printf("== GetAdaptersAddresses\n");
    fflush(stdout);
    DWORD r = GetAdaptersAddresses(AF_INET, 0, NULL, NULL, &size);
    IP_ADAPTER_ADDRESSES *aa = malloc(size ? size : 1);
    r = GetAdaptersAddresses(AF_INET, 0, NULL, aa, &size);
    if (r != NO_ERROR) printf("  failed: %lu\n", r);
    else
        for (IP_ADAPTER_ADDRESSES *a = aa; a; a = a->Next) {
            printf("  %ls type=%lu up=%d mac=", a->FriendlyName, a->IfType, a->OperStatus == IfOperStatusUp);
            for (ULONG i = 0; i < a->PhysicalAddressLength; i++) printf("%02x", a->PhysicalAddress[i]);
            printf("\n");
            for (IP_ADAPTER_UNICAST_ADDRESS *u = a->FirstUnicastAddress; u; u = u->Next) {
                struct sockaddr_in *in = (struct sockaddr_in *)u->Address.lpSockaddr;
                printf("    %s/%u\n", inet_ntoa(in->sin_addr), u->OnLinkPrefixLength);
                if (a->IfType != IF_TYPE_SOFTWARE_LOOPBACK && nbroadcasts < 16 && u->OnLinkPrefixLength < 32) {
                    ULONG mask = u->OnLinkPrefixLength ? htonl(0xFFFFFFFFu << (32 - u->OnLinkPrefixLength)) : 0;
                    broadcasts[nbroadcasts++] = in->sin_addr.s_addr | ~mask;
                }
            }
        }
    free(aa);

    printf("== GetAdaptersInfo\n");
    size = 0;
    GetAdaptersInfo(NULL, &size);
    IP_ADAPTER_INFO *ai = malloc(size ? size : 1);
    r = GetAdaptersInfo(ai, &size);
    if (r != NO_ERROR) printf("  failed: %lu\n", r);
    else
        for (IP_ADAPTER_INFO *a = ai; a; a = a->Next)
            printf("  %s %s/%s gw %s\n", a->Description, a->IpAddressList.IpAddress.String,
                   a->IpAddressList.IpMask.String, a->GatewayList.IpAddress.String);
    free(ai);

    gethostname(name, sizeof(name));
    printf("== gethostbyname(%s)\n", name);
    struct hostent *h = gethostbyname(name);
    if (!h) printf("  failed: %d\n", WSAGetLastError());
    else
        for (int i = 0; h->h_addr_list[i]; i++) printf("  %s\n", inet_ntoa(*(struct in_addr *)h->h_addr_list[i]));
    fflush(stdout);
}

static char *addr(const struct sockaddr_in *in) {
    static char buf[4][32];
    static int n;
    char *b = buf[n++ & 3];
    snprintf(b, 32, "%s:%u", inet_ntoa(in->sin_addr), ntohs(in->sin_port));
    return b;
}

int main(int argc, char **argv) {
    int seconds = argc > 1 ? atoi(argv[1]) : 30;
    WSADATA wsa;
    WSAStartup(MAKEWORD(2, 2), &wsa);
    setvbuf(stdout, NULL, _IONBF, 0);
    list_adapters();

    SOCKET udp = socket(AF_INET, SOCK_DGRAM, 0);
    BOOL yes = TRUE;
    setsockopt(udp, SOL_SOCKET, SO_BROADCAST, (char *)&yes, sizeof(yes));
    setsockopt(udp, SOL_SOCKET, SO_REUSEADDR, (char *)&yes, sizeof(yes));
    struct sockaddr_in any = {.sin_family = AF_INET, .sin_port = htons(UDP_PORT)};
    if (bind(udp, (struct sockaddr *)&any, sizeof(any))) printf("udp bind failed: %d\n", WSAGetLastError());
    SOCKET listener = socket(AF_INET, SOCK_STREAM, 0);
    any.sin_port = htons(TCP_PORT);
    if (bind(listener, (struct sockaddr *)&any, sizeof(any)) || listen(listener, 4))
        printf("tcp listen failed: %d\n", WSAGetLastError());
    u_long nb = 1;
    ioctlsocket(udp, FIONBIO, &nb);
    ioctlsocket(listener, FIONBIO, &nb);

    char hello[300];
    snprintf(hello, sizeof(hello), "hello %s %lu", name, GetCurrentProcessId());
    ULONG met[32];
    int nmet = 0;
    printf("== Listening %d s (UDP %d, TCP %d)\n", seconds, UDP_PORT, TCP_PORT);
    for (int tick = 0; tick < seconds * 10; tick++) {
        if (tick % 10 == 0) {
            struct sockaddr_in to = {.sin_family = AF_INET, .sin_port = htons(UDP_PORT)};
            to.sin_addr.s_addr = INADDR_BROADCAST;
            sendto(udp, hello, (int)strlen(hello), 0, (struct sockaddr *)&to, sizeof(to));
            for (int i = 0; i < nbroadcasts; i++) {
                to.sin_addr.s_addr = broadcasts[i];
                sendto(udp, hello, (int)strlen(hello), 0, (struct sockaddr *)&to, sizeof(to));
            }
        }
        char buf[512];
        struct sockaddr_in from;
        int flen = sizeof(from);
        int n;
        while ((n = recvfrom(udp, buf, sizeof(buf) - 1, 0, (struct sockaddr *)&from, &flen)) > 0) {
            buf[n] = 0;
            if (strcmp(buf, hello) == 0) continue;
            int seen = 0;
            for (int i = 0; i < nmet; i++) seen |= met[i] == from.sin_addr.s_addr;
            if (seen) continue;
            if (nmet < 32) met[nmet++] = from.sin_addr.s_addr;
            printf("UDP from %s: %s\n", addr(&from), buf);
            // Connect back over TCP, as a game joining a host would.
            SOCKET c = socket(AF_INET, SOCK_STREAM, 0);
            struct sockaddr_in to = from;
            to.sin_port = htons(TCP_PORT);
            if (connect(c, (struct sockaddr *)&to, sizeof(to)) == 0) {
                struct sockaddr_in me, peer;
                int l1 = sizeof(me), l2 = sizeof(peer);
                getsockname(c, (struct sockaddr *)&me, &l1);
                getpeername(c, (struct sockaddr *)&peer, &l2);
                char line[300];
                snprintf(line, sizeof(line), "tcp hi from %s", name);
                send(c, line, (int)strlen(line), 0);
                int got = recv(c, buf, sizeof(buf) - 1, 0);
                buf[got > 0 ? got : 0] = 0;
                printf("TCP to %s ok (me %s, peer %s): \"%s\"\n", addr(&to), addr(&me), addr(&peer), buf);
            } else {
                printf("TCP to %s failed: %d\n", addr(&to), WSAGetLastError());
            }
            closesocket(c);
            flen = sizeof(from);
        }
        SOCKET a = accept(listener, (struct sockaddr *)&from, &flen);
        if (a != INVALID_SOCKET) {
            u_long block = 0;
            ioctlsocket(a, FIONBIO, &block);
            int got = recv(a, buf, sizeof(buf) - 1, 0);
            buf[got > 0 ? got : 0] = 0;
            struct sockaddr_in me;
            int l1 = sizeof(me);
            getsockname(a, (struct sockaddr *)&me, &l1);
            printf("TCP from %s (me %s): \"%s\"\n", addr(&from), addr(&me), buf);
            char line[300];
            snprintf(line, sizeof(line), "tcp reply from %s", name);
            send(a, line, (int)strlen(line), 0);
            closesocket(a);
        }
        Sleep(100);
    }
    printf("== Done\n");
    return 0;
}
