package com.github.haru73376.post_collector.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePostRequest(
        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "^https?://\\S+$", message = "url must be a valid URL starting with http:// or https://")
        String url,

        @NotBlank @Size(max = 255)
        String title,

        @Size(max = 65535)
        String memo,

        @Size(max = 2048)
        String thumbnailUrl,

        @NotNull
        Platform platform,

        UUID categoryId,

        @Size(max = 20)
        List<Long> tagIds,

        Boolean isFavorite
) {
}