package com.thedavelopers.eventqr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.FilterType;

import com.thedavelopers.eventqr.features.rewards.repository.PointTransactionRepository;
import com.thedavelopers.eventqr.features.transactions.repository.TransactionLogRepository;
import com.thedavelopers.eventqr.shared.interfaces.ActivityLookupPort;

/**
 * Real component scanning of the application package, narrowed to ActivityLookupPort implementations. The
 * attendee self-cancel guard iterates every ActivityLookupPort bean; if one is renamed away, loses @Component or
 * moves out of the scanned package, the guard would silently shrink. This fails instead.
 */
class ActivityLookupPortWiringTest {

    @Configuration
    @ComponentScan(basePackages = "com.thedavelopers.eventqr", useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ActivityLookupPort.class))
    static class ScanConfig {
        @Bean PointTransactionRepository pointTransactionRepository() {
            return mock(PointTransactionRepository.class);
        }

        @Bean TransactionLogRepository transactionLogRepository() {
            return mock(TransactionLogRepository.class);
        }
    }

    @Test
    void rewardsAndTransactionsLookupsAreBothRegistered() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ScanConfig.class)) {
            List<String> beans = context.getBeansOfType(ActivityLookupPort.class).values().stream()
                    .map(bean -> bean.getClass().getSimpleName()).toList();
            assertThat(beans).containsExactlyInAnyOrder("RewardsActivityLookup", "TransactionActivityLookup");
        }
    }
}
