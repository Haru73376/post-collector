package com.github.haru73376.post_collector.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTagRequest(
        @NotBlank @Size(max = 50) @Schema(example = "cooking") String name
) {
}
