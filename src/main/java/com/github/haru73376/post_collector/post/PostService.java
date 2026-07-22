package com.github.haru73376.post_collector.post;

import com.github.haru73376.post_collector.category.Category;
import com.github.haru73376.post_collector.category.CategoryRepository;
import com.github.haru73376.post_collector.common.dto.PageResponse;
import com.github.haru73376.post_collector.common.exception.BusinessRuleViolationException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import com.github.haru73376.post_collector.tag.PostTag;
import com.github.haru73376.post_collector.tag.PostTagRepository;
import com.github.haru73376.post_collector.tag.Tag;
import com.github.haru73376.post_collector.tag.TagRepository;
import com.github.haru73376.post_collector.tag.TagResponse;
import com.github.haru73376.post_collector.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "updatedAt", "title");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_MEMO_LENGTH = 65535;
    private static final int MAX_THUMBNAIL_URL_LENGTH = 2048;
    private static final String MSG_CATEGORY_NOT_FOUND = "Category not found";
    private static final String MSG_TAG_NOT_FOUND = "One or more tags not found";
    private static final String MSG_POST_NOT_FOUND = "Saved post not found";
    private static final Pattern THUMBNAIL_URL_PATTERN = Pattern.compile("^https://\\S+$");

    private final SavedPostRepository savedPostRepository;
    private final PostTagRepository postTagRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<PostSummaryResponse> getPosts(UUID userId, PostSearchCriteria criteria, Pageable pageable) {
        validateSort(pageable.getSort());
        Pageable safePageable = clampPageSize(pageable);

        Specification<SavedPost> spec = SavedPostSpecification.withCriteria(userId, criteria);
        Page<SavedPost> posts = savedPostRepository.findAll(spec, safePageable);

        List<UUID> postIds = posts.map(SavedPost::getId).getContent();
        Map<UUID, List<TagResponse>> tagMap = postIds.isEmpty()
                ? Map.of()
                : postTagRepository.findTagsByPostIdIn(postIds);

        Page<PostSummaryResponse> responsePage = posts.map(post ->
                toResponse(post, tagMap.getOrDefault(post.getId(), List.of())));
        return PageResponse.of(responsePage);
    }

    @Transactional
    public PostDetailResponse createPost(UUID userId, CreatePostRequest request) {
        Category category = validateAndGetCategory(request.categoryId(), userId);
        List<Tag> tags = validateAndGetTags(request.tagIds(), userId);

        if (request.thumbnailUrl() != null) {
            validateThumbnailUrl(request.thumbnailUrl());
        }

        SavedPost post = new SavedPost();
        post.setUser(userRepository.getReferenceById(userId));
        post.setCategory(category);
        post.setUrl(request.url());
        post.setTitle(request.title());
        post.setMemo(request.memo());
        post.setThumbnailUrl(request.thumbnailUrl());
        post.setPlatform(request.platform());
        post.setFavorite(request.isFavorite() != null ? request.isFavorite() : false);

        // saveAndFlush ensures @CreationTimestamp/@UpdateTimestamp and the generated id
        // are populated before toDetailResponse() reads them
        SavedPost savedPost = savedPostRepository.saveAndFlush(post);

        if (!tags.isEmpty()) {
            List<PostTag> postTags = tags.stream()
                    .map(tag -> new PostTag(savedPost.getId(), tag.getId()))
                    .toList();
            postTagRepository.saveAll(postTags);
        }

        return toDetailResponse(savedPost, toTagResponses(tags));
    }

    @Transactional
    public PostDetailResponse updatePost(UUID userId, UUID postId, UpdatePostRequest request) {
        SavedPost post = findOwnedPost(postId, userId);

        if (request.url() != null) {
            post.setUrl(request.url());
        }
        if (request.title() != null) {
            post.setTitle(request.title());
        }
        if (request.platform() != null) {
            post.setPlatform(request.platform());
        }
        if (request.isFavorite() != null) {
            post.setFavorite(request.isFavorite());
        }
        if (request.memo().isPresent()) {
            // Unlike Optional, isPresent() is true whenever "memo" was in the JSON at all
            // (even as explicit null) — false only when the key was omitted
            String memo = request.memo().get();
            validateMemoLength(memo);
            post.setMemo(memo);
        }
        if (request.thumbnailUrl().isPresent()) {
            // Same JsonNullable semantics as memo above: true for explicit null too
            String thumbnailUrl = request.thumbnailUrl().get();
            if (thumbnailUrl != null) {
                validateThumbnailUrlLength(thumbnailUrl);
                validateThumbnailUrl(thumbnailUrl);
            }
            post.setThumbnailUrl(thumbnailUrl);
        }
        if (request.categoryId().isPresent()) {
            // Same JsonNullable semantics as memo above: true for explicit null too.
            // A null value here means "clear the category" (validateAndGetCategory returns null for null input)
            post.setCategory(validateAndGetCategory(request.categoryId().get(), userId));
        }

        List<TagResponse> tagResponses = request.tagIds() != null
                ? updatePostTags(postId, request.tagIds(), userId)
                : postTagRepository.findTagsByPostIdIn(List.of(postId)).getOrDefault(postId, List.of());

        // Flush to trigger @UpdateTimestamp before building the response
        savedPostRepository.flush();

        return toDetailResponse(post, tagResponses);
    }

    @Transactional
    public void deletePost(UUID userId, UUID postId) {
        SavedPost post = findOwnedPost(postId, userId);
        post.setDeletedAt(LocalDateTime.now());
    }

    private List<TagResponse> updatePostTags(UUID postId, List<Long> tagIds, UUID userId) {
        List<Tag> tags = validateAndGetTags(tagIds, userId);

        postTagRepository.deleteAllByPostId(postId);
        if (!tags.isEmpty()) {
            List<PostTag> postTags = tags.stream()
                    .map(tag -> new PostTag(postId, tag.getId()))
                    .toList();
            postTagRepository.saveAll(postTags);
        }

        return toTagResponses(tags);
    }

    private SavedPost findOwnedPost(UUID id, UUID userId) {
        return savedPostRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_POST_NOT_FOUND));
    }

    private void validateMemoLength(String memo) {
        if (memo != null && memo.length() > MAX_MEMO_LENGTH) {
            throw new BusinessRuleViolationException("memo must not exceed " + MAX_MEMO_LENGTH + " characters");
        }
    }

    private void validateThumbnailUrlLength(String thumbnailUrl) {
        if (thumbnailUrl.length() > MAX_THUMBNAIL_URL_LENGTH) {
            throw new BusinessRuleViolationException(
                    "thumbnailUrl must not exceed " + MAX_THUMBNAIL_URL_LENGTH + " characters");
        }
    }

    private List<TagResponse> toTagResponses(List<Tag> tags) {
        return tags.stream()
                .map(tag -> new TagResponse(tag.getId(), tag.getName()))
                .toList();
    }

    private Category validateAndGetCategory(UUID categoryId, UUID userId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(MSG_CATEGORY_NOT_FOUND));
    }

    private List<Tag> validateAndGetTags(List<Long> tagIds, UUID userId) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        // Deduplicate requested tag IDs
        Set<Long> uniqueTagIds = new HashSet<>(tagIds);
        List<Tag> tags = tagRepository.findAllByIdInAndUserId(List.copyOf(uniqueTagIds), userId);
        if (tags.size() != uniqueTagIds.size()) {
            throw new ResourceNotFoundException(MSG_TAG_NOT_FOUND);
        }
        return tags;
    }

    private void validateThumbnailUrl(String thumbnailUrl) {
        if (!THUMBNAIL_URL_PATTERN.matcher(thumbnailUrl).matches()) {
            throw new BusinessRuleViolationException("thumbnailUrl must be a valid HTTPS URL");
        }
    }

    private PostDetailResponse toDetailResponse(SavedPost post, List<TagResponse> tags) {
        CategoryBriefResponse category = post.getCategory() != null
                ? new CategoryBriefResponse(post.getCategory().getId(), post.getCategory().getName())
                : null;

        return new PostDetailResponse(
                post.getId(),
                post.getUrl(),
                post.getTitle(),
                post.getMemo(),
                post.getThumbnailUrl(),
                post.getPlatform().name(),
                post.isFavorite(),
                category,
                tags,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private void validateSort(Sort sort) {
        boolean invalid = sort.stream().anyMatch(order -> !ALLOWED_SORT_PROPERTIES.contains(order.getProperty()));
        if (invalid) {
            throw new BusinessRuleViolationException("Invalid sort property");
        }
    }

    private Pageable clampPageSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    private PostSummaryResponse toResponse(SavedPost post, List<TagResponse> tags) {
        CategoryBriefResponse category = post.getCategory() != null
                ? new CategoryBriefResponse(post.getCategory().getId(), post.getCategory().getName())
                : null;

        return new PostSummaryResponse(
                post.getId(),
                post.getUrl(),
                post.getTitle(),
                post.getMemo(),
                post.getThumbnailUrl(),
                post.getPlatform().name(),
                post.isFavorite(),
                category,
                tags,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}