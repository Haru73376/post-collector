package com.github.haru73376.post_collector.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.haru73376.post_collector.common.dto.ErrorResponse;
import com.github.haru73376.post_collector.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    private static final String[] PUBLIC_PATHS = new String[]{
            "/api/v1/auth/**",
            // swagger
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/api-docs/**"
    };
    private static final String AUTHENTICATION_REQUIRED = "Authentication required";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");

                            HttpStatus status = HttpStatus.UNAUTHORIZED;
                            ErrorResponse errorResponse = new ErrorResponse(
                                    status.value(),
                                    status.getReasonPhrase(),
                                    AUTHENTICATION_REQUIRED
                            );

                            objectMapper.writeValue(response.getWriter(), errorResponse);
                        })
                )

                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}