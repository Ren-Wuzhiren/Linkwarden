package com.webcollector.collection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record CollectionSummary (
        Long id,
        String name,
        String description,
        boolean isInbox,
        long linkCount,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt
) {

}
