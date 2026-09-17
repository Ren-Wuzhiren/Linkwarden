package com.webcollector.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void cleanup() {
        RequestIdContext.clear();
        MDC.remove("requestId");
    }

    @Test
    void shouldKeepValidRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "req-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInsideChain = new AtomicReference<>();

        FilterChain chain = (req, res) ->
                requestIdInsideChain.set(RequestIdContext.get());

        filter.doFilter(request, response, chain);

        assertEquals("req-123", response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
        assertEquals("req-123", requestIdInsideChain.get());
        assertNull(RequestIdContext.get());
    }

    @Test
    void shouldGenerateUuidWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        String requestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);

        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
    }

    @Test
    void shouldClearContextAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RequestIdContext.set("old-request-id");

        filter.doFilter(request, response, (req, res) -> {});

        assertNull(RequestIdContext.get());
        assertNull(MDC.get("requestId"));
    }

    @Test
    void shouldReplaceInvalidRequestId() throws Exception {
        String invalidRequestId = "a".repeat(101);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, invalidRequestId);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String actualRequestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);

        assertNotEquals(invalidRequestId, actualRequestId);
        assertDoesNotThrow(() -> UUID.fromString(actualRequestId));
    }

    @Test
    void shouldReplaceRequestIdWithUnsafeCharacters() throws Exception {
        String unsafeRequestId = "req-123\ninjected";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, unsafeRequestId);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        String actualRequestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);

        assertNotEquals(unsafeRequestId, actualRequestId);
        assertDoesNotThrow(() -> UUID.fromString(actualRequestId));
    }
}
