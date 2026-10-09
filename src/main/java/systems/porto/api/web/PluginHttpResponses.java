package systems.porto.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import systems.porto.api.config.PortoApiProperties;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Component
public class PluginHttpResponses {

    private final PortoApiProperties portoApiProperties;
    private final ObjectMapper objectMapper;

    public PluginHttpResponses(final PortoApiProperties portoApiProperties, final ObjectMapper objectMapper) {
        this.portoApiProperties = portoApiProperties;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<Object> created(final String resourceCollection, final Object body) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        extractId(body).ifPresent(id -> response.location(resourceUri(resourceCollection, id)));
        return response.body(body);
    }

    private URI resourceUri(final String resourceCollection, final int id) {
        String basePath = portoApiProperties.getApiBasePath();
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        return URI.create(basePath + "/" + resourceCollection + "/" + id);
    }

    private Optional<Integer> extractId(final Object body) {
        if (body == null) {
            return Optional.empty();
        }
        Map<?, ?> map = objectMapper.convertValue(body, Map.class);
        Object id = map.get("id");
        if (id instanceof Number number) {
            return Optional.of(number.intValue());
        }
        return Optional.empty();
    }
}
