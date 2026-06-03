package com.github.haru73376.post_collector.category;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        UUID parentId,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
