package com.timesheetManagement.dto;

import com.timesheetManagement.entity.Gender;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.TypeOfEmployment;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for {@code POST /api/users}.
 *
 * <p>Manager-assignment rules (enforced in the service layer):
 * <ul>
 *   <li>{@code role = ROLE_USER}              → {@code managerId} is <b>required</b>.</li>
 *   <li>{@code role = ROLE_MANAGER/ROLE_ADMIN} → {@code managerId} must be <b>null</b>.</li>
 *   <li>The referenced manager must exist and hold {@code ROLE_MANAGER}.</li>
 *   <li>{@code managerId} must not refer to the user being created (no self-assignment).</li>
 * </ul>
 */
@Data
public class CreateUserRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp  = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Password must be at least 8 characters and include uppercase, lowercase, digit, and special character (@$!%*?&)"
    )
    private String password;

    private Gender gender;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;

    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;

    private TypeOfEmployment typeOfEmployment;

    /**
     * Desired role for the new account. Defaults to {@code ROLE_USER} when absent.
     */
    private RoleName role;

    /**
     * Primary key of an existing user with {@code ROLE_MANAGER}.
     * <ul>
     *   <li>Required when {@code role = ROLE_USER}.</li>
     *   <li>Must be {@code null} when {@code role = ROLE_MANAGER} or {@code ROLE_ADMIN}.</li>
     * </ul>
     */
    private Long managerId;
}

