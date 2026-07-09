package com.github.haru73376.post_collector.tag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.haru73376.post_collector.common.config.SecurityConfig;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(TagController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "JWT_SECRET=dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLW9ubHk=",
        "DB_USERNAME=test",
        "DB_PASSWORD=test"

})
public class TagControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TagService tagService;

    @MockitoBean
    SecurityContextUtils securityContextUtils;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    // -------------------------------------------------------------------------
    // getAllTags()
    // -------------------------------------------------------------------------

    @Test
    void getTags_authenticated_noTags_returns200WithEmptyArray() throws Exception {
        UUID userId = UUID.randomUUID();

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(tagService.getAllTags(userId)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray())
               .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getTags_authenticated_returns200WithTagList() throws Exception {
        UUID userId = UUID.randomUUID();
        TagResponse response1 = new TagResponse(
                1L, "tagName1"
        );
        TagResponse response2 = new TagResponse(
                2L, "tagName2"
        );

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(tagService.getAllTags(userId)).willReturn(List.of(response1, response2));

        mockMvc.perform(get("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(1L))
               .andExpect(jsonPath("$[0].name").value("tagName1"))
               .andExpect(jsonPath("$[1].id").value(2L))
               .andExpect(jsonPath("$[1].name").value("tagName2"));
    }

    @Test
    void getTags_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tags"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void getTags_invalidToken_returns401() throws Exception {
        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
               .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // createTag()
    // -------------------------------------------------------------------------

    @Test
    void createTag_validRequest_returns201() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateTagRequest request = new CreateTagRequest("tagName");
        TagDetailResponse response = new TagDetailResponse(
                1L, "tagName", LocalDateTime.now()
        );

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(tagService.createTag(eq(userId), any(CreateTagRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(1L))
               .andExpect(jsonPath("$.name").value("tagName"))
               .andExpect(jsonPath("$.createdAt").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
    }

    @Test
    void createTag_blankName_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateTagRequest request = new CreateTagRequest("");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("Validation failed"))
               .andExpect(jsonPath("$.details[0]").value("name must not be blank"));
    }

    @Test
    void createTag_nameTooLong_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateTagRequest request = new CreateTagRequest("a".repeat(51));

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(post("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("Validation failed"))
               .andExpect(jsonPath("$.details[0]").value("name size must be between 0 and 50"));
    }

    @Test
    void createTag_duplicateName_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        CreateTagRequest request = new CreateTagRequest("sameTagName");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(tagService.createTag(eq(userId), any(CreateTagRequest.class))).willThrow(
                new ConflictException("Tag with the same name already exists"));

        mockMvc.perform(post("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.message").value("Tag with the same name already exists"));
    }

    @Test
    void createTag_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/tags"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void createTag_invalidToken_returns401() throws Exception {
        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/tags")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
               .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // updateTag()
    // -------------------------------------------------------------------------

    @Test
    void updateTag_validRequest_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;
        UpdateTagRequest request = new UpdateTagRequest("newTagName");
        TagResponse response = new TagResponse(tagId, "newTagName");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(tagService.updateTag(eq(userId), eq(tagId), any(UpdateTagRequest.class))).willReturn(response);

        mockMvc.perform(patch("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(tagId))
               .andExpect(jsonPath("$.name").value("newTagName"));
    }

    @Test
    void updateTag_blankName_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;
        UpdateTagRequest request = new UpdateTagRequest("");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("Validation failed"))
               .andExpect(jsonPath("$.details[0]").value("name must not be blank"));
    }

    @Test
    void updateTag_nameTooLong_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;
        UpdateTagRequest request = new UpdateTagRequest("a".repeat(51));

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(patch("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("Validation failed"))
               .andExpect(jsonPath("$.details[0]").value("name size must be between 0 and 50"));
    }

    @Test
    void updateTag_tagNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;
        UpdateTagRequest request = new UpdateTagRequest("tagName");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(tagService.updateTag(eq(userId), eq(tagId), any(UpdateTagRequest.class))).willThrow(
                new ResourceNotFoundException("Tag not found"));

        mockMvc.perform(patch("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message").value("Tag not found"));
    }

    @Test
    void updateTag_duplicateName_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;
        UpdateTagRequest request = new UpdateTagRequest("tagName");

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        given(tagService.updateTag(eq(userId), eq(tagId), any(UpdateTagRequest.class))).willThrow(
                new ConflictException("Tag with the same name already exists"));

        mockMvc.perform(patch("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict())
               .andExpect(jsonPath("$.message").value("Tag with the same name already exists"));
    }

    @Test
    void updateTag_noToken_returns401() throws Exception {
        Long tagId = 1L;

        mockMvc.perform(patch("/api/v1/tags/{id}", tagId))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void updateTag_invalidToken_returns401() throws Exception {
        Long tagId = 1L;

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
               .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // deleteTag()
    // -------------------------------------------------------------------------

    @Test
    void deleteTag_validRequest_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);

        mockMvc.perform(delete("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
               .andExpect(status().isNoContent());

        verify(tagService).deleteTag(userId, tagId);
    }

    @Test
    void deleteTag_tagNotFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;

        given(jwtTokenProvider.extractUserId("token")).willReturn(Optional.of(userId));
        given(securityContextUtils.getCurrentUserId()).willReturn(userId);
        willThrow(new ResourceNotFoundException("Tag not found"))
                .given(tagService)
                .deleteTag(userId, tagId);

        mockMvc.perform(delete("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message").value("Tag not found"));
    }

    @Test
    void deleteTag_noToken_returns401() throws Exception {
        Long tagId = 1L;

        mockMvc.perform(delete("/api/v1/tags/{id}", tagId))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void deleteTag_invalidToken_returns401() throws Exception {
        Long tagId = 1L;

        given(jwtTokenProvider.extractUserId("invalid-token")).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/tags/{id}", tagId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
               .andExpect(status().isUnauthorized());
    }
}
