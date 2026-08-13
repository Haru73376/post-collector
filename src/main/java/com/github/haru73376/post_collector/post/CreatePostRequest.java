package com.github.haru73376.post_collector.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePostRequest(
        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "url must be a valid URL starting with http:// or https://")
        @Schema(example = "https://www.instagram.com/p/Cxxxxxxxxxx/")
        String url,

        @NotBlank @Size(max = 255)
        @Schema(example = "10-minute pasta recipe")
        String title,

        @Size(max = 65535)
        @Schema(example = "Looked good, try this on the weekend")
        String memo,

        @Size(max = 2048)
        @Schema(description = "Must be an HTTPS URL if provided", example = "https://example.com/thumbnail.jpg")
        String thumbnailUrl,

        @NotNull
        @Schema(example = "INSTAGRAM")
        Platform platform,

        @Schema(description = "Omit for an uncategorized post")
        UUID categoryId,

        @Size(max = 20)
        @Schema(description = "Omit or send an empty list for no tags")
        List<Long> tagIds,

        @Schema(description = "Defaults to false if omitted")
        Boolean isFavorite
) {
}