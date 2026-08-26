package com.github.haru73376.post_collector.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        @Schema(example = "01912e0a-5a1a-7c3a-8b3a-abcdef123456") UUID id,
        @Schema(example = "alice") String username,
        @Schema(example = "alice@example.com") String email,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(example = "2026-08-25T10:15:30") LocalDateTime createdAt
) {
}
