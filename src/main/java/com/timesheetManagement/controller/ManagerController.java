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

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
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
        description = "Required headers: firstName,lastName,username,email,password. "
            + "Optional: gender,location,designation,typeOfEmployment,role,managerEmail. "
            + "Use managerEmail (not managerId) to assign a manager — the service resolves the email to the manager's id internally. "
            + "Each row is its own transaction. Welcome email sent async per user. Max 500 rows.")
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

    // ── GET /api/users/import/csv/template ────────────────────────────────
    @Operation(
        summary     = "Download a sample CSV template for bulk user import (ADMIN/MANAGER only)",
        description = """
            Returns a ready-to-fill `.csv` file containing all supported column headers
            and representative sample rows.

            **Column guide:**

            | Column             | Required | Allowed values |
            |--------------------|----------|----------------|
            | firstName          | ✅       | Text (max 50)  |
            | lastName           | ✅       | Text (max 50)  |
            | username           | ✅       | 3-50 chars, unique |
            | email              | ✅       | Valid email, unique |
            | password           | ✅       | Min 8 chars · uppercase · lowercase · digit · special char (@$!%*?&) |
            | gender             | optional | MALE / FEMALE / OTHER / PREFER_NOT_TO_SAY |
            | location           | optional | Text (max 100) |
            | designation        | optional | Text (max 100) |
            | typeOfEmployment   | optional | FULL_TIME / PART_TIME / CONTRACT / INTERNSHIP / FREELANCE |
            | role               | optional | ROLE_USER (default) / ROLE_MANAGER / ROLE_ADMIN |
            | managerEmail       | required for ROLE_USER | Email of an existing ROLE_MANAGER user |

            **Rules:**
            - `managerEmail` is **required** when `role = ROLE_USER`.
            - `managerEmail` must be **blank** when `role = ROLE_MANAGER` or `ROLE_ADMIN`.
            - Upload the filled file to `POST /api/users/import/csv`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "CSV template file returned as attachment"),
        @ApiResponse(responseCode = "403", description = "ADMIN or MANAGER role required")
    })
    @GetMapping("/import/csv/template")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ByteArrayResource> downloadCsvTemplate() {
        log.info("GET /api/users/import/csv/template — serving sample CSV");

        String csv = buildSampleCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        ByteArrayResource resource = new ByteArrayResource(bytes);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"users-import-template.csv\"")
                .contentType(org.springframework.http.MediaType
                        .parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(resource);
    }

    /**
     * Builds the sample CSV string with headers + illustrative data rows.
     *
     * <p>Rows are designed to demonstrate every column and both role types
     * (ROLE_USER with managerEmail, ROLE_MANAGER without managerEmail) so
     * the downloader has a concrete reference before filling real data.
     */
    private String buildSampleCsv() {
        StringBuilder sb = new StringBuilder();

        // ── Header row ────────────────────────────────────────────────────
        sb.append("firstName,lastName,username,email,password,")
          .append("gender,location,designation,typeOfEmployment,role,managerEmail")
          .append("\n");

        // ── Sample ROLE_USER rows ─────────────────────────────────────────
        sb.append("John,Doe,john.doe,john.doe@company.com,Welcome@1234,")
          .append("MALE,New York,Software Engineer,FULL_TIME,ROLE_USER,manager@company.com")
          .append("\n");

        sb.append("Jane,Smith,jane.smith,jane.smith@company.com,Welcome@1234,")
          .append("FEMALE,Chicago,QA Engineer,FULL_TIME,ROLE_USER,manager@company.com")
          .append("\n");

        sb.append("Robert,Brown,robert.brown,robert.brown@company.com,Welcome@1234,")
          .append("MALE,Austin,DevOps Engineer,CONTRACT,ROLE_USER,manager@company.com")
          .append("\n");

        sb.append("Emily,Clark,emily.clark,emily.clark@company.com,Welcome@1234,")
          .append("FEMALE,Seattle,Business Analyst,PART_TIME,ROLE_USER,manager@company.com")
          .append("\n");

        sb.append("Priya,Sharma,priya.sharma,priya.sharma@company.com,Welcome@1234,")
          .append("FEMALE,Noida,Data Analyst,FULL_TIME,ROLE_USER,manager@company.com")
          .append("\n");

        sb.append("Rahul,Gupta,rahul.gupta,rahul.gupta@company.com,Welcome@1234,")
          .append("MALE,Bangalore,Mobile Developer,INTERNSHIP,ROLE_USER,manager@company.com")
          .append("\n");

        sb.append("David,Martinez,david.martinez,david.martinez@company.com,Welcome@1234,")
          .append("MALE,Denver,UI/UX Designer,FREELANCE,ROLE_USER,manager@company.com")
          .append("\n");

        // ── Sample ROLE_MANAGER rows (managerEmail must be blank) ─────────
        sb.append("Tom,Jackson,tom.jackson,tom.jackson@company.com,Manager@9876,")
          .append("MALE,New York,Engineering Manager,FULL_TIME,ROLE_MANAGER,")
          .append("\n");

        sb.append("Lisa,White,lisa.white,lisa.white@company.com,Manager@9876,")
          .append("FEMALE,Chicago,Project Manager,FULL_TIME,ROLE_MANAGER,")
          .append("\n");

        return sb.toString();
    }

    // ── GET /api/users/managers ────────────────────────────────────────────
    @Operation(summary = "Fetch all managers for UI dropdown")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of managers (may be empty)")
    })
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