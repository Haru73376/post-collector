package com.github.haru73376.post_collector.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.InvalidCredentialsException;
import com.github.haru73376.post_collector.common.exception.InvalidTokenException;
import com.github.haru73376.post_collector.common.security.JwtTokenProvider;
import com.github.haru73376.post_collector.user.UserResponse;
import jakarta.servlet.http.Cookie;
import com.github.haru73376.post_collector.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "JWT_SECRET=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHk=",
        "DB_USERNAME=test",
        "DB_PASSWORD=test"
})
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthService authService;

    // JwtAuthenticationFilter depends on JwtTokenProvider, so it must be mocked
    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    // -------------------------------------------------------------------------
    // register()
    // -------------------------------------------------------------------------

    @Test
    void register_validRequest_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");
        UserResponse userResponse = new UserResponse(
                UUID.randomUUID(), "alice", "alice@example.com", LocalDateTime.now());

        given(authService.register(any(RegisterRequest.class))).willReturn(userResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void register_blankUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("", "alice@example.com", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("username")));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "not-an-email", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("email")));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "short");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("password")));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");

        given(authService.register(any(RegisterRequest.class)))
                .willThrow(new ConflictException("Email already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");

        given(authService.register(any(RegisterRequest.class)))
                .willThrow(new ConflictException("Username already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    // -------------------------------------------------------------------------
    // login()
    // -------------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200WithTokenAndCookie() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "password123");
        AuthResult authResult = new AuthResult(
                new TokenResponse("access.token.value", "Bearer", 900),
                "raw-refresh-token",
                604800L
        );

        given(authService.login(any(LoginRequest.class))).willReturn(authResult);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token.value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=raw-refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "wrongpassword");

        given(authService.login(any(LoginRequest.class)))
                .willThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_blankEmail_returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("email")));
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("password")));
    }

    // -------------------------------------------------------------------------
    // refresh()
    // -------------------------------------------------------------------------

    @Test
    void refresh_validCookie_returns200WithNewTokenAndRotatedCookie() throws Exception {
        AuthResult authResult = new AuthResult(
                new TokenResponse("new.access.token", "Bearer", 900),
                "new-raw-refresh-token",
                604800L
        );

        given(authService.refresh("old-raw-token")).willReturn(authResult);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "old-raw-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new-raw-refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));
    }

    @Test
    void refresh_missingCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is missing"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        given(authService.refresh("invalid-token"))
                .willThrow(new InvalidTokenException("Refresh token is invalid or expired"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "invalid-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is invalid or expired"));
    }

    // -------------------------------------------------------------------------
    // logout()
    // -------------------------------------------------------------------------

    @Test
    void logout_withCookie_returns204AndInvalidatedCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "some-refresh-token")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(authService).logout("some-refresh-token");
    }

    @Test
    void logout_withoutCookie_returns204AndInvalidatedCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }
}