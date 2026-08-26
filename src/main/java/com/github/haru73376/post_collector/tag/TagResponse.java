package com.github.haru73376.post_collector.tag;

import io.swagger.v3.oas.annotations.media.Schema;

public record TagResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "cooking") String name
) {

}
