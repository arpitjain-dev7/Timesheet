package com.timesheetManagement.controller;

import com.timesheetManagement.dto.CreateUserRequest;
import com.timesheetManagement.dto.CsvBulkImportResponse;
import com.timesheetManagement.dto.ManagerResponse;
import com.timesheetManagement.dto.UserRequestDTO;
import com.timesheetManagement.dto.UserResponseDTO;
import com.timesheetManagement.service.CsvUserImportService;
import com.timesheetManagement.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users (Manager-Aware)", description = "Create users, bulk CSV import, managers dropdown, and update users")
@SecurityRequirement(name = "bearerAuth")
public class ManagerController {

    private final UserService          userService;
    private final CsvUserImportService csvUserImportService;

    @Operation(summary = "Create a new user with manager assignment")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User created"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "404", description = "Manager not found"),
        @ApiResponse(responseCode = "409", description = "Username/email conflict or invalid manager assignment")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("POST /api/users -> username='{}'", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUserWithManager(request));
    }

    @Operation(summary = "Bulk-create users from CSV file (ADMIN/MANAGER only)",
        description = "Required headers: firstName,lastName,username,email,password. Optional: gender,location,designation,typeOfEmployment,role,managerId. Each row its own transaction. Welcome email async. Max 500 rows.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Import completed",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = CsvBulkImportResponse.class))),
        @ApiResponse(responseCode = "400", description = "File missing, wrong type, or missing required CSV headers"),
        @ApiResponse(responseCode = "403", description = "ADMIN or MANAGER role required")
    })
    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<CsvBulkImportResponse> importUsersFromCsv(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/users/import/csv - uploadedBy='{}', file='{}', size={} bytes",
                userDetails.getUsername(), file.getOriginalFilename(), file.getSize());
        CsvBulkImportResponse response = csvUserImportService.importUsers(file);
        log.info("POST /api/users/import/csv - DONE uploadedBy='{}': total={}, success={}, failed={}",
                userDetails.getUsername(), response.getTotalRows(), response.getSuccessCount(), response.getFailureCount());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fetch all managers for UI dropdown")
    @GetMapping("/managers")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<List<ManagerResponse>> getAllManagers() {
        log.info("GET /api/users/managers");
        return ResponseEntity.ok(userService.getAllManagers());
    }

    @Operation(summary = "Update a user - JSON body (alias for PUT /api/user/{id})")
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<UserResponseDTO> updateUserJson(
            @PathVariable Long id, @Valid @RequestBody UserRequestDTO dto) {
        log.info("PUT /api/users/{} (JSON alias)", id);
        return ResponseEntity.ok(userService.updateUser(id, dto, null));
    }

    @Operation(summary = "Update a user with optional photo - multipart (alias for PUT /api/user/{id})")
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