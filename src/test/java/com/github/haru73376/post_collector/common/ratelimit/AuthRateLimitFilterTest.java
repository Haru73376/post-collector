package com.github.haru73376.post_collector.common.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    @Mock
    RateLimiterRegistry rateLimiterRegistry;
    @Mock
    HttpServletRequest request;
    @Mock
    FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthRateLimitFilter authRateLimitFilter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        authRateLimitFilter = new AuthRateLimitFilter(rateLimiterRegistry, objectMapper);
        response = new MockHttpServletResponse();
    }

    @Test
    void doesNotTouchRateLimiter_whenMethodIsNotPost() throws Exception {
        given(request.getMethod()).willReturn("GET");

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiterRegistry);
    }

    @Test
    void doesNotTouchRateLimiter_whenPathIsNotLoginOrRegister() throws Exception {
        given(request.getMethod()).willReturn("POST");
        given(request.getRequestURI()).willReturn("/api/v1/other");

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiterRegistry);
    }

    @Test
    void consumesLoginRule_whenPostToLoginPath() throws Exception {
        given(request.getMethod()).willReturn("POST");
        given(request.getRequestURI()).willReturn("/api/v1/auth/login");
        given(request.getRemoteAddr()).willReturn("203.0.113.5");
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(true);

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterRegistry).tryConsume("/api/v1/auth/login:203.0.113.5", RateLimitRules.LOGIN);
    }

    @Test
    void consumesRegisterRule_whenPostToRegisterPath() throws Exception {
        given(request.getMethod()).willReturn("POST");
        given(request.getRequestURI()).willReturn("/api/v1/auth/register");
        given(request.getRemoteAddr()).willReturn("203.0.113.5");
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(true);

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterRegistry).tryConsume("/api/v1/auth/register:203.0.113.5", RateLimitRules.REGISTER);
    }

    @Test
    void continuesChain_whenWithinLimit() throws Exception {
        given(request.getMethod()).willReturn("POST");
        given(request.getRequestURI()).willReturn("/api/v1/auth/login");
        given(request.getRemoteAddr()).willReturn("203.0.113.5");
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(true);

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksWithTooManyRequestsResponse_whenLimitExceeded() throws Exception {
        given(request.getMethod()).willReturn("POST");
        given(request.getRequestURI()).willReturn("/api/v1/auth/login");
        given(request.getRemoteAddr()).willReturn("203.0.113.5");
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(false);

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("message").asText()).isEqualTo("Too many requests. Please try again later.");
    }

    @Test
    void usesFirstForwardedForValue_whenHeaderPresent() throws Exception {
        given(request.getMethod()).willReturn("POST");
        given(request.getRequestURI()).willReturn("/api/v1/auth/login");
        given(request.getHeader("X-Forwarded-For")).willReturn("203.0.113.5, 10.0.0.1");
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(true);

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterRegistry).tryConsume("/api/v1/auth/login:203.0.113.5", RateLimitRules.LOGIN);
    }

    @Test
    void fallsBackToRemoteAddr_whenForwardedForHeaderAbsent() throws Exception {
        given(request.getMethod()).willReturn("POST");
        given(request.getRequestURI()).willReturn("/api/v1/auth/login");
        given(request.getHeader("X-Forwarded-For")).willReturn(null);
        given(request.getRemoteAddr()).willReturn("198.51.100.9");
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(true);

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterRegistry).tryConsume("/api/v1/auth/login:198.51.100.9", RateLimitRules.LOGIN);
    }

    @Test
    void fallsBackToRemoteAddr_whenForwardedForHeaderIsBlank() throws Exception {
        given(request.getMethod()).willReturn("POST");
        given(request.getRequestURI()).willReturn("/api/v1/auth/login");
        given(request.getHeader("X-Forwarded-For")).willReturn("   ");
        given(request.getRemoteAddr()).willReturn("198.51.100.9");
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(true);

        authRateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(rateLimiterRegistry).tryConsume("/api/v1/auth/login:198.51.100.9", RateLimitRules.LOGIN);
    }
}
