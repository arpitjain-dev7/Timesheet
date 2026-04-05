package com.timesheetManagement.controller;

import com.timesheetManagement.dto.*;
import com.timesheetManagement.entity.RefreshToken;
import com.timesheetManagement.security.JwtUtils;
import com.timesheetManagement.security.UserDetailsServiceImpl;
import com.timesheetManagement.service.AuthService;
import com.timesheetManagement.service.ForgotPasswordService;
import com.timesheetManagement.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, Login, Refresh Token, and Forgot Password APIs")
public class AuthController {

    private final AuthService            authService;
    private final RefreshTokenService    refreshTokenService;
    private final JwtUtils               jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final ForgotPasswordService  forgotPasswordService;

    // ── POST /api/auth/register ───────────────────────────────────────────
    @Operation(summary = "Register a new user",
               description = "Send as application/json with all profile fields. "
                           + "To upload a profile photo use PUT /api/user/{id}/photo after registering. "
                           + "Returns 201 + success message only. Use /login to get a JWT.")
    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, null));
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────
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

        String username  = refreshToken.getUser().getUsername();
        UserDetails ud   = userDetailsService.loadUserByUsername(username);
        String newAccess = jwtUtils.generateToken(ud);

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

    // ══════════════════════════════════════════════════════════════════════
    //  FORGOT PASSWORD FLOW  (no authentication required)
    // ══════════════════════════════════════════════════════════════════════

    // ── POST /api/auth/forgot-password ────────────────────────────────────
    @Operation(
        summary     = "Step 1 — Request a password-reset OTP",
        description = """
            Sends a 6-digit OTP to the registered email address.
            - Always responds with the same generic message regardless of whether the email exists (anti-enumeration).
            - OTP is valid for **10 minutes**.
            - Successive requests within a **2-minute cooldown** are silently ignored.
            - No authentication required.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Generic success — OTP sent if email is registered"),
        @ApiResponse(responseCode = "400", description = "Validation failed — invalid email format", content = @Content)
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {
        forgotPasswordService.processForgotPassword(req);
        return ResponseEntity.ok(Map.of(
                "message", "If that email address is registered, an OTP has been sent to it."));
    }

    // ── POST /api/auth/verify-otp ─────────────────────────────────────────
    @Operation(
        summary     = "Step 2 — Verify OTP and receive a password-reset token",
        description = """
            Validates the 6-digit OTP sent to the user's email.
            - On success returns a **resetToken** (valid for **15 minutes**) to use in `/reset-password`.
            - OTP is **invalidated** immediately after successful verification (one-time use).
            - Account is **locked** after 5 consecutive wrong attempts — request a new OTP to unlock.
            - No authentication required.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OTP verified — resetToken returned"),
        @ApiResponse(responseCode = "400", description = "Invalid OTP / account locked / OTP not yet requested", content = @Content),
        @ApiResponse(responseCode = "410", description = "OTP has expired — request a new one", content = @Content)
    })
    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest req) {
        return ResponseEntity.ok(forgotPasswordService.verifyOtp(req));
    }

    // ── POST /api/auth/reset-password ─────────────────────────────────────
    @Operation(
        summary     = "Step 3 — Reset password using the reset token",
        description = """
            Sets a new password using the `resetToken` obtained from `/verify-otp`.
            - `newPassword` and `confirmPassword` must match exactly.
            - Password strength: min 8 characters, uppercase, lowercase, digit, and special character.
            - On success: password updated and **all active sessions are invalidated** (forces re-login on all devices).
            - The reset token is **single-use** and expires 15 minutes after OTP verification.
            - No authentication required.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Passwords don't match / token invalid or expired / OTP not verified", content = @Content)
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {
        forgotPasswordService.resetPassword(req);
        return ResponseEntity.ok(Map.of(
                "message", "Password has been reset successfully. Please log in with your new password."));
    }
}
