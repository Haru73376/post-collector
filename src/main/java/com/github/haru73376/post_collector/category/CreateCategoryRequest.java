package com.github.haru73376.post_collector.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) @Schema(example = "Recipes") String name,
        @Schema(description = "Omit or null for a root-level category") UUID parentId,
        @PositiveOrZero @Schema(example = "0") Integer sortOrder
) {
}
