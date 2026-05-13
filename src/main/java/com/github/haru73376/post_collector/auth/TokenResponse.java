package com.github.haru73376.post_collector.auth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        int expiresIn
) {
}
