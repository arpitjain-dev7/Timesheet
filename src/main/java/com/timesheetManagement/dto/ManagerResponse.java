package com.timesheetManagement.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/** Minimal projection of a manager returned by GET /api/users/managers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerResponse {
    private Long   id;
    private String firstName;
    private String lastName;
    private String email;
}
