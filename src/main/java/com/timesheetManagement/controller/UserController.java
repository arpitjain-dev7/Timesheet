package com.timesheetManagement.controller;

import com.timesheetManagement.dto.ChangePasswordRequest;
import com.timesheetManagement.dto.UserRequestDTO;
import com.timesheetManagement.dto.UserResponseDTO;
import com.timesheetManagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User", description = "Full CRUD + profile endpoints — USER and ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // ── GET /api/user/me ──────────────────────────────────────────────────
    @Operation(summary = "Get the currently authenticated user's full profile")
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponseDTO> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/user/me → {}", userDetails.getUsername());
        return ResponseEntity.ok(userService.getUserByUsernameDTO(userDetails.getUsername()));
    }

    // ── GET /api/user/dashboard ───────────────────────────────────────────
    @Operation(summary = "User dashboard welcome message")
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Map<String, String>> dashboard(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome, " + userDetails.getUsername() + "!",
                "access",  "USER / ADMIN"
        ));
    }

    // ── GET /api/user/{id} ────────────────────────────────────────────────
    @Operation(summary = "Get a user by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        log.info("GET /api/user/{}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // ── GET /api/user ─────────────────────────────────────────────────────
    @Operation(summary = "Get all users — paginated & sortable (ADMIN / MANAGER)",
               description = "Params: page (0-based), size, sortBy, sortDir (asc|desc)")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Field to sort by (e.g. username, email, createdAt)")
            @RequestParam(defaultValue = "id") String sortBy,
            @Parameter(description = "Sort direction: asc or desc")
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Page<UserResponseDTO> result = userService.getAllUsers(PageRequest.of(page, size, sort));
        log.info("GET /api/user → page={}, size={}, total={}", page, size, result.getTotalElements());

        return ResponseEntity.ok(Map.of(
                "users",       result.getContent(),
                "currentPage", result.getNumber(),
                "totalItems",  result.getTotalElements(),
                "totalPages",  result.getTotalPages(),
                "isLast",      result.isLast()
        ));
    }

    // ── PUT /api/user/{id} ────────────────────────────────────────────────
    @Operation(summary = "Update a user's profile",
               description = "Send as multipart/form-data with two named parts:\n" +
                             "• **dto**   — JSON part (Content-Type: application/json) with profile fields.\n" +
                             "• **photo** — (optional) image file part.\n" +
                             "Leave password blank in dto to keep the existing password.")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestPart("dto") UserRequestDTO dto,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        log.info("PUT /api/user/{}", id);
        return ResponseEntity.ok(userService.updateUser(id, dto, photo));
    }

    // ── DELETE /api/user/{id} ─────────────────────────────────────────────
    @Operation(summary = "Delete a user by ID — ADMIN / MANAGER")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        log.info("DELETE /api/user/{}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User with id " + id + " deleted successfully"));
    }

    // ── POST /api/user/me/change-password ─────────────────────────────────
    @Operation(
        summary     = "Change password for the currently authenticated user",
        description = """
            Allows the currently logged-in user to change their own password.
            - `currentPassword` must match the user's existing password.
            - `newPassword` must satisfy strength requirements: min 8 chars, uppercase, lowercase, digit, special char (@$!%*?&).
            - `newPassword` and `confirmPassword` must match exactly.
            - `newPassword` must differ from `currentPassword`.
            - On success: all active refresh tokens are invalidated (forces re-login on all devices).
            - Requires a valid JWT in the Authorization header.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed / current password incorrect / passwords don't match"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated — missing or invalid JWT")
    })
    @PostMapping("/me/change-password")
    @PreAuthorize("hasAnyRole('USER','ADMIN','MANAGER')")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        log.info("POST /api/user/me/change-password → {}", username);
        userService.changePassword(username, request);
        return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully. Please log in again with your new password."));
    }

    // ── PUT /api/user/{id}/photo ──────────────────────────────────────────
    @Operation(summary = "Upload or replace profile photo for a user",
               description = "Send as multipart/form-data with field name 'photo'. " +
                             "Allowed types: JPEG, PNG, GIF, WEBP. Max size: 5 MB.")
    @PutMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponseDTO> uploadPhoto(
            @PathVariable Long id,
            @RequestPart("photo") MultipartFile photo) {
        log.info("PUT /api/user/{}/photo", id);
        return ResponseEntity.ok(userService.updatePhoto(id, photo));
    }
}
