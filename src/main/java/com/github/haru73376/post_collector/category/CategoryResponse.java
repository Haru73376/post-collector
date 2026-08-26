package com.github.haru73376.post_collector.category;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryResponse(
        @Schema(example = "01912e0a-7c3a-7c3a-8b3a-1234567890ab") UUID id,
        @Schema(example = "Recipes") String name,
        @Schema(example = "01912e0a-6b2a-7c3a-8b3a-0987654321cd") UUID parentId,
        @Schema(example = "0") int sortOrder,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(example = "2026-08-25T10:15:30") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(example = "2026-08-25T10:15:30") LocalDateTime updatedAt
) {
}
