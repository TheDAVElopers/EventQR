package com.thedavelopers.eventqr.shared.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP for rate limiting and auditing.
 *
 * <p>Resolution order:
 * <ol>
 *   <li><b>A trusted single-value header</b>, when one is configured
 *       ({@code app.client-ip.trusted-header}, {@code CF-Connecting-IP} on Render). Render's edge
 *       (Cloudflare) writes that header on every request and overwrites whatever the caller
 *       sent, so it is accurate and the client cannot forge it. Only use this where the platform
 *       really guarantees that; on a host without such an edge a caller could set it themselves,
 *       so it can be turned off by setting the property empty.</li>
 *   <li>The <b>rightmost</b> valid entry of {@code X-Forwarded-For}. A client can spoof its own
 *       value, and every hop appends on the right, so the leftmost entries must never be
 *       trusted; the rightmost is the peer seen by the outermost proxy.</li>
 *   <li>{@code getRemoteAddr()} when there is no valid forwarded address (e.g. local
 *       development).</li>
 * </ol>
 *
 * <p>Only well-formed IPv4/IPv6 literals are accepted at every step, so a spoofed header value
 * cannot produce an arbitrary rate-limit bucket key.
 *
 * <p>This replaces the need for {@code server.tomcat.remoteip.internal-proxies}: Render does not
 * publish its inbound proxy ranges, so there is no list to configure.
 */
public final class ClientIp {

    private static final int MAX_XFF_LENGTH = 512;
    private static final int MAX_LITERAL_LENGTH = 45;
    /** Sentinel returned when no valid client IP literal can be resolved. */
    public static final String UNKNOWN = "unknown";

    /** Name of the header the platform guarantees to be the real client IP; null/blank = not used. */
    private static volatile String trustedHeader;

    private ClientIp() {
    }

    /** Set once at startup from {@code app.client-ip.trusted-header}; blank disables it. */
    public static void configureTrustedHeader(String headerName) {
        trustedHeader = (headerName == null || headerName.isBlank()) ? null : headerName.trim();
    }

    /**
     * Returns the canonical text of the resolved client IP (IPv4 dotted-decimal, IPv4-mapped IPv6 folded to
     * IPv4, lower-case RFC 5952 compressed IPv6) so every spelling of one address shares a rate-limit bucket,
     * or {@value #UNKNOWN} when no valid literal is available.
     */
    public static String from(HttpServletRequest request) {
        String header = trustedHeader;
        if (header != null) {
            String value = request.getHeader(header);
            String canonical = value == null ? null : canonicalize(value.trim());
            if (canonical != null) {
                return canonical;
            }
        }
        // Rightmost entry is only trustworthy when exactly one trusted proxy appends to X-Forwarded-For.
        String candidate = rightmostValidAddress(request.getHeader("X-Forwarded-For"));
        if (candidate != null) {
            return candidate;
        }
        String canonicalRemote = canonicalize(request.getRemoteAddr());
        return canonicalRemote != null ? canonicalRemote : UNKNOWN;
    }

    /** Canonical text of a valid IP literal, or {@code null} when the value is not one. */
    public static String canonicalize(String value) {
        byte[] bytes = parseLiteral(value);
        if (bytes == null) {
            return null;
        }
        if (bytes.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= bytes[i] == 0;
            }
            if (mapped && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                bytes = java.util.Arrays.copyOfRange(bytes, 12, 16);
            }
        }
        if (bytes.length == 4) {
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + "." + (bytes[3] & 0xff);
        }
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
        }
        int bestStart = -1;
        int bestLen = 0;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && groups[j] == 0) {
                j++;
            }
            if (j - i > bestLen) {
                bestStart = i;
                bestLen = j - i;
            }
            i = j;
        }
        if (bestLen < 2) {
            bestStart = -1;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLen - 1;
                continue;
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ':') {
                sb.append(':');
            }
            sb.append(Integer.toHexString(groups[i]));
        }
        return sb.toString();
    }

    /**
     * Returns the last syntactically valid IP in the given X-Forwarded-For list, or
     * {@code null} if there is none. The rightmost entry is the peer of the outermost
     * trusted proxy (Render) and thus the real client; earlier entries may be spoofed by
     * the client itself and are intentionally ignored.
     */
    private static String rightmostValidAddress(String xForwardedFor) {
        if (xForwardedFor == null || xForwardedFor.isBlank() || xForwardedFor.length() > MAX_XFF_LENGTH) {
            return null;
        }
        String[] parts = xForwardedFor.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            String canonical = canonicalize(candidate);
            if (canonical != null) {
                return canonical;
            }
        }
        return null;
    }

    /**
     * Strictly parses an IPv4 or IPv6 <em>literal</em> into its raw bytes (4 or 16), or returns
     * {@code null} for anything else (hostnames, zone ids, garbage, over-long input). No DNS lookup
     * is ever performed, so the result is safe to feed to {@code InetAddress.getByAddress}.
     */
    public static byte[] parseLiteral(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LITERAL_LENGTH) {
            return null;
        }
        return value.indexOf(':') >= 0 ? parseIpv6(value) : parseIpv4(value);
    }

    private static byte[] parseIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String octet = octets[i];
            // Leading zeros are rejected: legacy parsers read "010" as octal, so the spelling is ambiguous.
            if (octet.isEmpty() || octet.length() > 3 || (octet.length() > 1 && octet.charAt(0) == '0')) {
                return null;
            }
            int parsed = 0;
            for (int j = 0; j < octet.length(); j++) {
                char c = octet.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                parsed = parsed * 10 + (c - '0');
            }
            if (parsed > 255) {
                return null;
            }
            out[i] = (byte) parsed;
        }
        return out;
    }

    private static byte[] parseIpv6(String value) {
        int compression = value.indexOf("::");
        if (compression >= 0 && value.indexOf("::", compression + 1) >= 0) {
            return null;
        }
        int[] head = parseGroups(compression >= 0 ? value.substring(0, compression) : value, compression < 0);
        int[] tail = compression >= 0 ? parseGroups(value.substring(compression + 2), true) : new int[0];
        if (head == null || tail == null) {
            return null;
        }
        int total = head.length + tail.length;
        if (compression < 0 ? total != 8 : total > 7) {
            return null;
        }
        int[] groups = new int[8];
        System.arraycopy(head, 0, groups, 0, head.length);
        System.arraycopy(tail, 0, groups, 8 - tail.length, tail.length);
        byte[] out = new byte[16];
        for (int i = 0; i < 8; i++) {
            out[i * 2] = (byte) (groups[i] >> 8);
            out[i * 2 + 1] = (byte) groups[i];
        }
        return out;
    }

    /** Parses colon-separated hex groups; the last token may be a dotted IPv4 when {@code allowV4Tail}. */
    private static int[] parseGroups(String part, boolean allowV4Tail) {
        if (part.isEmpty()) {
            return new int[0];
        }
        String[] tokens = part.split(":", -1);
        java.util.ArrayList<Integer> groups = new java.util.ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.indexOf('.') >= 0) {
                byte[] v4 = (allowV4Tail && i == tokens.length - 1) ? parseIpv4(token) : null;
                if (v4 == null) {
                    return null;
                }
                groups.add(((v4[0] & 0xff) << 8) | (v4[1] & 0xff));
                groups.add(((v4[2] & 0xff) << 8) | (v4[3] & 0xff));
                continue;
            }
            if (token.isEmpty() || token.length() > 4) {
                return null;
            }
            int parsed = 0;
            for (int j = 0; j < token.length(); j++) {
                int digit = Character.digit(token.charAt(j), 16);
                if (digit < 0 || token.charAt(j) > 'f') {
                    return null;
                }
                parsed = parsed * 16 + digit;
            }
            groups.add(parsed);
        }
        return groups.stream().mapToInt(Integer::intValue).toArray();
    }
}
