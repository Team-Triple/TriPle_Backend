package org.triple.backend.global.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.triple.backend.auth.session.SessionManager;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class RequestMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();

        try {
            putMdc(request);
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            MDC.put("status", String.valueOf(response.getStatus()));
            MDC.put("latencyMs", String.valueOf(latencyMs));
            log.info("http request completed");
            MDC.clear();
        }
    }

    private void putMdc(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(SessionManager.SESSION_KEY);
        String sessionId = session == null ? null : session.getId();
        String maskedUserId = userId == null ? "anonymous" : MaskUtil.maskId(userId);
        String maskedSessionId = sessionId == null ? "none" : MaskUtil.maskString(sessionId);

        MDC.put("method", request.getMethod());
        MDC.put("path", request.getRequestURI());
        MDC.put("userId", maskedUserId);
        MDC.put("sessionId", maskedSessionId);
    }
}
