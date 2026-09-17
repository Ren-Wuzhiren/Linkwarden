package com.webcollector.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";
    private static final int MAX_LENGTH = 100;
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        RequestIdContext.set(requestId);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            RequestIdContext.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId == null
            || requestId.isBlank()
            || requestId.length() > MAX_LENGTH
            || !SAFE_ID.matcher(requestId).matches()) {
            return UUID.randomUUID().toString();
        }

        return requestId;
    }
}
