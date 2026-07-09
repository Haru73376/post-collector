package com.github.haru73376.post_collector.tag;

import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TagServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TagService tagService;

    // -------------------------------------------------------------------------
    // getAllTags()
    // -------------------------------------------------------------------------

    @Test
    void getAllTags_returnsEmptyList_whenUserHasNoTags() {
        UUID userId = UUID.randomUUID();

        given(tagRepository.findAllByUserIdOrderByNameAsc(userId)).willReturn(List.of());

        List<TagResponse> result = tagService.getAllTags(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllTags_returnsAllTagResponses_whenTagsExist() {
        UUID userId = UUID.randomUUID();

        Tag tag1 = new Tag();
        String tag1Name = "tag1";
        Long tag1Id = 1L;
        ReflectionTestUtils.setField(tag1, "id", tag1Id);
        tag1.setName(tag1Name);

        Tag tag2 = new Tag();
        String tag2Name = "tag2";
        Long tag2Id = 2L;
        ReflectionTestUtils.setField(tag2, "id", tag2Id);
        tag2.setName(tag2Name);

        given(tagRepository.findAllByUserIdOrderByNameAsc(userId)).willReturn(List.of(tag1, tag2));

        List<TagResponse> result = tagService.getAllTags(userId);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()
                         .id()).isEqualTo(tag1Id);
        assertThat(result.getFirst()
                         .name()).isEqualTo(tag1Name);
        assertThat(result.getLast()
                         .id()).isEqualTo(tag2Id);
        assertThat(result.getLast()
                         .name()).isEqualTo(tag2Name);
    }

    // -------------------------------------------------------------------------
    // createTag()
    // -------------------------------------------------------------------------

    @Test
    void createTag_returnsCreatedTagResponse_whenValidRequest() {
        UUID userId = UUID.randomUUID();
        CreateTagRequest request = new CreateTagRequest("tagName");
        Tag savedTag = new Tag();
        Long tagId = 1L;
        LocalDateTime createdAt = LocalDateTime.now();
        ReflectionTestUtils.setField(savedTag, "id", tagId);
        savedTag.setName(request.name());
        ReflectionTestUtils.setField(savedTag, "createdAt", createdAt);

        given(tagRepository.existsByUserIdAndName(any(), any())).willReturn(false);
        given(userRepository.getReferenceById(any())).willReturn(new User());
        given(tagRepository.saveAndFlush(any())).willReturn(savedTag);

        TagDetailResponse result = tagService.createTag(userId, request);

        assertThat(result.id()).isEqualTo(tagId);
        assertThat(result.name()).isEqualTo(request.name());
        assertThat(result.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void createTag_savesTrimmedName_whenNameHasLeadingOrTrailingWhitespace() {
        UUID userId = UUID.randomUUID();
        CreateTagRequest request = new CreateTagRequest(" 　tagName 　");
        String expectedName = "tagName";

        given(tagRepository.existsByUserIdAndName(any(), any())).willReturn(false);
        given(userRepository.getReferenceById(any())).willReturn(new User());
        given(tagRepository.saveAndFlush(any())).willReturn(new Tag());

        tagService.createTag(userId, request);

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()
                         .getName()).isEqualTo(expectedName);

        verify(tagRepository).existsByUserIdAndName(userId, expectedName);
    }

    @Test
    void createTag_throwsConflictException_whenNameAlreadyExists() {
        UUID userId = UUID.randomUUID();
        CreateTagRequest request = new CreateTagRequest("tagName");

        given(tagRepository.existsByUserIdAndName(any(), any())).willReturn(true);

        assertThatThrownBy(() -> tagService.createTag(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Tag with the same name already exists");
    }

    // -------------------------------------------------------------------------
    // updateTag()
    // -------------------------------------------------------------------------

    @Test
    void updateTag_returnsUpdatedTagResponse_whenValidRequest() {
        UUID userId = UUID.randomUUID();
        String newTagName = "newTagName";
        UpdateTagRequest request = new UpdateTagRequest(newTagName);
        Tag oldTag = new Tag();
        Long tagId = 1L;
        ReflectionTestUtils.setField(oldTag, "id", tagId);
        oldTag.setName("oldTagName");

        given(tagRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(oldTag));
        given(tagRepository.existsByUserIdAndName(any(), any())).willReturn(false);

        TagResponse result = tagService.updateTag(userId, tagId, request);

        assertThat(result.id()).isEqualTo(tagId);
        assertThat(result.name()).isEqualTo(newTagName);
    }

    @Test
    void updateTag_skipsDuplicateCheck_whenNameIsUnchanged() {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;
        String sameName = "tagName";
        UpdateTagRequest request = new UpdateTagRequest(sameName);

        Tag tag = new Tag();
        tag.setName(sameName);

        given(tagRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(tag));

        tagService.updateTag(userId, tagId, request);

        verify(tagRepository, never()).existsByUserIdAndName(any(), any());
    }

    @Test
    void updateTag_treatsNameAsUnchanged_whenOnlyWhitespaceDiffers() {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;
        Tag tag = new Tag();
        tag.setName("tagName");
        UpdateTagRequest request = new UpdateTagRequest(" 　tagName 　");

        given(tagRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(tag));

        TagResponse result = tagService.updateTag(userId, tagId, request);

        // If newName is trimmed, it matches the current name and the duplicate check is skipped
        verify(tagRepository, never()).existsByUserIdAndName(any(), any());
        // Confirms the saved name itself is trimmed
        assertThat(result.name()).isEqualTo("tagName");
    }

    @Test
    void updateTag_throwsResourceNotFoundException_whenTagNotOwned() {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;
        UpdateTagRequest request = new UpdateTagRequest("tagName");

        given(tagRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.updateTag(userId, tagId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tag not found");
    }

    @Test
    void updateTag_throwsConflictException_whenNameAlreadyExists() {
        UUID userId = UUID.randomUUID();
        String newTagName = "newTagName";
        UpdateTagRequest request = new UpdateTagRequest(newTagName);
        Tag oldTag = new Tag();
        Long tagId = 1L;
        ReflectionTestUtils.setField(oldTag, "id", tagId);
        oldTag.setName("oldTagName");

        given(tagRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(oldTag));
        given(tagRepository.existsByUserIdAndName(any(), any())).willReturn(true);

        assertThatThrownBy(() -> tagService.updateTag(userId, tagId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Tag with the same name already exists");
    }

    // -------------------------------------------------------------------------
    // deleteTag()
    // -------------------------------------------------------------------------

    @Test
    void deleteTag_deletesTag_whenOwned() {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;

        Tag tag = new Tag();
        given(tagRepository.findByIdAndUserId(any(), any())).willReturn(Optional.of(tag));

        tagService.deleteTag(userId, tagId);

        verify(tagRepository).delete(tag);
    }

    @Test
    void deleteTag_throwsResourceNotFoundException_whenTagNotOwned() {
        UUID userId = UUID.randomUUID();
        Long tagId = 1L;

        given(tagRepository.findByIdAndUserId(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.deleteTag(userId, tagId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tag not found");
    }
}
