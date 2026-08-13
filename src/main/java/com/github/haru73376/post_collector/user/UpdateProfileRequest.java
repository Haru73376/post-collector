package com.github.haru73376.post_collector.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 50) @Schema(example = "alice-updated") String username
) {
}
