package systems.porto.api.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springdoc.api.OpenApiResourceNotFoundException;
import systems.porto.api.plugin.PluginRuntime;

import java.util.Map;

@RestControllerAdvice
public class PluginExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PluginExceptionHandler.class);

    private static final String RESOURCE_NOT_FOUND = "systems.porto.api.spi.ResourceNotFoundException";
    private static final String CONFLICT = "systems.porto.api.spi.ConflictException";
    private static final String BAD_REQUEST = "systems.porto.api.spi.BadRequestException";
    private static final String UNAUTHORIZED = "systems.porto.api.auth.UnauthorizedException";
    private static final String FORBIDDEN = "systems.porto.api.auth.ForbiddenException";

    @ExceptionHandler(OpenApiResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOpenApiNotFound(final OpenApiResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("status", "not_found", "message", ex.getMessage()));
    }

    @ExceptionHandler(PluginRuntime.PluginValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(final PluginRuntime.PluginValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("status", "validation_failed", "errors", ex.getErrors()));
    }

    @ExceptionHandler(PluginRuntime.PluginNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFoundRoute(final PluginRuntime.PluginNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("status", "not_found", "message", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(final ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("status", "error", "message", message));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handlePluginRuntime(final RuntimeException ex) {
        if (RESOURCE_NOT_FOUND.equals(ex.getClass().getName())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", "not_found", "message", ex.getMessage()));
        }
        if (CONFLICT.equals(ex.getClass().getName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", "conflict", "message", ex.getMessage()));
        }
        if (BAD_REQUEST.equals(ex.getClass().getName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", "bad_request", "message", ex.getMessage()));
        }
        if (UNAUTHORIZED.equals(ex.getClass().getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "unauthorized", "message", ex.getMessage()));
        }
        if (FORBIDDEN.equals(ex.getClass().getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", "forbidden", "message", ex.getMessage()));
        }
        log.error("Unhandled plugin request error", ex);
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            message = message + ": " + ex.getCause().getMessage();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("status", "error", "message", message));
    }
}
