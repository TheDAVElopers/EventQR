package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.config.MonitorSettings;
import com.thedavelopers.eventqr.features.monitor.config.MonitorStartupLogger;
import com.thedavelopers.eventqr.shared.security.MonitorTokenFilter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class MonitorPublicSettingsTest {

    private static final String TOKEN_32 = "0123456789abcdef0123456789abcdef";

    @Configuration
    static class GuardedConfig {
        @Bean
        @MonitorEnabled
        String guarded() {
            return "on";
        }
    }

    @Test
    @DisplayName("enabled = public OR a usable token")
    void enabledTruthTable() {
        assertThat(MonitorSettings.isEnabled(null, false)).isFalse();
        assertThat(MonitorSettings.isEnabled("short", false)).isFalse();
        assertThat(MonitorSettings.isEnabled(TOKEN_32, false)).isTrue();
        assertThat(MonitorSettings.isEnabled(null, true)).isTrue();
        assertThat(MonitorSettings.isEnabled("short", true)).isTrue();
        assertThat(MonitorSettings.isEnabled(TOKEN_32, true)).isTrue();
    }

    @Test
    @DisplayName("the @MonitorEnabled condition honours app.monitor.public (default off)")
    void conditionHonoursPublicFlag() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GuardedConfig.class);
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("guarded"));
        runner.withPropertyValues("app.monitor.public=false").run(ctx -> assertThat(ctx).doesNotHaveBean("guarded"));
        runner.withPropertyValues("app.monitor.public=").run(ctx -> assertThat(ctx).doesNotHaveBean("guarded"));
        runner.withPropertyValues("app.monitor.public=false", "app.monitor.token=short")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("guarded"));
        runner.withPropertyValues("app.monitor.public=true").run(ctx -> assertThat(ctx).hasBean("guarded"));
        runner.withPropertyValues("app.monitor.public=true", "app.monitor.token=short")
                .run(ctx -> assertThat(ctx).hasBean("guarded"));
        runner.withPropertyValues("app.monitor.public=false", "app.monitor.token=" + TOKEN_32)
                .run(ctx -> assertThat(ctx).hasBean("guarded"));
    }

    @Test
    @DisplayName("MonitorTokenFilter in public mode: never 404/401 itself, header ignored, matching token still authenticates")
    void filterInPublicMode() throws Exception {
        try {
            for (String header : new String[] { null, "Bearer wrong", "Basic abc" }) {
                for (String token : new String[] { null, "", "short", TOKEN_32 }) {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/monitor/events");
                    if (header != null) {
                        request.addHeader("Authorization", header);
                    }
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    MockFilterChain chain = new MockFilterChain();
                    new MonitorTokenFilter(token, true).doFilter(request, response, chain);
                    assertThat(response.getStatus()).isEqualTo(200);
                    assertThat(chain.getRequest()).isNotNull();
                    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                }
            }
            MockHttpServletRequest withToken = new MockHttpServletRequest("GET", "/api/v1/monitor/events");
            withToken.addHeader("Authorization", "Bearer " + TOKEN_32);
            new MonitorTokenFilter(TOKEN_32, true).doFilter(withToken, new MockHttpServletResponse(), new MockFilterChain());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("MonitorTokenFilter with public off keeps the old behaviour (404 when unconfigured)")
    void filterNonPublicUnchanged() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new MonitorTokenFilter(null, false).doFilter(new MockHttpServletRequest("GET", "/api/v1/monitor/events"),
                response, chain);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("startup warns that the feed is PUBLIC, plus the blank-origin warning; a valid origin adds nothing more")
    void startupWarnsAboutPublicMode() {
        Logger logger = (Logger) LoggerFactory.getLogger(MonitorStartupLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new MonitorStartupLogger("", "", true).logConfiguration();
            assertThat(appender.list).hasSize(2).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("PUBLIC").contains("LOG_VIEWER_PUBLIC=true");
            assertThat(appender.list.get(1).getFormattedMessage()).contains("LOG_VIEWER_ORIGIN");

            appender.list.clear();
            new MonitorStartupLogger(TOKEN_32, "https://viewer.example.test", true).logConfiguration();
            assertThat(appender.list).singleElement().satisfies(e ->
                    assertThat(e.getFormattedMessage()).contains("PUBLIC").doesNotContain(TOKEN_32));

            appender.list.clear();
            new MonitorStartupLogger(TOKEN_32, "https://viewer.example.test", false).logConfiguration();
            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
