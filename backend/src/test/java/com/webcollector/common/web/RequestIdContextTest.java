package com.webcollector.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdContextTest {

    @AfterEach
    void tearDown() {
        RequestIdContext.clear();
    }

    @Test
    void shouldStoreAndReadRequestId() {
        RequestIdContext.set("request-001");

        assertEquals("request-001", RequestIdContext.get());
    }

    @Test
    void shouldClearRequestId() {
        RequestIdContext.set("request-002");

        RequestIdContext.clear();

        assertNull(RequestIdContext.get());
    }
}
