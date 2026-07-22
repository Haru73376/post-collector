package com.github.haru73376.post_collector.post;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.haru73376.post_collector.tag.TagResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PostDetailResponse(
        UUID id,
        String url,
        String title,
        String memo,
        String thumbnailUrl,
        String platform,
        boolean isFavorite,
        CategoryBriefResponse category,
        List<TagResponse> tags,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt
) {
}
