package com.github.haru73376.post_collector.category;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record CategoryTreeResponse(
        @Schema(example = "01912e0a-7c3a-7c3a-8b3a-1234567890ab") UUID id,
        @Schema(example = "Recipes") String name,
        @Schema(example = "0") int sortOrder,
        @Schema(example = "12") long postCount,
        List<CategoryTreeResponse> children
) {
}
