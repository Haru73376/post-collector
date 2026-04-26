package com.github.haru73376.post_collector.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String error;
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> details;

    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
