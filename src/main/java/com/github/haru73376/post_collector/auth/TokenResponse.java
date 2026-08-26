package com.github.haru73376.post_collector.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3OCJ9.signature") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") int expiresIn
) {
}
