package com.timesheetManagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request DTO for POST /api/user/me/change-password.
 * The currently authenticated user must supply their current password and
 * a new strong password (with confirmation) to change it.
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    /**
     * Password strength rules (all must be satisfied):
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one digit
     * - At least one special character: @$!%*?&
     */
    @NotBlank(message = "New password is required")
    @Pattern(
        regexp  = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "New password must be at least 8 characters and include uppercase, lowercase, digit, and special character (@$!%*?&)"
    )
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}

