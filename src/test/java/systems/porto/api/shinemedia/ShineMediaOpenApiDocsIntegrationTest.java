package systems.porto.api.shinemedia;

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
    "porto.api.application=shine-media",
    "porto.api.home-directory=${user.dir}/src/test/resources/runtime-fixtures/shine-media",
    "porto.api.config-path=${user.dir}/src/test/resources/runtime-fixtures/shine-media/config",
    "porto.api.application-path=${user.dir}/src/test/resources/runtime-fixtures/shine-media/config/shine-media",
    "porto.api.plugins-path=${user.dir}/src/test/resources/runtime-fixtures/shine-media/plugins",
    "spring.datasource.url=jdbc:h2:mem:shine-media-openapi-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=shine-media",
    "spring.datasource.password=shine-media",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
class ShineMediaOpenApiDocsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void activeApplicationDocsEndpoint() throws Exception {
        mockMvc.perform(get("/api/docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.application", is("shine-media")))
            .andExpect(jsonPath("$.openApiJsonUrl", is("/v3/api-docs/shine-media")))
            .andExpect(jsonPath("$.swaggerUiUrl", is("/swagger-ui/index.html")));
    }

    @Test
    void swaggerUiConfigUsesSingleApplicationGroupUrl() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.urls[0].url", is("/v3/api-docs/shine-media")))
            .andExpect(jsonPath("$.urls[0].name", is("shine-media")));
    }

    @Test
    void openApiJsonForActiveApplication() throws Exception {
        mockMvc.perform(get("/v3/api-docs/shine-media"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title", is("Shine Media API")))
            .andExpect(jsonPath("$.paths['/cities']").exists())
            .andExpect(jsonPath("$.paths['/media-categories']").exists())
            .andExpect(jsonPath("$.paths['/auth/token']").exists())
            .andExpect(jsonPath("$.paths['/auth/password']").exists())
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme", is("bearer")))
            .andExpect(jsonPath("$.components.schemas.SalesCommission.properties.rateFullStack.type", is("number")))
            .andExpect(jsonPath("$.components.schemas.SalesCommissionPageResponse.properties.page.type", is("integer")))
            .andExpect(jsonPath("$.components.schemas.SalesCommissionPageResponse.properties.size.type", is("integer")))
            .andExpect(jsonPath("$.components.schemas.SalesCommissionPageResponse.properties.total.type", is("integer")));
    }

    @Test
    void applicationCatalogIncludesShineMedia() throws Exception {
        mockMvc.perform(get("/api/docs/applications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeApplication", is("shine-media")))
            .andExpect(jsonPath("$.applications[*].application", hasItem("shine-media")))
            .andExpect(jsonPath("$.applications.length()", is(1)));
    }
}
