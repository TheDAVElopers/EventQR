package com.thedavelopers.eventqr.features.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.features.auth.service.AuthService;
import com.thedavelopers.eventqr.features.auth.service.ChangePasswordService;
import com.thedavelopers.eventqr.features.auth.service.PasswordResetService;
import com.thedavelopers.eventqr.features.users.service.UserService;
import com.thedavelopers.eventqr.shared.constants.AccountRole;
import com.thedavelopers.eventqr.shared.exceptions.ForbiddenException;
import com.thedavelopers.eventqr.shared.exceptions.GlobalExceptionHandler;
import com.thedavelopers.eventqr.shared.exceptions.UnauthorizedException;
import com.thedavelopers.eventqr.shared.security.ForgotPasswordRateLimiter;
import com.thedavelopers.eventqr.shared.security.JwtService;
import com.thedavelopers.eventqr.shared.security.LoginRateLimiter;

/** Login throttling as seen through the controller and the real exception handler. */
class AuthControllerLoginRateLimitTest {

    private static final String GENERIC_401 = "Invalid email or password";
    private static final String BLOCKED = "Too many login attempts. Please try again later.";

    private AuthService authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, mock(UserService.class),
                        mock(JwtService.class), mock(PasswordResetService.class), mock(ChangePasswordService.class),
                        mock(ForgotPasswordRateLimiter.class), new LoginRateLimiter(30, 6, 50)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(authService.login(any())).thenThrow(new UnauthorizedException(GENERIC_401));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private LoginResponse session() {
        return new LoginResponse("access", UUID.randomUUID(), "jane@example.com", "Jane", AccountRole.ATTENDEE,
                "Login successful", "refresh");
    }

    private static String statusAndBody(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        String body = result.getResponse().getContentAsString()
                .replaceAll("\"timestamp\":[^,]*,", "\"timestamp\":\"-\",");
        return result.getResponse().getStatus() + "|" + body;
    }

    private void failWithUnauthorized() {
        doThrow(new UnauthorizedException(GENERIC_401)).when(authService).login(any(LoginRequest.class));
    }

    @Test
    void sixWrongPasswordsAre401ThenThe429CarriesRetryAfterAndTheStandardErrorBody() throws Exception {
        for (int i = 0; i < 6; i++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }

        login("jane@example.com", "bad")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value(BLOCKED))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));
    }

    @Test
    void anUnknownEmailAndAKnownEmailWithAWrongPasswordLookIdentical() throws Exception {
        // AuthService answers both paths with the same UnauthorizedException (the DUMMY_HASH timing
        // equalisation for unknown emails is covered in AuthServiceTest), so the controller must not
        // distinguish them either: same status sequence and same bodies, including the 429.
        List<String> known = new ArrayList<>();
        List<String> ghost = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            known.add(statusAndBody(login("known@example.com", "bad")));
            ghost.add(statusAndBody(login("ghost@example.com", "bad")));
        }

        assertThat(known).isEqualTo(ghost);
        assertThat(known.subList(0, 6)).allMatch(r -> r.startsWith("401|") && r.contains(GENERIC_401));
        assertThat(known.get(6)).startsWith("429|").contains(BLOCKED);
    }

    @Test
    void aSuccessfulLoginResetsTheEmailAndPairCounters() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }
        doReturn(session()).when(authService).login(any(LoginRequest.class));
        login("jane@example.com", "good").andExpect(status().isOk());

        failWithUnauthorized();
        for (int i = 0; i < 6; i++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }
        login("jane@example.com", "bad").andExpect(status().isTooManyRequests());
    }

    @Test
    void aDisabledAccountIs403AndTheAttemptIsRefunded() throws Exception {
        doThrow(new ForbiddenException("Account is disabled. Contact support.")).when(authService).login(any(LoginRequest.class));

        for (int i = 0; i < 30; i++) {
            login("jane@example.com", "right").andExpect(status().isForbidden());
        }
    }

    @Test
    void aRuntimeFailureInTheServiceIsRefundedAndNeverCountsTowardTheLockout() throws Exception {
        RuntimeException[] failures = {
                new IllegalStateException("boom"),
                new QueryTimeoutException("db down"),
                new NullPointerException("bug")
        };
        for (RuntimeException failure : failures) {
            doThrow(failure).when(authService).login(any(LoginRequest.class));
            for (int i = 0; i < 20; i++) {
                login("jane@example.com", "pw").andExpect(status().isInternalServerError());
            }
        }

        // Nothing was recorded: the full six real guesses are still available afterwards.
        failWithUnauthorized();
        for (int i = 0; i < 6; i++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }
        login("jane@example.com", "bad").andExpect(status().isTooManyRequests());
    }

    @Test
    void aServerErrorRefundsOnlyItsOwnSlotAndKeepsEarlierGuessesCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }
        doThrow(new IllegalStateException("boom")).when(authService).login(any(LoginRequest.class));
        login("jane@example.com", "pw").andExpect(status().isInternalServerError());

        failWithUnauthorized();
        login("jane@example.com", "bad").andExpect(status().isUnauthorized()); // sixth real guess
        login("jane@example.com", "bad").andExpect(status().isTooManyRequests());
    }

    @Test
    void aBlockedRequestNeverReachesTheServiceSoThe429IsNeverRecordedOrRefunded() throws Exception {
        for (int i = 0; i < 6; i++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 10; i++) {
            login("jane@example.com", "bad").andExpect(status().isTooManyRequests());
        }
        verify(authService, times(6)).login(any(LoginRequest.class));
    }

    @Test
    void theCorrectPasswordWhileThrottledIsStill429AndTheServiceIsNotCalled() throws Exception {
        for (int i = 0; i < 6; i++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }
        doReturn(session()).when(authService).login(any(LoginRequest.class));

        login("jane@example.com", "right").andExpect(status().isTooManyRequests());
        verify(authService, times(6)).login(any(LoginRequest.class));
    }

    @Test
    void aMobileSubmitThatRetriesOnceBurnsTwoAttemptsSoFourSubmitsAreBlocked() throws Exception {
        for (int submit = 1; submit <= 3; submit++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }
        login("jane@example.com", "bad").andExpect(status().isTooManyRequests());
    }

    @Test
    void aMalformedBodyDoesNotConsumeAnAttempt() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"not-an-email\",\"password\":\"\"}")).andExpect(status().isBadRequest());
        }
        login("jane@example.com", "bad").andExpect(status().isUnauthorized());
    }

    @Test
    void anOversizedPasswordIs400BeforeTheLimiterAndTheServiceAreTouched() throws Exception {
        String huge = "a".repeat(129);
        for (int i = 0; i < 40; i++) {
            login("jane@example.com", huge).andExpect(status().isBadRequest());
        }
        verify(authService, org.mockito.Mockito.never()).login(any(LoginRequest.class));
        // 40 rejected requests consumed no attempts: all six real guesses are still available.
        for (int i = 0; i < 6; i++) {
            login("jane@example.com", "bad").andExpect(status().isUnauthorized());
        }
        login("jane@example.com", "a".repeat(128)).andExpect(status().isTooManyRequests());
    }

    @Test
    void clientErrorsFromTheServiceStayCountedAndOnlyServerFaultsAreRefunded() throws Exception {
        RuntimeException[] clientErrors = {
                new com.thedavelopers.eventqr.shared.exceptions.BadRequestException("bad"),
                new com.thedavelopers.eventqr.shared.exceptions.ConflictException("conflict"),
                new com.thedavelopers.eventqr.shared.exceptions.ResourceNotFoundException("nf"),
                new IllegalArgumentException("illegal"),
                new org.springframework.dao.DataIntegrityViolationException("dup"),
        };
        for (RuntimeException failure : clientErrors) {
            setUp();
            doThrow(failure).when(authService).login(any(LoginRequest.class));
            for (int i = 0; i < 6; i++) {
                login("jane@example.com", "pw").andReturn();
            }
            login("jane@example.com", "pw").andExpect(status().isTooManyRequests());
        }
    }
}
