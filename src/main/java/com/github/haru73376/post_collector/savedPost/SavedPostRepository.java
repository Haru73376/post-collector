package com.github.haru73376.post_collector.savedPost;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPost, UUID> {
    long countByCategoryIdAndDeletedAtIsNull(UUID categoryId);
}
