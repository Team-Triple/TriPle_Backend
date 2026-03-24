package org.triple.backend.global.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class RequestMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        try {
            putMdc(request);
            filterChain.doFilter(request, response);
        } finally {
            long endTime = System.currentTimeMillis();

            MDC.put("latency", Long.toString(endTime - startTime));
            MDC.clear();
        }
    }

    private void putMdc(HttpServletRequest request) {
        LogData logData = getLogData(request);

        MDC.put("traceId", logData.traceId);
        MDC.put("method", logData.method());
        MDC.put("path", logData.path());
    }

    private LogData getLogData(HttpServletRequest request) {
        return new LogData(UUID.randomUUID().toString(), request.getMethod(), request.getRequestURI());
    }

    record LogData(
            String traceId,
            String method,
            String path
    ) {
    }
}
