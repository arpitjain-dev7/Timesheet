package com.timesheetManagement.controller;

import com.timesheetManagement.dto.UserResponseDTO;
import com.timesheetManagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "System administration — ADMIN role only")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserService userService;

    // ── GET /api/admin/dashboard ──────────────────────────────────────────
    @Operation(summary = "Admin dashboard")
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminDashboard() {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the Admin Dashboard!",
                "access",  "ADMIN only"));
    }

    // ── GET /api/admin/users ──────────────────────────────────────────────
    @Operation(summary = "List all registered users (paginated) — ADMIN only")
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "10")  int size,
            @RequestParam(defaultValue = "id")  String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();

        Page<UserResponseDTO> result = userService.getAllUsers(PageRequest.of(page, size, sort));
        log.info("GET /api/admin/users → total={}", result.getTotalElements());

        return ResponseEntity.ok(Map.of(
                "users",       result.getContent(),
                "currentPage", result.getNumber(),
                "totalItems",  result.getTotalElements(),
                "totalPages",  result.getTotalPages(),
                "isLast",      result.isLast()));
    }
}
