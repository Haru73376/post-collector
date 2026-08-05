package com.github.haru73376.post_collector.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItaW50ZWdyYXRpb24tdGVzdHMtb25seSEh";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", 900_000L);
        ReflectionTestUtils.invokeMethod(jwtTokenProvider, "init");
    }

    @Test
    void generateAccessToken_thenExtractUserId_roundTripsToSameUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtTokenProvider.generateAccessToken(userId);
        Optional<UUID> extracted = jwtTokenProvider.extractUserId(token);

        assertThat(extracted).contains(userId);
    }

    @Test
    void extractUserId_returnsEmpty_whenTokenIsInvalid() {
        Optional<UUID> result = jwtTokenProvider.extractUserId("this-is-not-a-valid-jwt");

        assertThat(result).isEmpty();
    }
}
