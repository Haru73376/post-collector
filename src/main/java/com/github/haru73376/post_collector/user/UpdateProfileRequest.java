package com.github.haru73376.post_collector.user;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 50) String username
) {
}
