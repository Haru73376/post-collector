package com.github.haru73376.post_collector.category;

import java.util.List;
import java.util.UUID;

public record CategoryTreeResponse(
        UUID id,
        String name,
        int sortOrder,
        long postCount,
        List<CategoryTreeResponse> children
) {
}
