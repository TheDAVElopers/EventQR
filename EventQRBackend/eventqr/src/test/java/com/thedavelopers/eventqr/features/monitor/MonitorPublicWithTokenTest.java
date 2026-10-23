package com.thedavelopers.eventqr.features.monitor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import com.thedavelopers.eventqr.features.monitor.support.PoisonedDataSource;

/** Public mode together with a configured token: anonymous works (public wins), the token still works too. */
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
                "app.monitor.public=true",
                "app.monitor.token=public-and-token-0123456789-abcdef-xyz",
                "app.monitor.allowed-origin=https://viewer.example.test"
        })
@AutoConfigureMockMvc
class MonitorPublicWithTokenTest {

    private static final String URL = "/api/v1/monitor/events";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("public + token: anonymous, wrong-token and right-token reads all succeed")
    void everyReaderIsServed() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.events").isArray());
        mockMvc.perform(get(URL).header("Authorization", "Bearer nope")).andExpect(status().isOk());
        mockMvc.perform(get(URL).header("Authorization", "Bearer public-and-token-0123456789-abcdef-xyz"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("public + token: writes stay refused (405 with the token, 401 without)")
    void writesStayRefused() throws Exception {
        mockMvc.perform(post(URL).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).header("Authorization", "Bearer public-and-token-0123456789-abcdef-xyz")
                .content("{}")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("public + token: the rest of the API is still protected")
    void restOfApiProtected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized());
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
