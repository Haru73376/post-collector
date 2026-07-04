package com.github.haru73376.post_collector.tag;

import com.github.haru73376.post_collector.common.exception.ConflictException;
import com.github.haru73376.post_collector.common.exception.ResourceNotFoundException;
import com.github.haru73376.post_collector.user.User;
import com.github.haru73376.post_collector.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> getAllTags(UUID userId) {
        List<Tag> allTags = tagRepository.findAllByUserIdOrderByNameAsc(userId);

        return allTags.stream()
                      .map(tag -> new TagResponse(tag.getId(), tag.getName()))
                      .toList();
    }

    @Transactional
    public TagDetailResponse createTag(UUID userId, CreateTagRequest request) {
        String name = request.name().strip();

        validateNameNotDuplicated(userId, name);

        Tag tag = new Tag();
        User userRef = userRepository.getReferenceById(userId);
        tag.setUser(userRef);
        tag.setName(name);

        // saveAndFlush ensures @CreationTimestamp is populated before response
        Tag savedTag = tagRepository.saveAndFlush(tag);

        return new TagDetailResponse(
                savedTag.getId(),
                savedTag.getName(),
                savedTag.getCreatedAt()
        );
    }

    @Transactional
    public TagResponse updateTag(UUID userId, Long tagId, UpdateTagRequest request) {
        Tag tag = findOwnedTag(tagId, userId);

        String newName = request.name().strip();
        if (!tag.getName().equals(newName)) {
            validateNameNotDuplicated(userId, newName);
        }

        tag.setName(newName);

        return new TagResponse(tag.getId(), tag.getName());
    }

    @Transactional
    public void deleteTag(UUID userId, Long tagId) {
        Tag tag = findOwnedTag(tagId, userId);

        tagRepository.delete(tag);
    }

    private void validateNameNotDuplicated(UUID userId, String name) {
        if (tagRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException("Tag with the same name already exists");
        }
    }

    private Tag findOwnedTag(Long tagId, UUID userId) {
        return tagRepository.findByIdAndUserId(tagId, userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Tag not found"));
    }
}
