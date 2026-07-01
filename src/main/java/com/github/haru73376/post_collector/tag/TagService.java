package com.github.haru73376.post_collector.tag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> getAllTags(UUID userId) {
        List<Tag> allTags = tagRepository.findAllByUserIdOrderByNameAsc(userId);

        return allTags.stream()
                .map(tag -> new TagResponse(tag.getId(), tag.getName()))
                .toList();
    }
}
