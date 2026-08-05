package com.github.haru73376.post_collector.category;

import com.github.haru73376.post_collector.common.exception.BusinessRuleViolationException;
import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import com.github.haru73376.post_collector.post.SavedPostRepository;
import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SavedPostRepository savedPostRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CategoryService categoryService;

    // -------------------------------------------------------------------------
    // getCategoryTree()
    // -------------------------------------------------------------------------

    @Test
    void getCategoryTree_returnsEmptyList_whenUserHasNoCategories() {
        UUID userId = UUID.randomUUID();
        given(categoryRepository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId))
                .willReturn(List.of());

        List<CategoryTreeResponse> result = categoryService.getCategoryTree(userId);

        assertThat(result).isEmpty();
        verifyNoInteractions(savedPostRepository);
    }

    @Test
    void getCategoryTree_buildsTreeStructure_whenChildCategoriesExist() {
        UUID userId = UUID.randomUUID();

        Category root = new Category();
        ReflectionTestUtils.setField(root, "id", UUID.randomUUID());
        root.setName("root");

        Category child = new Category();
        UUID childId = UUID.randomUUID();
        ReflectionTestUtils.setField(child, "id", childId);
        child.setParent(root);
        child.setName("child");

        given(categoryRepository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId))
                .willReturn(List.of(root, child));
        given(savedPostRepository.countByCategoryIds(any()))
                .willReturn(List.of());

        List<CategoryTreeResponse> result = categoryService.getCategoryTree(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()
                         .children()).hasSize(1);
        assertThat(result.getFirst()
                         .children()
                         .getFirst()
                         .id()).isEqualTo(childId);
    }

    @Test
    void getCategoryTree_returnsRootCategoriesOnly_whenNoChildrenExist() {
        UUID userId = UUID.randomUUID();
        Category root = new Category();
        ReflectionTestUtils.setField(root, "id", UUID.randomUUID());
        root.setName("root");

        given(categoryRepository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId))
                .willReturn(List.of(root));
        given(savedPostRepository.countByCategoryIds(any()))
                .willReturn(List.of());

        List<CategoryTreeResponse> result = categoryService.getCategoryTree(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()
                         .children()).isEmpty();
    }

    @Test
    void getCategoryTree_mapsPostCountPerCategory() {
        UUID userId = UUID.randomUUID();

        Category root = new Category();
        UUID rootId = UUID.randomUUID();
        ReflectionTestUtils.setField(root, "id", rootId);
        root.setName("root");

        Category child = new Category();
        UUID childId = UUID.randomUUID();
        ReflectionTestUtils.setField(child, "id", childId);
        child.setName("child");
        child.setParent(root);

        given(categoryRepository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(userId))
                .willReturn(List.of(root, child));
        given(savedPostRepository.countByCategoryIds(any()))
                .willReturn(List.of(
                        new Object[]{rootId, 3L},
                        new Object[]{childId, 5L}
                ));

        List<CategoryTreeResponse> result = categoryService.getCategoryTree(userId);

        assertThat(result.getFirst()
                         .postCount()).isEqualTo(3L);
        assertThat(result.getFirst()
                         .children()
                         .getFirst()
                         .postCount()).isEqualTo(5L);
    }

    // -------------------------------------------------------------------------
    // createCategory()
    // -------------------------------------------------------------------------

    @Test
    void createCategory_returnsResponse_withNullParentId_whenParentNotSpecified() {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("CategoryName", null, 1);

        given(categoryRepository.existsByUserIdAndParentIdAndName(any(), any(), any())).willReturn(false);

        User user = new User();
        given(userRepository.getReferenceById(any())).willReturn(user);

        Category savedCategory = new Category();
        UUID categoryId = UUID.randomUUID();
        String name = request.name();
        Integer sortOrder = request.sortOrder();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime updatedAt = LocalDateTime.now();

        ReflectionTestUtils.setField(savedCategory, "id", categoryId);
        savedCategory.setName(name);
        savedCategory.setSortOrder(sortOrder);
        ReflectionTestUtils.setField(savedCategory, "createdAt", createdAt);
        ReflectionTestUtils.setField(savedCategory, "updatedAt", updatedAt);

        given(categoryRepository.saveAndFlush(any())).willReturn(savedCategory);

        CategoryResponse result = categoryService.createCategory(userId, request);

        assertThat(result.parentId()).isNull();
        assertThat(result.id()).isEqualTo(categoryId);
        assertThat(result.name()).isEqualTo(name);
        assertThat(result.sortOrder()).isEqualTo(sortOrder);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void createCategory_returnsResponse_withParentId_whenParentSpecified() {
        UUID userId = UUID.randomUUID();
        Category parent = new Category();
        ReflectionTestUtils.setField(parent, "id", UUID.randomUUID());
        CreateCategoryRequest request = new CreateCategoryRequest("CategoryName", parent.getId(), 1);

        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(parent));
        given(categoryRepository.existsByUserIdAndParentIdAndName(any(), any(), any())).willReturn(false);

        User user = new User();
        given(userRepository.getReferenceById(any())).willReturn(user);

        given(categoryRepository.getReferenceById(any())).willReturn(parent);

        Category savedCategory = new Category();
        savedCategory.setParent(parent);
        given(categoryRepository.saveAndFlush(any())).willReturn(savedCategory);

        CategoryResponse result = categoryService.createCategory(userId, request);

        assertThat(result.parentId()).isEqualTo(parent.getId());
    }

    @Test
    void createCategory_setsSortOrderToZero_whenSortOrderIsNull() {
        UUID userId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("CategoryName", null, null);

        given(categoryRepository.existsByUserIdAndParentIdAndName(any(), any(), any())).willReturn(false);

        User user = new User();
        given(userRepository.getReferenceById(any())).willReturn(user);

        Category category = new Category();
        given(categoryRepository.saveAndFlush(any())).willReturn(category);

        categoryService.createCategory(userId, request);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()
                         .getSortOrder()).isEqualTo(0);
    }

    @Test
    void createCategory_throwsResourceNotFoundException_whenParentNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest("CategoryName", parentId, null);

        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createCategory(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Parent category does not exist");
    }

    @Test
    void createCategory_throwsBusinessRuleViolationException_whenDepthExceedsLimit() {
        UUID userId = UUID.randomUUID();
        Category greatGrandParent = new Category();
        Category grandParent = new Category();
        grandParent.setParent(greatGrandParent);
        Category parent = new Category();
        ReflectionTestUtils.setField(parent, "id", UUID.randomUUID());
        parent.setParent(grandParent);

        CreateCategoryRequest request = new CreateCategoryRequest("CategoryName", parent.getId(), null);

        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> categoryService.createCategory(userId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Category depth cannot exceed 3 levels");
    }

    @Test
    void createCategory_throwsConflictException_whenDuplicateNameUnderSameParent() {
        UUID userId = UUID.randomUUID();

        CreateCategoryRequest request = new CreateCategoryRequest("CategoryName", null, null);

        given(categoryRepository.existsByUserIdAndParentIdAndName(any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Category with the same name already exists under this parent");
    }

    // -------------------------------------------------------------------------
    // updateCategory()
    // -------------------------------------------------------------------------

    @Test
    void updateCategory_returnsResponse_whenNoFieldsToUpdate() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, null, null);

        Category category = new Category();
        String name = "CategoryName";
        Category parent = new Category();
        UUID parentId = UUID.randomUUID();
        ReflectionTestUtils.setField(parent, "id", parentId);
        int sortOrder = 1;
        category.setName(name);
        category.setParent(parent);
        category.setSortOrder(sortOrder);
        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(category));

        CategoryResponse result = categoryService.updateCategory(userId, categoryId, request);

        assertThat(result.name()).isEqualTo(name);
        assertThat(result.parentId()).isEqualTo(parentId);
        assertThat(result.sortOrder()).isEqualTo(sortOrder);
    }

    @Test
    void updateCategory_returnsUpdatedResponse_whenFieldsAreUpdated() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Category parent = new Category();
        UUID parentId = UUID.randomUUID();
        ReflectionTestUtils.setField(parent, "id", parentId);
        UpdateCategoryRequest request = new UpdateCategoryRequest("CategoryName", parentId, 1);

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));
        given(categoryRepository.existsByUserIdAndParentIdAndName(any(), any(), any())).willReturn(false);
        given(categoryRepository.findByIdAndUserId(parentId, userId)).willReturn(Optional.of(parent));
        given(categoryRepository.findByParentId(any())).willReturn(List.of());

        CategoryResponse result = categoryService.updateCategory(userId, categoryId, request);

        assertThat(result.name()).isEqualTo(request.name());
        assertThat(result.parentId()).isEqualTo(request.parentId());
        assertThat(result.sortOrder()).isEqualTo(request.sortOrder());
    }

    @Test
    void updateCategory_throwsResourceNotFoundException_whenCategoryNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("CategoryName", null, null);

        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(userId, categoryId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void updateCategory_throwsResourceNotFoundException_whenNewParentNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID newParentId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, newParentId, null);

        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(new Category()));
        given(categoryRepository.findByIdAndUserId(newParentId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(userId, categoryId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Parent category does not exist");
    }

    @Test
    void updateCategory_throwsBusinessRuleViolationException_whenParentIsSelf() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, categoryId, null);

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", categoryId);
        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.updateCategory(userId, categoryId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("A category cannot be its own parent");
    }

    @Test
    void updateCategory_throwsBusinessRuleViolationException_whenParentIsDescendant() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Category descendant = new Category();
        UUID descendantId = UUID.randomUUID();
        ReflectionTestUtils.setField(descendant, "id", descendantId);
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, descendantId, null);

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", categoryId);

        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));
        given(categoryRepository.findByIdAndUserId(descendantId, userId)).willReturn(Optional.of(descendant));
        given(categoryRepository.findByParentId(categoryId)).willReturn(List.of(descendant));
        given(categoryRepository.findByParentId(descendantId)).willReturn(List.of());

        assertThatThrownBy(() -> categoryService.updateCategory(userId, categoryId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("A category cannot be moved to its own descendant");
    }

    @Test
    void updateCategory_throwsBusinessRuleViolationException_whenDepthExceedsLimit() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category newParent = new Category();
        UUID newParentId = UUID.randomUUID();
        ReflectionTestUtils.setField(newParent, "id", newParentId);
        newParent.setParent(new Category());
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, newParentId, null);

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", categoryId);
        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));
        given(categoryRepository.findByIdAndUserId(newParentId, userId)).willReturn(Optional.of(newParent));
        Category child = new Category();
        UUID childId = UUID.randomUUID();
        ReflectionTestUtils.setField(child, "id", childId);
        given(categoryRepository.findByParentId(categoryId)).willReturn(List.of(child));
        given(categoryRepository.findByParentId(childId)).willReturn(List.of());

        assertThatThrownBy(() -> categoryService.updateCategory(userId, categoryId, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Category depth cannot exceed 3 levels");
    }

    @Test
    void updateCategory_throwsConflictException_whenDuplicateNameUnderSameParent() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("newCategoryName", null, null);

        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(new Category()));
        given(categoryRepository.existsByUserIdAndParentIdAndName(any(), any(), any())).willReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(userId, categoryId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Category with the same name already exists under this parent");
    }

    @Test
    void updateCategory_scopesDuplicateCheckToCurrentParent_whenRenamingNonRootCategoryWithoutParentId() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest("newCategoryName", null, null);

        Category parent = new Category();
        UUID parentId = UUID.randomUUID();
        ReflectionTestUtils.setField(parent, "id", parentId);

        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", categoryId);
        category.setParent(parent);

        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));
        given(categoryRepository.existsByUserIdAndParentIdAndName(userId, parentId, "newCategoryName"))
                .willReturn(false);

        CategoryResponse result = categoryService.updateCategory(userId, categoryId, request);

        assertThat(result.name()).isEqualTo("newCategoryName");
        verify(categoryRepository).existsByUserIdAndParentIdAndName(userId, parentId, "newCategoryName");
    }

    // -------------------------------------------------------------------------
    // updateCategory()
    // -------------------------------------------------------------------------

    @Test
    void deleteCategory_deletesCategory_whenOwned() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Category category = new Category();
        given(categoryRepository.findByIdAndUserId(categoryId, userId)).willReturn(Optional.of(category));

        categoryService.deleteCategory(userId, categoryId);

        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategory_throwsResourceNotFoundException_whenNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        given(categoryRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(userId, categoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }
}