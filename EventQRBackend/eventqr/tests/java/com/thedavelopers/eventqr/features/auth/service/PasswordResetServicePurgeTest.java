package com.thedavelopers.eventqr.features.auth.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.thedavelopers.eventqr.features.auth.repository.PasswordResetTokenRepository;
import com.thedavelopers.eventqr.features.qremail.service.EmailGatewayService;
import com.thedavelopers.eventqr.features.users.repository.UserProfileRepository;

class PasswordResetServicePurgeTest {

    @Test
    void purgeExpiredTokensDeletesEverythingExpiredBeforeNow() {
        PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
        UserProfileRepository userRepository = mock(UserProfileRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        EmailGatewayService emailGatewayService = mock(EmailGatewayService.class);
        when(tokenRepository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(3);

        PasswordResetService service = new PasswordResetService(
                tokenRepository, userRepository, passwordEncoder, emailGatewayService, "https://eventqr.app");

        service.purgeExpiredTokens();

        verify(tokenRepository).deleteByExpiresAtBefore(any(Instant.class));
    }
}