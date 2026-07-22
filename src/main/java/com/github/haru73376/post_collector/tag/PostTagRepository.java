package com.github.haru73376.post_collector.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {
    void deleteAllByPostId(UUID postId);

    @Query("""
            SELECT pt.postId AS postId, t.id AS tagId, t.name AS tagName
            FROM PostTag pt
            JOIN Tag t ON t.id = pt.tagId
            WHERE pt.postId IN :postIds
            """)
    List<PostTagProjection> findTagRowsByPostIdIn(@Param("postIds") List<UUID> postIds);

    default Map<UUID, List<TagResponse>> findTagsByPostIdIn(List<UUID> postIds) {
        return findTagRowsByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(
                        PostTagProjection::getPostId,
                        Collectors.mapping(
                                row -> new TagResponse(row.getTagId(), row.getTagName()),
                                Collectors.toList()
                        )
                ));
    }
}