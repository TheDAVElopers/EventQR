package com.thedavelopers.eventqr.features.monitor.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Activates a monitor bean only when {@code app.monitor.token} is at least {@value MonitorSettings#MIN_TOKEN_LENGTH} characters. With no token the whole monitor
 * (recorder, Hibernate listeners, repository aspect, endpoint) is absent, so it costs no memory or per-call work.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Conditional(MonitorEnabled.TokenConfigured.class)
public @interface MonitorEnabled {

    class TokenConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return MonitorSettings.isUsableToken(context.getEnvironment().getProperty("app.monitor.token"));
        }
    }
}
