package com.github.haru73376.post_collector.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Schema(example = "password123") String currentPassword,
        @NotBlank @Size(min = 8, max = 100) @Schema(example = "newPassword456") String newPassword
) {
}
