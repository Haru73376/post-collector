package com.github.haru73376.post_collector.tag;

import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final SecurityContextUtils securityContextUtils;

    @GetMapping
    public ResponseEntity<List<TagResponse>> getTags() {
        return ResponseEntity.ok(tagService.getAllTags(securityContextUtils.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<TagDetailResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(tagService.createTag(securityContextUtils.getCurrentUserId(), request));
    }
}
