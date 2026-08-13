package com.github.haru73376.post_collector.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateCategoryRequest(
        @Size(min = 1, max = 100) @Schema(example = "Recipes", description = "Omit to leave unchanged") String name,
        @Schema(description = "Omit to leave unchanged") UUID parentId,
        @PositiveOrZero @Schema(description = "Omit to leave unchanged") Integer sortOrder
) {
}
