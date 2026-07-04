package com.github.haru73376.post_collector.tag;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "post_tags")
@IdClass(PostTagId.class)
@NoArgsConstructor
@AllArgsConstructor
public class PostTag {
    @Id
    @Column(name = "post_id")
    private UUID postId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;
}
