package com.github.haru73376.post_collector.auth;

public record AuthResult(
        TokenResponse response,
        String rawRefreshToken,
        long refreshTokenMaxAge
) {
}

