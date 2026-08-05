package com.github.haru73376.post_collector.common.exception;

import com.github.haru73376.post_collector.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleRateLimitExceeded_returns429WithMessage() {
        RateLimitExceededException exception =
                new RateLimitExceededException("Too many requests. Please try again later.");

        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleRateLimitExceeded(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().getMessage()).isEqualTo("Too many requests. Please try again later.");
    }
}
