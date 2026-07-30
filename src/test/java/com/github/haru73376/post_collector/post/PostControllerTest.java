package com.github.haru73376.post_collector.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.haru73376.post_collector.common.config.JacksonConfig;
import com.github.haru73376.post_collector.common.config.SecurityConfig;
import com.github.haru73376.post_collector.common.dto.PageResponse;
import com.github.haru73376.post_collector.common.exception.BusinessRuleViolationException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import com.github.haru73376.post_collector.common.ratelimit.RateLimiterRegistry;
import com.github.haru73376.post_collector.common.security.JwtTokenProvider;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import com.github.haru73376.post_collector.tag.TagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentCaptor.forClass;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
@TestPropertySource(properties = {
        "JWT_SECRET=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHk=",
        "DB_USERNAME=test",
        "DB_PASSWORD=test"
})
class PostControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PostService postService;

    @MockitoBean
    SecurityContextUtils securityContextUtils;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    // UserRateLimitInterceptor depends on RateLimiterRegistry; default-allow so rate limiting
    // (60/min per user) doesn't interfere with unrelated test scenarios
    @MockitoBean
    RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach
    void allowAllRequestsByDefault() {
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(true);
    }

    // -------------------------------------------------------------------------
    // getPosts()
    // -------------------------------------------------------------------------

    @Test
    void getPosts_authenticated_noFilters_returns200WithPageResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        CategoryBriefResponse category = new CategoryBriefResponse(categoryId, "category-name");
        TagResponse tag = new TagResponse(1L, "tag-name");
        PostSummaryResponse summary = new PostSummaryResponse(
                postId, "https://example.com/post", "title", "memo", "https://example.com/thumb.jpg",
                "INSTAGRAM", true, category, List.of(tag), LocalDateTime.now(), LocalDateTime.now());
        PageResponse<PostSummaryResponse> pageResponse = new PageResponse<>(List.of(summary), 0, 20, 1, 1);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.getPosts(eq(userId), any(PostSearchCriteria.class), any(Pageable.class)))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId.toString()))
                .andExpect(jsonPath("$.content[0].url").value("https://example.com/post"))
                .andExpect(jsonPath("$.content[0].title").value("title"))
                .andExpect(jsonPath("$.content[0].memo").value("memo"))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("https://example.com/thumb.jpg"))
                .andExpect(jsonPath("$.content[0].platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.content[0].isFavorite").value(true))
                .andExpect(jsonPath("$.content[0].category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.content[0].category.name").value("category-name"))
                .andExpect(jsonPath("$.content[0].tags[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].tags[0].name").value("tag-name"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getPosts_authenticated_noResults_returns200WithEmptyContent() throws Exception {
        UUID userId = UUID.randomUUID();
        PageResponse<PostSummaryResponse> pageResponse = new PageResponse<>(List.of(), 0, 20, 0, 0);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.getPosts(eq(userId), any(PostSearchCriteria.class), any(Pageable.class)))
                .willReturn(pageResponse);

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void getPosts_authenticated_allQueryParamsProvided_passesCriteriaToService() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.getPosts(eq(userId), any(PostSearchCriteria.class), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .param("categoryId", categoryId.toString())
                        .param("platform", "INSTAGRAM")
                        .param("tagId", "5")
                        .param("favorite", "true")
                        .param("keyword", "cats"))
                .andExpect(status().isOk());

        var captor = forClass(PostSearchCriteria.class);
        verify(postService).getPosts(eq(userId), captor.capture(), any(Pageable.class));
        PostSearchCriteria captured = captor.getValue();
        assertThat(captured.categoryId()).isEqualTo(categoryId);
        assertThat(captured.platform()).isEqualTo(Platform.INSTAGRAM);
        assertThat(captured.tagId()).isEqualTo(5L);
        assertThat(captured.favorite()).isTrue();
        assertThat(captured.keyword()).isEqualTo("cats");
    }

    @Test
    void getPosts_authenticated_pageAndSizeProvided_passesPageableToService() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.getPosts(eq(userId), any(PostSearchCriteria.class), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(), 2, 10, 0, 0));

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .param("page", "2")
                        .param("size", "10")
                        .param("sort", "title,asc"))
                .andExpect(status().isOk());

        var captor = forClass(Pageable.class);
        verify(postService).getPosts(eq(userId), any(PostSearchCriteria.class), captor.capture());
        Pageable captured = captor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(2);
        assertThat(captured.getPageSize()).isEqualTo(10);
        assertThat(captured.getSort()).isEqualTo(Sort.by("title")
                                                       .ascending());
    }

    @Test
    void getPosts_authenticated_pageParamsOmitted_usesConfiguredDefaults() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.getPosts(eq(userId), any(PostSearchCriteria.class), any(Pageable.class)))
                .willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk());

        var captor = forClass(Pageable.class);
        verify(postService).getPosts(eq(userId), any(PostSearchCriteria.class), captor.capture());
        Pageable captured = captor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured.getPageSize()).isEqualTo(20);
        assertThat(captured.getSort()).isEqualTo(Sort.by("createdAt")
                                                       .descending());
    }

    @Test
    void getPosts_invalidPlatformValue_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .param("platform", "INVALID_VALUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("platform")));
    }

    @Test
    void getPosts_invalidCategoryIdFormat_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .param("categoryId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void getPosts_serviceThrowsBusinessRuleViolationException_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.getPosts(eq(userId), any(PostSearchCriteria.class), any(Pageable.class)))
                .willThrow(new BusinessRuleViolationException("Invalid sort property"));

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .param("sort", "memo,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort property"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void getPosts_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void getPosts_invalidToken_returns401() throws Exception {
        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // createPost()
    // -------------------------------------------------------------------------

    @Test
    void createPost_validRequestWithAllFields_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://www.instagram.com/p/xyz789/", "post-title", "post-memo",
                "https://example.com/thumb.jpg", Platform.INSTAGRAM, categoryId, List.of(1L, 2L), true);

        CategoryBriefResponse category = new CategoryBriefResponse(categoryId, "category-name");
        List<TagResponse> tags = List.of(new TagResponse(1L, "tag1"), new TagResponse(2L, "tag2"));
        PostDetailResponse response = new PostDetailResponse(
                postId, "https://www.instagram.com/p/xyz789/", "post-title", "post-memo",
                "https://example.com/thumb.jpg", "INSTAGRAM", true, category, tags,
                LocalDateTime.now(), LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.createPost(eq(userId), any(CreatePostRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.url").value("https://www.instagram.com/p/xyz789/"))
                .andExpect(jsonPath("$.title").value("post-title"))
                .andExpect(jsonPath("$.memo").value("post-memo"))
                .andExpect(jsonPath("$.thumbnailUrl").value("https://example.com/thumb.jpg"))
                .andExpect(jsonPath("$.platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.isFavorite").value(true))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.name").value("category-name"))
                .andExpect(jsonPath("$.tags[0].id").value(1L))
                .andExpect(jsonPath("$.tags[0].name").value("tag1"))
                .andExpect(jsonPath("$.tags[1].id").value(2L))
                .andExpect(jsonPath("$.tags[1].name").value("tag2"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.updatedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void createPost_validRequestWithOnlyRequiredFields_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, null, null);

        PostDetailResponse response = new PostDetailResponse(
                postId, "https://example.com/post", "title", null, null, "INSTAGRAM", false, null, List.of(),
                LocalDateTime.now(), LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.createPost(eq(userId), any(CreatePostRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value(nullValue()))
                .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    void createPost_missingUrl_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                null, "title", null, null, Platform.INSTAGRAM, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("url must not be blank"));
    }

    @Test
    void createPost_missingTitle_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", null, null, null, Platform.INSTAGRAM, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("title must not be blank"));
    }

    @Test
    void createPost_invalidUrlFormat_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "not-a-url", "title", null, null, Platform.INSTAGRAM, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("url must be a valid URL")));
    }

    @Test
    void createPost_missingPlatform_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, null, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("platform must not be null"));
    }

    @Test
    void createPost_invalidPlatformEnumValue_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String rawJson = """
                {
                  "url": "https://example.com/post",
                  "title": "title",
                  "platform": "NOT_A_REAL_PLATFORM"
                }
                """;

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void createPost_tagIdsExceeds20_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        List<Long> tooManyTagIds = LongStream.rangeClosed(1, 21)
                                              .boxed()
                                              .toList();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, tooManyTagIds, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("tagIds")));
    }

    @Test
    void createPost_urlTooLong_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        String tooLongUrl = "https://example.com/" + "a".repeat(2048);
        CreatePostRequest request = new CreatePostRequest(
                tooLongUrl, "title", null, null, Platform.INSTAGRAM, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("url size must be between 0 and 2048"));
    }

    @Test
    void createPost_titleTooLong_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "a".repeat(256), null, null, Platform.INSTAGRAM, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("title size must be between 0 and 255"));
    }

    @Test
    void createPost_categoryNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM,
                UUID.randomUUID(), null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.createPost(eq(userId), any(CreatePostRequest.class)))
                .willThrow(new ResourceNotFoundException("Category not found"));

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void createPost_tagsNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM,
                null, List.of(1L, 2L), null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.createPost(eq(userId), any(CreatePostRequest.class)))
                .willThrow(new ResourceNotFoundException("One or more tags not found"));

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("One or more tags not found"));
    }

    @Test
    void createPost_thumbnailUrlNotHttps_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, "http://example.com/thumb.jpg",
                Platform.INSTAGRAM, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.createPost(eq(userId), any(CreatePostRequest.class)))
                .willThrow(new BusinessRuleViolationException("thumbnailUrl must be a valid HTTPS URL"));

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("thumbnailUrl must be a valid HTTPS URL"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void createPost_malformedJsonBody_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void createPost_noToken_returns401() throws Exception {
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, null, null);

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void createPost_invalidToken_returns401() throws Exception {
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/post", "title", null, null, Platform.INSTAGRAM, null, null, null);

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // updatePost()
    // -------------------------------------------------------------------------

    @Test
    void updatePost_validRequestWithAllFields_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        CategoryBriefResponse category = new CategoryBriefResponse(categoryId, "category-name");
        List<TagResponse> tags = List.of(new TagResponse(1L, "tag1"), new TagResponse(2L, "tag2"));
        PostDetailResponse response = new PostDetailResponse(
                postId, "https://www.instagram.com/p/updated/", "updated-title", "updated-memo",
                "https://example.com/updated-thumb.jpg", "INSTAGRAM", true, category, tags,
                LocalDateTime.now(), LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class))).willReturn(response);

        String rawJson = """
                {
                  "url": "https://www.instagram.com/p/updated/",
                  "title": "updated-title",
                  "memo": "updated-memo",
                  "thumbnailUrl": "https://example.com/updated-thumb.jpg",
                  "platform": "INSTAGRAM",
                  "categoryId": "%s",
                  "tagIds": [1, 2],
                  "isFavorite": true
                }
                """.formatted(categoryId);

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.url").value("https://www.instagram.com/p/updated/"))
                .andExpect(jsonPath("$.title").value("updated-title"))
                .andExpect(jsonPath("$.memo").value("updated-memo"))
                .andExpect(jsonPath("$.thumbnailUrl").value("https://example.com/updated-thumb.jpg"))
                .andExpect(jsonPath("$.platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.isFavorite").value(true))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.category.name").value("category-name"))
                .andExpect(jsonPath("$.tags[0].id").value(1L))
                .andExpect(jsonPath("$.tags[0].name").value("tag1"))
                .andExpect(jsonPath("$.tags[1].id").value(2L))
                .andExpect(jsonPath("$.tags[1].name").value("tag2"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.updatedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void updatePost_emptyBody_returns200WithUnchangedFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class)))
                .willReturn(buildPostDetailResponse(postId));

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        var captor = forClass(UpdatePostRequest.class);
        verify(postService).updatePost(eq(userId), eq(postId), captor.capture());
        UpdatePostRequest captured = captor.getValue();
        assertThat(captured.url()).isNull();
        assertThat(captured.title()).isNull();
        assertThat(captured.platform()).isNull();
        assertThat(captured.isFavorite()).isNull();
        assertThat(captured.memo().isPresent()).isFalse();
        assertThat(captured.thumbnailUrl().isPresent()).isFalse();
        assertThat(captured.categoryId().isPresent()).isFalse();
        assertThat(captured.tagIds()).isNull();
    }

    @Test
    void updatePost_explicitNullMemo_passesPresentJsonNullableWithNullValue() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class)))
                .willReturn(buildPostDetailResponse(postId));

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memo": null}
                                """))
                .andExpect(status().isOk());

        var captor = forClass(UpdatePostRequest.class);
        verify(postService).updatePost(eq(userId), eq(postId), captor.capture());
        UpdatePostRequest captured = captor.getValue();
        assertThat(captured.memo().isPresent()).isTrue();
        assertThat(captured.memo().get()).isNull();
    }

    @Test
    void updatePost_explicitNullThumbnailUrl_passesPresentJsonNullableWithNullValue() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class)))
                .willReturn(buildPostDetailResponse(postId));

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"thumbnailUrl": null}
                                """))
                .andExpect(status().isOk());

        var captor = forClass(UpdatePostRequest.class);
        verify(postService).updatePost(eq(userId), eq(postId), captor.capture());
        UpdatePostRequest captured = captor.getValue();
        assertThat(captured.thumbnailUrl().isPresent()).isTrue();
        assertThat(captured.thumbnailUrl().get()).isNull();
    }

    @Test
    void updatePost_explicitNullCategoryId_passesPresentJsonNullableWithNullValue() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);


        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId": null}
                                """))
                .andExpect(status().isOk());

        var captor = forClass(UpdatePostRequest.class);
        verify(postService).updatePost(eq(userId), eq(postId), captor.capture());
        UpdatePostRequest captured = captor.getValue();
        assertThat(captured.categoryId().isPresent()).isTrue();
        assertThat(captured.categoryId().get()).isNull();
    }

    @Test
    void updatePost_tagIdsEmptyArray_clearsAllTags() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class)))
                .willReturn(buildPostDetailResponse(postId));

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds": []}
                                """))
                .andExpect(status().isOk());

        var captor = forClass(UpdatePostRequest.class);
        verify(postService).updatePost(eq(userId), eq(postId), captor.capture());
        assertThat(captor.getValue().tagIds()).isEmpty();
    }

    @Test
    void updatePost_blankUrl_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UpdatePostRequest request = new UpdatePostRequest("", null, null, null, null, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("url size must be between 1 and 2048"));
    }

    @Test
    void updatePost_blankTitle_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UpdatePostRequest request = new UpdatePostRequest(null, "", null, null, null, null, null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("title size must be between 1 and 255"));
    }

    @Test
    void updatePost_tagIdsExceeds20_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        List<Long> tooManyTagIds = LongStream.rangeClosed(1, 21)
                                              .boxed()
                                              .toList();
        UpdatePostRequest request = new UpdatePostRequest(
                null, null, null, null, null, null, tooManyTagIds, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value(containsString("tagIds")));
    }

    @Test
    void updatePost_invalidPlatformEnumValue_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        String rawJson = """
                {"platform": "NOT_A_REAL_PLATFORM"}
                """;

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void updatePost_postNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class)))
                .willThrow(new ResourceNotFoundException("Saved post not found"));

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Saved post not found"));
    }

    @Test
    void updatePost_categoryNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class)))
                .willThrow(new ResourceNotFoundException("Category not found"));

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void updatePost_tagsNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class)))
                .willThrow(new ResourceNotFoundException("One or more tags not found"));

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("One or more tags not found"));
    }

    @Test
    void updatePost_thumbnailUrlNotHttps_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(postService.updatePost(eq(userId), eq(postId), any(UpdatePostRequest.class)))
                .willThrow(new BusinessRuleViolationException("thumbnailUrl must be a valid HTTPS URL"));

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"thumbnailUrl": "http://example.com/thumb.jpg"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("thumbnailUrl must be a valid HTTPS URL"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void updatePost_malformedJsonBody_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void updatePost_invalidIdPathVariable_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/posts/{id}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"))
                .andExpect(jsonPath("$.details[0]").value("id must be a valid UUID"));
    }

    @Test
    void updatePost_noToken_returns401() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void updatePost_invalidToken_returns401() throws Exception {
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // deletePost()
    // -------------------------------------------------------------------------

    @Test
    void deletePost_validRequest_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isNoContent());

        verify(postService).deletePost(userId, postId);
    }

    @Test
    void deletePost_postNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        willThrow(new ResourceNotFoundException("Saved post not found"))
                .given(postService).deletePost(userId, postId);

        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Saved post not found"));
    }

    @Test
    void deletePost_invalidIdPathVariable_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(delete("/api/v1/posts/{id}", "not-a-uuid")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"))
                .andExpect(jsonPath("$.details[0]").value("id must be a valid UUID"));
    }

    @Test
    void deletePost_noToken_returns401() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/posts/{id}", postId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void deletePost_invalidToken_returns401() throws Exception {
        UUID postId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private PostDetailResponse buildPostDetailResponse(UUID postId) {
        return new PostDetailResponse(
                postId, "https://example.com/post", "title", null, null, "INSTAGRAM", false, null, List.of(),
                LocalDateTime.now(), LocalDateTime.now());
    }
}