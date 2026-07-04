package com.github.haru73376.post_collector.tag;

import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
public class PostTagId implements Serializable {
    private UUID postId;
    private Long tagId;
}
