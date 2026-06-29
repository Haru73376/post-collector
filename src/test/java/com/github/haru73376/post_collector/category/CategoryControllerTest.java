package com.github.haru73376.post_collector.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.haru73376.post_collector.common.config.SecurityConfig;
import com.github.haru73376.post_collector.common.exception.BusinessRuleViolationException;
import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import com.github.haru73376.post_collector.common.security.JwtTokenProvider;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "JWT_SECRET=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHk=",
        "DB_USERNAME=test",
        "DB_PASSWORD=test"
})
class CategoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    CategoryService categoryService;

    @MockitoBean
    SecurityContextUtils securityContextUtils;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    // -------------------------------------------------------------------------
    // getCategories()
    // -------------------------------------------------------------------------

    @Test
    void getCategories_authenticated_returns200WithTree() throws Exception {
        UUID userId = UUID.randomUUID();
        CategoryTreeResponse child = new CategoryTreeResponse(
                UUID.randomUUID(), "crafts", 0, 5, List.of());
        CategoryTreeResponse root = new CategoryTreeResponse(
                UUID.randomUUID(), "hobbies", 0, 0, List.of(child));

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.getCategoryTree(userId)).willReturn(List.of(root));

        mockMvc.perform(get("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("hobbies"))
                .andExpect(jsonPath("$[0].sortOrder").value(0))
                .andExpect(jsonPath("$[0].postCount").value(0))
                .andExpect(jsonPath("$[0].children[0].name").value("crafts"))
                .andExpect(jsonPath("$[0].children[0].postCount").value(5));
    }

    @Test
    void getCategories_authenticated_noCategories_returns200WithEmptyArray() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.getCategoryTree(userId)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getCategories_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void getCategories_invalidToken_returns401() throws Exception {
        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // createCategory()
    // -------------------------------------------------------------------------

    @Test
    void createCategory_withoutParentId_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("work", null, 0);
        CategoryResponse response = new CategoryResponse(
                categoryId, "work", null, 0,
                LocalDateTime.now(), LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.createCategory(eq(userId), any(CreateCategoryRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.name").value("work"))
                .andExpect(jsonPath("$.parentId").value(nullValue()))
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.updatedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void createCategory_withParentId_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("design", parentId, 1);
        CategoryResponse response = new CategoryResponse(
                categoryId, "design", parentId, 1,
                LocalDateTime.now(), LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.createCategory(eq(userId), any(CreateCategoryRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.name").value("design"))
                .andExpect(jsonPath("$.parentId").value(parentId.toString()))
                .andExpect(jsonPath("$.sortOrder").value(1))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.updatedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void createCategory_blankName_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("", null, 0);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("name must not be blank"));
    }

    @Test
    void createCategory_nameTooLong_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("a".repeat(101), null, 0);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("name size must be between 0 and 100"));
    }

    @Test
    void createCategory_negativeSortOrder_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("work", null, -1);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("sortOrder must be greater than or equal to 0"));
    }

    @Test
    void createCategory_duplicateName_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("work", null, 0);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.createCategory(eq(userId), any(CreateCategoryRequest.class)))
                .willThrow(new ConflictException("Category with the same name already exists under this parent"));

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category with the same name already exists under this parent"));
    }

    @Test
    void createCategory_parentNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("design", UUID.randomUUID(), 0);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.createCategory(eq(userId), any(CreateCategoryRequest.class)))
                .willThrow(new ResourceNotFoundException("Parent category does not exist"));

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Parent category does not exist"));
    }

    @Test
    void createCategory_depthExceeded_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("detail", UUID.randomUUID(), 0);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.createCategory(eq(userId), any(CreateCategoryRequest.class)))
                .willThrow(new BusinessRuleViolationException("Category depth cannot exceed 3 levels"));

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category depth cannot exceed 3 levels"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void createCategory_noToken_returns401() throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest("work", null, 0);

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void createCategory_invalidToken_returns401() throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest("work", null, 0);

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // updateCategory()
    // -------------------------------------------------------------------------

    @Test
    void updateCategory_validRequest_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("updated", null, 2);
        CategoryResponse response = new CategoryResponse(
                categoryId, "updated", null, 2,
                LocalDateTime.now(), LocalDateTime.now());

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.updateCategory(eq(userId), eq(categoryId), any(UpdateCategoryRequest.class)))
                .willReturn(response);

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.name").value("updated"))
                .andExpect(jsonPath("$.sortOrder").value(2))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.updatedAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void updateCategory_emptyName_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("", null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("name size must be between 1 and 100"));
    }

    @Test
    void updateCategory_nameTooLong_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("a".repeat(101), null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("name size must be between 1 and 100"));
    }

    @Test
    void updateCategory_negativeSortOrder_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, null, -1);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[0]").value("sortOrder must be greater than or equal to 0"));
    }

    @Test
    void updateCategory_categoryNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("updated", null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.updateCategory(eq(userId), eq(categoryId), any(UpdateCategoryRequest.class)))
                .willThrow(new ResourceNotFoundException("Category not found"));

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void updateCategory_duplicateName_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("existing", null, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.updateCategory(eq(userId), eq(categoryId), any(UpdateCategoryRequest.class)))
                .willThrow(new ConflictException("Category with the same name already exists under this parent"));

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category with the same name already exists under this parent"));
    }

    @Test
    void updateCategory_parentNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, UUID.randomUUID(), null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.updateCategory(eq(userId), eq(categoryId), any(UpdateCategoryRequest.class)))
                .willThrow(new ResourceNotFoundException("Parent category does not exist"));

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Parent category does not exist"));
    }

    @Test
    void updateCategory_depthExceeded_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, UUID.randomUUID(), null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.updateCategory(eq(userId), eq(categoryId), any(UpdateCategoryRequest.class)))
                .willThrow(new BusinessRuleViolationException("Category depth cannot exceed 3 levels"));

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category depth cannot exceed 3 levels"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void updateCategory_circularReference_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, categoryId, null);

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(categoryService.updateCategory(eq(userId), eq(categoryId), any(UpdateCategoryRequest.class)))
                .willThrow(new BusinessRuleViolationException("A category cannot be its own parent"));

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A category cannot be its own parent"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void updateCategory_noToken_returns401() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("updated", null, null);

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void updateCategory_invalidToken_returns401() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("updated", null, null);

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // deleteCategory()
    // -------------------------------------------------------------------------

    @Test
    void deleteCategory_validRequest_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isNoContent());

        verify(categoryService).deleteCategory(userId, categoryId);
    }

    @Test
    void deleteCategory_categoryNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        willThrow(new ResourceNotFoundException("Category not found"))
                .given(categoryService).deleteCategory(userId, categoryId);

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }

    @Test
    void deleteCategory_noToken_returns401() throws Exception {
        UUID categoryId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void deleteCategory_invalidToken_returns401() throws Exception {
        UUID categoryId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}