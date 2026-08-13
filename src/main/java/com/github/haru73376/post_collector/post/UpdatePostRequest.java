package com.github.haru73376.post_collector.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

import java.util.List;
import java.util.UUID;

public record UpdatePostRequest(
        @Size(min = 1, max = 2048)
        @Schema(description = "Omit to leave unchanged", example = "https://www.instagram.com/p/Cxxxxxxxxxx/")
        String url,

        @Size(min = 1, max = 255)
        @Schema(description = "Omit to leave unchanged", example = "10-minute pasta recipe (updated)")
        String title,

        // memo/thumbnailUrl length limits can't be validated directly by Bean Validation
        // on a JsonNullable field (would require a custom ValueExtractor), so PostService checks them instead
        @Schema(description = "Omit to leave unchanged; send null to clear it")
        JsonNullable<String> memo,

        @Schema(description = "Omit to leave unchanged; send null to clear it. Must be an HTTPS URL if provided")
        JsonNullable<String> thumbnailUrl,

        @Schema(description = "Omit to leave unchanged")
        Platform platform,

        @Schema(description = "Omit to leave unchanged; send null to make the post uncategorized")
        JsonNullable<UUID> categoryId,

        @Size(max = 20)
        @Schema(description = "Omit to leave the existing tags unchanged; send an empty list to remove all tags. If provided, fully replaces the post's tags")
        List<Long> tagIds,

        @Schema(description = "Omit to leave unchanged")
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