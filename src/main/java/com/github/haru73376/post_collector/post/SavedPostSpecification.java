package com.github.haru73376.post_collector.post;

import com.github.haru73376.post_collector.tag.PostTag;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Objects;
import java.util.UUID;

public class SavedPostSpecification {

    private SavedPostSpecification() {
    }

    public static Specification<SavedPost> withCriteria(UUID userId, PostSearchCriteria criteria) {
        return Specification.allOf(
                hasUser(userId),
                hasCategory(criteria.categoryId()),
                hasPlatform(criteria.platform()),
                hasTag(criteria.tagId()),
                isFavorite(criteria.favorite()),
                matchesKeyword(criteria.keyword())
        );
    }

    private static Specification<SavedPost> hasUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    private static Specification<SavedPost> hasCategory(UUID categoryId) {
        if (categoryId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    private static Specification<SavedPost> hasPlatform(Platform platform) {
        if (platform == null) return null;
        return (root, query, cb) -> cb.equal(root.get("platform"), platform);
    }

    private static Specification<SavedPost> isFavorite(Boolean favorite) {
        if (favorite == null) return null;
        return (root, query, cb) -> cb.equal(root.get("isFavorite"), favorite);
    }

    private static Specification<SavedPost> matchesKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String pattern = "%" + keyword + "%";
        return (root, query, cb) -> cb.or(
                cb.like(root.get("title"), pattern),
                cb.like(root.get("memo"), pattern)
        );
    }

    private static Specification<SavedPost> hasTag(Long tagId) {
        if (tagId == null) return null;
        return (root, query, cb) -> {
            Subquery<Long> subquery = Objects.requireNonNull(query).subquery(Long.class);
            var postTag = subquery.from(PostTag.class);
            subquery.select(postTag.get("tagId"))
                    .where(
                            cb.equal(postTag.get("postId"), root.get("id")),
                            cb.equal(postTag.get("tagId"), tagId)
                    );
            return cb.exists(subquery);
        };
    }
}