package com.timesheetManagement.service;

import com.timesheetManagement.dto.AuthResponse;
import com.timesheetManagement.dto.LoginRequest;
import com.timesheetManagement.dto.MessageResponse;
import com.timesheetManagement.dto.RegisterRequest;
import com.timesheetManagement.entity.RefreshToken;
import com.timesheetManagement.entity.Role;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.User;
import com.timesheetManagement.exception.FileUploadException;
import com.timesheetManagement.exception.RoleNotFoundException;
import com.timesheetManagement.exception.UserAlreadyExistsException;
import com.timesheetManagement.repository.RoleRepository;
import com.timesheetManagement.repository.UserRepository;
import com.timesheetManagement.security.JwtUtils;
import com.timesheetManagement.util.FileUploadUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
public class AuthService {

    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final PasswordEncoder       passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils              jwtUtils;
    private final RefreshTokenService   refreshTokenService;
    private final FileUploadUtil        fileUploadUtil;

    // ── Register ──────────────────────────────────────────────────────────
    @Transactional
    public MessageResponse register(RegisterRequest request, MultipartFile photo) {
        log.info("[REGISTER] Attempting registration for username='{}', email='{}'",
                request.getUsername(), request.getEmail());

        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("[REGISTER] Username '{}' is already taken", request.getUsername());
            throw new UserAlreadyExistsException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("[REGISTER] Email '{}' is already registered", request.getEmail());
            throw new UserAlreadyExistsException("Email '" + request.getEmail() + "' is already registered");
        }

        RoleName roleName = request.getRole() != null ? request.getRole() : RoleName.ROLE_USER;
        log.debug("[REGISTER] Resolving role '{}'", roleName);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> {
                    log.error("[REGISTER] Role '{}' not found in database", roleName);
                    return new RoleNotFoundException(roleName.name());
                });

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        String photoPath = null;
        if (photo != null && !photo.isEmpty()) {
            log.debug("[REGISTER] Uploading profile photo for username='{}'", request.getUsername());
            try {
                photoPath = fileUploadUtil.savePhoto(request.getUsername(), photo);
                log.debug("[REGISTER] Photo saved at '{}'", photoPath);
            } catch (IOException e) {
                log.error("[REGISTER] Failed to upload photo for username='{}': {}",
                        request.getUsername(), e.getMessage(), e);
                throw new FileUploadException("Failed to upload profile photo: " + e.getMessage(), e);
            }
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .gender(request.getGender())
                .location(request.getLocation())
                .designation(request.getDesignation())
                .managerEmail(request.getManagerEmail())
                .typeOfEmployment(request.getTypeOfEmployment())
                .photoPath(photoPath)
                .roles(roles)
                .build();

        userRepository.save(user);
        log.info("[REGISTER] ✅ User registered successfully: username='{}', role='{}'",
                user.getUsername(), roleName);

        return new MessageResponse(HttpStatus.CREATED.value(), "User registered successfully");
    }

    // ── Login ─────────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("[LOGIN] Login attempt for usernameOrEmail='{}'", request.getUsernameOrEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtUtils.generateToken(userDetails);
        log.debug("[LOGIN] JWT generated for username='{}'", userDetails.getUsername());

        User user = userRepository
                .findByUsernameOrEmail(request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> {
                    log.error("[LOGIN] User not found after successful auth: '{}'",
                            request.getUsernameOrEmail());
                    return new RuntimeException("User not found after authentication");
                });

        refreshTokenService.deleteByUser(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        log.debug("[LOGIN] Refresh token issued for username='{}'", user.getUsername());

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        log.info("[LOGIN] ✅ Login successful for username='{}', roles={}", user.getUsername(), roles);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roles)
                .build();
    }
}
