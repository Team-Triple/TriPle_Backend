package org.triple.backend.global.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.triple.backend.auth.session.UserIdentityResolver;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestMdcFilter extends OncePerRequestFilter {
    private final UserIdentityResolver userIdentityResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            putMdc(request);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private void putMdc(HttpServletRequest request) {
        LogData logData = getLogData(request);

        MDC.put("traceId", logData.traceId);
        MDC.put("userUuid", logData.userId);
        MDC.put("sessionId", logData.sessionId);
        MDC.put("method", logData.method());
        MDC.put("path", logData.path());
    }

    private LogData getLogData(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String userUuid = MaskUtil.maskString(getUserUuid(session));
        String sessionId = MaskUtil.maskString(getSessionId(session));
        return new LogData(UUID.randomUUID().toString(), userUuid, sessionId, request.getMethod(), request.getRequestURI());
    }

    private String getUserUuid(HttpSession session) {
        if(session == null) return "anonymous";

        Object principal = session.getAttribute("USER_ID");
        if(principal == null) return "anonymous";
        UUID uuid = userIdentityResolver.parsePublicUuid(principal);

        return MaskUtil.maskString(uuid.toString());
    }

    private static String getSessionId(HttpSession session) {
        String sessionId = null;
        if(session != null) sessionId = session.getId();
        return sessionId;
    }

    record LogData(
            String traceId,
            String userId,
            String sessionId,
            String method,
            String path
    ) {
    }
}
