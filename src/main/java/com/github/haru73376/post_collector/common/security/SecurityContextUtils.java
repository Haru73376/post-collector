package com.github.haru73376.post_collector.common.security;

import com.github.haru73376.post_collector.common.exception.InvalidTokenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityContextUtils {

    public UUID getCurrentUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication.getPrincipal() instanceof UUID userId)) {
            throw new InvalidTokenException("Authentication required");
        }

        return userId;
    }
}
