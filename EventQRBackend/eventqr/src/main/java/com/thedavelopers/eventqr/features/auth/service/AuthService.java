package com.thedavelopers.eventqr.features.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.thedavelopers.eventqr.features.auth.model.dto.LoginRequest;
import com.thedavelopers.eventqr.features.auth.model.dto.LoginResponse;
import com.thedavelopers.eventqr.shared.exception.ResourceNotFoundException;
import com.thedavelopers.eventqr.shared.port.AttendeeDirectoryPort;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AttendeeDirectoryPort attendeeDirectoryPort;

    public AuthService(AttendeeDirectoryPort attendeeDirectoryPort) {
        this.attendeeDirectoryPort = attendeeDirectoryPort;
    }

    public LoginResponse login(LoginRequest request) {
        var attendee = attendeeDirectoryPort.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for email " + request.email()));
        return new LoginResponse("JWT_NOT_CONFIGURED", attendee.userId(), attendee.email(), attendee.fullName(),
                attendee.role(), "JWT support is not enabled in the current dependency set");
    }
}