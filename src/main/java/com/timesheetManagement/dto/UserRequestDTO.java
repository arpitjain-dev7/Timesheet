package com.timesheetManagement.dto;

import com.timesheetManagement.entity.Gender;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.TypeOfEmployment;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequestDTO {

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
    @Email(message = "Email should be valid")
    private String email;

    // Password is optional on update — leave blank to keep existing password
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private Gender gender;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;

    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;

    @Email(message = "Manager email should be valid")
    private String managerEmail;

    private TypeOfEmployment typeOfEmployment;

    // Optional role for registration; defaults to ROLE_USER
    private RoleName role;

    /**
     * ID of the manager (a user with ROLE_MANAGER) to assign to this user.
     *
     * <p>Update rules (enforced in the service layer — never trust the UI):
     * <ul>
     *   <li>If effective role is {@code ROLE_MANAGER} or {@code ROLE_ADMIN} →
     *       this field <b>must be null</b>; any non-null value is rejected (409).</li>
     *   <li>If effective role is {@code ROLE_USER} and a value is supplied →
     *       the referenced user must exist (404) and hold {@code ROLE_MANAGER} (409).</li>
     *   <li>Cannot equal the id of the user being updated (self-assignment → 409).</li>
     *   <li>If omitted ({@code null}) and role is not changing, the existing manager
     *       is preserved (partial-update friendly).</li>
     *   <li>If omitted and role is changing <em>to</em> {@code ROLE_USER} from a
     *       non-user role, and the user has no existing manager → 409.</li>
     * </ul>
     */
    private Long managerId;
}

