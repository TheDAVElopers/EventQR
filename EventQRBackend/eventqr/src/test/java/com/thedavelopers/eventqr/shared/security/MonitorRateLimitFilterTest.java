package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MonitorRateLimitFilterTest {

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final MonitorRateLimitFilter filter = new MonitorRateLimitFilter(15, 5, clock::get);

    private MockHttpServletResponse call(String path, String remoteAddr) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    @DisplayName("the 16th request in a burst gets 429 JSON with Retry-After; time refills the bucket")
    void burstThenThrottle() throws Exception {
        for (int i = 0; i < 15; i++) {
            assertThat(call("/api/v1/monitor/events", "198.51.100.7").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse limited = call("/api/v1/monitor/events", "198.51.100.7");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getContentType()).startsWith("application/json");
        assertThat(limited.getHeader("Retry-After")).isEqualTo("1");
        assertThat(limited.getContentAsString()).contains("\"status\":429").contains("\"error\":\"Too Many Requests\"")
                .contains("\"path\":\"/api/v1/monitor/events\"");

        assertThat(call("/api/v1/monitor/events", "198.51.100.8").getStatus()).as("another client").isEqualTo(200);

        clock.addAndGet(1000);
        for (int i = 0; i < 5; i++) {
            assertThat(call("/api/v1/monitor/events", "198.51.100.7").getStatus()).isEqualTo(200);
        }
        assertThat(call("/api/v1/monitor/events", "198.51.100.7").getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("the viewer's poll (one request every 1.5 s) never trips the limit")
    void viewerPollingIsNeverLimited() throws Exception {
        for (int i = 0; i < 2000; i++) {
            assertThat(call("/api/v1/monitor/events", "198.51.100.7").getStatus()).isEqualTo(200);
            clock.addAndGet(1500);
        }
    }

    @Test
    @DisplayName("IPv6 clients share one bucket per /64; a different /64 has its own")
    void ipv6KeysOnThe64Prefix() throws Exception {
        for (int i = 0; i < 15; i++) {
            assertThat(call("/api/v1/monitor/events", "2001:db8:1:2::" + (i + 1)).getStatus()).isEqualTo(200);
        }
        assertThat(call("/api/v1/monitor/events", "2001:db8:1:2:ffff:eeee:dddd:cccc").getStatus()).isEqualTo(429);
        assertThat(call("/api/v1/monitor/events", "2001:db8:1:3::1").getStatus()).isEqualTo(200);
        assertThat(MonitorRateLimitFilter.clientKey("2001:db8:1:2::1"))
                .isEqualTo(MonitorRateLimitFilter.clientKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd"))
                .isNotEqualTo(MonitorRateLimitFilter.clientKey("2001:db8:1:3::1"));
        assertThat(MonitorRateLimitFilter.clientKey("203.0.113.9")).isEqualTo("203.0.113.9");
        assertThat(MonitorRateLimitFilter.clientKey("unknown")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("other paths are never limited by this filter")
    void otherPathsUnaffected() throws Exception {
        for (int i = 0; i < 500; i++) {
            assertThat(call("/api/v1/events", "198.51.100.7").getStatus()).isEqualTo(200);
            assertThat(call("/api/v1/users/me", "198.51.100.7").getStatus()).isEqualTo(200);
            assertThat(call("/api/v1/monitoring-not-monitor", "198.51.100.7").getStatus()).isEqualTo(200);
        }
        // ...and the traffic above did not eat into the monitor budget either.
        assertThat(call("/api/v1/monitor/events", "198.51.100.7").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("every method and sub-path under /api/v1/monitor shares the bucket")
    void wholeMonitorPathIsCovered() throws Exception {
        for (int i = 0; i < 15; i++) {
            call("/api/v1/monitor/anything", "198.51.100.7");
        }
        assertThat(call("/api/v1/monitor/events", "198.51.100.7").getStatus()).isEqualTo(429);
        assertThat(call("/api/v1/monitor", "198.51.100.7").getStatus()).isEqualTo(429);
    }
}
