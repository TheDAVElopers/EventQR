package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.config.MonitorSettings;
import com.thedavelopers.eventqr.features.monitor.config.MonitorStartupLogger;
import com.thedavelopers.eventqr.features.monitor.support.PoisonedDataSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** LOG_VIEWER_PUBLIC is parsed as plain text by one helper: only "true" enables, nothing can fail startup. */
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
                "app.monitor.public=ture",
                "app.monitor.token="
        })
@AutoConfigureMockMvc
class MonitorPublicParsingTest {

    // Deliberately NOT @Configuration: a nested @Configuration would replace the application configuration that
    // @SpringBootTest otherwise discovers. It is only used as a lite configuration by ApplicationContextRunner.
    static class GuardedConfig {
        @Bean
        @MonitorEnabled
        String guarded() {
            return "on";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = { "true", "TRUE", " TRUE ", "True", "\ttrue\n" })
    @DisplayName("isPublic: true only for the text 'true'")
    void trueValues(String value) {
        assertThat(MonitorSettings.isPublic(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "false", "FALSE", "ture", "1", "yes", "on", "truee", "tru", "t", "true false" })
    @NullSource
    @DisplayName("isPublic: everything else is false (and never throws)")
    void falseValues(String value) {
        assertThat(MonitorSettings.isPublic(value)).isFalse();
    }

    @Test
    @DisplayName("only blank/true/false are recognised; other values count as unrecognised")
    void unrecognisedValues() {
        for (String ok : new String[] { null, "", " ", "true", "false", " TRUE ", "False" }) {
            assertThat(MonitorSettings.isUnrecognisedPublicValue(ok)).as(String.valueOf(ok)).isFalse();
        }
        for (String odd : new String[] { "ture", "1", "yes", "on" }) {
            assertThat(MonitorSettings.isUnrecognisedPublicValue(odd)).as(odd).isTrue();
        }
    }

    @Test
    @DisplayName("the @MonitorEnabled condition never fails on a strange value and only enables for 'true'")
    void conditionToleratesStrangeValues() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GuardedConfig.class);
        for (String off : new String[] { "", "ture", "1", "yes", "false" }) {
            runner.withPropertyValues("app.monitor.public=" + off).run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).doesNotHaveBean("guarded");
            });
        }
        runner.withPropertyValues("app.monitor.public= TRUE ").run(ctx -> assertThat(ctx).hasBean("guarded"));
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("guarded"));
    }

    @Test
    @DisplayName("a typo warns exactly once, naming the property but not echoing the value")
    void typoWarnsWithoutEchoing() {
        Logger logger = (Logger) LoggerFactory.getLogger(MonitorStartupLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new MonitorStartupLogger("", "", "ture").logConfiguration();
            List<ILoggingEvent> events = appender.list;
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(events.get(0).getFormattedMessage()).contains("app.monitor.public").doesNotContain("ture");

            appender.list.clear();
            for (String quiet : new String[] { "", "false", "FALSE", null }) {
                new MonitorStartupLogger("", "", quiet).logConfiguration();
            }
            assertThat(appender.list).isEmpty();

            new MonitorStartupLogger("", "https://viewer.example.test", " TRUE ").logConfiguration();
            assertThat(appender.list).singleElement().satisfies(e ->
                    assertThat(e.getFormattedMessage()).contains("PUBLIC"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("the application boots with LOG_VIEWER_PUBLIC=ture: the monitor stays disabled (404)")
    void applicationBootsWithTypo() throws Exception {
        mockMvc.perform(get("/api/v1/monitor/events")).andExpect(status().isNotFound());
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
