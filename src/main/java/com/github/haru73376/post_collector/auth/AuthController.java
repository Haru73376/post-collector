package com.github.haru73376.post_collector.auth;

import com.github.haru73376.post_collector.common.dto.ErrorResponse;
import com.github.haru73376.post_collector.common.exception.InvalidTokenException;
import com.github.haru73376.post_collector.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "Registration and JWT-based login/logout. /login and /register are IP rate-limited (5/min and 3/min respectively).")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
            description = "Creates a user account with a BCrypt-hashed password. Returns 409 if the email or username is already taken.")
    @ApiResponse(responseCode = "409", description = "Email or username is already taken",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":409,\"error\":\"Conflict\",\"message\":\"Email already exists\"}")))
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in",
            description = "Returns a short-lived access token in the response body and sets a long-lived, HttpOnly "
                    + "refresh token cookie.")
    @ApiResponse(responseCode = "401", description = "Email or password is incorrect",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid email or password\"}")))
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return buildTokenResponse(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh the access token",
            description = "Reads the refresh token cookie, rotates it (the presented token is invalidated even if "
                    + "reused afterward), and issues a new access token plus a new refresh token cookie.")
    @ApiResponse(responseCode = "401", description = "Refresh token cookie is missing, invalid, or expired",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Refresh token is invalid or expired\"}")))
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
    @Operation(summary = "Log out", description = "Invalidates the refresh token server-side and clears the cookie.")
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
