package com.github.haru73376.post_collector.post;

import java.util.UUID;

public record PostSearchCriteria(
        UUID categoryId,
        Platform platform,
        Long tagId,
        Boolean favorite,
        String keyword
) {
}
