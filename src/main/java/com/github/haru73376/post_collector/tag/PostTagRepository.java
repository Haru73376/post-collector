package com.github.haru73376.post_collector.tag;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {
    void deleteAllByTagId(Long tagId);
}
