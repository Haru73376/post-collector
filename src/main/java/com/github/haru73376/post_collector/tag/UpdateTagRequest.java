package com.github.haru73376.post_collector.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTagRequest(
        @NotBlank @Size(max = 50) String name
) {
}
