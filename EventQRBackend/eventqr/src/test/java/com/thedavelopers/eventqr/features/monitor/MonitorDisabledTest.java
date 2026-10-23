package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import com.thedavelopers.eventqr.features.monitor.aspect.RepositoryCrudAspect;
import com.thedavelopers.eventqr.features.monitor.controller.MonitorController;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventCapture;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventRecorder;
import com.thedavelopers.eventqr.features.monitor.service.HibernateCrudListenerRegistrar;
import com.thedavelopers.eventqr.features.monitor.support.PoisonedDataSource;

/** With no LOG_VIEWER_TOKEN the monitor does not exist: 404 for everyone, and no monitor beans are created. */
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
                "app.monitor.token="
        })
@AutoConfigureMockMvc
class MonitorDisabledTest {

    private static final String URL = "/api/v1/monitor/events";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("unset token: 404 with or without an Authorization header")
    void endpointIsAbsent() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isNotFound());
        mockMvc.perform(get(URL).header("Authorization", "Bearer anything")).andExpect(status().isNotFound());
        mockMvc.perform(get(URL).header("Authorization", "Bearer ")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("unset token: recorder, capture, listeners, aspect and controller are not instantiated")
    void noMonitorBeans() {
        for (Class<?> type : new Class<?>[] { CrudEventRecorder.class, CrudEventCapture.class,
                HibernateCrudListenerRegistrar.class, RepositoryCrudAspect.class, MonitorController.class }) {
            assertThat(context.getBeanNamesForType(type)).as(type.getSimpleName()).isEmpty();
        }
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
