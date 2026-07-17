package com.github.haru73376.post_collector.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPost, UUID>,
        JpaSpecificationExecutor<SavedPost> {
    @Query("""
            SELECT sp.category.id, COUNT(sp)
            FROM SavedPost sp
            WHERE sp.category.id IN :categoryIds
            AND sp.deletedAt IS NULL
            GROUP BY sp.category.id
            """)
    List<Object[]> countByCategoryIds(@Param("categoryIds") List<UUID> categoryIds);

    Optional<SavedPost> findByIdAndUserId(UUID id, UUID userId);
}
