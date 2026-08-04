package com.github.haru73376.post_collector.user;

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
class UserRepositoryTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    UserRepository userRepository;

    private User saveUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        // User.id is generated client-side (@UuidGenerator), so plain save() doesn't
        // necessarily flush immediately (see CategoryRepositoryTest for the same issue).
        // Use saveAndFlush so @CreationTimestamp/@UpdateTimestamp values are populated
        // back onto the returned instance right away.
        return userRepository.saveAndFlush(user);
    }

    // -------------------------------------------------------------------------
    // findByEmail()
    // -------------------------------------------------------------------------

    @Test
    void findByEmail_returnsUser_whenEmailMatches() {
        saveUser("user-a", "user-a@example.com");
        saveUser("user-b", "user-b@example.com");

        Optional<User> result = userRepository.findByEmail("user-a@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("user-a@example.com");
    }

    @Test
    void findByEmail_returnsEmpty_whenNoMatchingEmail() {
        saveUser("user-a", "user-a@example.com");

        Optional<User> result = userRepository.findByEmail("no-such@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmail_isCaseInsensitive_dueToColumnCollation() {
        saveUser("user-a", "User-A@Example.com");

        Optional<User> result = userRepository.findByEmail("user-a@example.com");

        assertThat(result).isPresent();
    }

    // -------------------------------------------------------------------------
    // existsByEmail()
    // -------------------------------------------------------------------------

    @Test
    void existsByEmail_returnsTrue_whenEmailMatches() {
        saveUser("user-a", "user-a@example.com");

        assertThat(userRepository.existsByEmail("user-a@example.com")).isTrue();
    }

    @Test
    void existsByEmail_returnsFalse_whenNoMatchingEmail() {
        saveUser("user-a", "user-a@example.com");

        assertThat(userRepository.existsByEmail("no-such@example.com")).isFalse();
    }

    // -------------------------------------------------------------------------
    // existsByUsername()
    // -------------------------------------------------------------------------

    @Test
    void existsByUsername_returnsTrue_whenUsernameMatches() {
        saveUser("user-a", "user-a@example.com");

        assertThat(userRepository.existsByUsername("user-a")).isTrue();
    }

    @Test
    void existsByUsername_returnsFalse_whenNoMatchingUsername() {
        saveUser("user-a", "user-a@example.com");

        assertThat(userRepository.existsByUsername("no-such-user")).isFalse();
    }

    // -------------------------------------------------------------------------
    // save() - @CreationTimestamp / @UpdateTimestamp
    // -------------------------------------------------------------------------

    @Test
    void save_populatesCreatedAtAndUpdatedAt_onInsert() {
        User saved = saveUser("user-a", "user-a@example.com");

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void save_updatesUpdatedAtOnly_whenFieldChanges() throws InterruptedException {
        User user = saveUser("original-name", "user-a@example.com");
        LocalDateTime initialCreatedAt = user.getCreatedAt();
        LocalDateTime initialUpdatedAt = user.getUpdatedAt();

        // updated_at is a MySQL TIMESTAMP (1-second precision, see V1__create_tables.sql),
        // so the two writes must be more than 1 second apart for this comparison to be reliable.
        Thread.sleep(1100);
        user.setUsername("changed-name");
        userRepository.flush();

        assertThat(user.getCreatedAt()).isEqualTo(initialCreatedAt);
        assertThat(user.getUpdatedAt()).isAfter(initialUpdatedAt);
    }
}