package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.thedavelopers.eventqr.features.monitor.service.CrudEventRecorder;
import com.thedavelopers.eventqr.features.monitor.support.PoisonedDataSource;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.RevokedTokenStore;
import com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker;

/**
 * Public mode ({@code app.monitor.public=true}) with NO token configured. Full context, real security chains and a
 * DataSource that can never connect.
 */
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
                "app.monitor.token=",
                "app.monitor.allowed-origin=https://viewer.example.test",
                "app.monitor.rate-limit.burst=100000"
        })
@AutoConfigureMockMvc
class MonitorPublicModeTest {

    private static final String URL = "/api/v1/monitor/events";
    private static final String VIEWER = "https://viewer.example.test";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private List<SecurityFilterChain> chains;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @MockitoBean
    private RevokedTokenStore revokedTokenStore;
    @MockitoBean
    private UserTokenRevocationChecker revocationChecker;

    @BeforeEach
    void resetMocks() {
        Mockito.clearInvocations(revokedTokenStore, revocationChecker);
    }

    @Test
    @DisplayName("public mode: 200 with no Authorization header, plain JSON, no-store, and no database access")
    void anonymousGetIsServed() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.bootId").isNotEmpty())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get(URL + "?after=0&limit=5")).andExpect(status().isOk());
        verifyNoInteractions(revokedTokenStore, revocationChecker);
        assertThat(context.getBeanNamesForType(CrudEventRecorder.class)).hasSize(1);
        assertThat(context.getBean(DataSource.class)).isInstanceOf(PoisonedDataSource.class);
    }

    @Test
    @DisplayName("public mode: a wrong or foreign Authorization header is ignored, not rejected")
    void wrongHeaderIsIgnored() throws Exception {
        String jwt = jwtService.createToken(UUID.randomUUID(), "user@example.test", AccountRole.STAFF);
        for (String value : new String[] { "Bearer wrong", "Bearer ", "Basic Y29ycmVjdA==", "garbage", "Bearer " + jwt }) {
            mockMvc.perform(get(URL).header("Authorization", value)).andExpect(status().isOk());
        }
        verifyNoInteractions(revokedTokenStore, revocationChecker);
    }

    @Test
    @DisplayName("public mode: write methods on the monitor path are never served, anonymously")
    void writeMethodsAreRefused() throws Exception {
        mockMvc.perform(post(URL).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(URL).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(URL).content("{}")).andExpect(status().isUnauthorized());
        // A wrong header does not turn a write into an allowed request either.
        mockMvc.perform(post(URL).header("Authorization", "Bearer wrong").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("public mode: only GET /api/v1/monitor/events is anonymous; other monitor paths and the API still need auth")
    void nothingElseBecomesAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/monitor")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/monitor/other")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/monitor/events/extra")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/stats")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
    }

    /** The request must not be served: either rejected by the firewall or answered with a non-200 status. */
    private void assertNotServed(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        try {
            int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
            assertThat(status).isNotEqualTo(200);
        } catch (Exception e) {
            Throwable cause = e;
            boolean rejected = false;
            while (cause != null) {
                rejected |= cause instanceof org.springframework.security.web.firewall.RequestRejectedException;
                cause = cause.getCause();
            }
            assertThat(rejected).as("unexpected failure: " + e).isTrue();
        }
        verifyNoInteractions(revokedTokenStore, revocationChecker);
    }

    @Test
    @DisplayName("public mode: path variants of the open endpoint are never served (slash, HEAD, case, ;param, %2F)")
    void pathVariantsAreNotServed() throws Exception {
        assertNotServed(get("/api/v1/monitor/events/"));
        assertNotServed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head(URL));
        assertNotServed(get("/API/V1/MONITOR/EVENTS"));
        assertNotServed(get("/api/v1/Monitor/events"));
        assertNotServed(get(java.net.URI.create("/api/v1/monitor/events;jsessionid=x")));
        assertNotServed(get(java.net.URI.create("/api/v1/monitor;jsessionid=x/events")));
        assertNotServed(get(java.net.URI.create("/api/v1/monitor%2Fevents")));
        assertNotServed(get(java.net.URI.create("/api/v1/monitor/%2e%2e/users/me")));
        assertNotServed(get("/api/v1/monitor/events/..;/users/me"));
    }

    @Test
    @DisplayName("public mode: the main chain still refuses the monitor path to every user JWT")
    void mainChainStillDeniesUserJwts() throws Exception {
        SecurityFilterChain mainChain = chains.stream()
                .filter(chain -> chain.matches(new MockHttpServletRequest("GET", "/api/v1/admin/x")))
                .findFirst().orElseThrow();
        MockMvc mainOnly = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new FilterChainProxy(mainChain)).build();
        given(revocationChecker.isAccessAllowed(any(), any(), any())).willReturn(true);
        for (AccountRole role : AccountRole.values()) {
            String jwt = jwtService.createToken(UUID.randomUUID(), "user@example.test", role);
            mainOnly.perform(get(URL).header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
        }
        mainOnly.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("public mode: CORS preflight and reads are allowed for the configured origin only")
    void corsIsUnchanged() throws Exception {
        mockMvc.perform(options(URL)
                        .header("Origin", VIEWER)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VIEWER))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,OPTIONS"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mockMvc.perform(get(URL).header("Origin", VIEWER))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VIEWER));

        for (String origin : new String[] { "https://evil.example.test", "https://eventqr.app" }) {
            mockMvc.perform(options(URL).header("Origin", origin).header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(URL).header("Origin", origin)).andExpect(status().isForbidden());
        }
        mockMvc.perform(options(URL).header("Origin", VIEWER).header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
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
