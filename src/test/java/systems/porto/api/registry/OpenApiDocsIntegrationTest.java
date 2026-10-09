package systems.porto.api.registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "porto.api.application=registry",
    "porto.api.home-directory=${user.dir}/src/test/resources/runtime-fixtures/registry",
    "porto.api.config-path=${user.dir}/src/test/resources/runtime-fixtures/registry/config",
    "porto.api.application-path=${user.dir}/src/test/resources/runtime-fixtures/registry/config/registry",
    "porto.api.plugins-path=${user.dir}/src/test/resources/runtime-fixtures/registry/plugins",
    "spring.datasource.url=jdbc:h2:mem:registry-openapi-test;DB_CLOSE_DELAY=-1"
})
class OpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void activeApplicationDocsEndpoint() throws Exception {
        mockMvc.perform(get("/api/docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application", is("registry")))
            .andExpect(jsonPath("$.openApiJsonUrl", is("/v3/api-docs/registry")))
            .andExpect(jsonPath("$.swaggerUiUrl", is("/swagger-ui/index.html")));
    }

    @Test
    void swaggerUiConfigUsesSingleApplicationGroupUrl() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.urls[0].url", is("/v3/api-docs/registry")))
            .andExpect(jsonPath("$.urls[0].name", is("registry")));
    }

    @Test
    void openApiJsonForActiveApplication() throws Exception {
        mockMvc.perform(get("/v3/api-docs/registry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title", is("Registry API")))
            .andExpect(jsonPath("$.paths['/products']").exists())
            .andExpect(jsonPath("$.components.schemas.Product.properties.id.type", is("integer")))
            .andExpect(jsonPath("$.components.schemas.ProductPageResponse.properties.page.type", is("integer")))
            .andExpect(jsonPath("$.components.schemas.ProductPageResponse.properties.size.type", is("integer")))
            .andExpect(jsonPath("$.components.schemas.ProductPageResponse.properties.total.type", is("integer")));
    }

    @Test
    void openApiJsonForWrongApplicationReturnsNotFound() throws Exception {
        mockMvc.perform(get("/v3/api-docs/pos"))
            .andExpect(status().isNotFound());
    }

    @Test
    void applicationCatalogListsDocumentedApps() throws Exception {
        mockMvc.perform(get("/api/docs/applications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeApplication", is("registry")))
            .andExpect(jsonPath("$.applications[*].application", hasItem("registry")))
            .andExpect(jsonPath("$.applications.length()", is(1)));
    }

    @Test
    void nonActiveApplicationDocsReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/docs/applications/pos"))
            .andExpect(status().isNotFound());
    }
}
