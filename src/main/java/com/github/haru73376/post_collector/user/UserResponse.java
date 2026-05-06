package com.github.haru73376.post_collector.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private LocalDateTime createdAt;
}
