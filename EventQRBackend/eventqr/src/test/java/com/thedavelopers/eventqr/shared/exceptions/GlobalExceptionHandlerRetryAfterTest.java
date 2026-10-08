package com.thedavelopers.eventqr.shared.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.thedavelopers.eventqr.shared.response.ErrorResponse;

class GlobalExceptionHandlerRetryAfterTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");

    @Test
    void retryAfterHeaderIsSetWhenSecondsArePositive() {
        ResponseEntity<ErrorResponse> response =
                handler.handleTooManyRequests(new TooManyRequestsException("slow down", 42), request);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void noRetryAfterHeaderWhenSecondsAreUnknown() {
        ResponseEntity<ErrorResponse> plain =
                handler.handleTooManyRequests(new TooManyRequestsException("slow down"), request);
        ResponseEntity<ErrorResponse> zero =
                handler.handleTooManyRequests(new TooManyRequestsException("slow down", 0), request);

        assertThat(plain.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
        assertThat(zero.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
        assertThat(plain.getStatusCode().value()).isEqualTo(429);
    }
}
