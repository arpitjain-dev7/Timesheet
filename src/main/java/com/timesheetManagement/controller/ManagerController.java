package com.timesheetManagement.controller;

import com.timesheetManagement.dto.CreateUserRequest;
import com.timesheetManagement.dto.ManagerResponse;
import com.timesheetManagement.dto.UserRequestDTO;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Endpoints under {@code /api/users} (plural) that handle manager-aware
 * user creation, the managers dropdown, and a PUT alias for user updates.
 *
 * <p>The update endpoints are an alias for {@code PUT /api/user/{id}}
 * (singular) in {@link UserController} so that frontends using the plural
 * path still work correctly.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users (Manager-Aware)", description = "Create users with manager assignment, fetch managers dropdown, and update users")
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

    // ── PUT /api/users/{id} — JSON only (alias for PUT /api/user/{id}) ────
    @Operation(
        summary     = "Update a user's profile — JSON body (alias for PUT /api/user/{id})",
        description = """
            Alias endpoint so frontends using the plural `/api/users/{id}` path
            work correctly. Delegates directly to the same service method as
            `PUT /api/user/{id}`.

            All profile fields including `role` and `managerId` are accepted.
            Leave `password` blank/omit to keep the existing password.

            Manager-assignment rules:
            - `role = ROLE_USER`    → `managerId` is required (non-null).
            - `role = ROLE_MANAGER` → `managerId` must be null.
            - `role = ROLE_ADMIN`   → `managerId` must be null.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User updated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "User or manager not found"),
        @ApiResponse(responseCode = "409", description = "Duplicate username/email or invalid manager assignment")
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<UserResponseDTO> updateUserJson(
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDTO dto) {
        log.info("PUT /api/users/{} (JSON alias)", id);
        return ResponseEntity.ok(userService.updateUser(id, dto, null));
    }

    // ── PUT /api/users/{id} — multipart (alias for PUT /api/user/{id}) ────
    @Operation(
        summary     = "Update a user's profile with optional photo — multipart (alias for PUT /api/user/{id})",
        description = """
            Multipart alias for frontends using the plural path.
            Send as `multipart/form-data` with:
            - **dto**   — JSON part (Content-Type: application/json)
            - **photo** — optional image file (JPEG/PNG/GIF/WEBP, max 5 MB)
            """
    )
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<UserResponseDTO> updateUserMultipart(
            @PathVariable Long id,
            @Valid @RequestPart("dto") UserRequestDTO dto,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        log.info("PUT /api/users/{} (multipart alias)", id);
        return ResponseEntity.ok(userService.updateUser(id, dto, photo));
    }
}




