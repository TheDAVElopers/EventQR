package com.thedavelopers.eventqr.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);
    private static final String PASSWORD_RESET_SERVICE = "PasswordResetService";

    /**
     * Full diagnostics (method, parameter COUNT, stack trace) for every generic @Async method. Parameter values are
     * never logged. Password-reset tasks log only the exception class (see handler) so tokens never reach the logs.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            int paramCount = params == null ? 0 : params.length;
            if (PASSWORD_RESET_SERVICE.equals(method.getDeclaringClass().getSimpleName())) {
                // A commit-time failure escapes the method's own guard, and JDBC/Hibernate messages can embed bound
                // values (the reset token), so only the exception class is logged for these methods.
                log.error("Async password reset task failed method={} cause={}", method.getName(),
                        ex.getClass().getSimpleName());
                return;
            }
            log.error("Async task failed method={} paramCount={}", method.getName(), paramCount, ex);
        };
    }

    /**
     * Dedicated executor for password-reset mail. When the pool and queue are full the work runs on the
     * request thread (slower, but the email is never dropped), unlike the shared eventTaskExecutor.
     */
    @Bean(name = "passwordResetExecutor")
    public TaskExecutor passwordResetExecutor() {
        return buildPasswordResetExecutor(2, 4, 256);
    }

    public static ThreadPoolTaskExecutor buildPasswordResetExecutor(int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("pwreset-async-");
        executor.setRejectedExecutionHandler(new WarnCallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    static final class WarnCallerRunsPolicy extends java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy {
        @Override
        public void rejectedExecution(Runnable task, java.util.concurrent.ThreadPoolExecutor executor) {
            log.warn("Password reset executor saturated: running on the request thread");
            super.rejectedExecution(task, executor);
        }
    }

    @Bean(name = "eventTaskExecutor")
    public TaskExecutor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("event-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
