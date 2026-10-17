package com.thedavelopers.eventqr.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.EventqrApplication;

/**
 * Guards the wiring that makes the empty-database bootstrap run in sliced tests: the callback must be @Import-ed by
 * {@link EventqrApplication} and must not carry @Component, since both together would register one bean name twice
 * and fail startup with bean-definition overriding disabled.
 */
class FreshDatabaseBootstrapCallbackWiringTest {

    @Test
    void theApplicationImportsTheCallback() {
        Import imports = AnnotatedElementUtils.findMergedAnnotation(EventqrApplication.class, Import.class);

        assertThat(imports).isNotNull();
        assertThat(imports.value()).contains(FreshDatabaseBootstrapCallback.class);
    }

    @Test
    void theCallbackIsNotAComponent() {
        assertThat(AnnotatedElementUtils.hasAnnotation(FreshDatabaseBootstrapCallback.class, Component.class)).isFalse();
    }
}
