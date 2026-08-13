package com.github.haru73376.post_collector.tag;

import com.github.haru73376.post_collector.common.dto.ErrorResponse;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags", description = "Flat, cross-category tags for saved posts (unlike categories, a tag isn't scoped to one category).")
public class TagController {

    private final TagService tagService;
    private final SecurityContextUtils securityContextUtils;

    @GetMapping
    @Operation(summary = "Get all tags", description = "Returns all of the current user's tags, ordered by name.")
    public ResponseEntity<List<TagResponse>> getTags() {
        return ResponseEntity.ok(tagService.getAllTags(securityContextUtils.getCurrentUserId()));
    }

    @PostMapping
    @Operation(summary = "Create a tag", description = "Rejects duplicate tag names for the same user.")
    @ApiResponse(responseCode = "409", description = "A tag with this name already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TagDetailResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(tagService.createTag(securityContextUtils.getCurrentUserId(), request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a tag",
            description = "Returns 404 if the tag doesn't exist or belongs to another user (the two cases are indistinguishable by design).")
    @ApiResponse(responseCode = "404", description = "Tag not found or not owned by the current user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "A tag with this name already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TagResponse> updateTag(
            @PathVariable Long id, @Valid @RequestBody UpdateTagRequest request
    ) {
        return ResponseEntity.ok(tagService.updateTag(securityContextUtils.getCurrentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tag", description = "Also removes this tag from any posts it was attached to.")
    @ApiResponse(responseCode = "404", description = "Tag not found or not owned by the current user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(securityContextUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
