package com.webcollector.auth.dto;

import java.time.Instant;

public record UserSummary (
        Long id,
        String username,
        String email,
        Instant createdAt
) {
}