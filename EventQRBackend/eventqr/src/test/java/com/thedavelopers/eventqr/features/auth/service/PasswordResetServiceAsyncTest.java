package com.thedavelopers.eventqr.features.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.scheduling.annotation.AsyncAnnotationAdvisor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.auth.model.entity.PasswordResetToken;
import com.thedavelopers.eventqr.features.auth.repository.PasswordResetTokenRepository;
import com.thedavelopers.eventqr.features.qremail.service.EmailGatewayService;
import com.thedavelopers.eventqr.features.users.model.entity.UserProfile;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;

/**
 * The reset work must run off the request thread so a known email (DB writes plus a Brevo call) and an
 * unknown email return equally fast. The service is wrapped in Spring's real async advice here.
 */
class PasswordResetServiceAsyncTest {

    private PasswordResetTokenRepository tokens;
    private UserProfileRepository users;
    private EmailGatewayService mail;
    private PasswordResetService proxy;
    private ThreadPoolTaskExecutor executor;
    private final CountDownLatch releaseMail = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        tokens = mock(PasswordResetTokenRepository.class);
        users = mock(UserProfileRepository.class);
        mail = mock(EmailGatewayService.class);
        PasswordResetService target = new PasswordResetService(tokens, users, mock(PasswordEncoder.class), mail,
                mock(RefreshTokenService.class));
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.initialize();
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        AsyncAnnotationAdvisor advisor = new AsyncAnnotationAdvisor(executor, (org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler) null);
        advisor.setBeanFactory(new org.springframework.beans.factory.support.StaticListableBeanFactory(
                java.util.Map.of("passwordResetExecutor", executor)));
        factory.addAdvisor(advisor);
        proxy = (PasswordResetService) factory.getProxy();

        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(i -> i.getArgument(0));
        doAnswer(i -> {
            releaseMail.await(10, TimeUnit.SECONDS);
            return null;
        }).when(mail).sendSimple(anyString(), anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        releaseMail.countDown();
        executor.shutdown();
    }

    @Test
    void aKnownEmailReturnsWithoutWaitingOnTheMailClientAndStillDoesTheWork() {
        UserProfile user = new UserProfile();
        user.setId(UUID.randomUUID());
        user.setEmail("jane@example.com");
        user.setFullName("Jane");
        when(users.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));

        assertThat(timed(() -> proxy.requestReset("jane@example.com"))).isLessThan(Duration.ofSeconds(2));

        // The mail call is still blocked, proving the caller did not wait for it; the work happens afterwards.
        releaseMail.countDown();
        verify(mail, timeout(5000)).sendSimple(anyString(), anyString(), anyString());
        verify(tokens, timeout(5000)).save(any(PasswordResetToken.class));
    }

    @Test
    void anUnknownEmailReturnsImmediatelyToo() {
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThat(timed(() -> proxy.requestReset("nobody@example.com"))).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void aMailFailureOnTheAsyncThreadNeverReachesTheCaller() {
        UserProfile user = new UserProfile();
        user.setId(UUID.randomUUID());
        user.setEmail("jane@example.com");
        user.setFullName("Jane");
        when(users.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
        doAnswer(i -> {
            throw new IllegalStateException("brevo down for jane@example.com");
        }).when(mail).sendSimple(anyString(), anyString(), anyString());

        proxy.requestReset("jane@example.com");

        verify(mail, timeout(5000)).sendSimple(anyString(), anyString(), anyString());
    }

    @Test
    void aSaturatedDedicatedExecutorRunsTheResetOnTheCallerSoTheEmailIsNeverDropped() throws Exception {
        ThreadPoolTaskExecutor tiny = com.thedavelopers.eventqr.shared.config.AsyncConfig
                .buildPasswordResetExecutor(1, 1, 1);
        try {
            UserProfile user = new UserProfile();
            user.setId(UUID.randomUUID());
            user.setEmail("jane@example.com");
            user.setFullName("Jane");
            when(users.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(user));
            PasswordResetService target = new PasswordResetService(tokens, users, mock(PasswordEncoder.class), mail,
                    mock(RefreshTokenService.class));
            ProxyFactory factory = new ProxyFactory(target);
            factory.setProxyTargetClass(true);
            AsyncAnnotationAdvisor advisor = new AsyncAnnotationAdvisor(tiny,
                    (org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler) null);
            advisor.setBeanFactory(new org.springframework.beans.factory.support.StaticListableBeanFactory(
                    java.util.Map.of("passwordResetExecutor", tiny)));
            factory.addAdvisor(advisor);
            PasswordResetService p = (PasswordResetService) factory.getProxy();

            // Mail blocks, so request 1 occupies the single thread, request 2 the single queue slot,
            // and request 3 overflows to the calling thread (which blocks on the same latch).
            releaseMail.countDown(); // keep mail non-blocking for the caller-run overflow
            java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
            tiny.execute(() -> {
                try {
                    gate.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            tiny.execute(() -> { });
            // pool busy + queue full: the next submission is rejected by the pool and must run here.
            p.requestReset("jane@example.com");

            verify(mail).sendSimple(anyString(), anyString(), anyString());
            gate.countDown();
        } finally {
            tiny.shutdown();
        }
    }

    @Test
    void requestResetNeverThrowsAndItsLogLineHasNeitherEmailNorToken() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                .getLogger(PasswordResetService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            PasswordResetService direct = new PasswordResetService(tokens, users, mock(PasswordEncoder.class), mail,
                    mock(RefreshTokenService.class));
            UserProfile user = new UserProfile();
            user.setId(UUID.randomUUID());
            user.setEmail("secret.jane@example.com");
            user.setFullName("Jane");
            when(users.findByEmailIgnoreCase("secret.jane@example.com")).thenReturn(Optional.of(user));
            when(tokens.save(any(PasswordResetToken.class)))
                    .thenThrow(new IllegalStateException("db down for secret.jane@example.com"));

            direct.requestReset("secret.jane@example.com"); // must not throw

            assertThat(appender.list).isNotEmpty();
            for (ch.qos.logback.classic.spi.ILoggingEvent event : appender.list) {
                assertThat(event.getFormattedMessage()).doesNotContain("secret.jane").doesNotContain("token=");
                assertThat(event.getThrowableProxy()).isNull();
            }
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains(user.getId().toString()).contains("IllegalStateException");

            when(users.findByEmailIgnoreCase(anyString())).thenThrow(new RuntimeException("boom"));
            direct.requestReset("anyone@example.com"); // unknown user id, still swallowed
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static Duration timed(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
