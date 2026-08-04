package com.github.haru73376.post_collector.category;

import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CategoryRepositoryTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    UserRepository userRepository;

    private User saveUser() {
        User user = new User();
        user.setUsername("username-" + UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hashed");
        return userRepository.save(user);
    }

    private Category saveCategory(User user, Category parent, String name, int sortOrder) {
        Category category = new Category();
        category.setUser(user);
        category.setParent(parent);
        category.setName(name);
        category.setSortOrder(sortOrder);
        // Category.id is generated client-side (@UuidGenerator), so plain save() doesn't
        // necessarily flush immediately, unlike RefreshToken's IDENTITY strategy. Use
        // saveAndFlush so @CreationTimestamp/@UpdateTimestamp values are populated back
        // onto the returned instance right away.
        return categoryRepository.saveAndFlush(category);
    }

    // -------------------------------------------------------------------------
    // findAllByUserIdOrderBySortOrderAscCreatedAtAsc()
    // -------------------------------------------------------------------------

    @Test
    void findAllByUserId_returnsOwnCategoriesOrderedBySortOrder() {
        User user = saveUser();
        saveCategory(user, null, "second", 2);
        saveCategory(user, null, "first", 1);
        saveCategory(user, null, "third", 3);

        User otherUser = saveUser();
        saveCategory(otherUser, null, "other-user-category", 0);

        List<Category> result = categoryRepository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(user.getId());

        assertThat(result).extracting(Category::getName).containsExactly("first", "second", "third");
    }

    @Test
    void findAllByUserId_tieBreaksBySortOrderThenCreatedAt() {
        User user = saveUser();
        Category earlier = saveCategory(user, null, "earlier", 1);
        Category later = saveCategory(user, null, "later", 1);

        ReflectionTestUtils.setField(earlier, "createdAt", LocalDateTime.now().minusMinutes(10));
        ReflectionTestUtils.setField(later, "createdAt", LocalDateTime.now().minusMinutes(5));
        categoryRepository.saveAndFlush(earlier);
        categoryRepository.saveAndFlush(later);

        List<Category> result = categoryRepository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(user.getId());

        assertThat(result).extracting(Category::getName).containsExactly("earlier", "later");
    }

    @Test
    void findAllByUserId_returnsEmptyList_whenUserHasNoCategories() {
        User otherUser = saveUser();
        saveCategory(otherUser, null, "other-user-category", 0);

        List<Category> result = categoryRepository.findAllByUserIdOrderBySortOrderAscCreatedAtAsc(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByIdAndUserId()
    // -------------------------------------------------------------------------

    @Test
    void findByIdAndUserId_returnsCategory_whenOwnedByUser() {
        User user = saveUser();
        Category category = saveCategory(user, null, "mine", 0);

        assertThat(categoryRepository.findByIdAndUserId(category.getId(), user.getId())).isPresent();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenOwnedByAnotherUser() {
        User otherUser = saveUser();
        Category category = saveCategory(otherUser, null, "not-mine", 0);

        assertThat(categoryRepository.findByIdAndUserId(category.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenIdDoesNotExistButUserOwnsOtherCategories() {
        User user = saveUser();
        saveCategory(user, null, "some-other-category", 0);

        assertThat(categoryRepository.findByIdAndUserId(UUID.randomUUID(), user.getId())).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenOnlyUnrelatedDataExists() {
        User otherUser = saveUser();
        saveCategory(otherUser, null, "unrelated", 0);

        assertThat(categoryRepository.findByIdAndUserId(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
    }

    // -------------------------------------------------------------------------
    // existsByUserIdAndParentIdAndName()
    // -------------------------------------------------------------------------

    @Test
    void existsByUserIdAndParentIdAndName_returnsTrue_whenAllMatch() {
        User user = saveUser();
        Category parent = saveCategory(user, null, "parent", 0);
        saveCategory(user, parent, "child-name", 0);

        boolean result = categoryRepository.existsByUserIdAndParentIdAndName(user.getId(), parent.getId(), "child-name");

        assertThat(result).isTrue();
    }

    @Test
    void existsByUserIdAndParentIdAndName_returnsTrue_whenBothParentIdAreNull() {
        User user = saveUser();
        saveCategory(user, null, "root-name", 0);

        boolean result = categoryRepository.existsByUserIdAndParentIdAndName(user.getId(), null, "root-name");

        assertThat(result).isTrue();
    }

    @Test
    void existsByUserIdAndParentIdAndName_returnsFalse_whenNameDiffers() {
        User user = saveUser();
        saveCategory(user, null, "original", 0);

        boolean result = categoryRepository.existsByUserIdAndParentIdAndName(user.getId(), null, "different");

        assertThat(result).isFalse();
    }

    @Test
    void existsByUserIdAndParentIdAndName_returnsFalse_whenParentIdDiffers() {
        User user = saveUser();
        Category parent1 = saveCategory(user, null, "parent-1", 0);
        Category parent2 = saveCategory(user, null, "parent-2", 0);
        saveCategory(user, parent1, "child-name", 0);

        boolean result = categoryRepository.existsByUserIdAndParentIdAndName(user.getId(), parent2.getId(), "child-name");

        assertThat(result).isFalse();
    }

    @Test
    void existsByUserIdAndParentIdAndName_returnsFalse_whenUserIdDiffers() {
        User user = saveUser();
        saveCategory(user, null, "shared-name", 0);

        boolean result = categoryRepository.existsByUserIdAndParentIdAndName(UUID.randomUUID(), null, "shared-name");

        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // findByParentId()
    // -------------------------------------------------------------------------

    @Test
    void findByParentId_returnsDirectChildren() {
        User user = saveUser();
        Category parent = saveCategory(user, null, "parent", 0);
        Category child1 = saveCategory(user, parent, "child-1", 0);
        saveCategory(user, parent, "child-2", 1);
        saveCategory(user, child1, "grandchild", 0);

        List<Category> result = categoryRepository.findByParentId(parent.getId());

        assertThat(result).extracting(Category::getName).containsExactlyInAnyOrder("child-1", "child-2");
    }

    @Test
    void findByParentId_returnsEmptyList_whenNoChildren() {
        User user = saveUser();
        Category leaf = saveCategory(user, null, "leaf", 0);

        List<Category> result = categoryRepository.findByParentId(leaf.getId());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // save() - @CreationTimestamp / @UpdateTimestamp
    // -------------------------------------------------------------------------

    @Test
    void save_populatesCreatedAtAndUpdatedAt_onInsert() {
        User user = saveUser();

        Category saved = saveCategory(user, null, "new-category", 0);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void save_updatesUpdatedAtOnly_whenFieldChanges() throws InterruptedException {
        User user = saveUser();
        Category category = saveCategory(user, null, "original-name", 0);
        LocalDateTime initialCreatedAt = category.getCreatedAt();
        LocalDateTime initialUpdatedAt = category.getUpdatedAt();

        // The updated_at column is a MySQL TIMESTAMP (1-second precision, see V1__create_tables.sql),
        // so the two writes must be more than 1 second apart for this comparison to be reliable.
        Thread.sleep(1100);
        category.setName("changed-name");
        categoryRepository.flush();

        assertThat(category.getCreatedAt()).isEqualTo(initialCreatedAt);
        assertThat(category.getUpdatedAt()).isAfter(initialUpdatedAt);
    }
}