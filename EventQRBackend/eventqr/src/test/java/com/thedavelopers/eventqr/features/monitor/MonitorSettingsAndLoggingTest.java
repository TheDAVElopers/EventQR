package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.config.MonitorSettings;
import com.thedavelopers.eventqr.features.monitor.config.MonitorStartupLogger;
import com.thedavelopers.eventqr.shared.config.CorsConfig;
import com.thedavelopers.eventqr.shared.security.MonitorTokenFilter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class MonitorSettingsAndLoggingTest {

    private static final String TOKEN_32 = "0123456789abcdef0123456789abcdef";

    @Configuration
    static class GuardedConfig {
        @Bean
        @MonitorEnabled
        String guarded() {
            return "on";
        }
    }

    private static ListAppender<ILoggingEvent> capture(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @Test
    @DisplayName("a token shorter than 32 characters is not usable; 32 is")
    void tokenStrength() {
        assertThat(MonitorSettings.isUsableToken(null)).isFalse();
        assertThat(MonitorSettings.isUsableToken("")).isFalse();
        assertThat(MonitorSettings.isUsableToken("   ")).isFalse();
        assertThat(MonitorSettings.isUsableToken(TOKEN_32.substring(1))).isFalse();
        assertThat(MonitorSettings.isUsableToken("  " + TOKEN_32.substring(1) + "  ")).isFalse();
        assertThat(MonitorSettings.isUsableToken(TOKEN_32)).isTrue();
    }

    @Test
    @DisplayName("the @MonitorEnabled condition follows token strength")
    void conditionFollowsTokenStrength() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GuardedConfig.class);
        runner.withPropertyValues("app.monitor.token=").run(ctx -> assertThat(ctx).doesNotHaveBean("guarded"));
        runner.withPropertyValues("app.monitor.token=too-short").run(ctx -> assertThat(ctx).doesNotHaveBean("guarded"));
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("guarded"));
        runner.withPropertyValues("app.monitor.token=" + TOKEN_32).run(ctx -> assertThat(ctx).hasBean("guarded"));
    }

    @Test
    @DisplayName("MonitorTokenFilter: a short configured token keeps the endpoint at 404 even for a matching header")
    void filterTreatsShortTokenAsUnconfigured() throws Exception {
        String shortToken = "short-token-1234567890";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/monitor/events");
        request.addHeader("Authorization", "Bearer " + shortToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new MonitorTokenFilter(shortToken).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();

        MockHttpServletRequest ok = new MockHttpServletRequest("GET", "/api/v1/monitor/events");
        ok.addHeader("Authorization", "Bearer " + TOKEN_32);
        MockFilterChain okChain = new MockFilterChain();
        new MonitorTokenFilter(TOKEN_32).doFilter(ok, new MockHttpServletResponse(), okChain);
        assertThat(okChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("startup warns about a short token with its length only, never its value")
    void warnsAboutShortToken() {
        ListAppender<ILoggingEvent> appender = capture(MonitorStartupLogger.class);
        new MonitorStartupLogger("sekrit-short-value", "https://viewer.example.test").logConfiguration();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("18").contains("32").doesNotContain("sekrit");
        });
    }

    @Test
    @DisplayName("startup warns once when the token is valid but the origin is blank, and is quiet otherwise")
    void warnsAboutBlankOrigin() {
        ListAppender<ILoggingEvent> appender = capture(MonitorStartupLogger.class);
        new MonitorStartupLogger(TOKEN_32, "").logConfiguration();
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).doesNotContain(TOKEN_32);
        });
        appender.list.clear();
        new MonitorStartupLogger(TOKEN_32, "https://viewer.example.test").logConfiguration();
        new MonitorStartupLogger("", "").logConfiguration();
        assertThat(appender.list).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://viewer.example.test", "http://localhost:5173", "https://event-qr-rose.vercel.app",
            "http://127.0.0.1:8080", "https://a.b.c.example.org:443" })
    @DisplayName("valid origins")
    void validOrigins(String origin) {
        assertThat(MonitorSettings.isValidOrigin(origin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "*", "null", "", " ", "https://*.example.test", "https://viewer.example.test/",
            "https://viewer.example.test/path", "https://viewer.example.test?x=1", "https://viewer.example.test#f",
            "ftp://viewer.example.test", "viewer.example.test", "https://user@viewer.example.test",
            "https://viewer.example.test:port", "https://", "https://-bad.example.test" })
    @DisplayName("invalid origins")
    void invalidOrigins(String origin) {
        assertThat(MonitorSettings.isValidOrigin(origin)).isFalse();
    }

    @Test
    @DisplayName("an invalid allowed-origin registers no monitor CORS entry (and warns); a valid one does")
    void corsRegistersOnlyValidOrigins() {
        ListAppender<ILoggingEvent> appender = capture(CorsConfig.class);
        for (String invalid : List.of("*", "null", "https://viewer.example.test/")) {
            CorsConfiguration config = corsFor(invalid);
            assertThat(config.getAllowedOrigins()).containsExactly("https://eventqr.app");
        }
        assertThat(appender.list).hasSize(3).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
        CorsConfiguration valid = corsFor("https://viewer.example.test");
        assertThat(valid.getAllowedOrigins()).containsExactly("https://viewer.example.test");
        assertThat(valid.getAllowedMethods()).containsExactly("GET", "OPTIONS");
        assertThat(valid.getMaxAge()).isEqualTo(3600L);
        assertThat(corsFor("").getAllowedOrigins()).containsExactly("https://eventqr.app");
    }

    private static CorsConfiguration corsFor(String origin) {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "frontendBaseUrl", "https://eventqr.app");
        ReflectionTestUtils.setField(config, "monitorAllowedOrigin", origin);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/monitor/events");
        return config.corsConfigurationSource().getCorsConfiguration(request);
    }
}
