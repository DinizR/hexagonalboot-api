package systems.porto.api.docs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import systems.porto.api.config.PortoApiProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/docs")
public class ApplicationDocsController {

    private final PortoApiProperties properties;
    private final ApplicationOpenApiLoader openApiLoader;

    public ApplicationDocsController(
        final PortoApiProperties properties,
        final ApplicationOpenApiLoader openApiLoader
    ) {
        this.properties = properties;
        this.openApiLoader = openApiLoader;
    }

    @GetMapping
    ApplicationDocsResponse activeApplicationDocs() {
        String application = properties.getApplication();
        String openApiUrl = "/v3/api-docs/" + application;
        return new ApplicationDocsResponse(
            application,
            openApiUrl,
            "/swagger-ui/index.html"
        );
    }

    @GetMapping("/applications")
    ApplicationCatalogResponse applicationCatalog() throws IOException {
        String activeApplication = properties.getApplication();
        Path configPath = Path.of(properties.getConfigPath());
        if (!Files.isDirectory(configPath)) {
            return new ApplicationCatalogResponse(activeApplication, List.of());
        }

        try (Stream<Path> directories = Files.list(configPath).filter(Files::isDirectory)) {
            List<ApplicationCatalogEntry> applications = directories
                .map(path -> toCatalogEntry(path, activeApplication))
                .filter(entry -> entry.documented())
                .sorted(Comparator.comparing(ApplicationCatalogEntry::application))
                .toList();
            return new ApplicationCatalogResponse(activeApplication, applications);
        }
    }

    @GetMapping("/applications/{application}")
    ApplicationDocsResponse applicationDocs(@PathVariable final String application) {
        if (!application.equals(properties.getApplication())) {
            throw new ResponseStatusException(
                NOT_FOUND,
                "Application '" + application + "' is not the running application ('"
                    + properties.getApplication() + "')"
            );
        }
        return activeApplicationDocs();
    }

    private ApplicationCatalogEntry toCatalogEntry(final Path applicationDirectory, final String activeApplication) {
        String application = applicationDirectory.getFileName().toString();
        boolean documented = openApiLoader.hasSpec(applicationDirectory, properties.getEnvironment());
        return new ApplicationCatalogEntry(
            application,
            application.equals(activeApplication),
            documented,
            documented ? "/v3/api-docs/" + application : null
        );
    }

    public record ApplicationDocsResponse(
        String application,
        String openApiJsonUrl,
        String swaggerUiUrl
    ) {
    }

    public record ApplicationCatalogEntry(
        String application,
        boolean active,
        boolean documented,
        String openApiJsonUrl
    ) {
    }

    public record ApplicationCatalogResponse(
        String activeApplication,
        List<ApplicationCatalogEntry> applications
    ) {
    }
}
