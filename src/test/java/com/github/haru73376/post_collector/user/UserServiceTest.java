package com.github.haru73376.post_collector.user;

import com.github.haru73376.post_collector.common.exception.BusinessRuleViolationException;
import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // -------------------------------------------------------------------------
    // getMyProfile()
    // -------------------------------------------------------------------------

    @Test
    void getMyProfile_throwsResourceNotFoundException_whenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();

        given(userRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getMyProfile_mapsAllFieldsFromEntityToResponse_whenUserExists() {
        UUID userId = UUID.randomUUID();

        User user = buildUser("username");

        given(userRepository.findById(any())).willReturn(Optional.of(user));

        UserResponse result = userService.getMyProfile(userId);

        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.username()).isEqualTo("username");
        assertThat(result.email()).isEqualTo("email123@example.com");
        assertThat(result.createdAt()).isEqualTo(user.getCreatedAt());
    }

    // -------------------------------------------------------------------------
    // updateProfile()
    // -------------------------------------------------------------------------

    @Test
    void updateProfile_throwsResourceNotFoundException_whenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("newUsername");

        given(userRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, never()).existsByUsername(any());
        verify(userRepository, never()).flush();
    }

    @Test
    void updateProfile_doesNotChangeUsername_whenUsernameIsNull() {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest(null);

        User user = buildUser("originalUsername");
        given(userRepository.findById(any())).willReturn(Optional.of(user));

        UserResponse result = userService.updateProfile(userId, request);

        assertThat(result.username()).isEqualTo("originalUsername");
        verify(userRepository, never()).existsByUsername(any());
    }

    @Test
    void updateProfile_skipsDuplicateCheck_whenUsernameEqualsCurrentValue() {
        UUID userId = UUID.randomUUID();
        UpdateProfileRequest request = new UpdateProfileRequest("sameUsername");

        User user = buildUser("sameUsername");
        given(userRepository.findById(any())).willReturn(Optional.of(user));

        UserResponse result = userService.updateProfile(userId, request);

        assertThat(result.username()).isEqualTo("sameUsername");
        verify(userRepository, never()).existsByUsername(any());
    }

    @Test
    void updateProfile_updatesUsername_whenNewUsernameIsNotDuplicated() {
        UUID userId = UUID.randomUUID();
        String newUsername = "newUsername";
        UpdateProfileRequest request = new UpdateProfileRequest(newUsername);

        User user = buildUser("oldUsername");
        given(userRepository.findById(any())).willReturn(Optional.of(user));
        given(userRepository.existsByUsername(any())).willReturn(false);

        UserResponse result = userService.updateProfile(userId, request);

        assertThat(result.username()).isEqualTo(newUsername);
        assertThat(result.email()).isEqualTo(user.getEmail());
        assertThat(result.createdAt()).isEqualTo(user.getCreatedAt());
        assertThat(result.id()).isEqualTo(user.getId());
        verify(userRepository).existsByUsername(newUsername);
        verify(userRepository).flush();
    }

    @Test
    void updateProfile_throwsConflictException_whenNewUsernameIsDuplicated() {
        UUID userId = UUID.randomUUID();
        String newUsername = "newUsername";
        UpdateProfileRequest request = new UpdateProfileRequest(newUsername);

        User user = buildUser("originalUsername");
        given(userRepository.findById(any())).willReturn(Optional.of(user));
        given(userRepository.existsByUsername(any())).willReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already exists");
        assertThat(user.getUsername()).isEqualTo("originalUsername");
        verify(userRepository).existsByUsername(newUsername);
    }

    // -------------------------------------------------------------------------
    // changePassword()
    // -------------------------------------------------------------------------

    @Test
    void changePassword_throwsResourceNotFoundException_whenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "newPw1234!");
        
        given(userRepository.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void changePassword_throwsBusinessRuleViolationException_whenCurrentPasswordDoesNotMatch() {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "newPw1234!");

        User user = buildUser("username");
        given(userRepository.findById(any())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(any(), any())).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword(userId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Current password is incorrect");
        verify(passwordEncoder).matches(request.currentPassword(), user.getPasswordHash());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void changePassword_updatesPasswordHash_whenCurrentPasswordMatches() {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("currentPw1234!", "newPw1234!");

        String newPasswordHash = "encoded-new-hash";
        User user = buildUser("username");

        given(userRepository.findById(any())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())).willReturn(true);
        given(passwordEncoder.encode(any())).willReturn(newPasswordHash);

        userService.changePassword(userId, request);

        assertThat(user.getPasswordHash()).isEqualTo(newPasswordHash);
        verify(passwordEncoder).encode(request.newPassword());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private User buildUser(String username) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now());
        user.setUsername(username);
        user.setEmail("email123@example.com");
        user.setPasswordHash("encoded-hash");
        return user;
    }
}