package com.github.haru73376.post_collector.tag;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class PostTagId implements Serializable {
    private UUID postId;
    private Long tagId;
}
