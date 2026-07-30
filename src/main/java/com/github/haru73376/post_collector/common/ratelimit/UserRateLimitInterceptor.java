package com.github.haru73376.post_collector.common.ratelimit;

import com.github.haru73376.post_collector.common.exception.RateLimitExceededException;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

// Applies to all authenticated endpoints except /api/v1/auth/** (see WebMvcConfig). Implemented
// as a HandlerInterceptor rather than a Filter because it needs the authenticated user's ID,
// which is only available once Spring Security's filter chain (JwtAuthenticationFilter) has
// already run; interceptors execute after that filter chain, so there's no ordering to manage
// against it the way AuthRateLimitFilter has to be positioned relative to JwtAuthenticationFilter.
@Component
@RequiredArgsConstructor
public class UserRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final SecurityContextUtils securityContextUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID userId = securityContextUtils.getCurrentUserId();

        if (rateLimiterRegistry.tryConsume(userId.toString(), RateLimitRules.AUTHENTICATED_API)) {
            return true;
        }

        throw new RateLimitExceededException("Too many requests. Please try again later.");
    }
}