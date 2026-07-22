package com.github.haru73376.post_collector.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
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

    // Eagerly fetches category to avoid N+1 in getPosts; safe to combine with
    // Specification since EntityGraph hints are applied independently of the WHERE clause
    // (see SimpleJpaRepository#getQuery -> applyRepositoryMethodMetadata)
    @EntityGraph(attributePaths = "category")
    @Override
    Page<SavedPost> findAll(Specification<SavedPost> spec, Pageable pageable);
}
