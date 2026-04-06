package com.timesheetManagement.controller;

import com.timesheetManagement.dto.CreateUserRequest;
import com.timesheetManagement.dto.ManagerResponse;
import com.timesheetManagement.dto.UserResponseDTO;
import com.timesheetManagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints under {@code /api/users} (plural) that handle manager-aware
 * user creation and the managers dropdown.
 *
 * <p>Kept separate from {@link UserController} ({@code /api/user}) so the
 * two path namespaces stay clean and the existing endpoints are untouched.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users (Manager-Aware)", description = "Create users with manager assignment and fetch managers dropdown")
@SecurityRequirement(name = "bearerAuth")
public class ManagerController {

    private final UserService userService;

    // ── POST /api/users ───────────────────────────────────────────────────
    @Operation(
        summary     = "Create a new user with manager assignment",
        description = """
            Creates a user account enforcing the following rules in the service layer:

            | Role          | managerId       |
            |---------------|-----------------|
            | ROLE_USER     | **Required**    |
            | ROLE_MANAGER  | Must be null    |
            | ROLE_ADMIN    | Must be null    |

            Additional validations:
            - `managerId` must exist in the database (404 if not found).
            - The referenced user must hold `ROLE_MANAGER` (409 if not).
            - `managerId` cannot refer to the account being created (self-assignment guard).
            - Username and email must be unique (409 if duplicate).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed — missing or invalid fields"),
        @ApiResponse(responseCode = "404", description = "Manager not found (managerId does not exist)"),
        @ApiResponse(responseCode = "409", description = "Username/email conflict, or invalid manager-role assignment")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<UserResponseDTO> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        log.info("POST /api/users → username='{}'", request.getUsername());
        UserResponseDTO created = userService.createUserWithManager(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── GET /api/users/managers ───────────────────────────────────────────
    @Operation(
        summary     = "Fetch all managers (for UI dropdown)",
        description = """
            Returns every user whose role is ROLE_MANAGER, sorted alphabetically
            by first name then last name.

            The response contains only the minimal fields needed to build a
            "select a manager" dropdown:
            - `id`        — pass this as `managerId` when creating a user
            - `firstName` / `lastName`
            - `email`

            Single optimised query — no N+1 round-trips.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of managers returned (may be empty)")
    })
    @GetMapping("/managers")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<List<ManagerResponse>> getAllManagers() {
        log.info("GET /api/users/managers");
        return ResponseEntity.ok(userService.getAllManagers());
    }
}

