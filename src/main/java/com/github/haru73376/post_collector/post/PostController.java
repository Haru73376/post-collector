package com.github.haru73376.post_collector.post;

import com.github.haru73376.post_collector.common.dto.PageResponse;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
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
public class PostController {

    private final PostService postService;
    private final SecurityContextUtils securityContextUtils;

    @GetMapping
    public ResponseEntity<PageResponse<PostSummaryResponse>> getPosts(
            @ModelAttribute PostSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(postService.getPosts(securityContextUtils.getCurrentUserId(), criteria, pageable));
    }

    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(postService.createPost(securityContextUtils.getCurrentUserId(), request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PostDetailResponse> updatePost(
            @PathVariable UUID id, @Valid @RequestBody UpdatePostRequest request
    ) {
        return ResponseEntity.ok(postService.updatePost(securityContextUtils.getCurrentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        postService.deletePost(securityContextUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
