package com.timesheetManagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for POST /api/auth/verify-otp.
 * Returns the one-time reset token the client must use within 15 minutes.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerifyOtpResponse {

    /** Raw (unhashed) reset token — send this in the reset-password request. */
    private String resetToken;

    /** How many minutes until the reset token expires. */
    private int expiresInMinutes;

    /** Human-readable status message. */
    private String message;
}

