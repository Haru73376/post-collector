package com.github.haru73376.post_collector.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findAllByUserId(UUID userId);

    Optional<Tag> findByIdAndUserId(Long id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);

    List<Tag> findAllByIdInAndUserId(List<Long> ids, UUID userId);
}
