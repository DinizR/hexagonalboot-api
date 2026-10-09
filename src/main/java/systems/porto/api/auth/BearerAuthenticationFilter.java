package systems.porto.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import systems.porto.api.auth.AccessTokenService;
import systems.porto.api.auth.AuthenticatedPrincipal;
import systems.porto.api.auth.Identity;
import systems.porto.api.auth.IdentityProvider;
import systems.porto.api.auth.TokenValidationResult;
import systems.porto.api.config.PortoApiProperties;
import systems.porto.api.plugin.PluginRuntime;
import systems.porto.api.spi.HostContextConstants;
import systems.porto.api.spi.OperationLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Validates {@code Authorization: Bearer} using the loaded {@link IdentityProvider}
 * (local JWT or a future Keycloak plugin). Rejects protected routes when auth is required.
 */
public class BearerAuthenticationFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = "porto.authenticatedPrincipal";

    private final PortoApiProperties properties;
    private final PluginRuntime pluginRuntime;
    private final AccessTokenService accessTokenService;

    public BearerAuthenticationFilter(
        final PortoApiProperties properties,
        final PluginRuntime pluginRuntime,
        final AccessTokenService accessTokenService
    ) {
        this.properties = properties;
        this.pluginRuntime = pluginRuntime;
        this.accessTokenService = accessTokenService;
    }

    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> rawToken = accessTokenService.extractBearer(request.getHeader("Authorization"));
        if (rawToken.isPresent()) {
            TokenValidationResult result = validate(rawToken.get());
            if (!result.valid()) {
                if (requiresToken(request)) {
                    unauthorized(response, result.failureReason());
                    return;
                }
            } else {
                bindPrincipal(request, result.identity());
                if (result.identity().passwordExpired() && requiresToken(request) && !isPasswordChange(request)) {
                    forbidden(response, "Password has expired; change it at POST /auth/password");
                    return;
                }
            }
        } else if (requiresToken(request)) {
            unauthorized(response, "missing_token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private TokenValidationResult validate(final String rawToken) {
        return findIdentityProvider()
            .map(provider -> provider.validateAccessToken(rawToken))
            .orElseGet(() -> accessTokenService.validate(rawToken));
    }

    private Optional<IdentityProvider> findIdentityProvider() {
        return Optional.ofNullable(pluginRuntime.getApiContext())
            .flatMap(context -> context.getVariable(HostContextConstants.CONTEXT_IDENTITY_PROVIDER))
            .filter(IdentityProvider.class::isInstance)
            .map(IdentityProvider.class::cast);
    }

    private boolean requiresToken(final HttpServletRequest request) {
        if (!properties.getAuth().isRequired()) {
            return false;
        }
        return !isPublic(request);
    }

    private boolean isPublic(final HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        String apiBase = properties.getApiBasePath();
        return path.equals("/error")
            || path.equals("/actuator/health")
            || path.equals("/actuator/info")
            || path.equals("/api/docs")
            || path.startsWith("/api/docs/")
            || path.startsWith("/swagger-ui")
            || path.equals("/v3/api-docs")
            || path.startsWith("/v3/api-docs/")
            || path.equals(apiBase + "/health")
            || path.equals(apiBase + "/auth/token")
            || path.startsWith("/h2-console");
    }

    private boolean isPasswordChange(final HttpServletRequest request) {
        String path = request.getRequestURI();
        return "POST".equalsIgnoreCase(request.getMethod())
            && path != null
            && path.equals(properties.getApiBasePath() + "/auth/password");
    }

    private void bindPrincipal(final HttpServletRequest request, final Identity identity) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(identity, issuer(), Instant.now());
        request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        List<SimpleGrantedAuthority> authorities = identity.profiles().stream()
            .map(profile -> new SimpleGrantedAuthority("ROLE_" + profile))
            .toList();
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(identity.username(), "n/a", authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        OperationLog.bindUser(identity.username());
    }

    private String issuer() {
        return properties.getAuth().getJwt().getIssuer();
    }

    private static void unauthorized(final HttpServletResponse response, final String reason) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized",
            reason == null || reason.isBlank() ? "Authentication is required" : reason);
    }

    private static void forbidden(final HttpServletResponse response, final String reason) throws IOException {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden",
            reason == null || reason.isBlank() ? "Forbidden" : reason);
    }

    private static void writeError(
        final HttpServletResponse response,
        final int status,
        final String code,
        final String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":\"" + code + "\",\"message\":\"" + escape(message) + "\"}");
    }

    private static String escape(final String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
