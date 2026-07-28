package com.github.haru73376.post_collector.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.haru73376.post_collector.common.config.SecurityConfig;
import com.github.haru73376.post_collector.common.exception.BusinessRuleViolationException;
import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import com.github.haru73376.post_collector.common.security.JwtTokenProvider;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "JWT_SECRET=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHk=",
        "DB_USERNAME=test",
        "DB_PASSWORD=test"
})
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserService userService;

    @MockitoBean
    SecurityContextUtils securityContextUtils;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    // -------------------------------------------------------------------------
    // getMyProfile()
    // -------------------------------------------------------------------------

    @Test
    void getMyProfile_authenticated_returns200WithUserResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse response = new UserResponse(
                userId, "username", "email123@example.com", LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(userService.getMyProfile(userId)).willReturn(response);

        mockMvc.perform(get("/api/v1/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(userId.toString()))
               .andExpect(jsonPath("$.username").value("username"))
               .andExpect(jsonPath("$.email").value("email123@example.com"))
               .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void getMyProfile_userNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(userService.getMyProfile(userId))
                .willThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void getMyProfile_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void getMyProfile_invalidToken_returns401() throws Exception {
        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // updateProfile()
    // -------------------------------------------------------------------------

    @Test
    void updateProfile_validRequest_returns200WithUpdatedUsername() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("newUsername");
        UserResponse response = new UserResponse(
                userId, "newUsername", "email123@example.com", LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(userService.updateProfile(eq(userId), any(UpdateProfileRequest.class))).willReturn(response);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("newUsername"))
                .andExpect(jsonPath("$.email").value("email123@example.com"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void updateProfile_emptyBody_passesNullUsername() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse response = new UserResponse(
                userId, "originalUsername", "email123@example.com", LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(userService.updateProfile(eq(userId), any(UpdateProfileRequest.class))).willReturn(response);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        var captor = forClass(UpdateProfileRequest.class);
        verify(userService).updateProfile(eq(userId), captor.capture());
        assertThat(captor.getValue().username()).isNull();
    }

    @Test
    void updateProfile_blankUsername_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("username size must be between 1 and 50"));
    }

    @Test
    void updateProfile_usernameTooLong_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("a".repeat(51));

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("username size must be between 1 and 50"));
    }

    @Test
    void updateProfile_usernameDuplicated_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("duplicateUsername");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(userService.updateProfile(eq(userId), any(UpdateProfileRequest.class)))
                .willThrow(new ConflictException("Username already exists"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void updateProfile_userNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("newUsername");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(userService.updateProfile(eq(userId), any(UpdateProfileRequest.class)))
                .willThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void updateProfile_malformedJsonBody_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void updateProfile_noToken_returns401() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("newUsername");

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void updateProfile_invalidToken_returns401() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("newUsername");

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // changePassword()
    // -------------------------------------------------------------------------

    @Test
    void changePassword_validRequest_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "newPw1234!");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(userId, request);
    }

    @Test
    void changePassword_missingCurrentPassword_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest(null, "newPw1234!");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("currentPassword must not be blank"));
    }

    @Test
    void changePassword_missingNewPassword_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("newPassword must not be blank"));
    }

    @Test
    void changePassword_newPasswordTooShort_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "short1");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("newPassword size must be between 8 and 100"));
    }

    @Test
    void changePassword_newPasswordTooLong_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "a".repeat(101));

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("newPassword size must be between 8 and 100"));
    }

    @Test
    void changePassword_currentPasswordIncorrect_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("wrongCurrentPw1234!", "newPw1234!");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        willThrow(new BusinessRuleViolationException("Current password is incorrect"))
                .given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void changePassword_userNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "newPw1234!");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        willThrow(new ResourceNotFoundException("User not found"))
                .given(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void changePassword_malformedJsonBody_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void changePassword_noToken_returns401() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "newPw1234!");

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void changePassword_invalidToken_returns401() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "newPw1234!");

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}