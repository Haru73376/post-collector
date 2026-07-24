package com.github.haru73376.post_collector.post;

import com.github.haru73376.post_collector.category.Category;
import com.github.haru73376.post_collector.category.CategoryRepository;
import com.github.haru73376.post_collector.common.dto.PageResponse;
import com.github.haru73376.post_collector.common.exception.BusinessRuleViolationException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import com.github.haru73376.post_collector.tag.PostTag;
import com.github.haru73376.post_collector.tag.PostTagId;
import com.github.haru73376.post_collector.tag.PostTagRepository;
import com.github.haru73376.post_collector.tag.Tag;
import com.github.haru73376.post_collector.tag.TagRepository;
import com.github.haru73376.post_collector.tag.TagResponse;
import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private SavedPostRepository savedPostRepository;
    @Mock
    private PostTagRepository postTagRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostService postService;

    // -------------------------------------------------------------------------
    // getPosts()
    // -------------------------------------------------------------------------

    @Test
    void getPosts_throwsBusinessRuleViolationException_whenSortPropertyNotAllowed() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20, Sort.by("memo")
                                                      .descending()
        );

        assertThatThrownBy(() -> postService.getPosts(userId, criteria, pageable))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Invalid sort property");

        verify(savedPostRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getPosts_usesRequestedPageSize_whenSizeWithinLimit() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(Page.empty(pageable));

        postService.getPosts(userId, criteria, pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(savedPostRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue()
                         .getPageSize()).isEqualTo(20);
    }

    @Test
    void getPosts_clampsPageSizeTo100_whenSizeExceedsLimit() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(2, 500, Sort.by("title")
                                                        .ascending());

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(Page.empty(pageable));

        postService.getPosts(userId, criteria, pageable);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(savedPostRepository).findAll(any(Specification.class), captor.capture());
        Pageable captured = captor.getValue();
        assertThat(captured.getPageSize()).isEqualTo(100);
        assertThat(captured.getPageNumber()).isEqualTo(2);
        assertThat(captured.getSort()).isEqualTo(Sort.by("title")
                                                       .ascending());
    }

    @Test
    void getPosts_returnsEmptyContent_whenNoPostsMatch() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(Page.empty(pageable));

        PageResponse<PostSummaryResponse> result = postService.getPosts(userId, criteria, pageable);

        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(postTagRepository);
    }

    @Test
    void getPosts_fetchesTagsInSingleBatchCall_whenPostsExist() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        UUID post1Id = UUID.randomUUID();
        SavedPost post1 = buildSavedPost(post1Id, Platform.INSTAGRAM);

        UUID post2Id = UUID.randomUUID();
        SavedPost post2 = buildSavedPost(post2Id, Platform.YOUTUBE);

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post1, post2)));
        given(postTagRepository.findTagsByPostIdIn(any())).willReturn(Map.of());

        postService.getPosts(userId, criteria, pageable);

        verify(postTagRepository).findTagsByPostIdIn(List.of(post1Id, post2Id));
    }

    @Test
    void getPosts_includesCategoryBriefResponse_whenPostHasCategory() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        Category category = new Category();
        UUID categoryId = UUID.randomUUID();
        ReflectionTestUtils.setField(category, "id", categoryId);
        category.setName("category-name");

        SavedPost post = buildSavedPost(UUID.randomUUID(), Platform.INSTAGRAM);
        post.setCategory(category);

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post)));
        given(postTagRepository.findTagsByPostIdIn(any())).willReturn(Map.of());

        PageResponse<PostSummaryResponse> result = postService.getPosts(userId, criteria, pageable);

        CategoryBriefResponse categoryResponse = result.getContent()
                                                         .getFirst()
                                                         .category();
        assertThat(categoryResponse).isNotNull();
        assertThat(categoryResponse.id()).isEqualTo(categoryId);
        assertThat(categoryResponse.name()).isEqualTo("category-name");
    }

    @Test
    void getPosts_categoryIsNull_whenPostHasNoCategory() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        SavedPost post = buildSavedPost(UUID.randomUUID(), Platform.INSTAGRAM);

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post)));
        given(postTagRepository.findTagsByPostIdIn(any())).willReturn(Map.of());

        PageResponse<PostSummaryResponse> result = postService.getPosts(userId, criteria, pageable);

        assertThat(result.getContent()
                         .getFirst()
                         .category()).isNull();
    }

    @Test
    void getPosts_includesAllTags_whenPostHasTags() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);

        TagResponse tag1 = new TagResponse(1L, "tag1");
        TagResponse tag2 = new TagResponse(2L, "tag2");

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post)));
        given(postTagRepository.findTagsByPostIdIn(any())).willReturn(Map.of(postId, List.of(tag1, tag2)));

        PageResponse<PostSummaryResponse> result = postService.getPosts(userId, criteria, pageable);

        assertThat(result.getContent()
                         .getFirst()
                         .tags()).containsExactly(tag1, tag2);
    }

    @Test
    void getPosts_tagsIsEmpty_whenPostHasNoMatchingTags() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        SavedPost post = buildSavedPost(UUID.randomUUID(), Platform.INSTAGRAM);

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post)));
        given(postTagRepository.findTagsByPostIdIn(any())).willReturn(Map.of());

        PageResponse<PostSummaryResponse> result = postService.getPosts(userId, criteria, pageable);

        assertThat(result.getContent()
                         .getFirst()
                         .tags()).isEmpty();
    }

    @Test
    void getPosts_mapsPageMetadata_whenResultsReturned() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(1, 20);

        SavedPost post = buildSavedPost(UUID.randomUUID(), Platform.INSTAGRAM);

        Page<SavedPost> page = new PageImpl<>(List.of(post), pageable, 45);

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);
        given(postTagRepository.findTagsByPostIdIn(any())).willReturn(Map.of());

        PageResponse<PostSummaryResponse> result = postService.getPosts(userId, criteria, pageable);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(45);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    void getPosts_mapsAllFieldsFromEntityToResponse_whenPostExists() {
        UUID userId = UUID.randomUUID();
        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 20);

        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setUrl("https://www.instagram.com/p/xyz789/");
        post.setTitle("post-title");
        post.setMemo("post-memo");
        post.setThumbnailUrl("https://example.com/thumb.jpg");
        post.setFavorite(true);
        LocalDateTime createdAt = LocalDateTime.now()
                                                .minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now();
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        ReflectionTestUtils.setField(post, "updatedAt", updatedAt);

        given(savedPostRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(post)));
        given(postTagRepository.findTagsByPostIdIn(any())).willReturn(Map.of());

        PageResponse<PostSummaryResponse> result = postService.getPosts(userId, criteria, pageable);

        PostSummaryResponse response = result.getContent()
                                              .getFirst();
        assertThat(response.id()).isEqualTo(postId);
        assertThat(response.url()).isEqualTo("https://www.instagram.com/p/xyz789/");
        assertThat(response.title()).isEqualTo("post-title");
        assertThat(response.memo()).isEqualTo("post-memo");
        assertThat(response.thumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(response.platform()).isEqualTo("INSTAGRAM");
        assertThat(response.isFavorite()).isTrue();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    // -------------------------------------------------------------------------
    // createPost()
    // -------------------------------------------------------------------------

    @Test
    void createPost_createsUncategorizedPost_whenCategoryIdIsNull() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, null, null);

        given(userRepository.getReferenceById(userId)).willReturn(new User());
        stubSaveAndFlushToEcho();

        PostDetailResponse result = postService.createPost(userId, request);

        assertThat(result.category()).isNull();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void createPost_setsCategory_whenCategoryIdIsOwnedByUser() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category category = buildCategory(categoryId, "category-name");
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, categoryId, null, null);

        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));
        given(userRepository.getReferenceById(userId)).willReturn(new User());
        stubSaveAndFlushToEcho();

        PostDetailResponse result = postService.createPost(userId, request);

        assertThat(result.category()
                         .id()).isEqualTo(categoryId);
        assertThat(result.category()
                         .name()).isEqualTo("category-name");
    }

    @Test
    void createPost_throwsResourceNotFoundException_whenCategoryIdNotOwnedOrNotFound() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, categoryId, null, null);

        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());

        // Same exception whether categoryId doesn't exist or belongs to another user (IDOR protection)
        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");

        verifyNoInteractions(tagRepository, savedPostRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void createPost_createsPostWithoutTags_whenTagIdsIsNullOrEmpty(List<Long> tagIds) {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, tagIds, null);

        given(userRepository.getReferenceById(userId)).willReturn(new User());
        stubSaveAndFlushToEcho();

        PostDetailResponse result = postService.createPost(userId, request);

        assertThat(result.tags()).isEmpty();
        verifyNoInteractions(tagRepository);
        verify(postTagRepository, never()).saveAll(any());
    }

    @Test
    void createPost_deduplicatesTagIds_whenTagIdsContainDuplicates() {
        UUID userId = UUID.randomUUID();
        Tag tag1 = buildTag(1L, "tag1");
        Tag tag2 = buildTag(2L, "tag2");
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM,
                null, List.of(1L, 1L, 2L), null);

        given(tagRepository.findAllByIdInAndUserId(any(), any())).willReturn(List.of(tag1, tag2));
        given(userRepository.getReferenceById(userId)).willReturn(new User());
        stubSaveAndFlushToEcho();

        postService.createPost(userId, request);

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(tagRepository).findAllByIdInAndUserId(captor.capture(), eq(userId));
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void createPost_attachesAllTags_whenTagIdsAreOwnedByUser() {
        UUID userId = UUID.randomUUID();
        Tag tag1 = buildTag(1L, "tag1");
        Tag tag2 = buildTag(2L, "tag2");
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, List.of(1L, 2L), null);

        given(tagRepository.findAllByIdInAndUserId(any(), any())).willReturn(List.of(tag1, tag2));
        given(userRepository.getReferenceById(userId)).willReturn(new User());
        UUID postId = UUID.randomUUID();
        stubSaveAndFlushToEcho(postId, LocalDateTime.now(), LocalDateTime.now());

        PostDetailResponse result = postService.createPost(userId, request);

        ArgumentCaptor<List<PostTag>> captor = ArgumentCaptor.forClass(List.class);
        verify(postTagRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(PostTag::getId)
                .containsExactlyInAnyOrder(new PostTagId(postId, 1L), new PostTagId(postId, 2L));
        assertThat(result.tags()).containsExactlyInAnyOrder(new TagResponse(1L, "tag1"), new TagResponse(2L, "tag2"));
    }

    @Test
    void createPost_throwsResourceNotFoundException_whenTagIdsContainNotOwnedOrNotFound() {
        UUID userId = UUID.randomUUID();
        Tag tag1 = buildTag(1L, "tag1");
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, List.of(1L, 2L), null);

        given(tagRepository.findAllByIdInAndUserId(any(), any())).willReturn(List.of(tag1));

        // Same exception whether a tagId doesn't exist or belongs to another user (IDOR protection)
        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("One or more tags not found");

        verifyNoInteractions(savedPostRepository, postTagRepository);
    }

    @Test
    void createPost_skipsThumbnailValidation_whenThumbnailUrlIsNull() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, null, null);

        given(userRepository.getReferenceById(userId)).willReturn(new User());
        stubSaveAndFlushToEcho();

        PostDetailResponse result = postService.createPost(userId, request);

        assertThat(result.thumbnailUrl()).isNull();
    }

    @Test
    void createPost_savesThumbnailUrl_whenHttpsUrlProvided() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, "https://example.com/thumb.jpg",
                Platform.INSTAGRAM, null, null, null);

        given(userRepository.getReferenceById(userId)).willReturn(new User());
        stubSaveAndFlushToEcho();

        PostDetailResponse result = postService.createPost(userId, request);

        assertThat(result.thumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
    }

    @Test
    void createPost_throwsBusinessRuleViolationException_whenThumbnailUrlIsNotHttps() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, "http://example.com/thumb.jpg",
                Platform.INSTAGRAM, null, null, null);

        assertThatThrownBy(() -> postService.createPost(userId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("thumbnailUrl must be a valid HTTPS URL");

        verifyNoInteractions(categoryRepository, tagRepository, postTagRepository, savedPostRepository);
    }

    @Test
    void createPost_defaultsFavoriteToFalse_whenIsFavoriteIsNull() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, null, null);

        given(userRepository.getReferenceById(userId)).willReturn(new User());
        stubSaveAndFlushToEcho();

        PostDetailResponse result = postService.createPost(userId, request);

        assertThat(result.isFavorite()).isFalse();
    }

    @Test
    void createPost_setsFavorite_whenIsFavoriteIsProvided() {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, null, true);

        given(userRepository.getReferenceById(userId)).willReturn(new User());
        stubSaveAndFlushToEcho();

        PostDetailResponse result = postService.createPost(userId, request);

        assertThat(result.isFavorite()).isTrue();
    }

    @Test
    void createPost_returnsPostDetailResponse_onHappyPath() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category category = buildCategory(categoryId, "category-name");
        Tag tag1 = buildTag(1L, "tag1");
        Tag tag2 = buildTag(2L, "tag2");

        CreatePostRequest request = new CreatePostRequest(
                "https://www.instagram.com/p/xyz789/", "post-title", "post-memo",
                "https://example.com/thumb.jpg", Platform.INSTAGRAM, categoryId, List.of(1L, 2L), true);

        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));
        given(tagRepository.findAllByIdInAndUserId(any(), any())).willReturn(List.of(tag1, tag2));
        given(userRepository.getReferenceById(userId)).willReturn(new User());

        UUID postId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now()
                                                .minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now();
        stubSaveAndFlushToEcho(postId, createdAt, updatedAt);

        PostDetailResponse result = postService.createPost(userId, request);

        assertThat(result.id()).isEqualTo(postId);
        assertThat(result.url()).isEqualTo("https://www.instagram.com/p/xyz789/");
        assertThat(result.title()).isEqualTo("post-title");
        assertThat(result.memo()).isEqualTo("post-memo");
        assertThat(result.thumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(result.platform()).isEqualTo("INSTAGRAM");
        assertThat(result.isFavorite()).isTrue();
        assertThat(result.category()
                         .id()).isEqualTo(categoryId);
        assertThat(result.category()
                         .name()).isEqualTo("category-name");
        assertThat(result.tags()).containsExactlyInAnyOrder(new TagResponse(1L, "tag1"), new TagResponse(2L, "tag2"));
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    // -------------------------------------------------------------------------
    // updatePost()
    // -------------------------------------------------------------------------

    @Test
    void updatePost_throwsResourceNotFoundException_whenPostNotOwnedOrNotFound() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, null, null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.empty());

        // Same exception whether the post doesn't exist, belongs to another user,
        // or is already soft-deleted (IDOR protection)
        assertThatThrownBy(() -> postService.updatePost(userId, postId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Saved post not found");

        verifyNoInteractions(categoryRepository, tagRepository, postTagRepository);
    }

    @Test
    void updatePost_keepsExistingSimpleFields_whenOmitted() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setUrl("https://example.com/original");
        post.setTitle("original-title");
        post.setFavorite(true);
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, null, null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getUrl()).isEqualTo("https://example.com/original");
        assertThat(post.getTitle()).isEqualTo("original-title");
        assertThat(post.getPlatform()).isEqualTo(Platform.INSTAGRAM);
        assertThat(post.isFavorite()).isTrue();
    }

    @Test
    void updatePost_updatesSimpleFields_whenProvided() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setUrl("https://example.com/original");
        post.setTitle("original-title");
        post.setFavorite(false);
        UpdatePostRequest request = new UpdatePostRequest(
                "https://example.com/updated", "updated-title", null, null, Platform.YOUTUBE, null, null, true);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getUrl()).isEqualTo("https://example.com/updated");
        assertThat(post.getTitle()).isEqualTo("updated-title");
        assertThat(post.getPlatform()).isEqualTo(Platform.YOUTUBE);
        assertThat(post.isFavorite()).isTrue();
    }

    @Test
    void updatePost_keepsExistingMemo_whenOmitted() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setMemo("original-memo");
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, null, null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getMemo()).isEqualTo("original-memo");
    }

    @Test
    void updatePost_clearsMemo_whenExplicitlyNull() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setMemo("original-memo");
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, JsonNullable.of(null), null, null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getMemo()).isNull();
    }

    @Test
    void updatePost_updatesMemo_whenWithinLengthLimit() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setMemo("original-memo");
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, JsonNullable.of("updated-memo"), null, null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getMemo()).isEqualTo("updated-memo");
    }

    @Test
    void updatePost_throwsBusinessRuleViolationException_whenMemoExceedsLengthLimit() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setMemo("original-memo");
        String tooLongMemo = "a".repeat(65536);
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, JsonNullable.of(tooLongMemo), null, null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(userId, postId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("memo must not exceed 65535 characters");

        assertThat(post.getMemo()).isEqualTo("original-memo");
    }

    @Test
    void updatePost_keepsExistingThumbnailUrl_whenOmitted() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setThumbnailUrl("https://example.com/original.jpg");
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, null, null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getThumbnailUrl()).isEqualTo("https://example.com/original.jpg");
    }

    @Test
    void updatePost_clearsThumbnailUrl_whenExplicitlyNull() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setThumbnailUrl("https://example.com/original.jpg");
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, JsonNullable.of(null), null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getThumbnailUrl()).isNull();
    }

    @Test
    void updatePost_throwsBusinessRuleViolationException_whenThumbnailUrlExceedsLengthLimit() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        String tooLongUrl = "https://example.com/" + "a".repeat(2048);
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, JsonNullable.of(tooLongUrl), null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(userId, postId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("must not exceed 2048");
    }

    @Test
    void updatePost_throwsBusinessRuleViolationException_whenThumbnailUrlIsNotHttps() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, JsonNullable.of("http://example.com/thumb.jpg"), null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(userId, postId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("must be a valid HTTPS URL");
    }

    @Test
    void updatePost_updatesThumbnailUrl_whenValid() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, JsonNullable.of("https://example.com/new.jpg"), null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getThumbnailUrl()).isEqualTo("https://example.com/new.jpg");
    }

    @Test
    void updatePost_keepsExistingCategory_whenCategoryIdOmitted() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Category originalCategory = buildCategory(UUID.randomUUID(), "original-category");
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setCategory(originalCategory);
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, null, null, null, null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getCategory()).isEqualTo(originalCategory);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void updatePost_clearsCategory_whenCategoryIdExplicitlyNull() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Category originalCategory = buildCategory(UUID.randomUUID(), "original-category");
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setCategory(originalCategory);
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, null, null, JsonNullable.of(null), null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getCategory()).isNull();
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void updatePost_updatesCategory_whenCategoryIdOwnedByUser() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        UUID newCategoryId = UUID.randomUUID();
        Category newCategory = buildCategory(newCategoryId, "new-category");
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, null, null, JsonNullable.of(newCategoryId), null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(categoryRepository.findByIdAndUserId(newCategoryId, userId)).willReturn(Optional.of(newCategory));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getCategory()).isEqualTo(newCategory);
    }

    @Test
    void updatePost_throwsResourceNotFoundException_whenCategoryIdNotOwnedOrNotFound() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        UUID categoryId = UUID.randomUUID();
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, null, null, JsonNullable.of(categoryId), null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());

        // Same exception whether categoryId doesn't exist or belongs to another user (IDOR protection)
        assertThatThrownBy(() -> postService.updatePost(userId, postId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");

        verifyNoInteractions(tagRepository, postTagRepository);
    }

    @Test
    void updatePost_keepsExistingTags_whenTagIdsOmitted() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, null, null, null, null, null);
        TagResponse existingTag = new TagResponse(1L, "existing-tag");

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of(postId, List.of(existingTag)));

        PostDetailResponse result = postService.updatePost(userId, postId, request);

        assertThat(result.tags()).containsExactly(existingTag);
        verify(postTagRepository, never()).deleteAllByPostId(any());
        verify(postTagRepository, never()).saveAll(any());
    }

    @Test
    void updatePost_clearsAllTags_whenTagIdsIsEmptyList() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, null, null, null, List.of(), null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));

        PostDetailResponse result = postService.updatePost(userId, postId, request);

        assertThat(result.tags()).isEmpty();
        verify(postTagRepository).deleteAllByPostId(postId);
        verify(postTagRepository, never()).saveAll(any());
    }

    @Test
    void updatePost_replacesTags_whenTagIdsProvided() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        Tag tag1 = buildTag(1L, "tag1");
        Tag tag2 = buildTag(2L, "tag2");
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, null, null, null, List.of(1L, 2L), null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(tagRepository.findAllByIdInAndUserId(any(), any())).willReturn(List.of(tag1, tag2));

        PostDetailResponse result = postService.updatePost(userId, postId, request);

        InOrder inOrder = Mockito.inOrder(postTagRepository);
        inOrder.verify(postTagRepository).deleteAllByPostId(postId);
        inOrder.verify(postTagRepository).saveAll(any());
        assertThat(result.tags()).containsExactlyInAnyOrder(new TagResponse(1L, "tag1"), new TagResponse(2L, "tag2"));
    }

    @Test
    void updatePost_throwsResourceNotFoundException_whenTagIdsContainNotOwnedOrNotFound() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        Tag tag1 = buildTag(1L, "tag1");
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, null, null, null, List.of(1L, 2L), null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(tagRepository.findAllByIdInAndUserId(any(), any())).willReturn(List.of(tag1));

        // Same exception whether a tagId doesn't exist or belongs to another user (IDOR protection)
        assertThatThrownBy(() -> postService.updatePost(userId, postId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("One or more tags not found");

        verify(postTagRepository, never()).deleteAllByPostId(any());
    }

    @Test
    void updatePost_updatesOnlyFavorite_whenOnlyIsFavoriteProvided() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Category originalCategory = buildCategory(UUID.randomUUID(), "original-category");
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setUrl("https://example.com/original");
        post.setTitle("original-title");
        post.setMemo("original-memo");
        post.setThumbnailUrl("https://example.com/original.jpg");
        post.setCategory(originalCategory);
        post.setFavorite(false);
        UpdatePostRequest request = new UpdatePostRequest(null, null, null, null, null, null, null, true);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.isFavorite()).isTrue();
        assertThat(post.getUrl()).isEqualTo("https://example.com/original");
        assertThat(post.getTitle()).isEqualTo("original-title");
        assertThat(post.getMemo()).isEqualTo("original-memo");
        assertThat(post.getThumbnailUrl()).isEqualTo("https://example.com/original.jpg");
        assertThat(post.getCategory()).isEqualTo(originalCategory);
        verifyNoInteractions(categoryRepository, tagRepository);
        verify(postTagRepository, never()).deleteAllByPostId(any());
        verify(postTagRepository, never()).saveAll(any());
    }

    @Test
    void updatePost_updatesOnlyProvidedFields_whenMultipleFieldsProvided() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        post.setUrl("https://example.com/original");
        post.setMemo("original-memo");
        post.setThumbnailUrl("https://example.com/original.jpg");
        post.setFavorite(false);
        UUID newCategoryId = UUID.randomUUID();
        Category newCategory = buildCategory(newCategoryId, "new-category");
        UpdatePostRequest request = new UpdatePostRequest(
                null, "updated-title", JsonNullable.of(null), null, null,
                JsonNullable.of(newCategoryId), null, null);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(categoryRepository.findByIdAndUserId(newCategoryId, userId)).willReturn(Optional.of(newCategory));
        given(postTagRepository.findTagsByPostIdIn(List.of(postId))).willReturn(Map.of());

        postService.updatePost(userId, postId, request);

        assertThat(post.getTitle()).isEqualTo("updated-title");
        assertThat(post.getMemo()).isNull();
        assertThat(post.getCategory()).isEqualTo(newCategory);
        assertThat(post.getUrl()).isEqualTo("https://example.com/original");
        assertThat(post.getThumbnailUrl()).isEqualTo("https://example.com/original.jpg");
        assertThat(post.isFavorite()).isFalse();
    }

    @Test
    void updatePost_returnsPostDetailResponse_mappingAllFields_afterUpdate() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);
        LocalDateTime createdAt = LocalDateTime.now()
                                                .minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now()
                                                .minusHours(1);
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        ReflectionTestUtils.setField(post, "updatedAt", updatedAt);

        UUID categoryId = UUID.randomUUID();
        Category category = buildCategory(categoryId, "category-name");
        Tag tag1 = buildTag(1L, "tag1");
        Tag tag2 = buildTag(2L, "tag2");

        UpdatePostRequest request = new UpdatePostRequest(
                "https://example.com/updated", "updated-title", JsonNullable.of("updated-memo"),
                JsonNullable.of("https://example.com/updated.jpg"), Platform.YOUTUBE,
                JsonNullable.of(categoryId), List.of(1L, 2L), true);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));
        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));
        given(tagRepository.findAllByIdInAndUserId(any(), any())).willReturn(List.of(tag1, tag2));

        PostDetailResponse result = postService.updatePost(userId, postId, request);

        assertThat(result.id()).isEqualTo(postId);
        assertThat(result.url()).isEqualTo("https://example.com/updated");
        assertThat(result.title()).isEqualTo("updated-title");
        assertThat(result.memo()).isEqualTo("updated-memo");
        assertThat(result.thumbnailUrl()).isEqualTo("https://example.com/updated.jpg");
        assertThat(result.platform()).isEqualTo("YOUTUBE");
        assertThat(result.isFavorite()).isTrue();
        assertThat(result.category()
                         .id()).isEqualTo(categoryId);
        assertThat(result.category()
                         .name()).isEqualTo("category-name");
        assertThat(result.tags()).containsExactlyInAnyOrder(new TagResponse(1L, "tag1"), new TagResponse(2L, "tag2"));
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    // -------------------------------------------------------------------------
    // deletePost()
    // -------------------------------------------------------------------------

    @Test
    void deletePost_throwsResourceNotFoundException_whenPostNotOwnedOrNotFound() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.empty());

        // Same exception whether the post doesn't exist, belongs to another user,
        // or is already soft-deleted (IDOR protection)
        assertThatThrownBy(() -> postService.deletePost(userId, postId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Saved post not found");
    }

    @Test
    void deletePost_setsDeletedAt_onHappyPath() {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        SavedPost post = buildSavedPost(postId, Platform.INSTAGRAM);

        given(savedPostRepository.findByIdAndUserId(postId, userId)).willReturn(Optional.of(post));

        LocalDateTime before = LocalDateTime.now();
        postService.deletePost(userId, postId);
        LocalDateTime after = LocalDateTime.now();

        assertThat(post.getDeletedAt()).isNotNull();
        assertThat(post.getDeletedAt()).isBetween(before, after);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private SavedPost buildSavedPost(UUID id, Platform platform) {
        SavedPost post = new SavedPost();
        ReflectionTestUtils.setField(post, "id", id);
        post.setPlatform(platform);
        return post;
    }

    private Category buildCategory(UUID id, String name) {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", id);
        category.setName(name);
        return category;
    }

    private Tag buildTag(Long id, String name) {
        Tag tag = new Tag();
        ReflectionTestUtils.setField(tag, "id", id);
        tag.setName(name);
        return tag;
    }

    // Makes saveAndFlush echo back the exact SavedPost the service built, with only the
    // DB-generated fields (id/createdAt/updatedAt) populated - mirrors real persistence
    // behavior so assertions on the response trace back to what createPost actually built,
    // instead of an independently stubbed object disconnected from the request.
    private void stubSaveAndFlushToEcho(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        given(savedPostRepository.saveAndFlush(any())).willAnswer(invocation -> {
            SavedPost post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "id", id);
            ReflectionTestUtils.setField(post, "createdAt", createdAt);
            ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
            return post;
        });
    }

    private void stubSaveAndFlushToEcho() {
        stubSaveAndFlushToEcho(UUID.randomUUID(), LocalDateTime.now(), LocalDateTime.now());
    }
}