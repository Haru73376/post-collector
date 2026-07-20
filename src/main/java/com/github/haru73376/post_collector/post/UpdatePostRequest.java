package com.github.haru73376.post_collector.post;

import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

import java.util.List;
import java.util.UUID;

public record UpdatePostRequest(
        @Size(max = 2048)
        String url,

        @Size(max = 255)
        String title,

        // memo/thumbnailUrl length limits can't be validated directly by Bean Validation
        // on a JsonNullable field (would require a custom ValueExtractor), so PostService checks them instead
        JsonNullable<String> memo,

        JsonNullable<String> thumbnailUrl,

        Platform platform,

        JsonNullable<UUID> categoryId,

        @Size(max = 20)
        List<Long> tagIds,

        Boolean isFavorite
) {
    public UpdatePostRequest {
        if (memo == null) {
            memo = JsonNullable.undefined();
        }
        if (thumbnailUrl == null) {
            thumbnailUrl = JsonNullable.undefined();
        }
        if (categoryId == null) {
            categoryId = JsonNullable.undefined();
        }
    }
}