package com.github.haru73376.post_collector.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Schema(example = "alice@example.com") String email,
        @NotBlank @Schema(example = "password123") String password
) {
}
