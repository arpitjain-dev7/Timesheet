package com.timesheetManagement.service;

import com.timesheetManagement.dto.ChangePasswordRequest;
import com.timesheetManagement.dto.CreateUserRequest;
import com.timesheetManagement.dto.ManagerResponse;
import com.timesheetManagement.dto.UserRequestDTO;
import com.timesheetManagement.dto.UserResponseDTO;
import com.timesheetManagement.entity.Role;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.User;
import com.timesheetManagement.exception.FileUploadException;
import com.timesheetManagement.exception.InvalidManagerAssignmentException;
import com.timesheetManagement.exception.ManagerNotFoundException;
import com.timesheetManagement.exception.ResourceNotFoundException;
import com.timesheetManagement.exception.RoleNotFoundException;
import com.timesheetManagement.exception.UserAlreadyExistsException;
import com.timesheetManagement.repository.ProjectAssignmentRepository;
import com.timesheetManagement.repository.ProjectRepository;
import com.timesheetManagement.repository.RefreshTokenRepository;
import com.timesheetManagement.repository.RoleRepository;
import com.timesheetManagement.repository.TimesheetRepository;
import com.timesheetManagement.repository.UserRepository;
import com.timesheetManagement.util.FileUploadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository              userRepository;
    private final RoleRepository              roleRepository;
    private final PasswordEncoder             passwordEncoder;
    private final FileUploadUtil              fileUploadUtil;
    private final RefreshTokenRepository      refreshTokenRepository;
    private final ProjectRepository           projectRepository;
    private final ProjectAssignmentRepository assignmentRepository;
    private final TimesheetRepository         timesheetRepository;
    private final EmailService                emailService;

    // ── CREATE with manager validation ─────────────────────────────────────
    /**
     * Creates a new user account with strict manager-assignment rules:
     * <ol>
     *   <li>Username and email must be unique.</li>
     *   <li>ROLE_USER → {@code managerId} is required.</li>
     *   <li>ROLE_MANAGER / ROLE_ADMIN → {@code managerId} must be null.</li>
     *   <li>Referenced manager must exist in the DB.</li>
     *   <li>Referenced manager must hold {@code ROLE_MANAGER}.</li>
     *   <li>{@code managerId} cannot point to the account being created
     *       (self-assignment guard — always false on create since the new user
     *       has no ID yet, but enforced here so the same rule applies on future
     *       update flows that reuse this logic).</li>
     * </ol>
     *
     * @throws UserAlreadyExistsException        username or email already taken (409)
     * @throws InvalidManagerAssignmentException manager rule violated (409)
     * @throws ManagerNotFoundException          managerId not found in DB (404)
     */
    @Transactional
    public UserResponseDTO createUserWithManager(CreateUserRequest request) {
        log.info("[USER_CREATE_MGR] username='{}', email='{}', role='{}'",
                request.getUsername(), request.getEmail(), request.getRole());

        // ── 1. Uniqueness ─────────────────────────────────────────────────
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("[USER_CREATE_MGR] Username '{}' already taken", request.getUsername());
            throw new UserAlreadyExistsException(
                    "Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("[USER_CREATE_MGR] Email '{}' already registered", request.getEmail());
            throw new UserAlreadyExistsException(
                    "Email '" + request.getEmail() + "' is already registered");
        }

        // ── 2. Resolve role ───────────────────────────────────────────────
        RoleName roleName = request.getRole() != null ? request.getRole() : RoleName.ROLE_USER;

        // ── 3. Manager-assignment rule matrix ─────────────────────────────
        if (roleName == RoleName.ROLE_USER) {
            if (request.getManagerId() == null) {
                log.warn("[USER_CREATE_MGR] managerId is required for ROLE_USER");
                throw new InvalidManagerAssignmentException(
                        "A manager must be assigned when creating a user with role ROLE_USER");
            }
        } else {
            // ROLE_MANAGER and ROLE_ADMIN must NOT have a manager
            if (request.getManagerId() != null) {
                log.warn("[USER_CREATE_MGR] managerId must be null for role '{}'", roleName);
                throw new InvalidManagerAssignmentException(
                        "Users with role " + roleName + " cannot be assigned a manager. "
                        + "Set managerId to null.");
            }
        }

        // ── 4. Resolve and validate manager ──────────────────────────────
        User manager = null;
        if (request.getManagerId() != null) {
            manager = userRepository.findById(request.getManagerId())
                    .orElseThrow(() -> {
                        log.warn("[USER_CREATE_MGR] Manager id={} not found", request.getManagerId());
                        return new ManagerNotFoundException(request.getManagerId());
                    });

            // Manager must hold ROLE_MANAGER
            boolean hasManagerRole = manager.getRoles().stream()
                    .anyMatch(r -> r.getName() == RoleName.ROLE_MANAGER);
            if (!hasManagerRole) {
                log.warn("[USER_CREATE_MGR] User id={} is not a MANAGER", request.getManagerId());
                throw new InvalidManagerAssignmentException(
                        "User with id " + request.getManagerId()
                        + " does not have the ROLE_MANAGER role and cannot be assigned as a manager");
            }

            // Self-assignment guard (always false on create; kept for symmetry with update)
            // The new user has no ID yet so this branch can never trigger here,
            // but the check is documented for completeness.
        }

        // ── 5. Resolve role entity ────────────────────────────────────────
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> {
                    log.error("[USER_CREATE_MGR] Role '{}' not found in DB", roleName);
                    return new RoleNotFoundException(roleName.name());
                });

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        // ── 6. Persist ────────────────────────────────────────────────────
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .gender(request.getGender())
                .location(request.getLocation())
                .designation(request.getDesignation())
                .typeOfEmployment(request.getTypeOfEmployment())
                .manager(manager)
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        log.info("[USER_CREATE_MGR] ✅ User created: id={}, username='{}', managerId={}",
                saved.getId(), saved.getUsername(),
                manager != null ? manager.getId() : "none");

        // ── 7. Welcome email ──────────────────────────────────────────────
        String displayRole = toDisplayRole(roleName);
        emailService.sendWelcomeEmail(
                saved.getEmail(),
                saved.getFirstName() + " " + saved.getLastName(),
                saved.getUsername(),
                request.getPassword(),    // plaintext — before BCrypt
                displayRole
        );

        return toResponseDTO(saved);
    }

    // ── FETCH ALL MANAGERS (for UI dropdown) ──────────────────────────────
    /**
     * Returns every user with {@code ROLE_MANAGER}, sorted alphabetically
     * by first name then last name — exactly what a "pick a manager" dropdown
     * needs.  A single JPQL query; no N+1.
     */
    @Transactional(readOnly = true)
    public List<ManagerResponse> getAllManagers() {
        log.debug("[MANAGER_LIST] Fetching all managers");
        List<ManagerResponse> managers = userRepository
                .findAllByRoleName(RoleName.ROLE_MANAGER)
                .stream()
                .map(u -> ManagerResponse.builder()
                        .id(u.getId())
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .email(u.getEmail())
                        .build())
                .toList();
        log.debug("[MANAGER_LIST] Found {} managers", managers.size());
        return managers;
    }

    // ── CREATE ─────────────────────────────────────────────────────────────
    @Transactional
    public UserResponseDTO createUser(UserRequestDTO dto, MultipartFile photo) {
        log.info("[USER_CREATE] Attempting to create user: username='{}', email='{}'",
                dto.getUsername(), dto.getEmail());

        if (userRepository.existsByUsername(dto.getUsername())) {
            log.warn("[USER_CREATE] Username '{}' already taken", dto.getUsername());
            throw new UserAlreadyExistsException("Username '" + dto.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            log.warn("[USER_CREATE] Email '{}' already registered", dto.getEmail());
            throw new UserAlreadyExistsException("Email '" + dto.getEmail() + "' is already registered");
        }

        RoleName roleName = dto.getRole() != null ? dto.getRole() : RoleName.ROLE_USER;
        log.debug("[USER_CREATE] Resolving role '{}'", roleName);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> {
                    log.error("[USER_CREATE] Role '{}' not found in database", roleName);
                    return new RoleNotFoundException(roleName.name());
                });

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        String photoPath = null;
        if (photo != null && !photo.isEmpty()) {
            log.debug("[USER_CREATE] Uploading photo for username='{}'", dto.getUsername());
            try {
                photoPath = fileUploadUtil.savePhoto(dto.getUsername(), photo);
                log.debug("[USER_CREATE] Photo saved at '{}'", photoPath);
            } catch (IOException e) {
                log.error("[USER_CREATE] Photo upload failed for username='{}': {}", dto.getUsername(), e.getMessage(), e);
                throw new FileUploadException("Failed to upload profile photo: " + e.getMessage(), e);
            }
        }

        User user = User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .username(dto.getUsername())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .gender(dto.getGender())
                .location(dto.getLocation())
                .designation(dto.getDesignation())
                .managerEmail(dto.getManagerEmail())
                .typeOfEmployment(dto.getTypeOfEmployment())
                .photoPath(photoPath)
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        log.info("[USER_CREATE] ✅ User created: id={}, username='{}'", saved.getId(), saved.getUsername());

        // ── Send welcome email with login credentials ─────────────────────
        // dto.getPassword() is the raw plaintext password — read BEFORE
        // passwordEncoder.encode() was applied to the user builder.
        // The email is sent asynchronously so it never delays this response.
        String displayRole = toDisplayRole(roleName);
        log.info("[USER_CREATE] Triggering welcome email for username='{}', email='{}'",
                saved.getUsername(), saved.getEmail());
        emailService.sendWelcomeEmail(
                saved.getEmail(),
                saved.getFirstName() + " " + saved.getLastName(),
                saved.getUsername(),
                dto.getPassword(),     // plaintext — captured before BCrypt encoding
                displayRole
        );

        return toResponseDTO(saved);
    }

    // ── READ (single) ──────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {
        log.debug("[USER_GET] Fetching user by id={}", id);
        return toResponseDTO(findUserById(id));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserByUsernameDTO(String username) {
        log.debug("[USER_GET] Fetching user by username='{}'", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("[USER_GET] User not found: username='{}'", username);
                    return new ResourceNotFoundException("User not found: " + username);
                });
        return toResponseDTO(user);
    }

    // ── READ (list with pagination + sorting) ──────────────────────────────
    @Transactional(readOnly = true)
    public Page<UserResponseDTO> getAllUsers(Pageable pageable) {
        log.debug("[USER_LIST] Fetching USER-role accounts — page={}, size={}, sort={}",
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        Page<UserResponseDTO> page = userRepository
                .findAllExcludingRoles(
                        List.of(RoleName.ROLE_ADMIN, RoleName.ROLE_MANAGER),
                        pageable)
                .map(this::toResponseDTO);

        log.debug("[USER_LIST] Found {} users (total={})",
                page.getNumberOfElements(), page.getTotalElements());
        return page;
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────
    /**
     * Updates an existing user's profile, role, and/or manager assignment.
     *
     * <p><b>Role + Manager validation rules</b> (all enforced here — never in the controller):
     * <ol>
     *   <li>Effective role = ROLE_MANAGER / ROLE_ADMIN → {@code managerId} must be null;
     *       any existing manager is automatically cleared.</li>
     *   <li>Effective role = ROLE_USER + {@code managerId} supplied → manager must exist (404)
     *       and hold ROLE_MANAGER (409); cannot self-assign (409).</li>
     *   <li>Effective role = ROLE_USER + no {@code managerId} supplied + role is changing
     *       <em>to</em> ROLE_USER from a non-user role + user has no existing manager → 409.</li>
     *   <li>Effective role = ROLE_USER + no {@code managerId} supplied + user already has a manager
     *       → existing manager is silently preserved (partial-update friendly).</li>
     * </ol>
     *
     * @throws UserAlreadyExistsException        duplicate username or email (409)
     * @throws RoleNotFoundException             role enum value not seeded in DB (500)
     * @throws ManagerNotFoundException          managerId not in DB (404)
     * @throws InvalidManagerAssignmentException any manager-role rule violated (409)
     */
    @Transactional
    public UserResponseDTO updateUser(Long id, UserRequestDTO dto, MultipartFile photo) {
        log.info("[USER_UPDATE] Updating user id={}", id);
        User user = findUserById(id);

        // ── 1. Uniqueness checks ──────────────────────────────────────────
        if (!user.getUsername().equals(dto.getUsername())
                && userRepository.existsByUsername(dto.getUsername())) {
            log.warn("[USER_UPDATE] Username '{}' already taken", dto.getUsername());
            throw new UserAlreadyExistsException("Username '" + dto.getUsername() + "' is already taken");
        }
        if (!user.getEmail().equals(dto.getEmail())
                && userRepository.existsByEmail(dto.getEmail())) {
            log.warn("[USER_UPDATE] Email '{}' already registered", dto.getEmail());
            throw new UserAlreadyExistsException("Email '" + dto.getEmail() + "' is already registered");
        }

        // ── 2. Basic profile fields ───────────────────────────────────────
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setGender(dto.getGender());
        user.setLocation(dto.getLocation());
        user.setDesignation(dto.getDesignation());
        user.setManagerEmail(dto.getManagerEmail());   // legacy free-text field kept
        user.setTypeOfEmployment(dto.getTypeOfEmployment());

        // ── 3. Password (optional on update) ─────────────────────────────
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            log.debug("[USER_UPDATE] Password change requested for id={}", id);
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        // ── 4. Role resolution ────────────────────────────────────────────
        // Determine the user's current role and the effective role after this update.
        RoleName currentRole = user.getRoles().stream()
                .map(Role::getName)
                .findFirst()
                .orElse(RoleName.ROLE_USER);
        RoleName effectiveRole = dto.getRole() != null ? dto.getRole() : currentRole;

        // Apply the role change when a new role is explicitly supplied.
        if (dto.getRole() != null && dto.getRole() != currentRole) {
            log.debug("[USER_UPDATE] Role changing '{}' → '{}' for id={}", currentRole, dto.getRole(), id);
            Role newRole = roleRepository.findByName(dto.getRole())
                    .orElseThrow(() -> {
                        log.error("[USER_UPDATE] Role '{}' not seeded in DB", dto.getRole());
                        return new RoleNotFoundException(
                                "Role '" + dto.getRole() + "' is not configured in the system. "
                                + "Contact the system administrator.");
                    });
            user.getRoles().clear();
            user.getRoles().add(newRole);
        }

        // ── 5. Manager assignment — role-aware validation ─────────────────
        if (effectiveRole == RoleName.ROLE_MANAGER || effectiveRole == RoleName.ROLE_ADMIN) {
            // MANAGER and ADMIN must never have a manager above them.
            if (dto.getManagerId() != null) {
                log.warn("[USER_UPDATE] managerId must be null for role '{}', id={}", effectiveRole, id);
                throw new InvalidManagerAssignmentException(
                        "Users with role " + effectiveRole + " cannot be assigned a manager. "
                        + "Remove the 'managerId' field from the request (or set it to null).");
            }
            // If the role is changing to MANAGER/ADMIN, clear any previously assigned manager.
            if (user.getManager() != null) {
                log.debug("[USER_UPDATE] Clearing manager for id={} (role elevated to {})", id, effectiveRole);
                user.setManager(null);
            }

        } else {
            // Effective role is ROLE_USER.
            if (dto.getManagerId() != null) {

                // Self-assignment guard — a user cannot manage themselves.
                if (dto.getManagerId().equals(id)) {
                    log.warn("[USER_UPDATE] Self-assignment attempt for id={}", id);
                    throw new InvalidManagerAssignmentException(
                            "A user cannot be assigned as their own manager. "
                            + "Please choose a different manager.");
                }

                // Manager must exist in the DB.
                User manager = userRepository.findById(dto.getManagerId())
                        .orElseThrow(() -> {
                            log.warn("[USER_UPDATE] Manager id={} not found", dto.getManagerId());
                            return new ManagerNotFoundException(dto.getManagerId());
                        });

                // The designated manager must actually hold ROLE_MANAGER.
                boolean hasManagerRole = manager.getRoles().stream()
                        .anyMatch(r -> r.getName() == RoleName.ROLE_MANAGER);
                if (!hasManagerRole) {
                    log.warn("[USER_UPDATE] User id={} lacks ROLE_MANAGER — cannot manage id={}",
                            dto.getManagerId(), id);
                    throw new InvalidManagerAssignmentException(
                            "User with id " + dto.getManagerId()
                            + " ('" + manager.getFirstName() + " " + manager.getLastName() + "')"
                            + " does not hold the ROLE_MANAGER role and cannot be assigned as a manager.");
                }

                user.setManager(manager);
                log.debug("[USER_UPDATE] Manager set to id={} ('{}') for user id={}",
                        dto.getManagerId(), manager.getUsername(), id);

            } else {
                // managerId not supplied in this request.
                boolean roleIsChangingToUser = dto.getRole() == RoleName.ROLE_USER
                        && currentRole != RoleName.ROLE_USER;

                if (roleIsChangingToUser && user.getManager() == null) {
                    // Role is being promoted DOWN to ROLE_USER but no manager is set
                    // and none was supplied → we cannot leave the user in an inconsistent state.
                    log.warn("[USER_UPDATE] managerId required when changing role to ROLE_USER "
                            + "for id={} (currently has no manager)", id);
                    throw new InvalidManagerAssignmentException(
                            "A 'managerId' is required when changing the role to ROLE_USER "
                            + "for a user who has no manager assigned. "
                            + "Please provide a valid manager id.");
                }
                // Otherwise (role stays ROLE_USER or user already has a manager) →
                // leave the existing manager untouched (partial-update behaviour).
                log.debug("[USER_UPDATE] managerId not supplied — keeping existing manager for id={}", id);
            }
        }

        // ── 6. Profile photo (optional) ───────────────────────────────────
        if (photo != null && !photo.isEmpty()) {
            log.debug("[USER_UPDATE] Replacing photo for id={}", id);
            fileUploadUtil.deletePhoto(user.getPhotoPath());
            try {
                user.setPhotoPath(fileUploadUtil.savePhoto(user.getUsername(), photo));
                log.debug("[USER_UPDATE] New photo saved at '{}'", user.getPhotoPath());
            } catch (IOException e) {
                log.error("[USER_UPDATE] Photo upload failed for id={}: {}", id, e.getMessage(), e);
                throw new FileUploadException("Failed to upload profile photo: " + e.getMessage(), e);
            }
        }

        User saved = userRepository.save(user);
        log.info("[USER_UPDATE] ✅ User updated: id={}, username='{}', role='{}', managerId={}",
                saved.getId(), saved.getUsername(), effectiveRole,
                saved.getManager() != null ? saved.getManager().getId() : "none");
        return toResponseDTO(saved);
    }

    // ── PHOTO UPLOAD (dedicated endpoint) ─────────────────────────────────
    @Transactional
    public UserResponseDTO updatePhoto(Long id, MultipartFile photo) {
        log.info("[USER_PHOTO] Uploading photo for user id={}", id);
        User user = findUserById(id);
        fileUploadUtil.deletePhoto(user.getPhotoPath());
        try {
            user.setPhotoPath(fileUploadUtil.savePhoto(user.getUsername(), photo));
            log.debug("[USER_PHOTO] Photo saved at '{}'", user.getPhotoPath());
        } catch (IOException e) {
            log.error("[USER_PHOTO] Photo upload failed for id={}: {}", id, e.getMessage(), e);
            throw new FileUploadException("Failed to upload profile photo: " + e.getMessage(), e);
        }
        User saved = userRepository.save(user);
        log.info("[USER_PHOTO] ✅ Photo updated for id={}, username='{}'", saved.getId(), saved.getUsername());
        return toResponseDTO(saved);
    }

    // ── CHANGE PASSWORD (authenticated user) ───────────────────────────────
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        log.info("[CHANGE_PASSWORD] Password change requested by username='{}'", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("[CHANGE_PASSWORD] User not found: username='{}'", username);
                    return new ResourceNotFoundException("User not found: " + username);
                });

        // 1. Verify current password matches the stored BCrypt hash
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.warn("[CHANGE_PASSWORD] Incorrect current password for username='{}'", username);
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // 2. New password must differ from the current one
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            log.warn("[CHANGE_PASSWORD] New password is the same as the current one for username='{}'", username);
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        // 3. Confirm password must match new password
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            log.warn("[CHANGE_PASSWORD] Passwords do not match for username='{}'", username);
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        // 4. Encode and immediately persist the new password
        //    saveAndFlush forces the UPDATE to reach the DB right now (within this
        //    transaction) before any subsequent @Modifying query can disrupt the
        //    persistence-context flush cycle and silently drop the password change.
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.saveAndFlush(user);

        // 5. Invalidate all refresh tokens so existing sessions are forced to re-login
        //    Runs AFTER the password is already in the DB, so a failure here cannot
        //    corrupt the password that was just saved.
        refreshTokenRepository.deleteByUser(user);

        log.info("[CHANGE_PASSWORD] ✅ Password changed successfully for username='{}'", username);
    }

    // ── DELETE ─────────────────────────────────────────────────────────────
    @Transactional
    public void deleteUser(Long id) {
        log.info("[USER_DELETE] Deleting user id={}", id);
        User user = findUserById(id);

        // 1. Revoke active JWT refresh tokens
        log.debug("[USER_DELETE] Removing refresh tokens for username='{}'", user.getUsername());
        refreshTokenRepository.deleteByUser(user);

        // 2. Null-out projects.created_by_id  (project rows are kept, creator is cleared)
        log.debug("[USER_DELETE] Clearing project creator reference for user id={}", id);
        projectRepository.clearCreatedBy(user);

        // 3. Null-out project_assignments.assigned_by_id  (assignment rows are kept)
        log.debug("[USER_DELETE] Clearing assignment 'assignedBy' reference for user id={}", id);
        assignmentRepository.clearAssignedBy(user);

        // 4. Delete all project assignments where this user is the assignee (user_id)
        log.debug("[USER_DELETE] Deleting project assignments for user id={}", id);
        assignmentRepository.deleteAllByUser(user);

        // 5. Null-out timesheets.reviewed_by_id  (reviewed timesheets are kept)
        log.debug("[USER_DELETE] Clearing timesheet reviewer reference for user id={}", id);
        timesheetRepository.clearReviewedBy(user);

        // 6. Delete all timesheets owned by the user — entries cascade automatically
        log.debug("[USER_DELETE] Deleting timesheets for user id={}", id);
        timesheetRepository.deleteAllByUser(user);

        // 7. Delete profile photo from disk
        fileUploadUtil.deletePhoto(user.getPhotoPath());

        // 8. Finally delete the user record
        userRepository.delete(user);
        log.info("[USER_DELETE] ✅ User deleted: id={}, username='{}'", id, user.getUsername());
    }

    // ── Internal helpers ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("[USER_GET] User not found: username='{}'", username);
                    return new ResourceNotFoundException("User not found: " + username);
                });
    }

    // ── Mapper ────────────────────────────────────────────────────────────
    public UserResponseDTO toResponseDTO(User user) {
        // Resolve lazy manager safely — only accessed inside a @Transactional boundary
        User mgr = user.getManager();
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
                .managerId(mgr != null ? mgr.getId() : null)
                .managerName(mgr != null ? mgr.getFirstName() + " " + mgr.getLastName() : null)
                .typeOfEmployment(user.getTypeOfEmployment())
                .photoUrl(user.getPhotoPath())
                .roles(user.getRoles().stream()
                        .map(r -> r.getName().name())
                        .toList())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    // ── Private ──────────────────────────────────────────────────────────
    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    /**
     * Converts a {@link RoleName} enum to a display-friendly string for emails.
     * e.g. ROLE_USER → "User", ROLE_MANAGER → "Manager", ROLE_ADMIN → "Admin"
     */
    private String toDisplayRole(RoleName roleName) {
        String stripped = roleName.name().replace("ROLE_", "");  // "ROLE_USER" → "USER"
        return stripped.charAt(0) + stripped.substring(1).toLowerCase(); // "USER" → "User"
    }
}
