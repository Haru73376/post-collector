package com.github.haru73376.post_collector.tag;

import com.github.haru73376.post_collector.common.exception.ConflictException;
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

        if (tagRepository.existsByUserIdAndName(userId, name)) {
            throw new ConflictException("Tag with the same name already exists");
        }

        Tag tag = new Tag();
        User userRef = userRepository.getReferenceById(userId);
        tag.setUser(userRef);
        tag.setName(name);

        // saveAndFlush ensures @CreationTimestamp is populated before response
        Tag savedTag = tagRepository.saveAndFlush(tag);

        return new TagDetailResponse(
                savedTag.getId(),
                savedTag.getName(),
                savedTag.getCreatedAt());
    }
}
