package com.github.haru73376.post_collector.category;

import com.github.haru73376.post_collector.common.dto.ErrorResponse;
import com.github.haru73376.post_collector.common.security.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Categories", description = "Hierarchical categories (up to 3 levels deep) for organizing saved posts.")
public class CategoryController {

    private final CategoryService categoryService;
    private final SecurityContextUtils securityContextUtils;

    @GetMapping
    @Operation(summary = "Get the category tree",
            description = "Returns all of the current user's categories as a nested tree, including a saved-post count per category.")
    public ResponseEntity<List<CategoryTreeResponse>> getCategories() {
        return ResponseEntity.ok(categoryService.getCategoryTree(securityContextUtils.getCurrentUserId()));
    }

    @PostMapping
    @Operation(summary = "Create a category",
            description = "Optionally nested under a parent category (max depth 3). Rejects duplicate names under the same parent.")
    @ApiResponse(responseCode = "409", description = "A category with this name already exists under the same parent",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":409,\"error\":\"Conflict\",\"message\":\"Category with the same name already exists under this parent\"}")))
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(securityContextUtils.getCurrentUserId(), request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a category",
            description = "Only the fields provided are changed. Returns 404 if the category doesn't exist or belongs to another user "
                    + "(the two cases are indistinguishable by design).")
    @ApiResponse(responseCode = "404", description = "Category (or the new parentId, if provided) not found or not owned by the current user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Category not found\"}")))
    @ApiResponse(responseCode = "409", description = "A category with this name already exists under the same parent",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":409,\"error\":\"Conflict\",\"message\":\"Category with the same name already exists under this parent\"}")))
    @ApiResponse(responseCode = "400", description = "New parentId would exceed the max depth (3) or create a cycle",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":400,\"error\":\"Bad Request\",\"message\":\"Category depth cannot exceed 3 levels\"}")))
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request)
    {
        return ResponseEntity.ok(categoryService.updateCategory(securityContextUtils.getCurrentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category",
            description = "Posts that were in this category become uncategorized rather than being deleted.")
    @ApiResponse(responseCode = "404", description = "Category not found or not owned by the current user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = "{\"status\":404,\"error\":\"Not Found\",\"message\":\"Category not found\"}")))
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(securityContextUtils.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
