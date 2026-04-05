package com.timesheetManagement.exception;

/**
 * Thrown when the OTP submitted by the user has passed its expiry time.
 * Maps to HTTP 410 Gone via {@link GlobalExceptionHandler}.
 */
public class OtpExpiredException extends RuntimeException {

    public OtpExpiredException(String message) {
        super(message);
    }
}

