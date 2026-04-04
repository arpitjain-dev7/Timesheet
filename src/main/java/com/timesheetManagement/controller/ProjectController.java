package com.timesheetManagement.controller;

import com.timesheetManagement.dto.ProjectAssignmentRequest;
import com.timesheetManagement.dto.ProjectCreateRequest;
import com.timesheetManagement.dto.ProjectResponse;
import com.timesheetManagement.dto.ProjectUpdateRequest;
import com.timesheetManagement.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name        = "Project Management",
    description = """
        APIs for managing projects throughout their lifecycle.
        - **ADMIN / MANAGER**: create, update, deactivate, assign users, view all projects.
        - **USER**: view only projects assigned to them.
        All endpoints require a valid Bearer JWT token.
        """
)
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    // ── POST /api/projects ────────────────────────────────────────────────
    @Operation(
        summary     = "Create a new project",
        description = """
            Creates a new project in the system.
            - Allowed roles: **ADMIN**, **MANAGER**
            - The authenticated user is automatically recorded as the project creator.
            - `code` must be unique across all projects (e.g. `PROJ-001`).
            - `startDate` and `endDate` are optional; if both are provided, `endDate` must not be before `startDate`.
            - Newly created projects are **active** by default.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Project created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed — missing name, invalid code format, or endDate before startDate", content = @Content),
        @ApiResponse(responseCode = "409", description = "A project with the given code already exists", content = @Content),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN or MANAGER role required", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody ProjectCreateRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/projects → name='{}'", req.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(req, userDetails.getUsername()));
    }

    // ── POST /api/projects/assign ─────────────────────────────────────────
    @Operation(
        summary     = "Assign users to a project",
        description = """
            Assigns one or more users to an existing project.
            - Allowed roles: **ADMIN**, **MANAGER**
            - Users already assigned to the project are **silently skipped** (no error thrown).
            - Skipped usernames are returned in the `skipped` field of the response.
            - Cannot assign users to an **inactive / deactivated** project.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users assigned successfully; skipped list may be empty or contain already-assigned users"),
        @ApiResponse(responseCode = "400", description = "Validation failed — projectId or userIds missing", content = @Content),
        @ApiResponse(responseCode = "404", description = "Project or one of the specified users not found", content = @Content),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN or MANAGER role required", content = @Content)
    })
    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Map<String, Object>> assignProject(
            @Valid @RequestBody ProjectAssignmentRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/projects/assign → projectId={}, users={}",
                req.getProjectId(), req.getUserIds());
        List<String> skipped = projectService.assignProjectToUsers(req, userDetails.getUsername());
        return ResponseEntity.ok(Map.of(
                "message", "Project assigned successfully",
                "skipped", skipped));
    }

    // ── GET /api/projects ─────────────────────────────────────────────────
    @Operation(
        summary     = "Get all projects (paginated)",
        description = """
            Returns a paginated and sortable list of all projects in the system.
            - Allowed roles: **ADMIN**, **MANAGER**
            - Use `activeOnly=false` to include deactivated projects (default: `true`).
            - Supports sorting by any project field (e.g. `name`, `createdAt`, `startDate`).
            - Response includes pagination metadata: `currentPage`, `totalItems`, `totalPages`, `isLast`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paginated list of projects returned successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN or MANAGER role required", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Map<String, Object>> getAllProjects(
            @Parameter(description = "Filter to active projects only. Pass `false` to include deactivated projects.", example = "true")
            @RequestParam(defaultValue = "true")  boolean activeOnly,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0")     int page,

            @Parameter(description = "Number of records per page", example = "10")
            @RequestParam(defaultValue = "10")    int size,

            @Parameter(description = "Field to sort by (e.g. `name`, `createdAt`, `startDate`, `endDate`)", example = "name")
            @RequestParam(defaultValue = "name")  String sortBy,

            @Parameter(description = "Sort direction: `asc` for ascending, `desc` for descending", example = "asc")
            @RequestParam(defaultValue = "asc")   String sortDir) {

        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Page<ProjectResponse> result =
                projectService.getAllProjects(activeOnly, PageRequest.of(page, size, sort));

        log.info("GET /api/projects → activeOnly={}, total={}", activeOnly, result.getTotalElements());

        return ResponseEntity.ok(Map.of(
                "projects",    result.getContent(),
                "currentPage", result.getNumber(),
                "totalItems",  result.getTotalElements(),
                "totalPages",  result.getTotalPages(),
                "isLast",      result.isLast()
        ));
    }

    // ── GET /api/projects/my ──────────────────────────────────────────────
    @Operation(
        summary     = "Get my assigned projects",
        description = """
            Returns a list of all **active** projects assigned to the currently authenticated user.
            - Allowed roles: **ADMIN**, **MANAGER**, **USER**
            - Only returns projects where the user has an active assignment.
            - Deactivated projects are excluded from the result.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of assigned projects returned (empty list if none assigned)"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — valid Bearer token required", content = @Content)
    })
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<List<ProjectResponse>> getMyProjects(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                projectService.getProjectsAssignedToUser(userDetails.getUsername()));
    }

    // ── GET /api/projects/{id} ────────────────────────────────────────────
    @Operation(
        summary     = "Get a project by ID",
        description = """
            Fetches the full details of a single project by its unique ID.
            - Allowed roles: **ADMIN**, **MANAGER**, **USER**
            - Returns `404` if no project exists with the given ID.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project details returned successfully"),
        @ApiResponse(responseCode = "404", description = "Project not found with the given ID", content = @Content),
        @ApiResponse(responseCode = "403", description = "Access denied — authentication required", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','MANAGER','ADMIN')")
    public ResponseEntity<ProjectResponse> getById(
            @Parameter(description = "Unique ID of the project", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(projectService.getById(id));
    }

    // ── PUT /api/projects/{id} ────────────────────────────────────────────
    @Operation(
        summary     = "Update a project",
        description = """
            Partially updates an existing project. Only fields provided in the request body are updated — `null` fields are ignored.
            - Allowed roles: **ADMIN**, **MANAGER**
            - Updatable fields: `name`, `description`, `startDate`, `endDate`, `active`
            - Setting `active: false` **deactivates** the project (soft delete alternative).
            - `endDate` must not be before `startDate` if both are present.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project updated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed — e.g. endDate before startDate", content = @Content),
        @ApiResponse(responseCode = "404", description = "Project not found with the given ID", content = @Content),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN or MANAGER role required", content = @Content)
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<ProjectResponse> updateProject(
            @Parameter(description = "Unique ID of the project to update", example = "1", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ProjectUpdateRequest req) {
        log.info("PUT /api/projects/{} → {}", id, req);
        return ResponseEntity.ok(projectService.updateProject(id, req));
    }

    // ── DELETE /api/projects/{id} ─────────────────────────────────────────
    @Operation(
        summary     = "Deactivate a project (soft delete)",
        description = """
            Marks a project as **inactive** (soft delete). The project record is retained in the database.
            - Allowed roles: **ADMIN**, **MANAGER**
            - Deactivated projects are excluded from `GET /api/projects` (unless `activeOnly=false`).
            - Users can no longer log timesheets against a deactivated project.
            - To reactivate, use `PUT /api/projects/{id}` with `active: true`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project deactivated successfully"),
        @ApiResponse(responseCode = "404", description = "Project not found with the given ID", content = @Content),
        @ApiResponse(responseCode = "403", description = "Access denied — ADMIN or MANAGER role required", content = @Content)
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<Map<String, String>> deactivate(
            @Parameter(description = "Unique ID of the project to deactivate", example = "1", required = true)
            @PathVariable Long id) {
        projectService.deactivateProject(id);
        return ResponseEntity.ok(Map.of("message", "Project " + id + " deactivated successfully"));
    }
}
