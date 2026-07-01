package com.github.haru73376.post_collector.tag;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record TagDetailResponse(
        Long id,
        String name,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt
) {
}
