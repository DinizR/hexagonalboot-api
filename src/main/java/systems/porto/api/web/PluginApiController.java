package systems.porto.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import systems.porto.api.auth.AuthenticatedPrincipal;
import systems.porto.api.auth.BearerAuthenticationFilter;
import systems.porto.api.http.BinaryHttpResponse;
import systems.porto.api.plugin.PluginRuntime;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Generic HTTP front for plugin-registered routes under {@code porto.api.api-base-path}.
 * Each entity REST entry adapter registers its own paths at {@code start()}.
 */
@RestController
@RequestMapping("${porto.api.api-base-path:/api/v1.0.0}")
@Hidden
public class PluginApiController {

    private final PluginRuntime pluginRuntime;
    private final PluginHttpResponses pluginHttpResponses;
    private final ObjectMapper objectMapper;

    public PluginApiController(
        final PluginRuntime pluginRuntime,
        final PluginHttpResponses pluginHttpResponses,
        final ObjectMapper objectMapper
    ) {
        this.pluginRuntime = pluginRuntime;
        this.pluginHttpResponses = pluginHttpResponses;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializePlugins() {
        pluginRuntime.initialize();
    }

    @RequestMapping(
        value = "/**",
        method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}
    )
    public ResponseEntity<?> handle(final HttpServletRequest request) throws IOException {
        byte[] rawBody = request.getInputStream().readAllBytes();
        String method = request.getMethod();
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) request.getAttribute(
            BearerAuthenticationFilter.PRINCIPAL_ATTRIBUTE
        );
        Object response = pluginRuntime.dispatch(
            method,
            request.getRequestURI(),
            queryParams(request),
            parseJsonBody(request.getContentType(), rawBody),
            requestHeaders(request),
            rawBody,
            principal
        );
        if (response instanceof BinaryHttpResponse binary) {
            return binaryResponse(binary);
        }
        if ("POST".equalsIgnoreCase(method) && !isAuthJsonPost(request.getRequestURI())) {
            String resource = resourceSegment(request.getRequestURI());
            return pluginHttpResponses.created(resource, response);
        }
        return ResponseEntity.ok(response);
    }

    private static ResponseEntity<byte[]> binaryResponse(final BinaryHttpResponse binary) {
        HttpHeaders headers = new HttpHeaders();
        String contentType = binary.contentType() != null && !binary.contentType().isBlank()
            ? binary.contentType()
            : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        headers.setContentType(MediaType.parseMediaType(contentType));
        if (binary.fileName() != null && !binary.fileName().isBlank()) {
            headers.setContentDisposition(ContentDisposition.attachment().filename(binary.fileName()).build());
        }
        return new ResponseEntity<>(binary.body() != null ? binary.body() : new byte[0], headers, HttpStatus.OK);
    }

    private Object parseJsonBody(final String contentType, final byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0) {
            return null;
        }
        String type = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        if (type.contains("xml")) {
            return null;
        }
        if (!type.contains("json") && !type.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, Map.class);
        } catch (IOException e) {
            if (type.contains("json")) {
                throw new IllegalArgumentException("Request body is not valid JSON", e);
            }
            return null;
        }
    }

    private static Map<String, String> requestHeaders(final HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> {
            String value = request.getHeader(name);
            if (name != null && value != null) {
                headers.put(name, value);
            }
        });
        return headers;
    }

    private static Map<String, String> queryParams(final HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0 && values[0] != null) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    private static boolean isAuthJsonPost(final String requestUri) {
        String path = requestUri == null ? "" : requestUri;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        return path.endsWith("/auth/token") || path.endsWith("/auth/password");
    }

    private static String resourceSegment(final String requestUri) {
        String path = requestUri == null ? "" : requestUri;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) {
            return "resource";
        }
        return path.substring(slash + 1);
    }
}
