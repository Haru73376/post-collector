package com.github.haru73376.post_collector.tag;

import com.github.haru73376.post_collector.post.Platform;
import com.github.haru73376.post_collector.post.SavedPost;
import com.github.haru73376.post_collector.post.SavedPostRepository;
import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TagRepositoryTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    TagRepository tagRepository;

    @Autowired
    PostTagRepository postTagRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SavedPostRepository savedPostRepository;

    private User saveUser() {
        User user = new User();
        user.setUsername("username-" + UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hashed");
        return userRepository.save(user);
    }

    private Tag saveTag(User user, String name) {
        Tag tag = new Tag();
        tag.setUser(user);
        tag.setName(name);
        return tagRepository.save(tag);
    }

    private SavedPost savePost(User user) {
        SavedPost post = new SavedPost();
        post.setUser(user);
        post.setUrl("https://example.com/" + UUID.randomUUID());
        post.setTitle("title");
        post.setPlatform(Platform.OTHER);
        return savedPostRepository.save(post);
    }

    private PostTag savePostTag(UUID postId, Long tagId) {
        return postTagRepository.save(new PostTag(postId, tagId));
    }

    // -------------------------------------------------------------------------
    // findAllByUserIdOrderByNameAsc()
    // -------------------------------------------------------------------------

    @Test
    void findAllByUserId_returnsOwnTagsOrderedByName() {
        User user = saveUser();
        saveTag(user, "banana");
        saveTag(user, "apple");
        saveTag(user, "cherry");

        User otherUser = saveUser();
        saveTag(otherUser, "aaa");

        List<Tag> result = tagRepository.findAllByUserIdOrderByNameAsc(user.getId());

        assertThat(result).extracting(Tag::getName).containsExactly("apple", "banana", "cherry");
    }

    @Test
    void findAllByUserId_returnsEmptyList_whenUserHasNoTags() {
        User otherUser = saveUser();
        saveTag(otherUser, "aaa");

        List<Tag> result = tagRepository.findAllByUserIdOrderByNameAsc(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByIdAndUserId()
    // -------------------------------------------------------------------------

    @Test
    void findByIdAndUserId_returnsTag_whenOwnedByUser() {
        User user = saveUser();
        Tag tag = saveTag(user, "mine");

        assertThat(tagRepository.findByIdAndUserId(tag.getId(), user.getId())).isPresent();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenOwnedByAnotherUser() {
        User otherUser = saveUser();
        Tag tag = saveTag(otherUser, "not-mine");

        assertThat(tagRepository.findByIdAndUserId(tag.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenIdDoesNotExistButUserOwnsOtherTags() {
        User user = saveUser();
        saveTag(user, "some-other-tag");

        assertThat(tagRepository.findByIdAndUserId(Long.MAX_VALUE, user.getId())).isEmpty();
    }

    @Test
    void findByIdAndUserId_returnsEmpty_whenOnlyUnrelatedDataExists() {
        User otherUser = saveUser();
        saveTag(otherUser, "unrelated");

        assertThat(tagRepository.findByIdAndUserId(Long.MAX_VALUE, UUID.randomUUID())).isEmpty();
    }

    // -------------------------------------------------------------------------
    // existsByUserIdAndName()
    // -------------------------------------------------------------------------

    @Test
    void existsByUserIdAndName_returnsTrue_whenBothMatch() {
        User user = saveUser();
        saveTag(user, "shared-name");

        boolean result = tagRepository.existsByUserIdAndName(user.getId(), "shared-name");

        assertThat(result).isTrue();
    }

    @Test
    void existsByUserIdAndName_returnsFalse_whenNameDiffers() {
        User user = saveUser();
        saveTag(user, "original");

        boolean result = tagRepository.existsByUserIdAndName(user.getId(), "different");

        assertThat(result).isFalse();
    }

    @Test
    void existsByUserIdAndName_returnsFalse_whenUserIdDiffers() {
        User user = saveUser();
        saveTag(user, "shared-name");

        boolean result = tagRepository.existsByUserIdAndName(UUID.randomUUID(), "shared-name");

        assertThat(result).isFalse();
    }

    // -------------------------------------------------------------------------
    // findAllByIdInAndUserId()
    // -------------------------------------------------------------------------

    @Test
    void findAllByIdInAndUserId_returnsAllTags_whenAllOwnedByUser() {
        User user = saveUser();
        Tag tag1 = saveTag(user, "tag-1");
        Tag tag2 = saveTag(user, "tag-2");

        List<Tag> result = tagRepository.findAllByIdInAndUserId(List.of(tag1.getId(), tag2.getId()), user.getId());

        assertThat(result).extracting(Tag::getId).containsExactlyInAnyOrder(tag1.getId(), tag2.getId());
    }

    @Test
    void findAllByIdInAndUserId_excludesTagsOwnedByAnotherUser() {
        User user = saveUser();
        Tag ownTag = saveTag(user, "mine");
        User otherUser = saveUser();
        Tag otherTag = saveTag(otherUser, "not-mine");

        List<Tag> result = tagRepository.findAllByIdInAndUserId(List.of(ownTag.getId(), otherTag.getId()), user.getId());

        assertThat(result).extracting(Tag::getId).containsExactly(ownTag.getId());
    }

    @Test
    void findAllByIdInAndUserId_excludesNonExistentIds() {
        User user = saveUser();
        Tag tag = saveTag(user, "mine");

        List<Tag> result = tagRepository.findAllByIdInAndUserId(List.of(tag.getId(), Long.MAX_VALUE), user.getId());

        assertThat(result).extracting(Tag::getId).containsExactly(tag.getId());
    }

    @Test
    void findAllByIdInAndUserId_returnsEmptyList_whenIdsIsEmpty() {
        User user = saveUser();
        saveTag(user, "mine");

        List<Tag> result = tagRepository.findAllByIdInAndUserId(List.of(), user.getId());

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // save() - @CreationTimestamp
    // -------------------------------------------------------------------------

    @Test
    void save_populatesCreatedAtAutomatically() {
        User user = saveUser();

        Tag saved = saveTag(user, "new-tag");

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    // -------------------------------------------------------------------------
    // PostTagRepository.deleteAllByPostId()
    // -------------------------------------------------------------------------

    @Test
    void deleteAllByPostId_removesOnlyMatchingRows() {
        User user = saveUser();
        SavedPost postA = savePost(user);
        SavedPost postB = savePost(user);
        Tag tag1 = saveTag(user, "tag-1");
        Tag tag2 = saveTag(user, "tag-2");
        Tag tag3 = saveTag(user, "tag-3");
        savePostTag(postA.getId(), tag1.getId());
        savePostTag(postA.getId(), tag2.getId());
        savePostTag(postB.getId(), tag3.getId());

        postTagRepository.deleteAllByPostId(postA.getId());

        Map<UUID, List<TagResponse>> result = postTagRepository.findTagsByPostIdIn(List.of(postA.getId(), postB.getId()));
        assertThat(result).doesNotContainKey(postA.getId());
        assertThat(result.get(postB.getId())).extracting(TagResponse::id).containsExactly(tag3.getId());
    }

    @Test
    void deleteAllByPostId_doesNothing_whenNoMatchingRows() {
        User user = saveUser();
        SavedPost postA = savePost(user);
        SavedPost postB = savePost(user);
        Tag tag = saveTag(user, "tag-1");
        savePostTag(postB.getId(), tag.getId());

        postTagRepository.deleteAllByPostId(postA.getId());

        Map<UUID, List<TagResponse>> result = postTagRepository.findTagsByPostIdIn(List.of(postB.getId()));
        assertThat(result.get(postB.getId())).extracting(TagResponse::id).containsExactly(tag.getId());
    }

    // -------------------------------------------------------------------------
    // PostTagRepository.findTagsByPostIdIn()
    // -------------------------------------------------------------------------

    @Test
    void findTagsByPostIdIn_groupsTagsByPost() {
        User user = saveUser();
        SavedPost postA = savePost(user);
        SavedPost postB = savePost(user);
        Tag tag1 = saveTag(user, "tag-1");
        Tag tag2 = saveTag(user, "tag-2");
        Tag tag3 = saveTag(user, "tag-3");
        savePostTag(postA.getId(), tag1.getId());
        savePostTag(postA.getId(), tag2.getId());
        savePostTag(postB.getId(), tag3.getId());

        Map<UUID, List<TagResponse>> result = postTagRepository.findTagsByPostIdIn(List.of(postA.getId(), postB.getId()));

        assertThat(result.get(postA.getId()))
                .containsExactlyInAnyOrder(new TagResponse(tag1.getId(), "tag-1"), new TagResponse(tag2.getId(), "tag-2"));
        assertThat(result.get(postB.getId()))
                .containsExactly(new TagResponse(tag3.getId(), "tag-3"));
    }

    @Test
    void findTagsByPostIdIn_excludesPostIdsWithNoTags() {
        User user = saveUser();
        SavedPost postWithTag = savePost(user);
        SavedPost postWithoutTag = savePost(user);
        Tag tag = saveTag(user, "tag-1");
        savePostTag(postWithTag.getId(), tag.getId());

        Map<UUID, List<TagResponse>> result =
                postTagRepository.findTagsByPostIdIn(List.of(postWithTag.getId(), postWithoutTag.getId()));

        assertThat(result).containsOnlyKeys(postWithTag.getId());
    }

    @Test
    void findTagsByPostIdIn_returnsEmptyMap_whenPostIdsIsEmpty() {
        Map<UUID, List<TagResponse>> result = postTagRepository.findTagsByPostIdIn(List.of());

        assertThat(result).isEmpty();
    }
}