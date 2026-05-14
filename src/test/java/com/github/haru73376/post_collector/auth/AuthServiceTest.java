package com.github.haru73376.post_collector.auth;

import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.InvalidCredentialsException;
import com.github.haru73376.post_collector.common.exception.InvalidTokenException;
import com.github.haru73376.post_collector.common.security.JwtTokenProvider;
import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import com.github.haru73376.post_collector.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "accessTokenExpiration", 900_000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604_800_000L);
    }

    // -------------------------------------------------------------------------
    // register()
    // -------------------------------------------------------------------------

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");
        given(userRepository.existsByEmail("alice@example.com")).willReturn(false);
        given(userRepository.existsByUsername("alice")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("hashed");

        User savedUser = buildUser("alice", "alice@example.com", "hashed");
        given(userRepository.saveAndFlush(any(User.class))).willReturn(savedUser);

        UserResponse response = authService.register(request);

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.id()).isNotNull();
    }

    @Test
    void register_duplicateEmail_throwsConflictException() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");
        given(userRepository.existsByEmail("alice@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test
    void register_duplicateUsername_throwsConflictException() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");
        given(userRepository.existsByEmail("alice@example.com")).willReturn(false);
        given(userRepository.existsByUsername("alice")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already exists");
    }

    // -------------------------------------------------------------------------
    // login()
    // -------------------------------------------------------------------------

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("alice@example.com", "password123");
        User user = buildUser("alice", "alice@example.com", "hashed");

        given(userRepository.findByEmail("alice@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "hashed")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(any(UUID.class))).willReturn("access.token.value");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willReturn(new RefreshToken());

        AuthResult result = authService.login(request);

        assertThat(result.response().accessToken()).isEqualTo("access.token.value");
        assertThat(result.response().tokenType()).isEqualTo("Bearer");
        assertThat(result.rawRefreshToken()).isNotBlank();
    }

    @Test
    void login_userNotFound_throwsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("nobody@example.com", "password123");
        given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("alice@example.com", "wrongpassword");
        User user = buildUser("alice", "alice@example.com", "hashed");

        given(userRepository.findByEmail("alice@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongpassword", "hashed")).willReturn(false);

        // same message as userNotFound to prevent user enumeration attacks
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    // -------------------------------------------------------------------------
    // refresh()
    // -------------------------------------------------------------------------

    @Test
    void refresh_success() {
        User user = buildUser("alice", "alice@example.com", "hashed");

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));

        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));
        given(jwtTokenProvider.generateAccessToken(any(UUID.class))).willReturn("new.access.token");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willReturn(new RefreshToken());

        AuthResult result = authService.refresh("valid-raw-token");

        assertThat(result.response().accessToken()).isEqualTo("new.access.token");
        assertThat(result.rawRefreshToken()).isNotBlank();
        // old token must be rotated out
        verify(refreshTokenRepository).deleteByTokenHash(anyString());
    }

    @Test
    void refresh_tokenNotFound_throwsInvalidTokenException() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        // same message as expiredToken to avoid leaking whether the token ever existed
        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Refresh token is invalid or expired");
    }

    @Test
    void refresh_expiredToken_throwsInvalidTokenException() {
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setExpiresAt(LocalDateTime.now().minusDays(1));

        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(expiredToken));

        // same message as tokenNotFound to avoid leaking whether the token ever existed
        assertThatThrownBy(() -> authService.refresh("expired-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("Refresh token is invalid or expired");
        // expired token must be cleaned up from DB
        verify(refreshTokenRepository).deleteByTokenHash(anyString());
    }

    // -------------------------------------------------------------------------
    // logout()
    // -------------------------------------------------------------------------

    @Test
    void logout_deletesRefreshToken() {
        authService.logout("some-raw-token");

        verify(refreshTokenRepository).deleteByTokenHash(anyString());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private User buildUser(String username, String email, String passwordHash) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return user;
    }
}