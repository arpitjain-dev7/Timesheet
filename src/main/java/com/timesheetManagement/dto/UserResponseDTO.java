package com.timesheetManagement.dto;

import com.timesheetManagement.entity.Gender;
import com.timesheetManagement.entity.TypeOfEmployment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String email;            // ← password intentionally excluded
    private Gender gender;
    private String location;
    private String designation;
    private String managerEmail;
    private TypeOfEmployment typeOfEmployment;
    private String photoUrl;
    private List<String> roles;

    // ── Manager FK fields (null for MANAGER / ADMIN accounts) ─────────────
    /** Primary key of the assigned manager user, or {@code null} if none. */
    private Long   managerId;
    /** Full name ({@code firstName + " " + lastName}) of the assigned manager. */
    private String managerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

