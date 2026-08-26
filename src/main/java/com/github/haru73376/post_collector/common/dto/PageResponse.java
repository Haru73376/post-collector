package com.github.haru73376.post_collector.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;

    @Schema(example = "0")
    private int page;

    @Schema(example = "20")
    private int size;

    @Schema(example = "42")
    private long totalElements;

    @Schema(example = "3")
    private int totalPages;

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
