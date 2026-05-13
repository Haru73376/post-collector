package com.github.haru73376.post_collector.auth;

import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.InvalidCredentialsException;
import com.github.haru73376.post_collector.common.exception.InvalidTokenException;
import com.github.haru73376.post_collector.common.security.JwtTokenProvider;
import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import com.github.haru73376.post_collector.user.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;
    @Value("${spring.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already exists");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User savedUser = userRepository.saveAndFlush(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        // Intentionally treat missing user and wrong password as the same error to prevent user enumeration
        User user = userRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        return buildAuthResult(user);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow(
                () -> new InvalidTokenException("Refresh token is invalid or expired")
        );
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteByTokenHash(tokenHash);
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }

        refreshTokenRepository.deleteByTokenHash(tokenHash);

        return buildAuthResult(refreshToken.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.deleteByTokenHash(hashToken(rawRefreshToken));
    }

    private AuthResult buildAuthResult(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());

        String rawRefreshToken = issueRefreshToken(user);

        TokenResponse response = new TokenResponse(
                accessToken,
                "Bearer",
                (int) (accessTokenExpiration / 1000) // Convert milliseconds to seconds
        );

        return new AuthResult(
                response,
                rawRefreshToken,
                refreshTokenExpiration / 1000 // Convert milliseconds to seconds
        );
    }

    private String issueRefreshToken(User user) {
        String rawRefreshToken = generateRawRefreshToken();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(
                LocalDateTime.now().plus(
                        Duration.ofMillis(refreshTokenExpiration)
                )
        );

        refreshTokenRepository.save(refreshToken);
        return rawRefreshToken;
    }

    private String generateRawRefreshToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return encodeBase64Url(tokenBytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return encodeBase64Url(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java, so this exception will never be thrown
            throw new IllegalStateException("SHA-256 algorithm not found", e);
        }
    }

    private String encodeBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
