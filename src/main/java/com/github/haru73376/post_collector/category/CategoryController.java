package com.github.haru73376.post_collector.category;

import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final SecurityContextUtils securityContextUtils;

    @GetMapping
    public ResponseEntity<List<CategoryTreeResponse>> getCategories() {
        return ResponseEntity.ok(categoryService.getCategoryTree(securityContextUtils.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(securityContextUtils.getCurrentUserId(), request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request)
    {
        return ResponseEntity.ok(categoryService.updateCategory(securityContextUtils.getCurrentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(securityContextUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
