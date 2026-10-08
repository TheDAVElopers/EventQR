package com.thedavelopers.eventqr.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class AsyncConfigTest {

    public void sampleAsync(String secretArg, int other) {
    }

    @Test
    void globalHandlerLogsMethodParamCountAndStackTraceButNoArgumentValues() throws Exception {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(AsyncConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Method method = AsyncConfigTest.class.getMethod("sampleAsync", String.class, int.class);
            new AsyncConfig().getAsyncUncaughtExceptionHandler()
                    .handleUncaughtException(new IllegalStateException("boom"), method, "TOP-SECRET", 7);

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("sampleAsync").contains("paramCount=2")
                    .doesNotContain("TOP-SECRET");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo("java.lang.IllegalStateException");
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void passwordResetTaskFailuresLogOnlyTheExceptionClassNeverTheMessageOrStackTrace() throws Exception {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(AsyncConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Method method = com.thedavelopers.eventqr.features.auth.service.PasswordResetService.class
                    .getMethod("requestReset", String.class);
            // A commit-time failure whose message embeds the bound token and the email.
            new AsyncConfig().getAsyncUncaughtExceptionHandler().handleUncaughtException(
                    new IllegalStateException("could not execute statement [token=tok-SECRET-123, email=a@b.com]"),
                    method, "a@b.com");

            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("requestReset").contains("IllegalStateException")
                    .doesNotContain("tok-SECRET-123").doesNotContain("a@b.com");
            assertThat(event.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
        }
    }
}
