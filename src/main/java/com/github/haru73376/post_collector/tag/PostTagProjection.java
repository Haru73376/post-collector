package com.github.haru73376.post_collector.tag;

import java.util.UUID;

public interface PostTagProjection {
    UUID getPostId();

    Long getTagId();

    String getTagName();
}