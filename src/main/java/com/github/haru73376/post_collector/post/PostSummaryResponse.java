package com.github.haru73376.post_collector.post;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.haru73376.post_collector.tag.TagResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PostSummaryResponse(
    @Schema(example = "01912e0a-8d4a-7c3a-8b3a-1122334455ff") UUID id,
    @Schema(example = "https://instagram.com/p/xyz") String url,
    @Schema(example = "Great recipe") String title,
    @Schema(example = "Try this with less sugar next time") String memo,
    @Schema(example = "https://instagram.com/p/xyz/thumbnail.jpg") String thumbnailUrl,
    @Schema(example = "INSTAGRAM") String platform,
    @Schema(example = "false") boolean isFavorite,
    CategoryBriefResponse category,
    List<TagResponse> tags,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(example = "2026-08-25T10:15:30") LocalDateTime createdAt,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(example = "2026-08-25T10:15:30") LocalDateTime updatedAt
) {
}
