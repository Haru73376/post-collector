package com.github.haru73376.post_collector.common.ratelimit;

import com.github.haru73376.post_collector.common.exception.RateLimitExceededException;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRateLimitInterceptorTest {

    @Mock
    RateLimiterRegistry rateLimiterRegistry;
    @Mock
    SecurityContextUtils securityContextUtils;

    @InjectMocks
    UserRateLimitInterceptor userRateLimitInterceptor;

    @Test
    void returnsTrue_whenWithinLimit() {
        UUID userId = UUID.randomUUID();
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(rateLimiterRegistry.tryConsume(userId.toString(), RateLimitRules.AUTHENTICATED_API)).willReturn(true);

        boolean result = userRateLimitInterceptor.preHandle(null, null, null);

        assertThat(result).isTrue();
    }

    @Test
    void throwsRateLimitExceededException_whenLimitExceeded() {
        UUID userId = UUID.randomUUID();
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(rateLimiterRegistry.tryConsume(userId.toString(), RateLimitRules.AUTHENTICATED_API)).willReturn(false);

        assertThatThrownBy(() -> userRateLimitInterceptor.preHandle(null, null, null))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many requests. Please try again later.");

        verify(rateLimiterRegistry).tryConsume(userId.toString(), RateLimitRules.AUTHENTICATED_API);
    }
}