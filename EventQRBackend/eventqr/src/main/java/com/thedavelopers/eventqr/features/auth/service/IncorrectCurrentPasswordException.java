package com.thedavelopers.eventqr.features.auth.service;

import com.thedavelopers.eventqr.shared.exceptions.BadRequestException;

/**
 * The current password given to change-password was wrong. Still a 400 with the same message as before; the
 * distinct type lets the controller count only these failures against the change-password rate limit.
 */
public class IncorrectCurrentPasswordException extends BadRequestException {

    public IncorrectCurrentPasswordException() {
        super("Current password is incorrect");
    }
}
