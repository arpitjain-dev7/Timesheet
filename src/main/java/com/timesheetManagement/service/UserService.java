package com.timesheetManagement.service;

import com.timesheetManagement.dto.UserRequestDTO;
import com.timesheetManagement.dto.UserResponseDTO;
import com.timesheetManagement.entity.Role;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.User;
import com.timesheetManagement.exception.FileUploadException;
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
    @Transactional
    public UserResponseDTO updateUser(Long id, UserRequestDTO dto, MultipartFile photo) {
        log.info("[USER_UPDATE] Updating user id={}", id);
        User user = findUserById(id);

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

        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setGender(dto.getGender());
        user.setLocation(dto.getLocation());
        user.setDesignation(dto.getDesignation());
        user.setManagerEmail(dto.getManagerEmail());
        user.setTypeOfEmployment(dto.getTypeOfEmployment());

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            log.debug("[USER_UPDATE] Password change requested for id={}", id);
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        if (dto.getRole() != null) {
            log.debug("[USER_UPDATE] Role change to '{}' for id={}", dto.getRole(), id);
            Role role = roleRepository.findByName(dto.getRole())
                    .orElseThrow(() -> {
                        log.error("[USER_UPDATE] Role '{}' not found", dto.getRole());
                        return new RoleNotFoundException(dto.getRole().name());
                    });
            user.getRoles().clear();
            user.getRoles().add(role);
        }

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
        log.info("[USER_UPDATE] ✅ User updated: id={}, username='{}'", saved.getId(), saved.getUsername());
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
