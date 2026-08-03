package com.github.haru73376.post_collector.auth;

import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RefreshTokenRepositoryTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    UserRepository userRepository;

    private User saveUser() {
        User user = new User();
        user.setUsername("username-" + UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hashed");
        return userRepository.save(user);
    }

    private RefreshToken saveRefreshToken(User user, String tokenHash) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        return refreshTokenRepository.save(refreshToken);
    }

    // -------------------------------------------------------------------------
    // findByTokenHash()
    // -------------------------------------------------------------------------

    @Test
    void findByTokenHash_returnsOnlyMatchingToken_whenMultipleTokensExist() {
        User user = saveUser();
        saveRefreshToken(user, "hash-a");
        saveRefreshToken(user, "hash-b");

        Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("hash-a");

        assertThat(result).isPresent();
        assertThat(result.get().getTokenHash()).isEqualTo("hash-a");
    }

    @Test
    void findByTokenHash_returnsEmpty_whenNoMatchingToken() {
        User user = saveUser();
        saveRefreshToken(user, "hash-a");

        Optional<RefreshToken> result = refreshTokenRepository.findByTokenHash("no-such-hash");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // deleteByTokenHash()
    // -------------------------------------------------------------------------

    @Test
    void deleteByTokenHash_removesToken_whenTokenHashMatches() {
        User user = saveUser();
        saveRefreshToken(user, "hash-a");

        refreshTokenRepository.deleteByTokenHash("hash-a");

        assertThat(refreshTokenRepository.findByTokenHash("hash-a")).isEmpty();
    }

    @Test
    void deleteByTokenHash_doesNothing_whenNoMatchingToken() {
        User user = saveUser();
        saveRefreshToken(user, "hash-a");

        refreshTokenRepository.deleteByTokenHash("no-such-hash");

        assertThat(refreshTokenRepository.findByTokenHash("hash-a")).isPresent();
    }

    // -------------------------------------------------------------------------
    // save() - @CreationTimestamp
    // -------------------------------------------------------------------------

    @Test
    void save_populatesCreatedAtAutomatically() {
        User user = saveUser();

        RefreshToken saved = saveRefreshToken(user, "hash-a");

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }
}