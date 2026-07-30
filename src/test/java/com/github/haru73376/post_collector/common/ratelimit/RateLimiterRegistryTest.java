package com.github.haru73376.post_collector.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterRegistryTest {

    private final RateLimiterRegistry rateLimiterRegistry = new RateLimiterRegistry();

    @Test
    void tryConsume_returnsTrue_whileWithinCapacity() {
        Bandwidth rule = Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(1)));

        assertThat(rateLimiterRegistry.tryConsume("key", rule)).isTrue();
        assertThat(rateLimiterRegistry.tryConsume("key", rule)).isTrue();
        assertThat(rateLimiterRegistry.tryConsume("key", rule)).isTrue();
    }

    @Test
    void tryConsume_returnsFalse_afterCapacityExhausted() {
        Bandwidth rule = Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(1)));

        assertThat(rateLimiterRegistry.tryConsume("key", rule)).isTrue();
        assertThat(rateLimiterRegistry.tryConsume("key", rule)).isTrue();
        assertThat(rateLimiterRegistry.tryConsume("key", rule)).isTrue();

        assertThat(rateLimiterRegistry.tryConsume("key", rule)).isFalse();
    }

    @Test
    void tryConsume_tracksDifferentKeysIndependently() {
        Bandwidth rule = Bandwidth.classic(1, Refill.intervally(1, Duration.ofMinutes(1)));

        assertThat(rateLimiterRegistry.tryConsume("keyA", rule)).isTrue();
        assertThat(rateLimiterRegistry.tryConsume("keyA", rule)).isFalse();

        assertThat(rateLimiterRegistry.tryConsume("keyB", rule)).isTrue();
    }
}