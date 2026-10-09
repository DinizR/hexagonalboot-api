package systems.porto.api.docs;

import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.stereotype.Component;
import systems.porto.api.config.PortoApiProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Component
public class ApplicationOpenApiLoader {

    public OpenAPI load(final PortoApiProperties properties) {
        return resolveSpecPath(properties)
            .map(path -> readSpec(path, properties))
            .orElseGet(() -> fallback(properties));
    }

    public boolean hasSpec(final Path applicationConfigDirectory, final String environment) {
        return resolveSpecPath(applicationConfigDirectory, environment).isPresent();
    }

    public Optional<Path> resolveSpecPath(final PortoApiProperties properties) {
        if (properties.getApplicationPath() == null || properties.getApplicationPath().isBlank()) {
            return Optional.empty();
        }
        return resolveSpecPath(Path.of(properties.getApplicationPath()), properties.getEnvironment());
    }

    private Optional<Path> resolveSpecPath(final Path applicationConfigDirectory, final String environment) {
        Path envSpecific = applicationConfigDirectory.resolve("openapi-" + environment + ".yaml");
        if (Files.isRegularFile(envSpecific)) {
            return Optional.of(envSpecific);
        }
        Path defaultSpec = applicationConfigDirectory.resolve("openapi.yaml");
        if (Files.isRegularFile(defaultSpec)) {
            return Optional.of(defaultSpec);
        }
        return Optional.empty();
    }

    private OpenAPI readSpec(final Path path, final PortoApiProperties properties) {
        // Must use swagger-core's Yaml mapper (not plain Jackson). Springdoc 2.8 serializes
        // OpenAPI 3.1 via Json31, which emits schema `types`; plain Jackson only fills legacy
        // `type`, so Swagger UI shows rates/page/size/total as strings.
        try (InputStream inputStream = Files.newInputStream(path)) {
            OpenAPI spec = Yaml.mapper().readValue(inputStream, OpenAPI.class);
            enrichInfo(spec, properties);
            return spec;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read OpenAPI spec from " + path, ex);
        }
    }

    private OpenAPI fallback(final PortoApiProperties properties) {
        return new OpenAPI()
            .info(new Info()
                .title(properties.getApplication() + " API")
                .version("1.0.0")
                .description("No openapi.yaml found under " + properties.getApplicationPath()));
    }

    private void enrichInfo(final OpenAPI spec, final PortoApiProperties properties) {
        if (spec.getInfo() == null) {
            spec.setInfo(new Info());
        }
        spec.getInfo().addExtension("x-porto-application", properties.getApplication());
    }
}
