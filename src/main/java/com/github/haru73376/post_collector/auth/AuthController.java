package com.github.haru73376.post_collector.auth;

import com.github.haru73376.post_collector.common.exception.InvalidTokenException;
import com.github.haru73376.post_collector.user.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return buildTokenResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            // required=false to return 401 (not 400) when cookie is missing
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken
    ) {
        if (rawRefreshToken == null) {
            throw new InvalidTokenException("Refresh token is missing");
        }

        return buildTokenResponse(authService.refresh(rawRefreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken
    ) {
        if (rawRefreshToken != null) {
            authService.logout(rawRefreshToken);
        }

        ResponseCookie expiredCookie = ResponseCookie
                .from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0) // Invalidate the cookie
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    private ResponseEntity<TokenResponse> buildTokenResponse(AuthResult result) {
        ResponseCookie refreshTokenCookie = ResponseCookie
                .from("refreshToken", result.rawRefreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(result.refreshTokenMaxAge())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(result.response());
    }
}
