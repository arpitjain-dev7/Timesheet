package com.timesheetManagement.exception;

/**
 * Thrown when the password-reset token is missing, expired, or has already
 * been used. Maps to HTTP 400 Bad Request via {@link GlobalExceptionHandler}.
 */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}

