package com.github.haru73376.post_collector.category;

import com.github.haru73376.post_collector.common.exception.BusinessRuleViolationException;
import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import com.github.haru73376.post_collector.savedPost.SavedPostRepository;
import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private static final int MAX_DEPTH = 3;
    private static final String MSG_CATEGORY_NOT_FOUND = "Category not found";
    private static final String MSG_PARENT_NOT_FOUND = "Parent category does not exist";
    private static final String MSG_DUPLICATE_NAME = "Category with the same name already exists under this parent";

    private final CategoryRepository categoryRepository;
    private final SavedPostRepository savedPostRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getCategoryTree(UUID userId) {
        List<Category> allCategories = categoryRepository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        List<UUID> categoryIds = allCategories.stream()
                                              .map(Category::getId)
                                              .toList();
        // Fetch post counts per category in a single query to avoid N+1
        Map<UUID, Long> postCountMap = categoryIds.isEmpty()
                                       ? Collections.emptyMap()
                                       : savedPostRepository.countByCategoryIds(categoryIds)
                                                            .stream()
                                                            .collect(Collectors.toMap(
                                                                    row -> (UUID) row[0],
                                                                    row -> (Long) row[1]
                                                            ));

        // Group categories by parentId to build the tree without additional queries
        Map<UUID, List<Category>> childrenMap = allCategories.stream()
                                                             .filter(c -> c.getParent() != null)
                                                             .collect(groupingBy(c -> c.getParent().getId()
                                                             ));

        List<Category> roots = allCategories.stream()
                                            .filter(c -> c.getParent() == null)
                                            .toList();

        return roots.stream()
                    .map(root -> toTreeResponse(root, childrenMap, postCountMap))
                    .toList();
    }

    @Transactional
    public CategoryResponse createCategory(UUID userId, CreateCategoryRequest request) {
        UUID parentId = request.parentId();
        if (parentId != null) {
            Category parent = findOwnedCategory(parentId, userId, MSG_PARENT_NOT_FOUND);
            validateDepth(parent);
        }

        if (categoryRepository.existsByUserIdAndParentIdAndName(userId, parentId, request.name())) {
            throw new ConflictException(MSG_DUPLICATE_NAME);
        }

        Category category = new Category();
        User userRef = userRepository.getReferenceById(userId);
        category.setUser(userRef);
        if (parentId != null) {
            Category parentCategoryRef = categoryRepository.getReferenceById(parentId);
            category.setParent(parentCategoryRef);
        }
        category.setName(request.name());
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);

        Category savedCategory = categoryRepository.save(category);

        return toResponse(savedCategory);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID userId, UUID categoryId, UpdateCategoryRequest request) {
        Category category = findOwnedCategory(categoryId, userId, MSG_CATEGORY_NOT_FOUND);

        String newName = request.name();
        if (newName != null) {
            // If both name and parentId are being updated, check name uniqueness against the new parent.
            UUID effectiveParentId;
            if (request.parentId() != null) {
                effectiveParentId = request.parentId();
            } else {
                effectiveParentId = category.getParent() != null
                                    ? category.getParent().getId()
                                    : null;
            }

            if (categoryRepository.existsByUserIdAndParentIdAndName(userId, effectiveParentId, newName)) {
                throw new ConflictException(MSG_DUPLICATE_NAME);
            }
            category.setName(newName);
        }

        UUID newParentId = request.parentId();
        if (newParentId != null) {
            Category parent = findOwnedCategory(newParentId, userId, MSG_PARENT_NOT_FOUND);
            validateNoCycle(category, newParentId);

            int newParentDepth = getDepth(parent);
            int subTreeDepth = getSubtreeDepth(categoryId);
            if (newParentDepth + subTreeDepth > MAX_DEPTH) {
                throw new BusinessRuleViolationException("Category depth cannot exceed " + MAX_DEPTH + " levels");
            }
            category.setParent(parent);
        }

        Integer newSortOrder = request.sortOrder();
        if (newSortOrder != null) {
            category.setSortOrder(newSortOrder);
        }

        return toResponse(category);
    }

    @Transactional
    public void deleteCategory(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                                              .orElseThrow(() -> new ResourceNotFoundException(MSG_CATEGORY_NOT_FOUND));
        categoryRepository.delete(category);
    }

    private Category findOwnedCategory(UUID id, UUID userId, String message) {
        return categoryRepository.findByIdAndUserId(id, userId)
                                 .orElseThrow(() -> new ResourceNotFoundException(message));
    }

    private void validateNoCycle(Category target, UUID newParentId) {
        UUID targetId = target.getId();
        if (targetId.equals(newParentId)) {
            throw new BusinessRuleViolationException("A category cannot be its own parent");
        }

        Set<UUID> descendants = new HashSet<>();
        collectDescendants(targetId, descendants);
        if (descendants.contains(newParentId)) {
            throw new BusinessRuleViolationException("A category cannot be moved to its own descendant");
        }
    }

    private void collectDescendants(UUID parentId, Set<UUID> descendants) {
        List<Category> children = categoryRepository.findByParentId(parentId);
        for (Category child : children) {
            UUID childId = child.getId();
            descendants.add(childId);
            collectDescendants(childId, descendants);
        }
    }

    private void validateDepth(Category parent) {
        if (getDepth(parent) >= MAX_DEPTH) {
            throw new BusinessRuleViolationException("Category depth cannot exceed " + MAX_DEPTH + " levels");
        }
    }

    private int getSubtreeDepth(UUID categoryId) {
        List<Category> children = categoryRepository.findByParentId(categoryId);

        if (children.isEmpty()) return 1;
        return 1 + children.stream()
                           .mapToInt(c -> getSubtreeDepth(c.getId()))
                           .max()
                           .orElse(0);
    }

    private int getDepth(Category category) {
        int depth = 1;
        Category current = category;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private CategoryTreeResponse toTreeResponse(Category category, Map<UUID, List<Category>> childrenMap,
                                                Map<UUID, Long> postCountMap) {

        // Recursively build child nodes from the pre-grouped childrenMap
        List<CategoryTreeResponse> children = childrenMap.getOrDefault(category.getId(), List.of())
                                                         .stream()
                                                         .map(child -> toTreeResponse(child, childrenMap, postCountMap))
                                                         .toList();

        return new CategoryTreeResponse(
                category.getId(),
                category.getName(),
                category.getSortOrder(),
                postCountMap.getOrDefault(category.getId(), 0L),
                children
        );
    }

    private CategoryResponse toResponse(Category category) {
        UUID parentId = category.getParent() != null
                        ? category.getParent().getId()
                        : null;

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                parentId,
                category.getSortOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
