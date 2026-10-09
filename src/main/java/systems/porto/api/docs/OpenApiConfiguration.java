package systems.porto.api.docs;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import systems.porto.api.config.PortoApiProperties;

@Configuration
public class OpenApiConfiguration {

    @Bean
    GroupedOpenApi activeApplicationGroupedOpenApi(
        final PortoApiProperties properties,
        final ApplicationOpenApiLoader loader
    ) {
        String application = properties.getApplication();
        OpenApiCustomizer copyFromFile = openApi -> mergeOpenApi(openApi, loader.load(properties));
        return GroupedOpenApi.builder()
            .group(application)
            .addOpenApiCustomizer(copyFromFile)
            .build();
    }

    @Bean
    ApplicationRunner swaggerUiApplicationUrlConfigurer(
        final SwaggerUiConfigProperties swaggerUiConfigProperties,
        final PortoApiProperties properties
    ) {
        return args -> {
            // Base path only; springdoc appends /{group} when building swagger-config urls.
            swaggerUiConfigProperties.setUrl("/v3/api-docs");
            swaggerUiConfigProperties.setUrlsPrimaryName(properties.getApplication());
            swaggerUiConfigProperties.setTagsSorter("alpha");
            swaggerUiConfigProperties.setOperationsSorter("alpha");
        };
    }

    private void mergeOpenApi(final OpenAPI target, final OpenAPI source) {
        target.setOpenapi(source.getOpenapi());
        target.setInfo(source.getInfo());
        target.setServers(source.getServers());
        target.setPaths(source.getPaths());
        target.setComponents(source.getComponents());
        target.setTags(source.getTags());
        target.setSecurity(source.getSecurity());
        target.setExternalDocs(source.getExternalDocs());
    }
}
