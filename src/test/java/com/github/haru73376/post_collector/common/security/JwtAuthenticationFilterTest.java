package com.github.haru73376.post_collector.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    HttpServletRequest request;
    @Mock
    FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider);
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthentication_whenBearerTokenIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        given(request.getHeader("Authorization")).willReturn("Bearer valid-token");
        given(jwtTokenProvider.extractUserId("valid-token")).willReturn(Optional.of(userId));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotSetAuthentication_whenAuthorizationHeaderIsAbsent() throws Exception {
        given(request.getHeader("Authorization")).willReturn(null);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtTokenProvider);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotSetAuthentication_whenHeaderDoesNotStartWithBearer() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Basic dXNlcjpwYXNz");

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtTokenProvider);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doesNotSetAuthentication_whenTokenIsInvalid() throws Exception {
        given(request.getHeader("Authorization")).willReturn("Bearer invalid-token");
        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
