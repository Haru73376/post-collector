package com.github.haru73376.post_collector.post;

import com.github.haru73376.post_collector.tag.TagResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PostSummaryResponse(
    UUID id,
    String url,
    String title,
    String memo,
    String thumbnailUrl,
    String platform,
    boolean isFavorite,
    CategoryBriefResponse category,
    List<TagResponse> tags,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
