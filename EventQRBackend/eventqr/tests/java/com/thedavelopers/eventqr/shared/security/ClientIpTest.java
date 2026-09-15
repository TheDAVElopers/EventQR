package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientIpTest {

    @Mock
    private HttpServletRequest request;

    private void mock(String xff, String remoteAddr) {
        // lenient: getRemoteAddr() is only exercised on the fallback path.
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(xff);
        lenient().when(request.getRemoteAddr()).thenReturn(remoteAddr);
    }

    @Test
    void singleIpv4() {
        mock("203.0.113.7", "10.0.0.1");
        assertThat(ClientIp.from(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void ipv4ListReturnsRightmostValidEntry() {
        // Leftmost entries are client-spoofable; the rightmost is appended by the
        // outermost trusted proxy and is the real client.
        mock("1.2.3.4, 5.6.7.8, 198.51.100.42", "10.0.0.1");
        assertThat(ClientIp.from(request)).isEqualTo("198.51.100.42");
    }

    @Test
    void ignoresInvalidEntriesAndReturnsRightmostValid() {
        mock("999.999.999.999, not-an-ip, 10.0.0.9", "10.0.0.1");
        assertThat(ClientIp.from(request)).isEqualTo("10.0.0.9");
    }

    @Test
    void ipv6Valid() {
        mock("2001:db8::1", "10.0.0.1");
        assertThat(ClientIp.from(request)).isEqualTo("2001:db8::1");
    }

    @Test
    void ipv6InvalidFallsThroughToRemoteAddr() {
        mock(":::invalid:::zz", "10.0.0.1");
        assertThat(ClientIp.from(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void oversizedXffRejected() {
        String big = "";
        for (int i = 0; i < 600; i++) {
            big += "1.1.1.1,";
        }
        mock(big, "10.0.0.1");
        assertThat(ClientIp.from(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void blankXffFallsBackToRemoteAddr() {
        mock("   ", "198.51.100.5");
        assertThat(ClientIp.from(request)).isEqualTo("198.51.100.5");
    }

    @Test
    void nullRemoteAddrReturnsUnknown() {
        mock("", null);
        assertThat(ClientIp.from(request)).isEqualTo("unknown");
    }
}
