package com.github.haru73376.post_collector.tag;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record TagDetailResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "cooking") String name,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(example = "2026-08-25T10:15:30") LocalDateTime createdAt
) {
}
