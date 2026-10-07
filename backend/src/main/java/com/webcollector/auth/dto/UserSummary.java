package com.webcollector.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record UserSummary (
        Long id,
        String username,
        String email,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt
) {
}