package com.github.haru73376.post_collector.post;

import com.github.haru73376.post_collector.common.dto.ErrorResponse;
import com.github.haru73376.post_collector.common.dto.PageResponse;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Saved social media posts. Supports filtering, pagination, and soft delete.")
public class PostController {

    private final PostService postService;
    private final SecurityContextUtils securityContextUtils;

    @GetMapping
    @Operation(summary = "Search saved posts",
            description = "Filter by category/platform/tag/favorite/keyword (all optional, combined with AND). "
                    + "sort only accepts createdAt, updatedAt, or title; any other value returns 400. "
                    + "size is silently clamped to 100.")
    public ResponseEntity<PageResponse<PostSummaryResponse>> getPosts(
            @ModelAttribute PostSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(postService.getPosts(securityContextUtils.getCurrentUserId(), criteria, pageable));
    }

    @PostMapping
    @Operation(summary = "Save a new post",
            description = "categoryId and tagIds are optional (a post can be uncategorized/untagged). thumbnailUrl, if provided, must be an HTTPS URL.")
    @ApiResponse(responseCode = "404", description = "categoryId, or one of the tagIds, doesn't exist or isn't owned by the current user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "thumbnailUrl was provided but isn't a valid HTTPS URL",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PostDetailResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(postService.createPost(securityContextUtils.getCurrentUserId(), request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a saved post",
            description = "Only the fields provided are changed. memo/thumbnailUrl/categoryId can each be explicitly cleared by sending "
                    + "null; omitting them leaves the current value untouched. tagIds, if provided, fully replaces the post's tags.")
    @ApiResponse(responseCode = "404", description = "Post not found/not owned, or categoryId/one of the tagIds doesn't exist or isn't owned by the current user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "400", description = "memo/thumbnailUrl exceeds the length limit, or thumbnailUrl isn't a valid HTTPS URL",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PostDetailResponse> updatePost(
            @PathVariable UUID id, @Valid @RequestBody UpdatePostRequest request
    ) {
        return ResponseEntity.ok(postService.updatePost(securityContextUtils.getCurrentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved post",
            description = "Soft delete — the post is excluded from all queries but not physically removed from the database.")
    @ApiResponse(responseCode = "404", description = "Post not found or not owned by the current user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        postService.deletePost(securityContextUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
