package com.timesheetManagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timesheetManagement.config.CorsProperties;
import com.timesheetManagement.config.SecurityConfig;
import com.timesheetManagement.dto.ChangePasswordRequest;
import com.timesheetManagement.security.JwtAuthEntryPoint;
import com.timesheetManagement.security.JwtUtils;
import com.timesheetManagement.security.UserDetailsServiceImpl;
import com.timesheetManagement.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link UserController#changePassword} — POST /api/user/me/change-password.
 *
 * <p>Imports {@link SecurityConfig} and {@link JwtAuthEntryPoint} so that the full
 * Spring Security filter chain is active.  This lets us verify that unauthenticated
 * requests are rejected with 401 by the real entry-point, and that authenticated
 * requests with the correct role reach the controller.
 *
 * <p>Uses {@code SecurityMockMvcRequestPostProcessors.user()} (not {@code @WithMockUser})
 * because Spring Security 6's stateless {@code SecurityContextHolderFilter} reads the
 * security context from request attributes, which {@code user()} sets correctly, whereas
 * {@code @WithMockUser} only populates the {@code SecurityContextHolder} thread-local which
 * is overwritten before the controller is reached.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class})
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Mocked service / security dependencies ────────────────────────────
    @MockitoBean private UserService            userService;
    @MockitoBean private JwtUtils               jwtUtils;
    @MockitoBean private UserDetailsServiceImpl userDetailsService;

    /**
     * CorsProperties is a @Component not auto-loaded by @WebMvcTest.
     * We mock it so SecurityConfig can be wired, and configure it
     * before each test (before the filter chain is invoked).
     */
    @MockitoBean private CorsProperties corsProperties;

    private ChangePasswordRequest validRequest;

    @BeforeEach
    void setUp() {
        // SecurityConfig.corsConfigurationSource() calls corsProperties.getAllowedOrigins()
        // when processing CORS on the first real request — configure the mock up front.
        when(corsProperties.getAllowedOrigins()).thenReturn(List.of("http://localhost:5173"));

        validRequest = new ChangePasswordRequest();
        validRequest.setCurrentPassword("OldPass@1");
        validRequest.setNewPassword("NewPass@1");
        validRequest.setConfirmPassword("NewPass@1");
    }

    // ── Success ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/user/me/change-password → 200 on valid request")
    void changePassword_validRequest_returns200() throws Exception {
        doNothing().when(userService).changePassword(eq("testuser"), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/user/me/change-password")
                        .with(user("testuser").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Password changed successfully. Please log in again with your new password."));
    }

    // ── Validation failures ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/user/me/change-password → 400 when currentPassword is blank")
    void changePassword_blankCurrentPassword_returns400() throws Exception {
        validRequest.setCurrentPassword("");

        mockMvc.perform(post("/api/user/me/change-password")
                        .with(user("testuser").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.currentPassword").exists());
    }

    @Test
    @DisplayName("POST /api/user/me/change-password → 400 when newPassword is blank")
    void changePassword_blankNewPassword_returns400() throws Exception {
        validRequest.setNewPassword("");
        validRequest.setConfirmPassword("");

        mockMvc.perform(post("/api/user/me/change-password")
                        .with(user("testuser").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());
    }

    @Test
    @DisplayName("POST /api/user/me/change-password → 400 when newPassword is too weak")
    void changePassword_weakNewPassword_returns400() throws Exception {
        validRequest.setNewPassword("weakpassword");   // no uppercase / digit / special char
        validRequest.setConfirmPassword("weakpassword");

        mockMvc.perform(post("/api/user/me/change-password")
                        .with(user("testuser").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPassword").exists());
    }

    @Test
    @DisplayName("POST /api/user/me/change-password → 400 when confirmPassword is blank")
    void changePassword_blankConfirmPassword_returns400() throws Exception {
        validRequest.setConfirmPassword("");

        mockMvc.perform(post("/api/user/me/change-password")
                        .with(user("testuser").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.confirmPassword").exists());
    }

    // ── Business-logic errors surfaced as 400 ─────────────────────────────

    @Test
    @DisplayName("POST /api/user/me/change-password → 400 when current password is wrong")
    void changePassword_wrongCurrentPassword_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Current password is incorrect"))
                .when(userService).changePassword(eq("testuser"), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/user/me/change-password")
                        .with(user("testuser").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    @DisplayName("POST /api/user/me/change-password → 400 when newPassword equals currentPassword")
    void changePassword_sameAsCurrentPassword_returns400() throws Exception {
        doThrow(new IllegalArgumentException("New password must be different from the current password"))
                .when(userService).changePassword(eq("testuser"), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/user/me/change-password")
                        .with(user("testuser").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be different from the current password"));
    }

    @Test
    @DisplayName("POST /api/user/me/change-password → 400 when newPassword and confirmPassword don't match")
    void changePassword_passwordMismatch_returns400() throws Exception {
        doThrow(new IllegalArgumentException("New password and confirm password do not match"))
                .when(userService).changePassword(eq("testuser"), any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/user/me/change-password")
                        .with(user("testuser").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password and confirm password do not match"));
    }

    // ── Authentication guard ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/user/me/change-password → 401 when not authenticated")
    void changePassword_unauthenticated_returns401() throws Exception {
        // No user() post-processor — Spring Security's filter chain (loaded via @Import(SecurityConfig))
        // rejects the request before it reaches the controller.
        mockMvc.perform(post("/api/user/me/change-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }
}



