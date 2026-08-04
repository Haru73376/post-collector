package com.github.haru73376.post_collector.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.haru73376.post_collector.auth.LoginRequest;
import com.github.haru73376.post_collector.auth.RegisterRequest;
import com.github.haru73376.post_collector.auth.TokenResponse;
import com.github.haru73376.post_collector.category.CategoryResponse;
import com.github.haru73376.post_collector.category.CreateCategoryRequest;
import com.github.haru73376.post_collector.common.ratelimit.RateLimiterRegistry;
import com.github.haru73376.post_collector.post.CreatePostRequest;
import com.github.haru73376.post_collector.post.Platform;
import com.github.haru73376.post_collector.post.PostDetailResponse;
import com.github.haru73376.post_collector.post.SavedPost;
import com.github.haru73376.post_collector.post.SavedPostRepository;
import com.github.haru73376.post_collector.tag.CreateTagRequest;
import com.github.haru73376.post_collector.tag.TagResponse;
import com.github.haru73376.post_collector.user.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "JWT_SECRET=dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItaW50ZWdyYXRpb24tdGVzdHMtb25seSEh"
})
class ApplicationIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SavedPostRepository savedPostRepository;

    @MockitoBean
    RateLimiterRegistry rateLimiterRegistry;

    @BeforeEach
    void allowAllRequestsByDefault() {
        given(rateLimiterRegistry.tryConsume(any(), any())).willReturn(true);
    }

    private record AuthedUser(UUID userId, String accessToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private AuthedUser registerAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String username = "user-" + suffix;
        String email = suffix + "@example.com";
        String password = "password123";

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, email, password))))
                .andExpect(status().isCreated())
                .andReturn();
        UserResponse userResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), UserResponse.class);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        TokenResponse tokenResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), TokenResponse.class);

        return new AuthedUser(userResponse.id(), tokenResponse.accessToken());
    }

    private PostDetailResponse createPost(AuthedUser user, String title, UUID categoryId, List<Long> tagIds) throws Exception {
        CreatePostRequest request = new CreatePostRequest(
                "https://example.com/" + UUID.randomUUID(), title, null, null,
                Platform.OTHER, categoryId, tagIds, null);

        MvcResult result = mockMvc.perform(post("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), PostDetailResponse.class);
    }

    // -------------------------------------------------------------------------
    // Scenario 1: main happy path
    // -------------------------------------------------------------------------

    @Test
    void registerLoginCreateCategoryTagPost_thenListReturnsFullyLinkedPost() throws Exception {
        AuthedUser user = registerAndLogin();

        MvcResult categoryResult = mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest("tech", null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        CategoryResponse category = objectMapper.readValue(
                categoryResult.getResponse().getContentAsString(), CategoryResponse.class);

        MvcResult tagResult = mockMvc.perform(post("/api/v1/tags")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTagRequest("spring"))))
                .andExpect(status().isCreated())
                .andReturn();
        TagResponse tag = objectMapper.readValue(tagResult.getResponse().getContentAsString(), TagResponse.class);

        PostDetailResponse post = createPost(user, "post-title", category.id(), List.of(tag.id()));

        mockMvc.perform(get("/api/v1/posts")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(post.id().toString()))
                .andExpect(jsonPath("$.content[0].category.name").value("tech"))
                .andExpect(jsonPath("$.content[0].tags[0].name").value("spring"));
    }

    // -------------------------------------------------------------------------
    // Scenario 2: soft-deleting a post (deletePost's implicit @Transactional dependency)
    // -------------------------------------------------------------------------

    @Test
    void deletePost_persistsSoftDelete_viaImplicitTransactionalCommit() throws Exception {
        AuthedUser user = registerAndLogin();
        PostDetailResponse post = createPost(user, "post-to-delete", null, null);

        mockMvc.perform(delete("/api/v1/posts/{id}", post.id())
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isNoContent());

        assertThat(savedPostRepository.findByIdAndUserId(post.id(), user.userId())).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Scenario 3: authentication boundary (IDOR rejection for another user's resource)
    // -------------------------------------------------------------------------

    @Test
    void categoryOwnedByAnotherUser_isNotAccessibleViaRealJwt() throws Exception {
        AuthedUser userC = registerAndLogin();
        MvcResult categoryResult = mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, userC.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest("owned-by-c", null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        CategoryResponse category = objectMapper.readValue(
                categoryResult.getResponse().getContentAsString(), CategoryResponse.class);

        AuthedUser userD = registerAndLogin();

        String updateRequestJson = """
                {
                  "name": "hacked-by-d"
                }
                """;

        mockMvc.perform(patch("/api/v1/categories/{id}", category.id())
                        .header(HttpHeaders.AUTHORIZATION, userD.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Scenario 4: rollback verification (validation exception mid-updatePost)
    // -------------------------------------------------------------------------

    @Test
    void updatePost_rollsBackEarlierFieldChanges_whenLaterValidationFails() throws Exception {
        AuthedUser user = registerAndLogin();
        PostDetailResponse post = createPost(user, "original-title", null, null);

        String tooLongMemo = "a".repeat(65536);
        String updateRequestJson = """
                {
                  "title": "changed-title",
                  "memo": "%s"
                }
                """.formatted(tooLongMemo);

        mockMvc.perform(patch("/api/v1/posts/{id}", post.id())
                        .header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson))
                .andExpect(status().isBadRequest());

        SavedPost persisted = savedPostRepository.findByIdAndUserId(post.id(), user.userId()).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("original-title");
    }
}