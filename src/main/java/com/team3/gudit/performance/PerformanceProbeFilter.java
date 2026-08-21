package com.team3.gudit.performance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Profile("performance")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class PerformanceProbeFilter extends OncePerRequestFilter {

    public static final String RUN_ID_HEADER = "X-Performance-Test-Run-Id";
    public static final String REQUEST_ID_HEADER = "X-Performance-Test-Request-Id";
    public static final String REACHED_HEADER = "X-Performance-Test-Reached";

    private static final int MAX_IDENTIFIER_LENGTH = 160;

    private final PerformanceProbeRegistry registry;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String runId = validIdentifier(request.getHeader(RUN_ID_HEADER));
        String requestId = validIdentifier(request.getHeader(REQUEST_ID_HEADER));

        if (runId != null && requestId != null) {
            registry.recordArrival(runId, requestId);
            response.setHeader(REACHED_HEADER, "true");
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (runId != null && requestId != null) {
                registry.recordCompletion(runId, requestId);
            }
        }
    }

    private String validIdentifier(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDENTIFIER_LENGTH) {
            return null;
        }
        return value;
    }
}
