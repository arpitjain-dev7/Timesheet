package com.timesheetManagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timesheetManagement.dto.AuthResponse;
import com.timesheetManagement.dto.LoginRequest;
import com.timesheetManagement.dto.MessageResponse;
import com.timesheetManagement.dto.RegisterRequest;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.service.AuthService;
import com.timesheetManagement.service.RefreshTokenService;
import com.timesheetManagement.security.JwtUtils;
import com.timesheetManagement.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper is not auto-configured in the @WebMvcTest slice in Spring Boot 4.x
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private AuthService          authService;
    @MockitoBean private RefreshTokenService  refreshTokenService;
    @MockitoBean private JwtUtils             jwtUtils;
    @MockitoBean private UserDetailsServiceImpl userDetailsService;

    private MessageResponse successMessage;
    private AuthResponse    sampleAuthResponse;

    @BeforeEach
    void setUp() {
        successMessage = new MessageResponse(201, "User registered successfully");

        sampleAuthResponse = AuthResponse.builder()
                .accessToken("sample.jwt.token")
                .refreshToken("sample-refresh-uuid")
                .tokenType("Bearer")
                .username("testuser")
                .email("test@example.com")
                .roles(List.of("ROLE_USER"))
                .build();
    }

    // ── Registration Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register → 201 + success message on valid JSON request")
    @WithMockUser
    void register_validRequest_returns201() throws Exception {
        when(authService.register(any(RegisterRequest.class), isNull()))
                .thenReturn(successMessage);

        RegisterRequest req = new RegisterRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(HttpStatus.CREATED.value()))
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    @DisplayName("POST /api/auth/register → 400 when firstName is blank")
    @WithMockUser
    void register_blankFirstName_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("");           // blank — fails @NotBlank
        req.setLastName("Doe");
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register → 400 when email is invalid")
    @WithMockUser
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setUsername("testuser");
        req.setEmail("not-an-email");
        req.setPassword("secret123");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register → 400 when password is too short")
    @WithMockUser
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setUsername("testuser");
        req.setEmail("test@example.com");
        req.setPassword("abc");         // < 6 chars

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ── Login Tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login → 200 + full JWT response on valid credentials")
    @WithMockUser
    void login_validCredentials_returns200() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail("testuser");
        request.setPassword("secret123");

        when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("sample.jwt.token"))
                .andExpect(jsonPath("$.refreshToken").value("sample-refresh-uuid"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    @DisplayName("POST /api/auth/login → 400 when body is empty")
    @WithMockUser
    void login_emptyBody_returns400() throws Exception {
        LoginRequest request = new LoginRequest();

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
