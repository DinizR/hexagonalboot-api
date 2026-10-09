package systems.porto.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import systems.porto.api.config.PortoApiProperties;
import systems.porto.util.LoggingUtils;

import java.io.IOException;
import java.util.UUID;

/**
 * Binds Porto structured log MDC for each HTTP request: application, session, user, correlation.
 * Accepts {@code X-Correlation-Id} / {@code X-Session-Id} when present; otherwise generates
 * 16-char hex ids (same style as porto-smart-ui / {@link LoggingUtils}).
 * Branch/terminal are MPOS-only and are not used here.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestLogContextFilter extends OncePerRequestFilter {
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_SESSION_ID = "X-Session-Id";

    private static final String MDC_MISSING = "-";

    private final PortoApiProperties properties;

    public RequestLogContextFilter(final PortoApiProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String application = properties.getApplication();
            putMdc(LoggingUtils.MDC_APPLICATION, application);

            String sessionId = resolveOrGenerate(request.getHeader(HEADER_SESSION_ID));
            putMdc(LoggingUtils.MDC_SESSION, sessionId);
            response.setHeader(HEADER_SESSION_ID, sessionId);

            // Bearer auth runs later in the Security chain; user is rebound there
            // and again when PluginRuntime attaches the principal.
            putMdc(LoggingUtils.MDC_USER, resolveUser());

            String correlationId = resolveOrGenerate(request.getHeader(HEADER_CORRELATION_ID));
            putMdc(LoggingUtils.MDC_CORRELATION, correlationId);
            response.setHeader(HEADER_CORRELATION_ID, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String resolveOrGenerate(final String headerValue) {
        if (headerValue != null && !headerValue.isBlank()) {
            return normalizeId(headerValue.trim());
        }
        return newCorrelationId();
    }

    /**
     * Prefer the Porto 16-char hex form. Full UUIDs are shortened; other non-blank values are kept.
     */
    private static String normalizeId(final String value) {
        String compact = value.replace("-", "");
        if (compact.matches("[0-9a-fA-F]{16,}")) {
            return compact.substring(0, 16).toLowerCase();
        }
        return value;
    }

    private static String newCorrelationId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String resolveUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    private static void putMdc(final String key, final String value) {
        if (value == null || value.isBlank()) {
            MDC.put(key, MDC_MISSING);
        } else {
            MDC.put(key, value);
        }
    }
}
