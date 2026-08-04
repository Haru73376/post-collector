package com.github.haru73376.post_collector.post;

import com.github.haru73376.post_collector.category.Category;
import com.github.haru73376.post_collector.category.CategoryRepository;
import com.github.haru73376.post_collector.tag.PostTag;
import com.github.haru73376.post_collector.tag.PostTagRepository;
import com.github.haru73376.post_collector.tag.Tag;
import com.github.haru73376.post_collector.tag.TagRepository;
import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SavedPostRepositoryTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    SavedPostRepository savedPostRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    TagRepository tagRepository;

    @Autowired
    PostTagRepository postTagRepository;

    private User saveUser() {
        User user = new User();
        user.setUsername("username-" + UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hashed");
        return userRepository.save(user);
    }

    private Category saveCategory(User user, String name) {
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setSortOrder(0);
        return categoryRepository.saveAndFlush(category);
    }

    private Tag saveTag(User user, String name) {
        Tag tag = new Tag();
        tag.setUser(user);
        tag.setName(name);
        return tagRepository.save(tag);
    }

    private void savePostTag(UUID postId, Long tagId) {
        postTagRepository.save(new PostTag(postId, tagId));
    }

    private SavedPost savePost(User user) {
        return savePost(user, null, Platform.OTHER, false, "title", null);
    }

    private SavedPost savePost(User user, Category category, Platform platform, boolean favorite, String title, String memo) {
        SavedPost post = new SavedPost();
        post.setUser(user);
        post.setCategory(category);
        post.setUrl("https://example.com/" + UUID.randomUUID());
        post.setTitle(title);
        post.setMemo(memo);
        post.setPlatform(platform);
        post.setFavorite(favorite);
        return savedPostRepository.saveAndFlush(post);
    }

    private Map<UUID, Long> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    // -------------------------------------------------------------------------
    // countByCategoryIds()
    // -------------------------------------------------------------------------

    @Test
    void countByCategoryIds_returnsCountPerCategory_excludingUnrelatedCategories() {
        User user = saveUser();
        Category categoryA = saveCategory(user, "category-a");
        Category categoryB = saveCategory(user, "category-b");
        Category categoryC = saveCategory(user, "category-c");
        savePost(user, categoryA, Platform.OTHER, false, "a1", null);
        savePost(user, categoryA, Platform.OTHER, false, "a2", null);
        savePost(user, categoryB, Platform.OTHER, false, "b1", null);
        savePost(user, categoryC, Platform.OTHER, false, "c1", null);

        Map<UUID, Long> result = toCountMap(savedPostRepository.countByCategoryIds(List.of(categoryA.getId(), categoryB.getId())));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(categoryA.getId(), 2L, categoryB.getId(), 1L));
    }

    @Test
    void countByCategoryIds_omitsCategoryFromResult_whenNoPostsExist() {
        User user = saveUser();
        Category categoryA = saveCategory(user, "category-a");
        Category categoryB = saveCategory(user, "category-b");
        savePost(user, categoryA, Platform.OTHER, false, "a1", null);

        List<Object[]> result = savedPostRepository.countByCategoryIds(List.of(categoryA.getId(), categoryB.getId()));

        assertThat(toCountMap(result)).containsOnlyKeys(categoryA.getId());
    }

    @Test
    void countByCategoryIds_excludesSoftDeletedPosts() {
        User user = saveUser();
        Category categoryA = saveCategory(user, "category-a");
        savePost(user, categoryA, Platform.OTHER, false, "active", null);
        SavedPost deleted = savePost(user, categoryA, Platform.OTHER, false, "deleted", null);
        deleted.setDeletedAt(LocalDateTime.now());
        savedPostRepository.flush();

        Map<UUID, Long> result = toCountMap(savedPostRepository.countByCategoryIds(List.of(categoryA.getId())));

        assertThat(result).containsExactly(Map.entry(categoryA.getId(), 1L));
    }

    @Test
    void countByCategoryIds_returnsEmptyList_whenCategoryIdsIsEmpty() {
        User user = saveUser();
        Category categoryA = saveCategory(user, "category-a");
        savePost(user, categoryA, Platform.OTHER, false, "a1", null);

        List<Object[]> result = savedPostRepository.countByCategoryIds(List.of());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByIdAndUserId()
    // -------------------------------------------------------------------------

    @Test
    void findByIdAndUserId_returnsPost_whenOwnedByUser() {
        User user = saveUser();
        SavedPost post = savePost(user);

        assertThat(savedPostRepository.findByIdAndUserId(post.getId(), user.getId())).isPresent();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenOwnedByAnotherUser() {
        User otherUser = saveUser();
        SavedPost post = savePost(otherUser);

        assertThat(savedPostRepository.findByIdAndUserId(post.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenIdDoesNotExistButUserOwnsOtherPosts() {
        User user = saveUser();
        savePost(user);

        assertThat(savedPostRepository.findByIdAndUserId(UUID.randomUUID(), user.getId())).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenOnlyUnrelatedDataExists() {
        User otherUser = saveUser();
        savePost(otherUser);

        assertThat(savedPostRepository.findByIdAndUserId(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenPostIsSoftDeleted() {
        User user = saveUser();
        SavedPost post = savePost(user);
        post.setDeletedAt(LocalDateTime.now());
        savedPostRepository.flush();

        assertThat(savedPostRepository.findByIdAndUserId(post.getId(), user.getId())).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findAll(Specification, Pageable) - SavedPostSpecification.withCriteria
    // -------------------------------------------------------------------------

    private static final PostSearchCriteria NO_CRITERIA = new PostSearchCriteria(null, null, null, null, null);

    @Test
    void withCriteria_returnsAllOwnPosts_whenNoOptionalCriteriaSpecified() {
        User user = saveUser();
        Category category = saveCategory(user, "category");
        Tag tag = saveTag(user, "tag");
        SavedPost post1 = savePost(user, category, Platform.INSTAGRAM, true, "post1", null);
        savePostTag(post1.getId(), tag.getId());
        SavedPost post2 = savePost(user, null, Platform.YOUTUBE, false, "post2", null);

        User otherUser = saveUser();
        savePost(otherUser);

        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), NO_CRITERIA), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId)
                .containsExactlyInAnyOrder(post1.getId(), post2.getId());
    }

    @Test
    void withCriteria_filtersByCategory_whenCategoryIdSpecified() {
        User user = saveUser();
        Category categoryA = saveCategory(user, "category-a");
        Category categoryB = saveCategory(user, "category-b");
        SavedPost postA = savePost(user, categoryA, Platform.OTHER, false, "post-a", null);
        savePost(user, categoryB, Platform.OTHER, false, "post-b", null);

        PostSearchCriteria criteria = new PostSearchCriteria(categoryA.getId(), null, null, null, null);
        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), criteria), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId).containsExactly(postA.getId());
    }

    @Test
    void withCriteria_filtersByPlatform_whenPlatformSpecified() {
        User user = saveUser();
        SavedPost postInstagram = savePost(user, null, Platform.INSTAGRAM, false, "post-instagram", null);
        savePost(user, null, Platform.YOUTUBE, false, "post-youtube", null);

        PostSearchCriteria criteria = new PostSearchCriteria(null, Platform.INSTAGRAM, null, null, null);
        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), criteria), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId).containsExactly(postInstagram.getId());
    }

    @Test
    void withCriteria_filtersByTag_whenTagIdSpecified() {
        User user = saveUser();
        Tag tagA = saveTag(user, "tag-a");
        Tag tagB = saveTag(user, "tag-b");
        SavedPost post1 = savePost(user, null, Platform.OTHER, false, "post-1", null);
        savePostTag(post1.getId(), tagA.getId());
        savePostTag(post1.getId(), tagB.getId());
        SavedPost post2 = savePost(user, null, Platform.OTHER, false, "post-2", null);
        savePostTag(post2.getId(), tagB.getId());

        PostSearchCriteria criteria = new PostSearchCriteria(null, null, tagA.getId(), null, null);
        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), criteria), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId).containsExactly(post1.getId());
    }

    @Test
    void withCriteria_filtersByFavorite_whenFavoriteIsTrue() {
        User user = saveUser();
        SavedPost postFavorite = savePost(user, null, Platform.OTHER, true, "post-favorite", null);
        savePost(user, null, Platform.OTHER, false, "post-not-favorite", null);

        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, true, null);
        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), criteria), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId).containsExactly(postFavorite.getId());
    }

    @Test
    void withCriteria_matchesKeywordInTitle() {
        User user = saveUser();
        SavedPost matching = savePost(user, null, Platform.OTHER, false, "Find me here", null);
        savePost(user, null, Platform.OTHER, false, "unrelated title", null);

        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, "find me");
        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), criteria), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId).containsExactly(matching.getId());
    }

    @Test
    void withCriteria_matchesKeywordInMemo_whenTitleDoesNotMatch() {
        User user = saveUser();
        SavedPost matching = savePost(user, null, Platform.OTHER, false, "unrelated title", "secret keyword here");
        savePost(user, null, Platform.OTHER, false, "another title", "another memo");

        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, "secret keyword");
        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), criteria), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId).containsExactly(matching.getId());
    }

    @Test
    void withCriteria_treatsPercentAndUnderscoreInKeywordAsLiteral() {
        User user = saveUser();
        SavedPost literalMatch = savePost(user, null, Platform.OTHER, false, "50% off today", null);
        // Contains "50" but not the literal "50%" substring; would incorrectly match if
        // "%" were interpreted as a wildcard instead of a literal character.
        SavedPost wildcardTrap = savePost(user, null, Platform.OTHER, false, "50 items available", null);

        PostSearchCriteria criteria = new PostSearchCriteria(null, null, null, null, "50%");
        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), criteria), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId).containsExactly(literalMatch.getId());
        assertThat(result.getContent()).extracting(SavedPost::getId).doesNotContain(wildcardTrap.getId());
    }

    @Test
    void withCriteria_combinesMultipleCriteriaWithAnd() {
        User user = saveUser();
        Category category = saveCategory(user, "category");
        SavedPost allMatch = savePost(user, category, Platform.INSTAGRAM, true, "all-match", null);
        savePost(user, category, Platform.YOUTUBE, true, "platform-mismatch", null);

        PostSearchCriteria criteria = new PostSearchCriteria(category.getId(), Platform.INSTAGRAM, null, true, null);
        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), criteria), PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(SavedPost::getId).containsExactly(allMatch.getId());
    }

    @Test
    void withCriteria_excludesSoftDeletedPosts_regardlessOfCriteria() {
        User user = saveUser();
        SavedPost post = savePost(user);
        post.setDeletedAt(LocalDateTime.now());
        savedPostRepository.flush();

        Page<SavedPost> result = savedPostRepository.findAll(
                SavedPostSpecification.withCriteria(user.getId(), NO_CRITERIA), PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // save() - @CreationTimestamp / @UpdateTimestamp
    // -------------------------------------------------------------------------

    @Test
    void save_populatesCreatedAtAndUpdatedAt_onInsert() {
        User user = saveUser();

        SavedPost saved = savePost(user);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void save_updatesUpdatedAtOnly_whenFieldChanges() throws InterruptedException {
        User user = saveUser();
        SavedPost post = savePost(user);
        LocalDateTime initialCreatedAt = post.getCreatedAt();
        LocalDateTime initialUpdatedAt = post.getUpdatedAt();

        // updated_at is a MySQL TIMESTAMP (1-second precision, see V1__create_tables.sql),
        // so the two writes must be more than 1 second apart for this comparison to be reliable.
        Thread.sleep(1100);
        post.setTitle("changed-title");
        savedPostRepository.flush();

        assertThat(post.getCreatedAt()).isEqualTo(initialCreatedAt);
        assertThat(post.getUpdatedAt()).isAfter(initialUpdatedAt);
    }
}
