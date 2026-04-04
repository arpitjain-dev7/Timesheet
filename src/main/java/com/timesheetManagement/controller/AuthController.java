package com.timesheetManagement.controller;

import com.timesheetManagement.dto.*;
import com.timesheetManagement.entity.RefreshToken;
import com.timesheetManagement.security.JwtUtils;
import com.timesheetManagement.security.UserDetailsServiceImpl;
import com.timesheetManagement.service.AuthService;
import com.timesheetManagement.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, Login, and Refresh Token APIs")
public class AuthController {

    private final AuthService            authService;
    private final RefreshTokenService    refreshTokenService;
    private final JwtUtils               jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;

    // ── POST /api/auth/register ───────────────────────────────────────────
    // Accepts application/json. Photo can be uploaded later via PUT /api/user/{id}/photo
    // Returns ONLY 201 + success message — no JWT at registration time.
    @Operation(summary = "Register a new user",
               description = "Send as application/json with all profile fields. " +
                             "To upload a profile photo use PUT /api/user/{id}/photo after registering. " +
                             "Returns 201 + success message only. Use /login to get a JWT.")
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        MessageResponse response = authService.register(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────
    // Returns full JWT access token + refresh token + all user details.
    @Operation(summary = "Login — returns JWT access token, refresh token, and full user info")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────
    @Operation(summary = "Refresh access token using refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken());
        refreshTokenService.verifyExpiration(refreshToken);

        String username    = refreshToken.getUser().getUsername();
        UserDetails ud     = userDetailsService.loadUserByUsername(username);
        String newAccess   = jwtUtils.generateToken(ud);

        List<String> roles = ud.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return ResponseEntity.ok(AuthResponse.builder()
                .accessToken(newAccess)
                .refreshToken(refreshToken.getToken())
                .username(username)
                .email(refreshToken.getUser().getEmail())
                .roles(roles)
                .build());
    }
}
