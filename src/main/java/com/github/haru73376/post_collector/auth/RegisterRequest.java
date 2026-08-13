package com.github.haru73376.post_collector.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 50) @Schema(example = "alice") String username,
        @NotBlank @Email @Size(max = 255) @Schema(example = "alice@example.com") String email,
        @NotBlank @Size(min = 8, max = 100) @Schema(example = "password123") String password
) {
}
