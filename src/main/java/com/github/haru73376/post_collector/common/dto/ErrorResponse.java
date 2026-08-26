package com.github.haru73376.post_collector.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    @Schema(example = "404")
    private int status;

    @Schema(example = "Not Found")
    private String error;

    @Schema(example = "Saved post not found")
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(example = "[\"title must not be blank\"]")
    private List<String> details;

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
