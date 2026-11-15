package com.shopapp.webhook.delivery;

import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Which addresses a webhook may be delivered to: public unicast only (decisions.md 5-33). Refused: "this network"
 * 0/8 (incl. 0.0.0.0), loopback, RFC 1918 private, link-local (incl. the 169.254.169.254 metadata service), CGNAT
 * 100.64/10, IETF/test/benchmark ranges, multicast, reserved and broadcast; for IPv6 the unspecified and loopback
 * addresses, unique-local fc00::/7, link- and site-local, multicast, documentation, discard, Teredo, and every
 * form that embeds an IPv4 address (mapped, compatible, SIIT, NAT64 incl. local-use 64:ff9b:1::/48, 6to4) unless
 * that IPv4 address is public.
 */
final class AddressPolicy {

    private AddressPolicy() {
    }

    static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            return isPublicV6(bytes);
        }
        return isPublicV4(bytes, 0);
    }

    private static boolean isPublicV4(byte[] bytes, int offset) {
        int a = bytes[offset] & 0xff;
        int b = bytes[offset + 1] & 0xff;
        int c = bytes[offset + 2] & 0xff;
        boolean refused = a == 0                                  // 0.0.0.0/8
            || a == 10                                            // 10/8
            || a == 127                                           // loopback
            || a == 100 && (b & 0xc0) == 64                       // 100.64/10 CGNAT
            || a == 169 && b == 254                               // link-local, cloud metadata
            || a == 172 && (b & 0xf0) == 16                       // 172.16/12
            || a == 192 && b == 168                               // 192.168/16
            || a == 192 && b == 0 && (c == 0 || c == 2)           // IETF protocol assignments, TEST-NET-1
            || a == 198 && (b == 18 || b == 19)                   // benchmarking
            || a == 198 && b == 51 && c == 100                    // TEST-NET-2
            || a == 203 && b == 0 && c == 113                     // TEST-NET-3
            || a >= 224;                                          // multicast, reserved, broadcast
        return !refused;
    }

    private static boolean isPublicV6(byte[] b) {
        if (allZero(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
            return isPublicV4(b, 12);                             // ::ffff:a.b.c.d mapped
        }
        if (allZero(b, 0, 12)) {
            return false;                                         // ::, ::1, ::a.b.c.d compatible
        }
        if (allZero(b, 0, 8) && (b[8] & 0xff) == 0xff && (b[9] & 0xff) == 0xff && allZero(b, 10, 12)) {
            return isPublicV4(b, 12);                             // ::ffff:0:a.b.c.d SIIT (5-69)
        }
        if (b[0] == 0 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b && allZero(b, 4, 12)) {
            return isPublicV4(b, 12);                             // 64:ff9b::/96 NAT64
        }
        if (b[0] == 0 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b && b[4] == 0 && b[5] == 1) {
            // 64:ff9b:1::/48 local-use NAT64 (RFC 8215): the operator picks the prefix length, so only the /96 form
            // with a public IPv4 address is allowed; any other form is refused (5-69)
            return allZero(b, 6, 12) && isPublicV4(b, 12);
        }
        if (b[0] == 0x20 && b[1] == 0x02) {
            return isPublicV4(b, 2);                              // 2002::/16 6to4
        }
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        boolean refused = (first & 0xfe) == 0xfc                  // fc00::/7 unique local
            || first == 0xfe && (second & 0xc0) == 0x80           // fe80::/10 link-local
            || first == 0xfe && (second & 0xc0) == 0xc0           // fec0::/10 site-local
            || first == 0xff                                      // multicast
            || first == 0x20 && second == 0x01 && b[2] == 0 && b[3] == 0   // 2001::/32 Teredo
            || first == 0x20 && second == 0x01 && b[2] == 0x0d && (b[3] & 0xff) == 0xb8 // documentation
            || first == 0x01 && second == 0 && allZero(b, 2, 8);  // 100::/64 discard
        return !refused;
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
