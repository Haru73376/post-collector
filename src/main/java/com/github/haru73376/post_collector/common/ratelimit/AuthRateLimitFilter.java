package com.github.haru73376.post_collector.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.haru73376.post_collector.common.dto.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Handles /auth/login and /auth/register, which are called before the caller is authenticated,
// so a user ID isn't available yet and the client IP is the only identity signal we have.
// Implemented as a Filter (registered in SecurityConfig, same layer as JwtAuthenticationFilter)
// rather than a HandlerInterceptor, because it must run independently of authentication state.
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private final RateLimiterRegistry rateLimiterRegistry;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {
        Bandwidth rule = resolveRule(request);

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRequestURI() + ":" + extractClientIp(request);

        if (rateLimiterRegistry.tryConsume(key, rule)) {
            filterChain.doFilter(request, response);
        } else {
            writeRateLimitExceededResponse(response);
        }
    }

    private Bandwidth resolveRule(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        if (LOGIN_PATH.equals(request.getRequestURI())) {
            return RateLimitRules.LOGIN;
        }
        if (REGISTER_PATH.equals(request.getRequestURI())) {
            return RateLimitRules.REGISTER;
        }
        return null;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitExceededResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Too many requests. Please try again later."
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}