package com.timesheetManagement.service;

import com.timesheetManagement.dto.ProjectAssignmentRequest;
import com.timesheetManagement.dto.ProjectCreateRequest;
import com.timesheetManagement.dto.ProjectResponse;
import com.timesheetManagement.dto.ProjectUpdateRequest;
import com.timesheetManagement.dto.UserResponseDTO;
import com.timesheetManagement.entity.Project;
import com.timesheetManagement.entity.ProjectAssignment;
import com.timesheetManagement.entity.User;
import com.timesheetManagement.exception.DuplicateEntryException;
import com.timesheetManagement.exception.ResourceNotFoundException;
import com.timesheetManagement.repository.ProjectAssignmentRepository;
import com.timesheetManagement.repository.ProjectRepository;
import com.timesheetManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository           projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final UserRepository              userRepository;

    // ── CREATE PROJECT (MANAGER) ───────────────────────────────────────────
    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest req, String managerUsername) {
        log.info("[PROJECT_CREATE] manager='{}', name='{}'", managerUsername, req.getName());

        if (req.getCode() != null && !req.getCode().isBlank()
                && projectRepository.existsByCode(req.getCode())) {
            throw new DuplicateEntryException(
                    "A project with code '" + req.getCode() + "' already exists");
        }

        if (req.getStartDate() != null && req.getEndDate() != null
                && req.getEndDate().isBefore(req.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        User manager = findUser(managerUsername);

        Project project = Project.builder()
                .name(req.getName())
                .description(req.getDescription())
                .code(req.getCode())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .createdBy(manager)
                .active(true)
                .build();

        Project saved = projectRepository.save(project);
        log.info("[PROJECT_CREATE] ✅ id={}, name='{}'", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    // ── ASSIGN PROJECT TO USERS (MANAGER) ─────────────────────────────────
    @Transactional
    public List<String> assignProjectToUsers(ProjectAssignmentRequest req, String managerUsername) {
        log.info("[PROJECT_ASSIGN] manager='{}', projectId={}, users={}",
                managerUsername, req.getProjectId(), req.getUserIds());

        Project project = projectRepository.findById(req.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + req.getProjectId()));

        if (!project.isActive()) {
            throw new IllegalArgumentException(
                    "Cannot assign users to inactive project '" + project.getName() + "'");
        }

        User manager    = findUser(managerUsername);
        List<String> skipped = new ArrayList<>();

        for (Long userId : req.getUserIds()) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "User not found with id: " + userId));

            if (assignmentRepository.existsByProjectAndUser(project, user)) {
                skipped.add(user.getUsername() + " (already assigned)");
                continue;
            }

            assignmentRepository.save(ProjectAssignment.builder()
                    .project(project)
                    .user(user)
                    .assignedBy(manager)
                    .build());

            log.debug("[PROJECT_ASSIGN] assigned user='{}' to project='{}'",
                    user.getUsername(), project.getName());
        }

        log.info("[PROJECT_ASSIGN] ✅ projectId={} — skipped: {}", project.getId(), skipped);
        return skipped;
    }

    // ── GET ASSIGNED PROJECTS (USER) ───────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsAssignedToUser(String username) {
        User user = findUser(username);
        log.debug("[PROJECT_MY] username='{}'", username);
        return assignmentRepository.findActiveProjectsByUser(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── GET USERS BY PROJECT ID (ADMIN/MANAGER) ────────────────────────────
    /**
     * Returns all users assigned to the project with the given ID.
     *
     * @param projectId the unique ID of the project
     * @return list of assigned users, sorted by first name then last name
     * @throws ResourceNotFoundException if no project exists with that ID
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getUsersByProjectId(Long projectId) {
        log.debug("[PROJECT_USERS] Fetching users for projectId={}", projectId);

        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException(
                    "No project found with id: " + projectId);
        }

        List<User> users = assignmentRepository.findUsersByProjectId(projectId);
        log.info("[PROJECT_USERS] projectId={}, userCount={}", projectId, users.size());

        return users.stream()
                .map(this::toUserResponse)
                .toList();
    }

    // ── GET USERS BY PROJECT NAME (ADMIN/MANAGER) ──────────────────────────
    /**
     * Returns all users assigned to the project(s) matching the given name
     * (case-insensitive). If multiple projects share the same name, users
     * from all of them are returned (deduplicated).
     *
     * @param projectName the project name to look up
     * @return list of assigned users, sorted by first name then last name
     * @throws ResourceNotFoundException if no project exists with that name
     */
    @Transactional(readOnly = true)
    public List<UserResponseDTO> getUsersByProjectName(String projectName) {
        log.debug("[PROJECT_USERS] Fetching users for projectName='{}'", projectName);

        if (!projectRepository.existsByNameIgnoreCase(projectName)) {
            throw new ResourceNotFoundException(
                    "No project found with name: '" + projectName + "'");
        }

        List<User> users = assignmentRepository.findUsersByProjectName(projectName);
        log.info("[PROJECT_USERS] projectName='{}', userCount={}", projectName, users.size());

        return users.stream()
                .map(this::toUserResponse)
                .toList();
    }

    // ── GET ALL PROJECTS (paginated, ADMIN/MANAGER) ────────────────────────
    @Transactional(readOnly = true)
    public Page<ProjectResponse> getAllProjects(boolean activeOnly, Pageable pageable) {
        if (activeOnly) {
            return projectRepository.findAllByActiveTrue(pageable).map(this::toResponse);
        }
        return projectRepository.findAll(pageable).map(this::toResponse);
    }

    // ── GET BY ID ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id) {
        return toResponse(projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + id)));
    }

    // ── UPDATE PROJECT (ADMIN/MANAGER) ─────────────────────────────────────
    @Transactional
    public ProjectResponse updateProject(Long id, ProjectUpdateRequest req) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + id));

        if (req.getName() != null && !req.getName().isBlank()) {
            project.setName(req.getName());
        }
        if (req.getDescription() != null) {
            project.setDescription(req.getDescription());
        }
        if (req.getStartDate() != null) {
            project.setStartDate(req.getStartDate());
        }
        if (req.getEndDate() != null) {
            if (project.getStartDate() != null && req.getEndDate().isBefore(project.getStartDate())) {
                throw new IllegalArgumentException(
                        "End date cannot be before start date");
            }
            project.setEndDate(req.getEndDate());
        }
        if (req.getActive() != null) {
            project.setActive(req.getActive());
        }

        Project saved = projectRepository.save(project);
        log.info("[PROJECT_UPDATE] ✅ id={}, name='{}'", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    // ── DEACTIVATE (ADMIN/MANAGER) ─────────────────────────────────────────
    @Transactional
    public void deactivateProject(Long id) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found with id: " + id));
        p.setActive(false);
        projectRepository.save(p);
        log.info("[PROJECT_DEACTIVATE] ✅ id={}", id);
    }

    // ── Mapper ────────────────────────────────────────────────────────────
    public ProjectResponse toResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .code(p.getCode())
                .active(p.isActive())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .createdByUsername(p.getCreatedBy() != null ? p.getCreatedBy().getUsername() : null)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────
    private UserResponseDTO toUserResponse(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .username(user.getUsername())
                .email(user.getEmail())
                .gender(user.getGender())
                .location(user.getLocation())
                .designation(user.getDesignation())
                .managerEmail(user.getManagerEmail())
                .typeOfEmployment(user.getTypeOfEmployment())
                .photoUrl(user.getPhotoPath())
                .roles(user.getRoles().stream()
                        .map(r -> r.getName().name())
                        .toList())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));
    }
}
