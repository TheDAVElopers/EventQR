package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.thedavelopers.eventqr.features.monitor.support.PoisonedDataSource;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.RevokedTokenStore;
import com.thedavelopers.eventqr.shared.security.UserTokenRevocationChecker;

/**
 * Full application context (MockMvc, real security filter chains) with the monitor enabled and a DataSource that
 * can never connect, so any database access from the monitor request path would fail the test.
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
                "app.monitor.token=correct-horse-battery-staple-0123456789-abcdef",
                "app.monitor.allowed-origin=https://viewer.example.test",
                "app.monitor.rate-limit.burst=100000"
        })
@AutoConfigureMockMvc
class MonitorEndpointSecurityTest {

    private static final String URL = "/api/v1/monitor/events";
    private static final String TOKEN = "Bearer correct-horse-battery-staple-0123456789-abcdef";
    private static final String VIEWER = "https://viewer.example.test";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private java.util.List<org.springframework.security.web.SecurityFilterChain> chains;
    @Autowired
    private org.springframework.web.context.WebApplicationContext webApplicationContext;
    @MockitoBean
    private RevokedTokenStore revokedTokenStore;
    @MockitoBean
    private UserTokenRevocationChecker revocationChecker;

    @BeforeEach
    void resetMocks() {
        Mockito.clearInvocations(revokedTokenStore, revocationChecker);
    }

    @Test
    @DisplayName("no token and wrong tokens get 401 JSON")
    void rejectsMissingAndWrongTokens() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required: missing or invalid token"));
        mockMvc.perform(get(URL).header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", "Bearer correct-horse-battery-staple-0123456789-abcde")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", "Bearer ")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", "Basic Y29ycmVjdA==")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL).header("Authorization", "correct-horse-battery-staple-0123456789-abcdef")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the right token gets 200, plain JSON, no-store, without any database access")
    void acceptsCorrectToken() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.bootId").isNotEmpty())
                .andExpect(jsonPath("$.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.latestSeq").isNumber())
                .andExpect(jsonPath("$.bufferSize").value(2000))
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get(URL + "?after=999999&limit=5").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isEmpty());
        // The JWT filter (revocation store / account lookup) must not run for monitor requests.
        verifyNoInteractions(revokedTokenStore, revocationChecker);
    }

    @Test
    @DisplayName("a real user JWT is not accepted on the monitor endpoint and is not looked up")
    void userJwtIsRejected() throws Exception {
        String jwt = jwtService.createToken(UUID.randomUUID(), "admin@example.test", AccountRole.SUPER_ADMIN);
        given(revocationChecker.isAccessAllowed(any(), any(), any())).willReturn(true);

        mockMvc.perform(get(URL).header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        verifyNoInteractions(revokedTokenStore, revocationChecker);
    }

    @Test
    @DisplayName("the monitor token does not authenticate any other endpoint")
    void monitorTokenDoesNotOpenOtherEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", TOKEN)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/events").header("Authorization", TOKEN)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the monitor offers no write methods")
    void readOnly() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", TOKEN).content("{}")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post(URL).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("normal endpoints still use the JWT chain: no token 401, valid staff JWT passes auth and is role-checked")
    void mainChainIsUnchanged() throws Exception {
        mockMvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());

        String jwt = jwtService.createToken(UUID.randomUUID(), "staff@example.test", AccountRole.STAFF);
        given(revocationChecker.isAccessAllowed(any(), any(), any())).willReturn(true);
        // Authenticated as STAFF (so not 401) but /api/v1/admin/** needs ADMIN: 403 proves the JWT filter ran.
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
        verify(revokedTokenStore).findActive(anyString());
        verify(revocationChecker).isAccessAllowed(any(), any(), any());
    }

    @Test
    @DisplayName("main chain alone (monitor chain absent): no user JWT, whatever its role, reaches the monitor controller")
    void mainChainDeniesMonitorPathToEveryUserJwt() throws Exception {
        org.springframework.security.web.SecurityFilterChain mainChain = chains.stream()
                .filter(chain -> chain.matches(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/admin/x")))
                .findFirst().orElseThrow();
        assertThat(mainChain.matches(new org.springframework.mock.web.MockHttpServletRequest("GET", URL))).isTrue();
        org.springframework.test.web.servlet.MockMvc mainOnly = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .addFilters(new org.springframework.security.web.FilterChainProxy(mainChain))
                .build();
        given(revocationChecker.isAccessAllowed(any(), any(), any())).willReturn(true);

        for (AccountRole role : AccountRole.values()) {
            String jwt = jwtService.createToken(UUID.randomUUID(), "user@example.test", role);
            mainOnly.perform(get(URL).header("Authorization", "Bearer " + jwt)).andExpect(status().isForbidden());
        }
        mainOnly.perform(get(URL)).andExpect(status().isUnauthorized());
        // The shared monitor token is not a JWT, so it authenticates nobody on the main chain either.
        mainOnly.perform(get(URL).header("Authorization", TOKEN)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS: the configured viewer origin may preflight and read the monitor endpoint")
    void corsAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options(URL)
                        .header("Origin", VIEWER)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VIEWER))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,OPTIONS"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        mockMvc.perform(get(URL).header("Origin", VIEWER).header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VIEWER));
    }

    @Test
    @DisplayName("CORS: other origins, the app's own frontend origin and write methods are refused on the monitor path")
    void corsRejectsOtherOrigins() throws Exception {
        for (String origin : new String[] { "https://evil.example.test", "https://eventqr.app" }) {
            mockMvc.perform(options(URL)
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(URL).header("Origin", origin).header("Authorization", TOKEN))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(options(URL)
                        .header("Origin", VIEWER)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS: the existing catch-all registration still serves other paths and does not admit the viewer")
    void existingCorsUnchanged() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://eventqr.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://eventqr.app"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", VIEWER)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the DataSource used here really is unreachable (sanity check for the no-DB assertions)")
    void dataSourceIsPoisoned(@Autowired DataSource dataSource) {
        assertThat(dataSource).isInstanceOf(PoisonedDataSource.class);
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
