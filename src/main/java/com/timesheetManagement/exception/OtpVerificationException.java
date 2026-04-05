package com.timesheetManagement.exception;

/**
 * Thrown when an OTP verification attempt fails — wrong code, too many
 * attempts, or no OTP has been issued for the account.
 * Maps to HTTP 400 Bad Request via {@link GlobalExceptionHandler}.
 */
public class OtpVerificationException extends RuntimeException {

    public OtpVerificationException(String message) {
        super(message);
    }
}

