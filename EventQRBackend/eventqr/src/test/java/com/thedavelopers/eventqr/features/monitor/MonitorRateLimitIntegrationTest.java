package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.thedavelopers.eventqr.features.monitor.support.PoisonedDataSource;

/** The monitor bucket in the real filter chain, with default limits and a DataSource that can never connect. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
                "jwt.secret=testtesttesttesttesttesttesttesttesttesttesttest",
                "brevo.api.key=test-api-key-not-real",
                "brevo.sender.email=noreply@example.test",
                "app.monitor.public=true",
                "app.monitor.allowed-origin=https://viewer.example.test"
        })
@AutoConfigureMockMvc
class MonitorRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("hammering the monitor path gets 429 JSON with Retry-After; other paths keep answering normally")
    void monitorIsThrottledOthersAreNot() throws Exception {
        int limited = 0;
        MockHttpServletResponse last = null;
        for (int i = 0; i < 40; i++) {
            MockHttpServletResponse response = mockMvc.perform(get("/api/v1/monitor/events").header("CF-Connecting-IP", "203.0.113.10").header("X-Forwarded-For", "203.0.113.10")).andReturn().getResponse();
            if (response.getStatus() == 429) {
                limited++;
                last = response;
            } else {
                assertThat(response.getStatus()).isEqualTo(200);
            }
        }
        assertThat(limited).isBetween(15, 25);
        assertThat(last.getHeader("Retry-After")).isNotBlank();
        assertThat(last.getContentAsString()).contains("\"status\":429");

        for (int i = 0; i < 40; i++) {
            assertThat(mockMvc.perform(get("/api/v1/users/me")).andReturn().getResponse().getStatus())
                    .isEqualTo(401);
        }
    }

    @Test
    @DisplayName("the 429 for the allowed origin carries Allow-Origin and exposes Retry-After; other origins get no CORS headers")
    void throttledResponseIsReadableCrossOrigin() throws Exception {
        MockHttpServletResponse limited = null;
        for (int i = 0; i < 40 && limited == null; i++) {
            MockHttpServletResponse response = mockMvc.perform(get("/api/v1/monitor/events")
                    .header("Origin", "https://viewer.example.test")).andReturn().getResponse();
            if (response.getStatus() == 200) {
                assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://viewer.example.test");
                assertThat(response.getHeader("Access-Control-Expose-Headers")).contains("Retry-After");
            } else {
                limited = response;
            }
        }
        assertThat(limited).isNotNull();
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://viewer.example.test");
        assertThat(limited.getHeader("Access-Control-Expose-Headers")).contains("Retry-After");
        assertThat(limited.getHeaders("Vary")).contains("Origin");

        MockHttpServletResponse foreign = mockMvc.perform(get("/api/v1/monitor/events")
                .header("Origin", "https://evil.example.test")).andReturn().getResponse();
        assertThat(foreign.getStatus()).isEqualTo(429);
        assertThat(foreign.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(foreign.getHeader("Access-Control-Expose-Headers")).isNull();
    }

    @TestConfiguration
    static class PoisonedDataSourceConfiguration {

        @Bean
        @Primary
        DataSource dataSource() {
            return new PoisonedDataSource();
        }
    }
}
