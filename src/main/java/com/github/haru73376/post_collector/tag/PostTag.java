package com.github.haru73376.post_collector.tag;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

@Entity
@Table(name = "post_tags")
@IdClass(PostTagId.class)
@NoArgsConstructor
public class PostTag implements Persistable<PostTagId> {
    @Id
    @Column(name = "post_id")
    private UUID postId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    // postId/tagId are assigned manually (no @GeneratedValue), so the default
    // isNew() check (id != null) would always treat this as an existing row and
    // route save() through merge() (extra SELECT) instead of persist().
    @Transient
    private boolean isNew = true;

    public PostTag(UUID postId, Long tagId) {
        this.postId = postId;
        this.tagId = tagId;
    }

    @Override
    public PostTagId getId() {
        return new PostTagId(postId, tagId);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }
}
