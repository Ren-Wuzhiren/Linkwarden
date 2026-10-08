package com.webcollector.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record LoginResponse (
        String token,
        String tokenName,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant expiresAt,

        UserSummary user
) {

}
