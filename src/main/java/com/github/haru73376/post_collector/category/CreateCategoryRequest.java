package com.github.haru73376.post_collector.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name,
        UUID parentId,
        @PositiveOrZero Integer sortOrder
) {
}
